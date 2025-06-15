package me.ogulcan.chatmod.storage;

import java.util.UUID;

public class LogEntry {
    public UUID player;
    public String name;
    public String message;
    public long timestamp;

    public LogEntry(UUID player, String name, String message, long timestamp) {
        this.player = player;
        this.name = name;
        this.message = message;
        this.timestamp = timestamp;
    }
}
