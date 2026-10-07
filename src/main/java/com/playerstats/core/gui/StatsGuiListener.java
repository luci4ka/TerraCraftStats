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

        // ==================== ТАБЫ ====================
        switch (slot) {
            case 0 -> {
                plugin.getStatsGui().openPage(player, 0, null);
                return;
            }
            case 1 -> {
                plugin.getStatsGui().openPage(player, 0, "pvp");
                return;
            }
            case 2 -> {
                player.closeInventory();
                new AchievementsGui(plugin).open(player);
                return;
            }
        }

        // ==================== ГЛАВНАЯ СТРАНИЦА ====================
        if (page == 0 && subPage == null) {
            switch (slot) {
                // Кликабельные
                case 10 -> plugin.getStatsGui().openPage(player, 0, "blocks_placed");
                case 12 -> plugin.getStatsGui().openPage(player, 0, "mobs");
                case 16 -> plugin.getStatsGui().openPage(player, 0, "pvp");
                case 19 -> plugin.getStatsGui().openPage(player, 0, "killstreak");
                case 21 -> plugin.getStatsGui().openPage(player, 0, "distance");
                case 23 -> plugin.getStatsGui().openPage(player, 0, "damage");
                case 25 -> plugin.getStatsGui().openPage(player, 0, "craft");
                case 28 -> plugin.getStatsGui().openPage(player, 0, "arrows");
                case 30 -> plugin.getStatsGui().openPage(player, 0, "animals");
                case 32 -> plugin.getStatsGui().openPage(player, 0, "movement");
                case 34 -> plugin.getStatsGui().openPage(player, 0, "economy");

                // Время (инфо) — без действия
                // case 14 → ничего

                case 49 -> player.closeInventory();
                default -> { /* ничего */ }
            }
            return;
        }

        // ==================== ПОДСТРАНИЦА ====================
        if (slot == 49) {
            plugin.getStatsGui().openPage(player, 0, null);
            return;
        }

        if (slot == 48 && page > 0) {
            plugin.getStatsGui().openPage(player, page - 1, subPage);
            return;
        }
        if (slot == 50) {
            plugin.getStatsGui().openPage(player, page + 1, subPage);
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