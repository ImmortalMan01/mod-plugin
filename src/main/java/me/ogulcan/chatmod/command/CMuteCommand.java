package me.ogulcan.chatmod.command;

import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.task.UnmuteTask;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class CMuteCommand implements CommandExecutor {
    private final PunishmentStore store;
    private final Plugin plugin;
    public CMuteCommand(PunishmentStore store, Plugin plugin) {
        this.store = store;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) return false;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        long minutes;
        try {
            minutes = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid minutes.");
            return true;
        }
        store.mute(target.getUniqueId(), minutes);
        new UnmuteTask(plugin, target.getUniqueId(), store).runTaskLater(plugin, minutes * 60L * 20L);
        sender.sendMessage(ChatColor.GREEN + "Muted " + target.getName() + " for " + minutes + " minutes.");
        return true;
    }
}
