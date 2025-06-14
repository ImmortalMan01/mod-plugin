package me.ogulcan.chatmod.listener;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.service.ModerationService;
import me.ogulcan.chatmod.service.WordFilter;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.task.UnmuteTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class ChatListener implements Listener {
    private final Main plugin;
    private final ModerationService service;
    private final PunishmentStore store;
    private final List<String> categories;
    private final List<String> words;
    private final boolean useBlockedWords;

    public ChatListener(Main plugin, ModerationService service, PunishmentStore store) {
        this.plugin = plugin;
        this.service = service;
        this.store = store;
        this.categories = plugin.getConfig().getStringList("blocked-categories");
        this.words = plugin.getConfig().getStringList("blocked-words");
        this.useBlockedWords = plugin.getConfig().getBoolean("use-blocked-words", true);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (store.isMuted(uuid)) {
            long rem = store.remaining(uuid) / 1000;
            player.sendMessage(ChatColor.RED + "You are muted for " + format(rem) + ".");
            event.setCancelled(true);
            return;
        }
        String message = event.getMessage();
        if (useBlockedWords && WordFilter.containsBlockedWord(message, words)) {
            Bukkit.getScheduler().runTask(plugin, () -> applyPunishment(player));
            return;
        }
        service.moderate(message).thenAccept(result -> {
            if (!result.triggered) return;
            boolean categoryMatch = result.scores.keySet().stream().anyMatch(categories::contains);
            if (!categoryMatch && !result.blocked) return;
            Bukkit.getScheduler().runTask(plugin, () -> applyPunishment(player));
        });
    }

    private void applyPunishment(Player player) {
        UUID uuid = player.getUniqueId();
        int offences24h = store.offenceCount(uuid, Duration.ofHours(24).toMillis());
        long minutes;
        if (offences24h == 0) {
            minutes = plugin.getConfig().getLong("punishments.first", 5);
        } else if (offences24h == 1) {
            minutes = plugin.getConfig().getLong("punishments.second", 30);
        } else if (offences24h == 2) {
            minutes = plugin.getConfig().getLong("punishments.third", 1440);
        } else {
            minutes = plugin.getConfig().getLong("punishments.fourth", 10080);
            String msg = ChatColor.RED + player.getName() + " has been muted for repeated offences.";
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("chatmoderation.notify"))
                    .forEach(p -> p.sendMessage(msg));
        }
        store.mute(uuid, minutes);
        new UnmuteTask(plugin, uuid, store).runTaskLater(plugin, minutes * 60L * 20L);
        player.sendMessage(ChatColor.RED + "You have been muted for " + minutes + " minutes.");
    }

    private String format(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        return min + "m" + sec + "s";
    }
}
