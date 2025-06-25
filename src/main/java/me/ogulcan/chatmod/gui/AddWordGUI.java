package me.ogulcan.chatmod.gui;

import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.AddWordResult;
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
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.AnvilInventory;
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
        registerPaperListener();
        this.saved = false;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!e.getView().getTopInventory().equals(inventory)) return;
        handlePrepare(e.getInventory(), e);
    }


    private void handlePrepare(AnvilInventory inv, org.bukkit.event.Event parent) {
        renameText = inv.getRenameText();
        ItemStack base = inv.getItem(0);
        ItemStack result = null;
        if (base != null && renameText != null && !renameText.isBlank()) {
            result = base.clone();
            ItemMeta meta = result.getItemMeta();
            meta.setDisplayName(renameText);
            result.setItemMeta(meta);
            inv.setRepairCost(0);
        }
        if (parent instanceof PrepareAnvilEvent pae) {
            pae.setResult(result);
        } else if (parent.getClass().getName().equals("com.destroystokyo.paper.event.inventory.PrepareResultEvent")) {
            try {
                parent.getClass().getMethod("setResult", ItemStack.class).invoke(parent, result);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() != viewer) return;
        if (!e.getView().getTopInventory().equals(inventory)) return;
        if (e.getInventory().getType() == InventoryType.ANVIL && e.getSlotType() == SlotType.RESULT) {
            e.setCancelled(true);
            if (e.getView().getTopInventory() instanceof AnvilInventory anvil) {
                String text = anvil.getRenameText();
                if (text != null) renameText = text;
            }
            if ((renameText == null || renameText.isBlank()) && e.getCurrentItem() != null) {
                ItemStack item = e.getCurrentItem();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    renameText = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                }
            }
            if (renameText != null && !renameText.isBlank()) {
                me.ogulcan.chatmod.AddWordResult result = plugin.addBlockedWord(renameText);
                saved = true;
                switch (result) {
                    case ADDED -> viewer.sendMessage(plugin.getMessages().prefixed("word-added", renameText));
                    case EXISTS -> viewer.sendMessage(plugin.getMessages().prefixed("word-exists"));
                    case ERROR -> viewer.sendMessage(plugin.getMessages().prefixed("word-add-failed"));
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
        if (e.getInventory() instanceof AnvilInventory anvil) {
            String text = anvil.getRenameText();
            if (text != null) renameText = text;
        }
        if (!saved && renameText != null && !renameText.isBlank()) {
            me.ogulcan.chatmod.AddWordResult result = plugin.addBlockedWord(renameText);
            switch (result) {
                case ADDED -> viewer.sendMessage(plugin.getMessages().prefixed("word-added", renameText));
                case EXISTS -> viewer.sendMessage(plugin.getMessages().prefixed("word-exists"));
                case ERROR -> viewer.sendMessage(plugin.getMessages().prefixed("word-add-failed"));
            }
        }
        HandlerList.unregisterAll(this);
    }

    @SuppressWarnings("unchecked")
    private void registerPaperListener() {
        try {
            Class<? extends org.bukkit.event.Event> clazz =
                (Class<? extends org.bukkit.event.Event>) Class.forName("com.destroystokyo.paper.event.inventory.PrepareResultEvent");
            org.bukkit.Bukkit.getPluginManager().registerEvent(
                    clazz,
                    this,
                    org.bukkit.event.EventPriority.NORMAL,
                    (listener, event) -> {
                        try {
                            Object inventory = event.getClass().getMethod("getInventory").invoke(event);
                            if (inventory instanceof org.bukkit.inventory.AnvilInventory anv && inventory.equals(this.inventory)) {
                                handlePrepare(anv, (org.bukkit.event.Event) event);
                            }
                        } catch (Exception ignored) {
                        }
                    },
                    plugin,
                    true);
        } catch (ClassNotFoundException ignored) {
            // Paper event not present
        }
    }
}
