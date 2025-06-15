package me.ogulcan.chatmod.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores recent chat logs in a bounded list.
 */
public class LogStore {
    private final File file;
    private final int limit;
    private final Gson gson = new Gson();
    private List<LogEntry> entries = new ArrayList<>();

    public LogStore(File file, int limit) {
        this.file = file;
        this.limit = limit;
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        load();
    }

    public synchronized void addEntry(LogEntry entry) {
        entries.add(entry);
        while (entries.size() > limit) {
            entries.remove(0);
        }
        save();
    }

    public synchronized List<LogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    private void load() {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<LogEntry>>(){}.getType();
            entries = gson.fromJson(reader, type);
            if (entries == null) entries = new ArrayList<>();
        } catch (IOException ignored) {}
    }

    private void save() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(entries, writer);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not save logs: " + e.getMessage());
        }
    }
}
