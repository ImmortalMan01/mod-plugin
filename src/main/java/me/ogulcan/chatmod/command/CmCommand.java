package me.ogulcan.chatmod.command;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.gui.DashboardGUI;
import me.ogulcan.chatmod.gui.LogsGUI;
import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class CmCommand implements CommandExecutor {
    private final Main plugin;
    private final PunishmentStore store;

    public CmCommand(Main plugin, PunishmentStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMessages().get("commands-title"));
            sender.sendMessage(plugin.getMessages().get("command-mute"));
            sender.sendMessage(plugin.getMessages().get("command-unmute"));
            sender.sendMessage(plugin.getMessages().get("command-status"));
            sender.sendMessage(plugin.getMessages().get("command-reload"));
            sender.sendMessage(plugin.getMessages().get("command-gui"));
            sender.sendMessage(plugin.getMessages().get("command-logs"));
            sender.sendMessage(plugin.getMessages().get("command-clearlogs"));
            return true;
        }
        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "mute":
                return mute(sender, subArgs);
            case "unmute":
                return unmute(sender, subArgs);
            case "status":
                return status(sender, subArgs);
            case "reload":
                return reload(sender);
            case "gui":
                return gui(sender);
            case "logs":
                return logs(sender);
            case "clearlogs":
                return clearLogs(sender);
            default:
                return false;
        }
    }

    private boolean mute(CommandSender sender, String[] args) {
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
        plugin.scheduleUnmute(target.getUniqueId(), minutes * 60L * 20L);
        sender.sendMessage(plugin.getMessages().get("muted", target.getName(), minutes));
        return true;
    }

    private boolean unmute(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().get("player-not-found"));
            return true;
        }
        store.unmute(target.getUniqueId());
        plugin.cancelUnmute(target.getUniqueId());
        sender.sendMessage(plugin.getMessages().get("unmuted", target.getName()));
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().get("player-not-found"));
            return true;
        }
        if (store.isMuted(target.getUniqueId())) {
            long rem = store.remaining(target.getUniqueId()) / 1000;
            sender.sendMessage(plugin.getMessages().get("remaining-mute", target.getName(), rem / 60, rem % 60));
        } else {
            sender.sendMessage(plugin.getMessages().get("not-muted", target.getName()));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(plugin.getMessages().get("reloaded"));
        return true;
    }

    private boolean gui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("only-players"));
            return true;
        }
        if (!player.hasPermission("chatmoderation.gui")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }
        new DashboardGUI(plugin, store, plugin.getGuiConfig(), player).open();
        return true;
    }

    private boolean logs(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("only-players"));
            return true;
        }
        if (!player.hasPermission("chatmoderation.logs")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }
        new LogsGUI(plugin, plugin.getLogStore(), player);
        return true;
    }

    private boolean clearLogs(CommandSender sender) {
        plugin.getLogStore().clear();
        sender.sendMessage(plugin.getMessages().get("logs-cleared"));
        return true;
    }
}
