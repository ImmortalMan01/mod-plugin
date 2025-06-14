package me.ogulcan.chatmod.task;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class UnmuteTask extends BukkitRunnable {
    private final Main plugin;
    private final UUID uuid;
    private final PunishmentStore store;

    public UnmuteTask(Main plugin, UUID uuid, PunishmentStore store) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.store = store;
    }

    @Override
    public void run() {
        store.unmute(uuid);
        plugin.cancelUnmute(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(plugin.getMessages().get("mute-expired"));
        }
    }
}
