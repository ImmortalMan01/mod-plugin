package me.ogulcan.chatmod.gui;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.LogStore;
import me.ogulcan.chatmod.storage.LogStore.LogEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogsGUI implements Listener {
    private static final int PAGE_SIZE = 45;

    private final Main plugin;
    private final LogStore store;
    private final Player viewer;
    private Inventory inventory;
    private int page = 0;
    private boolean openingNew = false;

    public LogsGUI(Main plugin, LogStore store, Player viewer) {
        this.plugin = plugin;
        this.store = store;
        this.viewer = viewer;
        openPage(0);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void openPage(int p) {
        List<LogEntry> all = store.getLogs();
        int total = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (total == 0) total = 1;
        this.page = Math.max(0, Math.min(p, total - 1));
        String title = ChatColor.BLUE + "Logs " + (page + 1) + "/" + total;
        inventory = Bukkit.createInventory(null, 54, title);
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            LogEntry entry = all.get(all.size() - 1 - (start + i));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid);
            meta.setOwningPlayer(op);
            meta.setDisplayName(ChatColor.YELLOW + entry.name);
            List<String> lore = new ArrayList<>();
            String msg = entry.message.length() > 30 ? entry.message.substring(0, 30) + "..." : entry.message;
            if (entry.manual) {
                lore.add(ChatColor.LIGHT_PURPLE + "[Manual] " + msg);
            } else {
                lore.add(ChatColor.GRAY + msg);
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());
            lore.add(ChatColor.AQUA + fmt.format(Instant.ofEpochMilli(entry.timestamp)));
            meta.setLore(lore);
            head.setItemMeta(meta);
            inventory.setItem(i, head);
        }
        if (page > 0) inventory.setItem(45, item(Material.ARROW, ChatColor.GREEN + "Prev"));
        if (start + PAGE_SIZE < all.size()) inventory.setItem(53, item(Material.ARROW, ChatColor.GREEN + "Next"));
        openingNew = true;
        viewer.openInventory(inventory);
    }

    private ItemStack item(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() != viewer) return;
        if (e.getView().getTopInventory().equals(inventory) && e.getRawSlot() < inventory.getSize()) {
            e.setCancelled(true);
            if (e.getRawSlot() == 45) {
                openPage(page - 1);
            } else if (e.getRawSlot() == 53) {
                openPage(page + 1);
            }
        }
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

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() != viewer) return;
        if (openingNew) {
            openingNew = false;
            return;
        }
        HandlerList.unregisterAll(this);
    }
}
