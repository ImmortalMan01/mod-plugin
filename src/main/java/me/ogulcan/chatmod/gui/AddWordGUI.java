package me.ogulcan.chatmod.gui;

import me.ogulcan.chatmod.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Simple anvil GUI to add a new blocked word.
 */
public class AddWordGUI implements Listener {
    private final Main plugin;
    private final Player viewer;
    private final Inventory inventory;
    private String renameText;
    private boolean saved;

    public AddWordGUI(Main plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(null, InventoryType.ANVIL, plugin.getMessages().get("add-word-title"));
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(plugin.getMessages().get("add-word-hint"));
        paper.setItemMeta(meta);
        inventory.setItem(0, paper);
        viewer.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.saved = false;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!e.getInventory().equals(inventory)) return;
        renameText = e.getInventory().getRenameText();
        ItemStack base = e.getInventory().getItem(0);
        if (base != null && renameText != null && !renameText.isBlank()) {
            ItemStack result = base.clone();
            ItemMeta meta = result.getItemMeta();
            meta.setDisplayName(renameText);
            result.setItemMeta(meta);
            e.setResult(result);
            e.getInventory().setRepairCost(0);
        } else {
            e.setResult(null);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() != viewer) return;
        if (!e.getView().getTopInventory().equals(inventory)) return;
        if (e.getRawSlot() == 2) {
            e.setCancelled(true);
            if (renameText != null && !renameText.isBlank()) {
                boolean added = plugin.addBlockedWord(renameText);
                saved = true;
                if (added) {
                    viewer.sendMessage(plugin.getMessages().prefixed("word-added", renameText));
                } else {
                    viewer.sendMessage(plugin.getMessages().prefixed("word-exists"));
                }
            }
            viewer.closeInventory();
        } else if (e.getRawSlot() < inventory.getSize()) {
            e.setCancelled(true);
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
        if (!saved && renameText != null && !renameText.isBlank()) {
            boolean added = plugin.addBlockedWord(renameText);
            if (added) {
                viewer.sendMessage(plugin.getMessages().prefixed("word-added", renameText));
            } else {
                viewer.sendMessage(plugin.getMessages().prefixed("word-exists"));
            }
        }
        HandlerList.unregisterAll(this);
    }
}
