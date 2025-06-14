package me.ogulcan.chatmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;

public class PunishmentStore {
    private final File file;
    private final Gson gson = new Gson();
    private Map<UUID, Offender> offenders = new HashMap<>();

    public PunishmentStore(File file) {
        this.file = file;
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        load();
    }

    public synchronized void mute(UUID uuid, long durationMinutes) {
        Offender offender = offenders.getOrDefault(uuid, new Offender());
        offender.muteUntil = System.currentTimeMillis() + durationMinutes * 60_000L;
        offender.offences.add(System.currentTimeMillis());
        offenders.put(uuid, offender);
        save();
    }

    public synchronized void unmute(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender != null) {
            offender.muteUntil = 0;
            save();
        }
    }

    public synchronized boolean isMuted(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender == null) return false;
        if (offender.muteUntil <= System.currentTimeMillis()) {
            offender.muteUntil = 0;
            save();
            return false;
        }
        return true;
    }

    public synchronized long remaining(UUID uuid) {
        Offender offender = offenders.get(uuid);
        if (offender == null) return 0L;
        return Math.max(0L, offender.muteUntil - System.currentTimeMillis());
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
        save();
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

    public static class Offender {
        public long muteUntil = 0;
        public List<Long> offences = new ArrayList<>();
    }
}
