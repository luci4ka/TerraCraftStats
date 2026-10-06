package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.database.dao.LeaderboardDAO;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Кэш топов. Обновляется раз в N секунд в фоне.
 */
public class TopCache {

    private final Main plugin;
    private final LeaderboardDAO leaderboardDAO;
    private final int serverId;
    private final int limit;

    /**
     * Кэш: statName → List<LeaderboardEntry>.
     */
    private final Map<String, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();

    /**
     * Время последнего обновления кэша (millis).
     */
    private volatile long lastUpdate = 0;

    /**
     * TTL кэша — 30 секунд.
     */
    private static final long CACHE_TTL_MS = 30_000L;

    /**
     * Список статистик для кэширования.
     */
    private static final String[] CACHED_STATS = {
            "play_time", "mobs_killed", "blocks_placed", "balance",
            "kills", "deaths", "bosses_killed", "blocks_broken"
    };

    public TopCache(Main plugin, LeaderboardDAO leaderboardDAO, int serverId, int limit) {
        this.plugin = plugin;
        this.leaderboardDAO = leaderboardDAO;
        this.serverId = serverId;
        this.limit = limit;

        startCacheUpdater();
    }

    /**
     * Запускает фоновое обновление кэша раз в 30 секунд.
     */
    private void startCacheUpdater() {
        plugin.getThreadManager().runAsyncRepeating(() -> {
            try {
                refreshAll();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка обновления кэша топов", e);
            }
        }, 30, 60); // первый запуск через 5 сек, потом каждые 30 сек
    }

    /**
     * Обновить весь кэш.
     */
    public void refreshAll() {
        for (String stat : CACHED_STATS) {
            try {
                List<LeaderboardEntry> top = leaderboardDAO.getTop(stat, serverId, limit);
                cache.put(stat, top);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка обновления топа '" + stat + "'", e);
            }
        }
        lastUpdate = System.currentTimeMillis();
    }

    /**
     * Обновить кэш для конкретной статистики.
     */
    public void refresh(String statName) {
        try {
            List<LeaderboardEntry> top = leaderboardDAO.getTop(statName, serverId, limit);
            cache.put(statName, top);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка обновления топа '" + statName + "'", e);
        }
    }

    /**
     * Получить топ из кэша. Если кэш пуст — обновляет.
     */
    public List<LeaderboardEntry> getTop(String statName) {
        List<LeaderboardEntry> cached = cache.get(statName);
        if (cached == null) {
            refresh(statName);
            cached = cache.getOrDefault(statName, Collections.emptyList());
        }
        return cached;
    }

    /**
     * Инвалидировать весь кэш.
     */
    public void invalidateAll() {
        cache.clear();
        lastUpdate = 0;
    }

    /**
     * Инвалидировать конкретную статистику.
     */
    public void invalidate(String statName) {
        cache.remove(statName);
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public int getCachedSize() {
        return cache.size();
    }

    /**
     * Проверить, есть ли статистика в кэше.
     */
    public boolean isCached(String statName) {
        return cache.containsKey(statName);
    }
}