package me.ogulcan.chatmod;

import me.ogulcan.chatmod.command.CMuteCommand;
import me.ogulcan.chatmod.command.CStatusCommand;
import me.ogulcan.chatmod.command.CUnmuteCommand;
import me.ogulcan.chatmod.listener.ChatListener;
import me.ogulcan.chatmod.service.ModerationService;
import me.ogulcan.chatmod.storage.PunishmentStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {
    private ModerationService moderationService;
    private PunishmentStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String apiKey = getConfig().getString("openai-key", "");
        double threshold = getConfig().getDouble("threshold", 0.5);
        int rateLimit = getConfig().getInt("rate-limit", 60);
        this.moderationService = new ModerationService(apiKey, threshold, rateLimit, this.getLogger());
        this.store = new PunishmentStore(new File(getDataFolder(), "data/punishments.json"));
        getServer().getPluginManager().registerEvents(new ChatListener(this, moderationService, store), this);

        getCommand("cmute").setExecutor(new CMuteCommand(store, this));
        getCommand("cunmute").setExecutor(new CUnmuteCommand(store, this));
        getCommand("cstatus").setExecutor(new CStatusCommand(store));
    }

    public PunishmentStore getStore() {
        return store;
    }
}
