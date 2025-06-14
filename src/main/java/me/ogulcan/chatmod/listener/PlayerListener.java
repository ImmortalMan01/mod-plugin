package me.ogulcan.chatmod.listener;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final Main plugin;
    private final PunishmentStore store;

    public PlayerListener(Main plugin, PunishmentStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("countdown-offline", true)) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (store.isMuted(uuid)) {
                store.pause(uuid);
                plugin.cancelUnmute(uuid);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!plugin.getConfig().getBoolean("countdown-offline", true)) {
            if (store.resume(uuid)) {
                long delay = store.remaining(uuid) / 1000 * 20L;
                plugin.scheduleUnmute(uuid, delay);
            }
        } else if (store.isMuted(uuid)) {
            long delay = store.remaining(uuid) / 1000 * 20L;
            plugin.scheduleUnmute(uuid, delay);
        }
    }
}
