package me.ogulcan.chatmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class PunishmentStore {
    private final File file;
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    private boolean dirty = false;
    private final org.bukkit.scheduler.BukkitTask task;
    private Map<UUID, Offender> offenders = new HashMap<>();

    public PunishmentStore(JavaPlugin plugin, File file) {
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
                synchronized (PunishmentStore.this) {
                    if (dirty) {
                        dirty = false;
                        save();
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, interval, interval);
    }

    public synchronized void mute(UUID uuid, long durationMinutes) {
        Offender offender = offenders.getOrDefault(uuid, new Offender());
        offender.muteUntil = System.currentTimeMillis() + durationMinutes * 60_000L;
        offender.pausedRemaining = 0;
        offender.offences.add(System.currentTimeMillis());
        offenders.put(uuid, offender);
        markDirty();
    }

    public synchronized void unmute(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender != null) {
            offender.muteUntil = 0;
            offender.pausedRemaining = 0;
            markDirty();
        }
    }

    public synchronized boolean isMuted(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender == null) return false;
        if (offender.pausedRemaining > 0) {
            return true;
        }
        if (offender.muteUntil <= System.currentTimeMillis()) {
            offender.muteUntil = 0;
            markDirty();
            return false;
        }
        return true;
    }

    public synchronized long remaining(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender == null) return 0L;
        if (offender.pausedRemaining > 0) return offender.pausedRemaining;
        return Math.max(0L, offender.muteUntil - System.currentTimeMillis());
    }

    public synchronized void pause(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender == null) return;
        if (offender.muteUntil > System.currentTimeMillis()) {
            offender.pausedRemaining = offender.muteUntil - System.currentTimeMillis();
            offender.muteUntil = 0;
            markDirty();
        }
    }

    public synchronized boolean resume(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender == null) return false;
        if (offender.pausedRemaining > 0) {
            offender.muteUntil = System.currentTimeMillis() + offender.pausedRemaining;
            offender.pausedRemaining = 0;
            markDirty();
            return true;
        }
        return false;
    }

    public synchronized boolean isPaused(UUID uuid) {
        Offender offender = offenders.get(uuid);
        return offender != null && offender.pausedRemaining > 0;
    }

    public synchronized int offenceCount(UUID uuid, long windowMs) {
        Offender offender = offenders.get(uuid);
        if (offender == null) return 0;
        long now = System.currentTimeMillis();
        offender.offences.removeIf(t -> t + windowMs < now);
        return offender.offences.size();
    }

    /** Clears all stored offences and mute timers. */
    public synchronized void clear() {
        offenders.clear();
        markDirty();
    }

    private void load() {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<UUID, Offender>>(){}.getType();
            offenders = gson.fromJson(reader, type);
            if (offenders == null) offenders = new HashMap<>();
        } catch (IOException ignored) {}
    }

    private void save() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(offenders, writer);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not save punishments: " + e.getMessage());
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

    public static class Offender {
        public long muteUntil = 0;
        public long pausedRemaining = 0;
        public List<Long> offences = new ArrayList<>();
    }
}
