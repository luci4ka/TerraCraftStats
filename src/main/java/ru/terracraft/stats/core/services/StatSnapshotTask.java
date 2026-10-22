package ru.terracraft.stats.core.services;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.time.LocalDate;
import java.util.logging.Level;

/**
 * Раз в час проверяет, не наступил ли новый день.
 * Если да — сохраняет снапшот статистики за сегодня.
 *
 * <p>Время первого запуска — 60 сек, дальше — раз в 3600 сек.</p>
 */
public class StatSnapshotTask {

    private final PlayerStatsPlugin plugin;
    private LocalDate lastSnapshotDate;

    public StatSnapshotTask(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.lastSnapshotDate = LocalDate.now().minusDays(1);
        start();
    }

    private void start() {
        plugin.getThreadManager().runAsyncRepeating(() -> {
            LocalDate today = LocalDate.now();
            if (today.equals(lastSnapshotDate)) return;

            plugin.getLogger().info("Сохранение снапшота статистики за " + today + "...");

            try {
                plugin.getStatHistoryDAO().snapshotAllPlayers();
                lastSnapshotDate = today;
                plugin.getLogger().info("Снапшот сохранён.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка снапшота", e);
            }
        }, 60, 3600);
    }
}