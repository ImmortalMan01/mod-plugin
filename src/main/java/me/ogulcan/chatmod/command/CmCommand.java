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
            sender.sendMessage(plugin.getMessages().prefixed("commands-title"));
            sender.sendMessage(plugin.getMessages().prefixed("command-mute"));
            sender.sendMessage(plugin.getMessages().prefixed("command-unmute"));
            sender.sendMessage(plugin.getMessages().prefixed("command-status"));
            sender.sendMessage(plugin.getMessages().prefixed("command-reload"));
            sender.sendMessage(plugin.getMessages().prefixed("command-gui"));
            sender.sendMessage(plugin.getMessages().prefixed("command-logs"));
            sender.sendMessage(plugin.getMessages().prefixed("command-clearlogs"));
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
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessages().prefixed("usage-mute"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().prefixed("player-not-found"));
            return true;
        }
        long minutes;
        try {
            minutes = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessages().prefixed("invalid-minutes"));
            return true;
        }
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        store.mute(target.getUniqueId(), minutes);
        String actor = sender.getName();
        String r = reason.isBlank() ? "manual" : reason;
        plugin.getLogStore().add(target.getUniqueId(), target.getName(), r, "game", actor, minutes);
        plugin.getNotifier().notifyMute(target.getName(), r, minutes, actor, "game", System.currentTimeMillis());
        plugin.scheduleUnmute(target.getUniqueId(), minutes * 60L * 20L);
        sender.sendMessage(plugin.getMessages().prefixed("muted", target.getName(), minutes));
        return true;
    }

    private boolean unmute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessages().prefixed("usage-unmute"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().prefixed("player-not-found"));
            return true;
        }
        store.unmute(target.getUniqueId());
        plugin.cancelUnmute(target.getUniqueId());
        sender.sendMessage(plugin.getMessages().prefixed("unmuted", target.getName()));
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessages().prefixed("usage-status"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().prefixed("player-not-found"));
            return true;
        }
        if (store.isMuted(target.getUniqueId())) {
            long rem = store.remaining(target.getUniqueId()) / 1000;
            sender.sendMessage(plugin.getMessages().prefixed("remaining-mute", target.getName(), rem / 60, rem % 60));
        } else {
            sender.sendMessage(plugin.getMessages().prefixed("not-muted", target.getName()));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(plugin.getMessages().prefixed("reloaded"));
        return true;
    }

    private boolean gui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().prefixed("only-players"));
            return true;
        }
        if (!player.hasPermission("chatmoderation.gui")) {
            sender.sendMessage(plugin.getMessages().prefixed("no-permission"));
            return true;
        }
        new DashboardGUI(plugin, store, plugin.getGuiConfig(), player).open();
        return true;
    }

    private boolean logs(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().prefixed("only-players"));
            return true;
        }
        if (!player.hasPermission("chatmoderation.logs")) {
            sender.sendMessage(plugin.getMessages().prefixed("no-permission"));
            return true;
        }
        new LogsGUI(plugin, plugin.getLogStore(), player);
        return true;
    }

    private boolean clearLogs(CommandSender sender) {
        plugin.getLogStore().clear();
        sender.sendMessage(plugin.getMessages().prefixed("logs-cleared"));
        return true;
    }
}
