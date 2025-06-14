package me.ogulcan.chatmod.command;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CStatusCommand implements CommandExecutor {
    private final PunishmentStore store;
    private final Main plugin;

    public CStatusCommand(PunishmentStore store, Main plugin) {
        this.store = store;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().get("player-not-found"));
            return true;
        }
        if (store.isMuted(target.getUniqueId())) {
            long rem = store.remaining(target.getUniqueId()) / 1000;
            sender.sendMessage(plugin.getMessages().get("remaining-mute", target.getName(), rem/60, rem%60));
        } else {
            sender.sendMessage(plugin.getMessages().get("not-muted", target.getName()));
        }
        return true;
    }
}
