package me.ogulcan.chatmod.command;

import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class CUnmuteCommand implements CommandExecutor {
    private final PunishmentStore store;
    private final Plugin plugin;

    public CUnmuteCommand(PunishmentStore store, Plugin plugin) {
        this.store = store;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        store.unmute(target.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "Unmuted " + target.getName() + ".");
        return true;
    }
}
