package ru.terracraft.stats.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.terracraft.stats.PlayerStatsPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AchievementsGuiListener implements Listener {

    private final PlayerStatsPlugin plugin;

    public AchievementsGuiListener(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AchievementsGui.AchievementsGuiHolder gui)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getSlot();
        if (slot < 0) return;

        // --- Табы категорий (строка 0) ---
        if (slot >= 0 && slot <= 8) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            var cfg = plugin.getConfigManager().achievements();
            if (cfg == null) return;

            List<String> categories = new ArrayList<>(cfg.achievements().keySet());
            Collections.sort(categories);

            if (slot < categories.size()) {
                plugin.getAchievementsGui().openCategory(player, categories.get(slot), 0);
            }
            return;
        }

        // --- Навигация ---
        if (slot == 49) { // Закрыть
            player.closeInventory();
            return;
        }
        if (slot == 48 && gui.getPage() > 0) { // Назад
            plugin.getAchievementsGui().openCategory(player, gui.getStatKey(), gui.getPage() - 1);
            return;
        }
        if (slot == 50) { // Вперёд
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.ARROW) return;
            plugin.getAchievementsGui().openCategory(player, gui.getStatKey(), gui.getPage() + 1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AchievementsGui.AchievementsGuiHolder) {
            event.setCancelled(true);
        }
    }
}