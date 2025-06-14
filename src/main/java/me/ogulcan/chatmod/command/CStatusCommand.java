package me.ogulcan.chatmod.command;

import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CStatusCommand implements CommandExecutor {
    private final PunishmentStore store;

    public CStatusCommand(PunishmentStore store) {
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        if (store.isMuted(target.getUniqueId())) {
            long rem = store.remaining(target.getUniqueId()) / 1000;
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " remaining mute: " + (rem/60) + "m" + (rem%60) + "s");
        } else {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " is not muted.");
        }
        return true;
    }
}
