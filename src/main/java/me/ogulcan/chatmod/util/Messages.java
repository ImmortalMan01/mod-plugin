package me.ogulcan.chatmod.util;

import org.bukkit.ChatColor;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class Messages {
    private final ResourceBundle bundle;

    public Messages(String lang) {
        Locale locale = Locale.forLanguageTag(lang);
        this.bundle = ResourceBundle.getBundle("messages", locale);
    }

    public String get(String key, Object... args) {
        String msg = bundle.getString(key);
        if (args.length > 0) {
            msg = MessageFormat.format(msg, args);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
