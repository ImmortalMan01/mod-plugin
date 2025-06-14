package me.ogulcan.chatmod.listener;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.service.ModerationService;
import me.ogulcan.chatmod.service.WordFilter;
import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class ChatListener implements Listener {
    private final Main plugin;
    private final ModerationService service;
    private final PunishmentStore store;
    private final List<String> categories;
    private final List<String> words;
    private final boolean useBlockedWords;
    private final boolean useBlockedCategories;
    private final Map<String, Boolean> categoryEnabled;
    private final Map<String, Double> categoryRatio;

    public ChatListener(Main plugin, ModerationService service, PunishmentStore store) {
        this.plugin = plugin;
        this.service = service;
        this.store = store;
        this.categories = plugin.getConfig().getStringList("blocked-categories");
        this.words = plugin.getConfig().getStringList("blocked-words");
        this.useBlockedWords = plugin.getConfig().getBoolean("use-blocked-words", true);
        this.useBlockedCategories = plugin.getConfig().getBoolean("use-blocked-categories", true);
        this.categoryEnabled = new HashMap<>();
        this.categoryRatio = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection cs = plugin.getConfig().getConfigurationSection("category-settings");
        double def = plugin.getConfig().getDouble("threshold", 0.5);
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                categoryEnabled.put(key, cs.getBoolean(key + ".enabled", true));
                categoryRatio.put(key, cs.getDouble(key + ".ratio", def));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.isAutoMute()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (store.isMuted(uuid)) {
            long rem = store.remaining(uuid) / 1000;
            player.sendMessage(plugin.getMessages().get("still-muted", format(rem)));
            event.setCancelled(true);
            return;
        }
        String message = event.getMessage();
        if (useBlockedWords && WordFilter.containsBlockedWord(message, words)) {
            Bukkit.getScheduler().runTask(plugin, () -> applyPunishment(player));
            return;
        }
        service.moderate(message).thenAccept(result -> {
            boolean shouldMute = result.blocked;
            if (useBlockedCategories) {
                for (Map.Entry<String, Double> e : result.scores.entrySet()) {
                    String cat = e.getKey();
                    double score = e.getValue();
                    boolean enabled = categoryEnabled.getOrDefault(cat, categories.contains(cat));
                    if (!enabled) continue;
                    double ratio = categoryRatio.getOrDefault(cat, plugin.getConfig().getDouble("threshold", 0.5));
                    if (score >= ratio) {
                        shouldMute = true;
                        break;
                    }
                }
            }
            if (shouldMute) {
                Bukkit.getScheduler().runTask(plugin, () -> applyPunishment(player));
            }
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
            minutes = plugin.getConfig().getLong("punishments.third", 60);
        } else {
            minutes = plugin.getConfig().getLong("punishments.fourth", 180);
            String msg = plugin.getMessages().get("repeated-offence", player.getName());
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("chatmoderation.notify"))
                    .forEach(p -> p.sendMessage(msg));
        }
        store.mute(uuid, minutes);
        plugin.scheduleUnmute(uuid, minutes * 60L * 20L);
        player.sendMessage(plugin.getMessages().get("muted-player", minutes));

        // Broadcast mute information to all players
        int offences = store.offenceCount(uuid, Duration.ofHours(24).toMillis());
        long remaining = store.remaining(uuid) / 1000;
        String broadcast = plugin.getMessages().get(
                "mute-broadcast",
                player.getName(),
                offences,
                remaining / 60,
                remaining % 60
        );
        Bukkit.broadcastMessage(broadcast);
    }

    private String format(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        String lang = plugin.getConfig().getString("language", "en");
        if ("tr".equalsIgnoreCase(lang)) {
            return min + " dakika " + sec + " saniye";
        }
        return min + "m " + sec + "s";
    }
}
