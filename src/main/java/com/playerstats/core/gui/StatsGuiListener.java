package com.playerstats.core.gui;

import com.playerstats.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class StatsGuiListener implements Listener {

    private final Main plugin;

    public StatsGuiListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof StatsGui.StatsGuiHolder guiHolder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getSlot();
        int page = guiHolder.getPage();
        String subPage = guiHolder.getSubPage();

        // ==================== ГЛАВНАЯ СТРАНИЦА ====================
        if (page == 0 && subPage == null) {
            switch (slot) {
                case 10 -> plugin.getStatsGui().openPage(player, 0, "blocks_placed");
                case 12 -> plugin.getStatsGui().openPage(player, 0, "mobs");
                case 16 -> plugin.getStatsGui().openPage(player, 0, "pvp");
                case 32 -> plugin.getStatsGui().openPage(player, 0, "blocks_placed");
                case 34 -> plugin.getStatsGui().openPage(player, 0, "blocks_broken");
                case 49 -> player.closeInventory();
                default -> { /* ничего */ }
            }
            return;
        }

        // ==================== ПОДСТРАНИЦА ====================
        if (slot == 49) {
            // Кнопка "Назад"
            plugin.getStatsGui().openPage(player, 0, null);
            return;
        }

        // Навигация
        if (slot == 48) {
            // Предыдущая
            if (page > 0) {
                plugin.getStatsGui().openPage(player, page - 1, subPage);
            }
            return;
        }
        if (slot == 50) {
            // Следующая
            plugin.getStatsGui().openPage(player, page + 1, subPage);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StatsGui.StatsGuiHolder) {
            event.setCancelled(true);
        }
    }
}