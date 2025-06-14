package me.ogulcan.chatmod;

import me.ogulcan.chatmod.command.CmCommand;
import me.ogulcan.chatmod.listener.ChatListener;
import me.ogulcan.chatmod.listener.PlayerListener;
import me.ogulcan.chatmod.listener.PrivateMessageListener;
import me.ogulcan.chatmod.service.ModerationService;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.util.Messages;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.HandlerList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {
    private ModerationService moderationService;
    private PunishmentStore store;
    private Messages messages;
    private FileConfiguration guiConfig;
    private boolean autoMute = true;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File guiFile = new File(getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            saveResource("gui.yml", false);
        }
        this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(getResource("gui.yml"))) {
            if (reader != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
                this.guiConfig.setDefaults(def);
            }
        } catch (Exception ignored) {}
        String lang = getConfig().getString("language", "en");
        this.messages = new Messages(this, lang);
        String apiKey = getConfig().getString("openai-key", "");
        double threshold = getConfig().getDouble("threshold", 0.5);
        int rateLimit = getConfig().getInt("rate-limit", 60);
        String model = getConfig().getString("model", "omni-moderation-latest");
        boolean debug = getConfig().getBoolean("debug", false);
        if (debug) {
            getLogger().info("Debug mode enabled");
        }
        this.moderationService = new ModerationService(apiKey, model, threshold, rateLimit, this.getLogger(), debug);
        this.store = new PunishmentStore(new File(getDataFolder(), "data/punishments.json"));
        getServer().getPluginManager().registerEvents(new ChatListener(this, moderationService, store), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, store), this);
        getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, store), this);

        getCommand("cm").setExecutor(new CmCommand(this, store));
    }

    public PunishmentStore getStore() {
        return store;
    }

    public Messages getMessages() {
        return messages;
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public void reloadFiles() {
        reloadAll();
    }

    /**
     * Reload configuration, language and GUI files and re-register listeners
     * with a freshly created {@link ModerationService} instance.
     */
    public void reloadAll() {
        reloadConfig();
        String lang = getConfig().getString("language", "en");
        this.messages = new Messages(this, lang);
        File guiFile = new File(getDataFolder(), "gui.yml");
        this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(getResource("gui.yml"))) {
            if (reader != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
                this.guiConfig.setDefaults(def);
            }
        } catch (Exception ignored) {}

        String apiKey = getConfig().getString("openai-key", "");
        double threshold = getConfig().getDouble("threshold", 0.5);
        int rateLimit = getConfig().getInt("rate-limit", 60);
        String model = getConfig().getString("model", "omni-moderation-latest");
        boolean debug = getConfig().getBoolean("debug", false);
        this.moderationService = new ModerationService(apiKey, model, threshold, rateLimit, this.getLogger(), debug);

        HandlerList.unregisterAll(this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, moderationService, store), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, store), this);
        getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, store), this);
    }

    public boolean isAutoMute() {
        return autoMute;
    }

    public void setAutoMute(boolean autoMute) {
        this.autoMute = autoMute;
    }

    public void scheduleUnmute(UUID uuid, long delayTicks) {
        BukkitTask existing = tasks.remove(uuid);
        if (existing != null) existing.cancel();
        BukkitTask task = new me.ogulcan.chatmod.task.UnmuteTask(this, uuid, store)
                .runTaskLater(this, delayTicks);
        tasks.put(uuid, task);
    }

    public void cancelUnmute(UUID uuid) {
        BukkitTask task = tasks.remove(uuid);
        if (task != null) task.cancel();
    }
}
