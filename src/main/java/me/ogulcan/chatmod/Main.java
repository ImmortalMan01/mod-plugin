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
    private File blockedWordsFile;
    private long wordsLastModified;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String lang = getConfig().getString("language", "en");
        this.guiConfig = loadGui(lang);
        this.messages = new Messages(this, lang);
        java.util.List<String> blockedWords = loadBlockedWords(lang);
        String apiKey = getConfig().getString("openai-key", "");
        double threshold = getConfig().getDouble("threshold", 0.5);
        int rateLimit = getConfig().getInt("rate-limit", 60);
        String model = getConfig().getString("model", "omni-moderation-latest");
        String prompt = getConfig().getString("chat-prompt", me.ogulcan.chatmod.service.ModerationService.DEFAULT_SYSTEM_PROMPT);
        String effort = getConfig().getString("thinking-effort", "medium");
        long cacheMinutes = getConfig().getLong("moderation-cache-minutes", 5);
        boolean debug = getConfig().getBoolean("debug", false);
        if (getConfig().getBoolean("use-zemberek", false)) {
            me.ogulcan.chatmod.service.ZemberekStemmer.init();
        }
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
        this.chatListener = new ChatListener(this, moderationService, store, logStore, notifier, blockedWords);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, store), this);
        getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, store), this);

        getCommand("cm").setExecutor(new CmCommand(this, store));
        startConfigWatcher(configFile);
        startWordsWatcher();
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

    private java.util.List<String> loadBlockedWords(String lang) {
        String fileName = "blocked_words_" + lang + ".yml";
        this.blockedWordsFile = new File(getDataFolder(), fileName);
        if (!blockedWordsFile.exists()) {
            saveResource(fileName, false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(blockedWordsFile);
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(getResource(fileName))) {
            if (reader != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
                cfg.setDefaults(def);
            }
        } catch (Exception ignored) {}
        this.wordsLastModified = blockedWordsFile.lastModified();
        return cfg.getStringList("blocked-words");
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
        java.util.List<String> blockedWords = loadBlockedWords(lang);

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
        this.chatListener = new ChatListener(this, moderationService, store, logStore, notifier, blockedWords);
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

    public synchronized boolean addBlockedWord(String word) {
        if (blockedWordsFile == null) return false;
        if (!blockedWordsFile.getParentFile().exists()) {
            blockedWordsFile.getParentFile().mkdirs();
        }
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(blockedWordsFile);
        java.util.List<String> words = cfg.getStringList("blocked-words");
        String canon = me.ogulcan.chatmod.service.WordFilter.canonicalize(word);
        for (String w : words) {
            if (me.ogulcan.chatmod.service.WordFilter.canonicalize(w).equals(canon)) {
                return false;
            }
        }
        words.add(word);
        cfg.set("blocked-words", words);
        try {
            cfg.save(blockedWordsFile);
        } catch (Exception e) {
            getLogger().warning("Failed to save blocked words: " + e.getMessage());
        }
        wordsLastModified = blockedWordsFile.lastModified();
        if (chatListener != null) chatListener.updateBlockedWords(words);
        return true;
    }

    public synchronized boolean removeBlockedWord(String word) {
        if (blockedWordsFile == null) return false;
        if (!blockedWordsFile.getParentFile().exists()) {
            blockedWordsFile.getParentFile().mkdirs();
        }
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(blockedWordsFile);
        java.util.List<String> words = cfg.getStringList("blocked-words");
        String canon = me.ogulcan.chatmod.service.WordFilter.canonicalize(word);
        String found = null;
        for (String w : words) {
            if (me.ogulcan.chatmod.service.WordFilter.canonicalize(w).equals(canon)) {
                found = w;
                break;
            }
        }
        if (found == null) return false;
        words.remove(found);
        cfg.set("blocked-words", words);
        try {
            cfg.save(blockedWordsFile);
        } catch (Exception e) {
            getLogger().warning("Failed to save blocked words: " + e.getMessage());
        }
        wordsLastModified = blockedWordsFile.lastModified();
        if (chatListener != null) chatListener.updateBlockedWords(words);
        return true;
    }

    public java.io.File getBlockedWordsFile() {
        return blockedWordsFile;
    }

    private void startConfigWatcher(File configFile) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long mod = configFile.lastModified();
            if (mod > configLastModified) {
                configLastModified = mod;
                Bukkit.getScheduler().runTask(this, () -> {
                    reloadConfig();
                    getLogger().info("config.yml reloaded");
                });
            }
        }, 100L, 100L);
    }

    private void startWordsWatcher() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            File file = blockedWordsFile;
            if (file == null) return;
            long mod = file.lastModified();
            if (mod > wordsLastModified) {
                wordsLastModified = mod;
                java.util.List<String> words = YamlConfiguration.loadConfiguration(file).getStringList("blocked-words");
                Bukkit.getScheduler().runTask(this, () -> {
                    chatListener.updateBlockedWords(words);
                    getLogger().info("Blocked words reloaded from " + file.getName());
                });
            }
        }, 100L, 100L);
    }
}
