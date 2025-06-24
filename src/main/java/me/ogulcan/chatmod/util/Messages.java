package me.ogulcan.chatmod.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.text.MessageFormat;

public class Messages {
    private final FileConfiguration config;
    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin, String lang) {
        this.plugin = plugin;
        String fileName = "messages_" + lang + ".yml";
        File dataFile = new File(plugin.getDataFolder(), fileName);
        if (!dataFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        this.config = YamlConfiguration.loadConfiguration(dataFile);

        try (InputStreamReader reader = new InputStreamReader(plugin.getResource(fileName))) {
            if (reader != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
                this.config.setDefaults(def);
            }
        } catch (Exception ignored) {}
    }

    public String get(String key, Object... args) {
        String msg = config.getString(key, key);
        if (args.length > 0) {
            msg = MessageFormat.format(msg, args);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * Return the translated message prefixed with the configured value.
     */
    public String prefixed(String key, Object... args) {
        String msg = get(key, args);
        String pfx = plugin.getConfig().getString("prefix", "");
        if (pfx != null && !pfx.isBlank()) {
            msg = ChatColor.translateAlternateColorCodes('&', pfx) + msg;
        }
        return msg;
    }
}
