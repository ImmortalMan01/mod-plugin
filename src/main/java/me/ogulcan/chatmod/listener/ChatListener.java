package me.ogulcan.chatmod.listener;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.service.ModerationService;
import me.ogulcan.chatmod.service.WordFilter;
import me.ogulcan.chatmod.service.DiscordNotifier;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.storage.LogStore;
import me.ogulcan.chatmod.hook.BetterTeamsHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class ChatListener implements Listener {
    private final Main plugin;
    private final ModerationService service;
    private final PunishmentStore store;
    private final LogStore logStore;
    private final DiscordNotifier notifier;
    private final List<String> categories;
    private final AtomicReference<java.util.Set<String>> normalizedWords = new AtomicReference<>();
    private final AtomicReference<java.util.List<Pattern>> regexPatterns = new AtomicReference<>();
    private final boolean useBlockedWords;
    private final boolean useBlockedCategories;
    private final int blockedWordDistance;
    private final boolean useStemming;
    private final boolean useZemberek;
    private final Map<String, Boolean> categoryEnabled;
    private final Map<String, Double> categoryRatio;

    public ChatListener(Main plugin, ModerationService service, PunishmentStore store, LogStore logStore, DiscordNotifier notifier) {
        this.plugin = plugin;
        this.service = service;
        this.store = store;
        this.logStore = logStore;
        this.notifier = notifier;
        this.categories = plugin.getConfig().getStringList("blocked-categories");
        java.util.List<String> words = plugin.getConfig().getStringList("blocked-words");

        org.bukkit.configuration.ConfigurationSection mapSec =
                plugin.getConfig().getConfigurationSection("character-mapping");
        java.util.Map<Character, Character> charMap = new java.util.HashMap<>();
        if (mapSec != null) {
            for (String k : mapSec.getKeys(false)) {
                String v = mapSec.getString(k);
                if (k.length() == 1 && v != null && v.length() == 1) {
                    charMap.put(k.charAt(0), v.charAt(0));
                }
            }
        }
        WordFilter.setCharacterMap(charMap);
        WordFilter.setLanguage(plugin.getConfig().getString("language", "en"));
        updateBlockedWords(words);
        this.useBlockedWords = plugin.getConfig().getBoolean("use-blocked-words", true);
        this.useStemming = plugin.getConfig().getBoolean("use-stemming", false);
        this.useZemberek = plugin.getConfig().getBoolean("use-zemberek", false);
        this.blockedWordDistance = plugin.getConfig().getInt("blocked-word-distance", 1);
        this.useBlockedCategories = plugin.getConfig().getBoolean("use-blocked-categories", true);
        this.categoryEnabled = new HashMap<>();
        this.categoryRatio = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection cs = plugin.getConfig().getConfigurationSection("category-settings");
        double def = plugin.getConfig().getDouble("threshold", 0.5);
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                categoryEnabled.put(key, cs.getBoolean(key + ".enabled", true));
                categoryRatio.put(key, cs.getDouble(key + ".ratio", def));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.isAutoMute()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (store.isMuted(uuid)) {
            long rem = store.remaining(uuid) / 1000;
            player.sendMessage(plugin.getMessages().get("still-muted", format(rem)));
            event.setCancelled(true);
            return;
        }
        if (player.hasPermission("chatmoderation.bypass")) return;
        String message = event.getMessage();
        if (BetterTeamsHook.isTeamChat(player, message)) return;
        if (useBlockedWords && WordFilter.containsBlockedWord(message, normalizedWords.get(), regexPatterns.get(), true, blockedWordDistance, useStemming, useZemberek)) {
            Bukkit.getScheduler().runTask(plugin, () -> applyPunishment(player, message));
            return;
        }
        service.moderate(message).thenAccept(result -> {
            boolean shouldMute = result.blocked;
            if (useBlockedCategories) {
                for (Map.Entry<String, Double> e : result.scores.entrySet()) {
                    String cat = e.getKey();
                    double score = e.getValue();
                    boolean enabled = categoryEnabled.getOrDefault(cat, categories.contains(cat));
                    if (!enabled) continue;
                    double ratio = categoryRatio.getOrDefault(cat, plugin.getConfig().getDouble("threshold", 0.5));
                    if (score >= ratio) {
                        shouldMute = true;
                        break;
                    }
                }
            }
            if (shouldMute) {
                Bukkit.getScheduler().runTask(plugin, () -> applyPunishment(player, message));
            }
        });
    }

    private void applyPunishment(Player player, String message) {
        UUID uuid = player.getUniqueId();
        int offences24h = store.offenceCount(uuid, Duration.ofHours(24).toMillis());
        long minutes;
        if (offences24h == 0) {
            minutes = plugin.getConfig().getLong("punishments.first", 5);
        } else if (offences24h == 1) {
            minutes = plugin.getConfig().getLong("punishments.second", 30);
        } else if (offences24h == 2) {
            minutes = plugin.getConfig().getLong("punishments.third", 60);
        } else {
            minutes = plugin.getConfig().getLong("punishments.fourth", 180);
            String msg = plugin.getMessages().get("repeated-offence", player.getName());
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("chatmoderation.notify"))
                    .forEach(p -> p.sendMessage(msg));
        }
        store.mute(uuid, minutes);
        String sys = plugin.getMessages().get("system-name");
        String autoReason = plugin.getMessages().get("auto-detection");
        logStore.add(uuid, player.getName(), autoReason, "auto", sys, minutes);
        plugin.scheduleUnmute(uuid, minutes * 60L * 20L);
        player.sendMessage(plugin.getMessages().get("muted-player", minutes));

        // Broadcast mute information to all players
        int offences = store.offenceCount(uuid, Duration.ofHours(24).toMillis());
        long remaining = store.remaining(uuid) / 1000;
        String broadcast = plugin.getMessages().get(
                "mute-broadcast",
                player.getName(),
                offences,
                remaining / 60,
                remaining % 60
        );
        Bukkit.broadcastMessage(broadcast);

        notifier.notifyMute(player.getName(), autoReason, minutes, sys, "auto", System.currentTimeMillis());
    }

    private String format(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        String lang = plugin.getConfig().getString("language", "en");
        if ("tr".equalsIgnoreCase(lang)) {
            return min + " dakika " + sec + " saniye";
        }
        return min + "m " + sec + "s";
    }

    /**
     * Rebuild the normalized word and regex pattern lists from the provided
     * values. The references are swapped atomically so chat threads always see
     * a consistent snapshot.
     */
    public void updateBlockedWords(List<String> words) {
        java.util.Set<String> newSet = new java.util.HashSet<>();
        java.util.List<Pattern> newPatterns = new java.util.ArrayList<>();
        for (String w : words) {
            if (w.startsWith("/") && w.endsWith("/") && w.length() > 1) {
                newPatterns.add(Pattern.compile(w.substring(1, w.length() - 1)));
            } else {
                newSet.add(WordFilter.canonicalize(w));
            }
        }
        normalizedWords.set(newSet);
        regexPatterns.set(newPatterns);
    }
}
