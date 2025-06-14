package me.ogulcan.chatmod.task;

import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class UnmuteTask extends BukkitRunnable {
    private final Plugin plugin;
    private final UUID uuid;
    private final PunishmentStore store;

    public UnmuteTask(Plugin plugin, UUID uuid, PunishmentStore store) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.store = store;
    }

    @Override
    public void run() {
        store.unmute(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage("§aYour mute has expired.");
        }
    }
}
