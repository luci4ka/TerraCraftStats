package ru.terracraft.stats.core.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.api.event.PlayerStatChangeEvent;

public class AchievementsListener implements Listener {

    private final PlayerStatsPlugin plugin;

    public AchievementsListener(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStatChange(PlayerStatChangeEvent event) {
        if (event.getNewValue() <= event.getOldValue()) return;

        var player = Bukkit.getPlayer(event.getPlayerUuid());
        if (player == null) return;

        plugin.getAchievementsService().check(player, event.getStat(), event.getNewValue());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAchievementsService().unload(event.getPlayer().getUniqueId());
    }
}