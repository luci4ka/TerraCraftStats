package com.playerstats.core.gui;

import com.playerstats.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class AchievementsGuiListener implements Listener {

    private final Main plugin;

    public AchievementsGuiListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AchievementsGui.AchievementsGuiHolder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Кнопка "Назад" → StatsGui
        if (event.getSlot() == 49) {
            plugin.getStatsGui().open(player);
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