package me.ogulcan.chatmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simple persistent store for chat logs.
 */
public class LogStore {
    private final File file;
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    private boolean dirty = false;
    private final org.bukkit.scheduler.BukkitTask task;
    private List<LogEntry> logs = new ArrayList<>();

    public LogStore(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        load();
        // All file writes are performed by this dedicated async task running on
        // Bukkit's single async thread.
        long interval = plugin.getConfig().getLong("save-interval-ticks", 100L);
        this.task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                synchronized (LogStore.this) {
                    if (dirty) {
                        dirty = false;
                        save();
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, interval, interval);
    }

    public synchronized void add(UUID uuid, String name, String message) {
        logs.add(new LogEntry(uuid, name, message, System.currentTimeMillis()));
        int max = plugin.getConfig().getInt("max-log-entries", 1000);
        if (max > 0) {
            while (logs.size() > max) {
                logs.remove(0);
            }
        }
        markDirty();
    }

    /** Returns a copy of all logs in chronological order. */
    public synchronized List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }

    /** Remove all log entries from memory and disk. */
    public synchronized void clear() {
        logs.clear();
        markDirty();
    }

    private void load() {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<LogEntry>>(){}.getType();
            logs = gson.fromJson(reader, type);
            if (logs == null) logs = new ArrayList<>();
        } catch (IOException ignored) {}
    }

    private void save() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(logs, writer);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not save logs: " + e.getMessage());
        }
    }

    /**
     * Mark the store as dirty so the periodic async task will persist changes.
     * All actual file writes occur on that dedicated thread.
     */
    public void saveAsync() {
        markDirty();
    }

    private synchronized void markDirty() {
        dirty = true;
    }

    public synchronized void close() {
        task.cancel();
        if (dirty) {
            save();
            dirty = false;
        }
    }

    public static class LogEntry {
        public UUID uuid;
        public String name;
        public String message;
        public long timestamp;

        public LogEntry(UUID uuid, String name, String message, long timestamp) {
            this.uuid = uuid;
            this.name = name;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
