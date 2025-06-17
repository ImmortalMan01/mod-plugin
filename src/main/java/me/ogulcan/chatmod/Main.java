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
        if (webPort > 0) {
            try {
                this.webServer = new UnmuteServer(this, webPort, unmuteThreads);
                getLogger().info("Web server running on port " + webPort);
            } catch (Exception e) {
                getLogger().warning("Failed to start web server: " + e.getMessage());
            }
        }
        getServer().getPluginManager().registerEvents(new ChatListener(this, moderationService, store, logStore, notifier), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, store), this);
        getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, store), this);

        getCommand("cm").setExecutor(new CmCommand(this, store));
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
        getServer().getPluginManager().registerEvents(new ChatListener(this, moderationService, store, logStore, notifier), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, store), this);
        getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, store), this);
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
}
