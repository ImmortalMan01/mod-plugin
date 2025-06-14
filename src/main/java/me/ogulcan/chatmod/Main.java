package me.ogulcan.chatmod;

import me.ogulcan.chatmod.command.CmCommand;
import me.ogulcan.chatmod.listener.ChatListener;
import me.ogulcan.chatmod.service.ModerationService;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {
    private ModerationService moderationService;
    private PunishmentStore store;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String lang = getConfig().getString("language", "en");
        this.messages = new Messages(this, lang);
        String apiKey = getConfig().getString("openai-key", "");
        double threshold = getConfig().getDouble("threshold", 0.5);
        int rateLimit = getConfig().getInt("rate-limit", 60);
        this.moderationService = new ModerationService(apiKey, threshold, rateLimit, this.getLogger());
        this.store = new PunishmentStore(new File(getDataFolder(), "data/punishments.json"));
        getServer().getPluginManager().registerEvents(new ChatListener(this, moderationService, store), this);

        getCommand("cm").setExecutor(new CmCommand(this, store));
    }

    public PunishmentStore getStore() {
        return store;
    }

    public Messages getMessages() {
        return messages;
    }

    public void reloadFiles() {
        reloadConfig();
        String lang = getConfig().getString("language", "en");
        this.messages = new Messages(this, lang);
    }
}
