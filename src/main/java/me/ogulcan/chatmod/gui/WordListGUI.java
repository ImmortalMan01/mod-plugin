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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * Paginated GUI listing blocked words for removal.
 */
public class WordListGUI implements Listener {
    private static final int PAGE_SIZE = 45;

    private final Main plugin;
    private final Player viewer;
    private Inventory inventory;
    private int page = 0;
    private boolean openingNew = false;

    public WordListGUI(Main plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        openPage(0);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private List<String> loadWords() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(plugin.getBlockedWordsFile());
        return cfg.getStringList("blocked-words");
    }

    private void openPage(int p) {
        List<String> all = loadWords();
        int total = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (total == 0) total = 1;
        this.page = Math.max(0, Math.min(p, total - 1));
        String title = plugin.getMessages().get("blocked-words-title", page + 1, total);
        inventory = Bukkit.createInventory(null, 54, title);
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            String word = all.get(start + i);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.RED + word);
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
        if (page > 0) inventory.setItem(45, item(Material.ARROW, plugin.getMessages().get("blocked-words-prev")));
        if (start + PAGE_SIZE < all.size()) inventory.setItem(53, item(Material.ARROW, plugin.getMessages().get("blocked-words-next")));
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
            int slot = e.getRawSlot();
            if (slot == 45) {
                openPage(page - 1);
            } else if (slot == 53) {
                openPage(page + 1);
            } else {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    String word = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                    if (plugin.removeBlockedWord(word)) {
                        viewer.sendMessage(plugin.getMessages().prefixed("word-removed", word));
                        openPage(page);
                    }
                }
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
