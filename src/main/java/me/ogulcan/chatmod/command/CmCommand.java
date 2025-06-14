package me.ogulcan.chatmod.command;

import me.ogulcan.chatmod.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CmCommand implements CommandExecutor {
    private final Main plugin;

    public CmCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(plugin.getMessages().get("commands-title"));
        sender.sendMessage(plugin.getMessages().get("command-cmute"));
        sender.sendMessage(plugin.getMessages().get("command-cunmute"));
        sender.sendMessage(plugin.getMessages().get("command-cstatus"));
        return true;
    }
}
