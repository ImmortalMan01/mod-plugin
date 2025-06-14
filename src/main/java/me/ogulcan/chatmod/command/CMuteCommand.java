package me.ogulcan.chatmod.command;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.task.UnmuteTask;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CMuteCommand implements CommandExecutor {
    private final PunishmentStore store;
    private final Main plugin;
    public CMuteCommand(PunishmentStore store, Main plugin) {
        this.store = store;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) return false;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().get("player-not-found"));
            return true;
        }
        long minutes;
        try {
            minutes = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessages().get("invalid-minutes"));
            return true;
        }
        store.mute(target.getUniqueId(), minutes);
        new UnmuteTask(plugin, target.getUniqueId(), store).runTaskLater(plugin, minutes * 60L * 20L);
        sender.sendMessage(plugin.getMessages().get("muted", target.getName(), minutes));
        return true;
    }
}
