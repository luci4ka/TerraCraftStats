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

public class StatsGuiListener implements Listener {

    private final PlayerStatsPlugin plugin;

    public StatsGuiListener(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof StatsGui.StatsGuiHolder guiHolder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getSlot();
        if (slot < 0) return;

        int page = guiHolder.getPage();
        String subPage = guiHolder.getSubPage();

        // Табы
        switch (slot) {
            case 0 -> {
                plugin.getStatsGui().openPage(player, 0, null);
                return;
            }
            case 1 -> {
                plugin.getStatsGui().openPage(player, 0, "pvp");
                return;
            }
            case 8 -> {
                // Ссылка на достижения
                plugin.getAchievementsGui().open(player);
                return;
            }
        }

        // Главная страница
        if (page == 0 && subPage == null) {
            switch (slot) {
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
                case 37 -> plugin.getStatsGui().openPage(player, 0, "actions");
                case 39 -> plugin.getStatsGui().openPage(player, 0, "combat");
                case 41 -> plugin.getStatsGui().openPage(player, 0, "resources");
                case 49 -> player.closeInventory();
                default -> { }
            }
            return;
        }

        // Подстраница
        if (slot == 49) {
            plugin.getStatsGui().openPage(player, 0, null);
            return;
        }

        if (slot == 48 && page > 0) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.LIME_DYE) return;
            plugin.getStatsGui().openPage(player, page - 1, subPage);
            return;
        }
        if (slot == 50) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.LIME_DYE) return;
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