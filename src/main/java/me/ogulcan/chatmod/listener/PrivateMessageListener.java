package me.ogulcan.chatmod.listener;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;
import java.util.UUID;

public class PrivateMessageListener implements Listener {
    private final Main plugin;
    private final PunishmentStore store;
    private final Set<String> commands = Set.of(
            "msg", "tell", "w", "whisper", "m", "pm", "r", "reply"
    );

    public PrivateMessageListener(Main plugin, PunishmentStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon != -1) label = label.substring(colon + 1);
        if (!commands.contains(label)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (store.isMuted(uuid)) {
            long rem = store.remaining(uuid) / 1000;
            player.sendMessage(plugin.getMessages().get("still-muted", format(rem)));
            event.setCancelled(true);
        }
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
