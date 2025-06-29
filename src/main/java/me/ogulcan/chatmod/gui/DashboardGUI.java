package me.ogulcan.chatmod.gui;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.gui.LogsGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;

import java.util.*;

/**
 * Simple dashboard GUI for staff members.
 */
public class DashboardGUI implements Listener {
    private final Main plugin;
    private final PunishmentStore store;
    private FileConfiguration gui;
    private final Player viewer;
    private Inventory inventory;
    private UUID selected;
    private boolean openingNew = false;
    private List<Integer> playerSlots = new ArrayList<>();
    private final Map<Integer, ButtonInfo> mainButtons = new HashMap<>();
    private final Map<Integer, ButtonInfo> playerButtons = new HashMap<>();

    private static class ButtonInfo {
        final String action;
        final int value;

        ButtonInfo(String action, int value) {
            this.action = action;
            this.value = value;
        }
    }

    public DashboardGUI(Main plugin, PunishmentStore store, FileConfiguration gui, Player viewer) {
        this.plugin = plugin;
        this.store = store;
        this.gui = gui;
        this.viewer = viewer;
        createMain();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void createMain() {
        int size = gui.getInt("main.size", 54);
        String title = ChatColor.translateAlternateColorCodes('&', gui.getString("main.title", "ChatModeration"));
        inventory = Bukkit.createInventory(null, size, title);
        mainButtons.clear();
        playerSlots = gui.getIntegerList("main.player-slots");

        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (int i = 0; i < playerSlots.size() && i < players.length; i++) {
            int slot = playerSlots.get(i);
            Player p = players[i];
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            ChatColor color = store.isMuted(p.getUniqueId()) ? ChatColor.RED : ChatColor.GREEN;
            meta.setDisplayName(color + p.getName());

            List<String> lore = new ArrayList<>();
            if (store.isMuted(p.getUniqueId())) {
                long rem = store.remaining(p.getUniqueId()) / 1000;
                lore.add(plugin.getMessages().get("player-lore-muted", rem / 60, rem % 60));
            } else {
                lore.add(plugin.getMessages().get("player-lore-unmuted"));
            }
            int offences = store.offenceCount(p.getUniqueId(), Duration.ofHours(24).toMillis());
            lore.add(plugin.getMessages().get("player-lore-offences", offences));
            meta.setLore(lore);

            head.setItemMeta(meta);
            inventory.setItem(slot, head);
        }

        ConfigurationSection buttons = gui.getConfigurationSection("main.buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                ConfigurationSection sec = buttons.getConfigurationSection(key);
                int slot = sec.getInt("slot");
                String matName = sec.getString("material", "STONE");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.STONE;
                String action = sec.getString("action", "");
                String name;
                if ("toggle-automute".equals(action)) {
                    String on = sec.getString("name-on", "Auto-Mute ON");
                    String off = sec.getString("name-off", "Auto-Mute OFF");
                    name = plugin.isAutoMute() ? on : off;
                } else {
                    name = sec.getString("name", key);
                }
                name = ChatColor.translateAlternateColorCodes('&', name);
                java.util.List<String> lore = sec.getStringList("lore");
                if (lore != null && !lore.isEmpty()) {
                    java.util.List<String> list = new java.util.ArrayList<>(lore.size());
                    for (String line : lore) {
                        list.add(ChatColor.translateAlternateColorCodes('&', line));
                    }
                    inventory.setItem(slot, item(mat, name, list));
                } else {
                    inventory.setItem(slot, item(mat, name, null));
                }
                mainButtons.put(slot, new ButtonInfo(action, sec.getInt("value", 0)));
            }
        }
    }

    private Inventory createPlayerMenu(Player target) {
        int size = gui.getInt("player.size", 9);
        Inventory inv = Bukkit.createInventory(null, size, target.getName());
        playerButtons.clear();
        ConfigurationSection buttons = gui.getConfigurationSection("player.buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                int slot = buttons.getInt(key + ".slot");
                String matName = buttons.getString(key + ".material", "STONE");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.STONE;
                String name = ChatColor.translateAlternateColorCodes('&', buttons.getString(key + ".name", key));
                String action = buttons.getString(key + ".action", "");
                int value = buttons.getInt(key + ".value", 0);
                inv.setItem(slot, item(mat, name, null));
                playerButtons.put(slot, new ButtonInfo(action, value));
            }
        }
        return inv;
    }

    private ItemStack item(Material mat, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        openingNew = true;
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() != viewer) return;
        if (e.getView().getTopInventory().equals(inventory) && e.getRawSlot() < inventory.getSize()) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (playerSlots.contains(slot)) {
                Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
                int idx = playerSlots.indexOf(slot);
                if (idx < players.length) {
                    Player target = players[idx];
                    selected = target.getUniqueId();
                    openingNew = true;
                    viewer.openInventory(createPlayerMenu(target));
                }
            } else {
                ButtonInfo btn = mainButtons.get(slot);
                if (btn != null) {
                    if ("logs".equals(btn.action)) {
                        if (viewer.hasPermission("chatmoderation.logs")) {
                            openingNew = true;
                            new LogsGUI(plugin, plugin.getLogStore(), viewer);
                        } else {
                            viewer.sendMessage(plugin.getMessages().prefixed("no-permission"));
                        }
                    } else if (viewer.hasPermission("chatmoderation.admin")) {
                        switch (btn.action) {
                        case "reload" -> {
                            plugin.reloadFiles();
                            this.gui = plugin.getGuiConfig();
                            viewer.sendMessage(plugin.getMessages().prefixed("reloaded"));
                            createMain();
                            openingNew = true;
                            viewer.openInventory(inventory);
                        }
                        case "clear" -> {
                            store.clear();
                            viewer.sendMessage(plugin.getMessages().prefixed("history-cleared"));
                            createMain();
                            openingNew = true;
                            viewer.openInventory(inventory);
                        }
                        case "clear-logs" -> {
                            plugin.getLogStore().clear();
                            viewer.sendMessage(plugin.getMessages().prefixed("logs-cleared"));
                            createMain();
                            openingNew = true;
                            viewer.openInventory(inventory);
                        }
                        case "toggle-automute" -> {
                            plugin.setAutoMute(!plugin.isAutoMute());
                            createMain();
                            openingNew = true;
                            viewer.openInventory(inventory);
                        }
                        case "words" -> {
                            openingNew = true;
                            if (e.isLeftClick()) {
                                new AddWordGUI(plugin, viewer);
                            } else if (e.isRightClick()) {
                                new WordListGUI(plugin, viewer);
                            } else {
                                openingNew = false;
                            }
                        }
                        }
                    }
                }
            }
        } else if (selected != null && e.getView().getTitle().equals(Bukkit.getOfflinePlayer(selected).getName())) {
            e.setCancelled(true);
            Player target = Bukkit.getPlayer(selected);
            if (target == null) return;
            if (!viewer.hasPermission("chatmoderation.admin")) return;
            ButtonInfo btn = playerButtons.get(e.getRawSlot());
            if (btn != null) {
                switch (btn.action) {
                    case "add" -> {
                        long mins = store.remaining(selected) / 60000 + btn.value;
                        store.mute(selected, mins);
                        plugin.scheduleUnmute(selected, mins * 60L * 20L);
                    }
                    case "subtract" -> {
                        long mins = Math.max(0, store.remaining(selected) / 60000 - btn.value);
                        if (mins == 0) store.unmute(selected); else {
                            store.mute(selected, mins);
                            plugin.scheduleUnmute(selected, mins * 60L * 20L);
                        }
                    }
                    case "unmute" -> {
                        store.unmute(selected);
                        plugin.cancelUnmute(selected);
                    }
                }
            }
            openingNew = true;
            viewer.openInventory(inventory);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() != viewer) return;
        if (openingNew) {
            openingNew = false;
            return;
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() != viewer) return;
        if (e.getView().getTopInventory().equals(inventory)) {
            for (int slot : e.getRawSlots()) {
                if (slot < inventory.getSize()) {
                    e.setCancelled(true);
                    break;
                }
            }
        }
    }
}
