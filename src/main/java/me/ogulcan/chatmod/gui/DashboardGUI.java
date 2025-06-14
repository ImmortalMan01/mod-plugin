package me.ogulcan.chatmod.gui;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.PunishmentStore;
import me.ogulcan.chatmod.task.UnmuteTask;
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
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
        }

        ConfigurationSection buttons = gui.getConfigurationSection("main.buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                int slot = buttons.getInt(key + ".slot");
                Material mat = Material.valueOf(buttons.getString(key + ".material", "STONE"));
                String action = buttons.getString(key + ".action", "");
                String name;
                if ("toggle-automute".equals(action)) {
                    String on = buttons.getString(key + ".name-on", "Auto-Mute ON");
                    String off = buttons.getString(key + ".name-off", "Auto-Mute OFF");
                    name = plugin.isAutoMute() ? on : off;
                } else {
                    name = buttons.getString(key + ".name", key);
                }
                name = ChatColor.translateAlternateColorCodes('&', name);
                inventory.setItem(slot, item(mat, name));
                mainButtons.put(slot, new ButtonInfo(action, buttons.getInt(key + ".value", 0)));
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
                Material mat = Material.valueOf(buttons.getString(key + ".material", "STONE"));
                String name = ChatColor.translateAlternateColorCodes('&', buttons.getString(key + ".name", key));
                String action = buttons.getString(key + ".action", "");
                int value = buttons.getInt(key + ".value", 0);
                inv.setItem(slot, item(mat, name));
                playerButtons.put(slot, new ButtonInfo(action, value));
            }
        }
        return inv;
    }

    private ItemStack item(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
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
                if (btn != null && viewer.hasPermission("chatmoderation.admin")) {
                    switch (btn.action) {
                        case "reload" -> {
                            plugin.reloadFiles();
                            this.gui = plugin.getGuiConfig();
                            viewer.sendMessage(plugin.getMessages().get("reloaded"));
                            createMain();
                            openingNew = true;
                            viewer.openInventory(inventory);
                        }
                        case "clear" -> {
                            store.clear();
                            viewer.sendMessage(plugin.getMessages().get("history-cleared"));
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
                        new UnmuteTask(plugin, selected, store).runTaskLater(plugin, mins * 60L * 20L);
                    }
                    case "subtract" -> {
                        long mins = Math.max(0, store.remaining(selected) / 60000 - btn.value);
                        if (mins == 0) store.unmute(selected); else {
                            store.mute(selected, mins);
                            new UnmuteTask(plugin, selected, store).runTaskLater(plugin, mins * 60L * 20L);
                        }
                    }
                    case "unmute" -> store.unmute(selected);
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
