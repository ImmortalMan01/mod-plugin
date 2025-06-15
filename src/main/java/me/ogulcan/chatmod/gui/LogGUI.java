package me.ogulcan.chatmod.gui;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.storage.LogEntry;
import me.ogulcan.chatmod.storage.LogStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Simple GUI to view chat logs.
 */
public class LogGUI implements Listener {
    private static final int PAGE_SIZE = 45;

    private final Main plugin;
    private final LogStore store;
    private final Player viewer;
    private int page = 0;
    private Inventory inventory;

    public LogGUI(Main plugin, LogStore store, Player viewer) {
        this.plugin = plugin;
        this.store = store;
        this.viewer = viewer;
        create();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void create() {
        String title = ChatColor.translateAlternateColorCodes('&', plugin.getMessages().get("logs-title"));
        inventory = Bukkit.createInventory(null, 54, title);
        fill();
    }

    private void fill() {
        inventory.clear();
        List<LogEntry> entries = store.getEntries();
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());
        for (int i = start; i < end; i++) {
            LogEntry entry = entries.get(entries.size() - 1 - i); // newest first
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + entry.name);
            List<String> lore = new ArrayList<>();
            String msg = entry.message.length() > 30 ? entry.message.substring(0, 30) + "..." : entry.message;
            lore.add(ChatColor.WHITE + msg);
            lore.add(ChatColor.GRAY + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.timestamp)));
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(i - start, item);
        }
        if (page > 0) {
            inventory.setItem(45, navItem(Material.ARROW, "< Prev"));
        }
        if ((page + 1) * PAGE_SIZE < entries.size()) {
            inventory.setItem(53, navItem(Material.ARROW, "Next >"));
        }
    }

    private ItemStack navItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() != viewer) return;
        if (!e.getView().getTopInventory().equals(inventory)) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == 45 && page > 0) {
            page--;
            fill();
            viewer.openInventory(inventory);
        } else if (slot == 53 && (page + 1) * PAGE_SIZE < store.getEntries().size()) {
            page++;
            fill();
            viewer.openInventory(inventory);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() != viewer) return;
        HandlerList.unregisterAll(this);
    }
}
