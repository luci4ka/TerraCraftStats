package com.playerstats.core.services;

import com.playerstats.Main;

import java.time.LocalDate;
import java.util.logging.Level;

public class StatSnapshotTask {

    private final Main plugin;
    private LocalDate lastSnapshotDate;

    public StatSnapshotTask(Main plugin) {
        this.plugin = plugin;
        this.lastSnapshotDate = LocalDate.now().minusDays(1);
        start();
    }

    private void start() {
        // Раз в час проверяем — не наступил ли новый день
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