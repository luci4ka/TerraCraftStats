package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.database.dao.LeaderboardDAO;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Кэш топов. Обновляется раз в N секунд в фоне.
 * Также хранит UUID #1 для каждой категории (для top-chips).
 */
public class TopCache {

    private final Main plugin;
    private final LeaderboardDAO leaderboardDAO;
    private final int serverId;
    private final int limit;

    private final Map<String, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();

    /**
     * Кэш топ-1 UUID для каждой категории (statName → UUID #1).
     */
    private final Map<String, UUID> top1Cache = new ConcurrentHashMap<>();

    private volatile long lastUpdate = 0;

    private static final long CACHE_TTL_MS = 30_000L;

    private static final String[] CACHED_STATS = {
            "play_time", "mobs_killed", "blocks_placed", "balance",
            "kills", "deaths", "bosses_killed", "blocks_broken"
    };

    /**
     * Соответствие: statName → категория из config.yml (top-chips.chips).
     */
    private static final Map<String, String> TOP_CHIP_CATEGORIES = Map.of(
            "kills", "kills",
            "mobs_killed", "mobs",
            "blocks_placed", "blocks",
            "play_time", "playtime",
            "balance", "balance"
    );

    public TopCache(Main plugin, LeaderboardDAO leaderboardDAO, int serverId, int limit) {
        this.plugin = plugin;
        this.leaderboardDAO = leaderboardDAO;
        this.serverId = serverId;
        this.limit = limit;

        startCacheUpdater();
        startTop1Updater();
    }

    /**
     * Крон: обновление основного кэша топов.
     */
    private void startCacheUpdater() {
        plugin.getThreadManager().runAsyncRepeating(() -> {
            try {
                refreshAll();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка обновления кэша топов", e);
            }
        }, 5, 30);
    }

    /**
     * Крон: обновление top1Cache раз в N секунд (из config.yml).
     */
    private void startTop1Updater() {
        if (!plugin.getConfigHandler().isTopChipsEnabled()) {
            plugin.getLogger().info("Top-chips отключены в config.yml.");
            return;
        }

        long refreshSeconds = plugin.getConfigHandler().getTopChipsRefreshSeconds();
        plugin.getLogger().info("Top-chips: обновление раз в " + refreshSeconds + " сек.");

        plugin.getThreadManager().runAsyncRepeating(() -> {
            try {
                refreshAllTop1();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка обновления top-1", e);
            }
        }, 5, refreshSeconds);
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
     * Обновить все топ-1 UUID.
     */
    public void refreshAllTop1() {
        for (String stat : TOP_CHIP_CATEGORIES.keySet()) {
            refreshTop1(stat);
        }
    }

    /**
     * Обновить топ-1 для конкретной статистики.
     */
    private void refreshTop1(String statName) {
        try {
            List<LeaderboardEntry> top = leaderboardDAO.getTop(statName, serverId, 1);
            if (!top.isEmpty()) {
                top1Cache.put(statName, top.get(0).uuid());
            } else {
                top1Cache.remove(statName);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка обновления топ-1 для '" + statName + "'", e);
        }
    }

    /**
     * Получить UUID #1 для статистики.
     */
    public UUID getTop1Uuid(String statName) {
        return top1Cache.get(statName);
    }

    /**
     * Проверить, является ли игрок #1 в статистике.
     */
    public boolean isTop1(UUID uuid, String statName) {
        UUID top1 = top1Cache.get(statName);
        return top1 != null && top1.equals(uuid);
    }

    /**
     * Получить список категорий (для итерации).
     */
    public Set<String> getTopChipCategories() {
        return TOP_CHIP_CATEGORIES.keySet();
    }

    /**
     * Получить категорию для statName (kills → kills, mobs_killed → mobs).
     */
    public String getCategoryForStat(String statName) {
        return TOP_CHIP_CATEGORIES.getOrDefault(statName, statName);
    }

    public void refresh(String statName) {
        try {
            List<LeaderboardEntry> top = leaderboardDAO.getTop(statName, serverId, limit);
            cache.put(statName, top);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка обновления топа '" + statName + "'", e);
        }
    }

    public List<LeaderboardEntry> getTop(String statName) {
        List<LeaderboardEntry> cached = cache.get(statName);
        if (cached == null) {
            refresh(statName);
            cached = cache.getOrDefault(statName, Collections.emptyList());
        }
        return cached;
    }

    public void invalidateAll() {
        cache.clear();
        lastUpdate = 0;
    }

    public void invalidate(String statName) {
        cache.remove(statName);
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public int getCachedSize() {
        return cache.size();
    }

    public boolean isCached(String statName) {
        return cache.containsKey(statName);
    }
}