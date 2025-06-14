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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Simple dashboard GUI for staff members.
 */
public class DashboardGUI implements Listener {
    private final Main plugin;
    private final PunishmentStore store;
    private final Player viewer;
    private Inventory inventory;
    private UUID selected;

    public DashboardGUI(Main plugin, PunishmentStore store, Player viewer) {
        this.plugin = plugin;
        this.store = store;
        this.viewer = viewer;
        createMain();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void createMain() {
        inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "ChatModeration");
        int index = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (index > 5) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            // Use legacy API to support both Paper and Spigot
            meta.setOwningPlayer(p);
            ChatColor color = store.isMuted(p.getUniqueId()) ? ChatColor.RED : ChatColor.GREEN;
            meta.setDisplayName(color + p.getName());
            head.setItemMeta(meta);
            inventory.setItem(index * 9, head);
            index++;
        }
        inventory.setItem(8, item(Material.BOOK, ChatColor.YELLOW + "Reload Config"));
        inventory.setItem(17, item(Material.PAPER, ChatColor.YELLOW + "Clear Offences"));
        inventory.setItem(26, item(Material.LEVER, ChatColor.YELLOW + (plugin.isAutoMute() ? "Auto-Mute ON" : "Auto-Mute OFF")));
    }

    private Inventory createPlayerMenu(Player target) {
        Inventory inv = Bukkit.createInventory(null, 9, target.getName());
        inv.setItem(0, item(Material.ARROW, ChatColor.GREEN + "+5m"));
        inv.setItem(1, item(Material.ARROW, ChatColor.RED + "-5m"));
        inv.setItem(8, item(Material.BARRIER, ChatColor.RED + "Unmute"));
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
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() != viewer) return;
        if (e.getInventory().equals(inventory)) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot % 9 == 0 && slot / 9 <= 5) { // player column
                Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
                if (slot / 9 < players.length) {
                    Player target = players[slot / 9];
                    selected = target.getUniqueId();
                    viewer.openInventory(createPlayerMenu(target));
                }
            } else if (slot == 8) {
                if (viewer.hasPermission("chatmoderation.admin")) {
                    plugin.reloadFiles();
                    viewer.sendMessage(ChatColor.GREEN + "Config reloaded");
                    createMain();
                    viewer.openInventory(inventory);
                }
            } else if (slot == 17) {
                if (viewer.hasPermission("chatmoderation.admin")) {
                    store.clear();
                    viewer.sendMessage(ChatColor.GREEN + "Offence history cleared");
                    createMain();
                    viewer.openInventory(inventory);
                }
            } else if (slot == 26) {
                if (viewer.hasPermission("chatmoderation.admin")) {
                    plugin.setAutoMute(!plugin.isAutoMute());
                    createMain();
                    viewer.openInventory(inventory);
                }
            }
        } else if (selected != null && e.getView().getTitle().equals(Bukkit.getOfflinePlayer(selected).getName())) {
            e.setCancelled(true);
            Player target = Bukkit.getPlayer(selected);
            if (target == null) return;
            if (!viewer.hasPermission("chatmoderation.admin")) return;
            if (e.getRawSlot() == 0) {
                long mins = store.remaining(selected) / 60000 + 5;
                store.mute(selected, mins);
                new UnmuteTask(plugin, selected, store).runTaskLater(plugin, mins * 60L * 20L);
            } else if (e.getRawSlot() == 1) {
                long mins = Math.max(0, store.remaining(selected) / 60000 - 5);
                if (mins == 0) store.unmute(selected); else {
                    store.mute(selected, mins);
                    new UnmuteTask(plugin, selected, store).runTaskLater(plugin, mins * 60L * 20L);
                }
            } else if (e.getRawSlot() == 8) {
                store.unmute(selected);
            }
            viewer.openInventory(inventory);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() == viewer) {
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }
}
