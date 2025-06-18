package me.ogulcan.chatmod;

import me.ogulcan.chatmod.command.CmCommand;
import me.ogulcan.chatmod.listener.ChatListener;
import me.ogulcan.chatmod.listener.PlayerListener;
import me.ogulcan.chatmod.listener.PrivateMessageListener;
import me.ogulcan.chatmod.service.ModerationService;
import me.ogulcan.chatmod.service.DiscordNotifier;
import me.ogulcan.chatmod.web.UnmuteServer;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.storage.LogStore;
import me.ogulcan.chatmod.util.Messages;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.HandlerList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import okhttp3.OkHttpClient;
import okhttp3.Dispatcher;

public class Main extends JavaPlugin {
    private ModerationService moderationService;
    private PunishmentStore store;
    private LogStore logStore;
    private DiscordNotifier notifier;
    private UnmuteServer webServer;
    private OkHttpClient httpClient;
    private Messages messages;
    private FileConfiguration guiConfig;
    private boolean autoMute = true;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private ChatListener chatListener;
    private long configLastModified;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String lang = getConfig().getString("language", "en");
        this.guiConfig = loadGui(lang);
        this.messages = new Messages(this, lang);
        String apiKey = getConfig().getString("openai-key", "");
        double threshold = getConfig().getDouble("threshold", 0.5);
        int rateLimit = getConfig().getInt("rate-limit", 60);
        String model = getConfig().getString("model", "omni-moderation-latest");
        String prompt = getConfig().getString("chat-prompt", me.ogulcan.chatmod.service.ModerationService.DEFAULT_SYSTEM_PROMPT);
        String effort = getConfig().getString("thinking-effort", "medium");
        long cacheMinutes = getConfig().getLong("moderation-cache-minutes", 5);
        boolean debug = getConfig().getBoolean("debug", false);
        int connectTimeout = getConfig().getInt("http-connect-timeout", 10);
        int readTimeout = getConfig().getInt("http-read-timeout", 10);
        int maxRequests = getConfig().getInt("http-max-requests", 100);
        int maxRequestsPerHost = getConfig().getInt("http-max-requests-per-host", 100);
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(maxRequests);
        dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(readTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .build();
        String discordUrl = getConfig().getString("discord-url", "");
        int webPort = getConfig().getInt("web-port", 0);
        int unmuteThreads = getConfig().getInt("unmute-threads", 10);
        if (debug) {
            getLogger().info("Debug mode enabled");
        }
        this.moderationService = new ModerationService(apiKey, model, threshold, rateLimit,
                this.getLogger(), debug, prompt, effort, cacheMinutes, httpClient);
        this.notifier = new DiscordNotifier(discordUrl, httpClient);
        this.store = new PunishmentStore(this, new File(getDataFolder(), "data/punishments.json"));
        this.logStore = new LogStore(this, new File(getDataFolder(), "data/logs.json"));
        File configFile = new File(getDataFolder(), "config.yml");
        this.configLastModified = configFile.lastModified();
        if (webPort > 0) {
            try {
                this.webServer = new UnmuteServer(this, webPort, unmuteThreads);
                getLogger().info("Web server running on port " + webPort);
            } catch (Exception e) {
                getLogger().warning("Failed to start web server: " + e.getMessage());
            }
        }
        this.chatListener = new ChatListener(this, moderationService, store, logStore, notifier);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, store), this);
        getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, store), this);

        getCommand("cm").setExecutor(new CmCommand(this, store));
        startConfigWatcher(configFile);
    }

    public PunishmentStore getStore() {
        return store;
    }

    public LogStore getLogStore() {
        return logStore;
    }

    public Messages getMessages() {
        return messages;
    }

    public DiscordNotifier getNotifier() {
        return notifier;
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public void reloadFiles() {
        reloadAll();
    }

    private FileConfiguration loadGui(String lang) {
        String fileName = "gui_" + lang + ".yml";
        if (getResource(fileName) == null) {
            fileName = "gui.yml";
        }
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(getResource(fileName))) {
            if (reader != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
                cfg.setDefaults(def);
            }
        } catch (Exception ignored) {}
        return cfg;
    }

    /**
     * Reload configuration, language and GUI files and re-register listeners
     * with a freshly created {@link ModerationService} instance.
     */
    public void reloadAll() {
        reloadConfig();
        String lang = getConfig().getString("language", "en");
        this.messages = new Messages(this, lang);
        this.guiConfig = loadGui(lang);

        String apiKey = getConfig().getString("openai-key", "");
        double threshold = getConfig().getDouble("threshold", 0.5);
        int rateLimit = getConfig().getInt("rate-limit", 60);
        String model = getConfig().getString("model", "omni-moderation-latest");
        String prompt = getConfig().getString("chat-prompt", me.ogulcan.chatmod.service.ModerationService.DEFAULT_SYSTEM_PROMPT);
        String effort = getConfig().getString("thinking-effort", "medium");
        boolean debug = getConfig().getBoolean("debug", false);
        int connectTimeout = getConfig().getInt("http-connect-timeout", 10);
        int readTimeout = getConfig().getInt("http-read-timeout", 10);
        int maxRequests = getConfig().getInt("http-max-requests", 100);
        int maxRequestsPerHost = getConfig().getInt("http-max-requests-per-host", 100);
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(maxRequests);
        dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(readTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .build();
        String discordUrl = getConfig().getString("discord-url", "");
        int webPort = getConfig().getInt("web-port", 0);
        int unmuteThreads = getConfig().getInt("unmute-threads", 10);
        long cacheMinutes = getConfig().getLong("moderation-cache-minutes", 5);
        this.moderationService = new ModerationService(apiKey, model, threshold, rateLimit,
                this.getLogger(), debug, prompt, effort, cacheMinutes, httpClient);
        this.notifier = new DiscordNotifier(discordUrl, httpClient);

        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        if (webPort > 0) {
            try {
                webServer = new UnmuteServer(this, webPort, unmuteThreads);
                getLogger().info("Web server running on port " + webPort);
            } catch (Exception e) {
                getLogger().warning("Failed to start web server: " + e.getMessage());
            }
        }

        HandlerList.unregisterAll(this);
        this.chatListener = new ChatListener(this, moderationService, store, logStore, notifier);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, store), this);
        getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, store), this);
        this.configLastModified = new File(getDataFolder(), "config.yml").lastModified();
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.close();
        }
        if (logStore != null) {
            logStore.close();
        }
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
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

    private void startConfigWatcher(File configFile) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long mod = configFile.lastModified();
            if (mod > configLastModified) {
                configLastModified = mod;
                List<String> words = YamlConfiguration.loadConfiguration(configFile)
                        .getStringList("blocked-words");
                Bukkit.getScheduler().runTask(this, () -> {
                    reloadConfig();
                    chatListener.updateBlockedWords(words);
                    getLogger().info("Blocked words reloaded from config.yml");
                });
            }
        }, 100L, 100L);
    }
}
