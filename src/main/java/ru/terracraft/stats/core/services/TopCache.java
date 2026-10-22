package ru.terracraft.stats.core.services;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.database.dao.LeaderboardDAO;
import ru.terracraft.stats.core.database.dao.LeaderboardDAO.LeaderboardEntry;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;

/**
 * Кэш топов. Обновляется раз в {@link #CACHE_REFRESH_SECONDS} секунд в фоне.
 * Также хранит UUID #1 для каждой категории (для top-chips).
 *
 * <p><b>Никаких синхронных SQL-запросов:</b> {@link #getTop} <b>всегда</b>
 * возвращает то, что уже лежит в памяти. Если запись отсутствует (первый
 * вызов до первого cron'а или упавший refresh) — возвращается пустой список.
 * Refresh запускается <b>асинхронно</b> через {@link #scheduleRefresh}
 * (deduplicated: пока идёт запрос по стате, второй не стартует).</p>
 *
 * <p><b>Reload:</b> {@link #reloadConfig()} отменяет старые cron'ы,
 * сбрасывает кэш и запускает новые с актуальными настройками.</p>
 */
public class TopCache {

    /** Cron-период обновления {@link #cache}, секунды. */
    private static final long CACHE_REFRESH_SECONDS = 30L;

    /** Задержка перед первым запуском cron'а, секунды. */
    private static final long INITIAL_DELAY_SECONDS = 5L;

    /** Минимальный интервал между асинхронными refresh по одной стате (мс). */
    private static final long REFRESH_THROTTLE_MS = 5_000L;

    private final PlayerStatsPlugin plugin;
    private final LeaderboardDAO leaderboardDAO;
    private final int serverId;
    private final int limit;

    /** statName → список топ-записей. */
    private final Map<String, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();
    /** statName → timestamp последнего refresh (для throttle и метрик). */
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    /** statName → «refresh уже в полёте», чтобы не дублировать запросы. */
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

    /** statName → UUID #1 (для top-chips). */
    private final Map<String, UUID> top1Cache = new ConcurrentHashMap<>();

    private volatile long lastUpdate = 0;

    private static final String[] CACHED_STATS = {
            // базовые
            "play_time", "mobs_killed", "blocks_placed", "balance",
            "kills", "deaths", "bosses_killed", "blocks_broken",
            // новые — для /top
            "jumps", "diamonds_mined", "commands_used",
            "experience_gained", "villager_trades", "damage_blocked"
    };

    private static final Map<String, String> TOP_CHIP_CATEGORIES = Map.of(
            "kills", "kills",
            "mobs_killed", "mobs",
            "blocks_placed", "blocks",
            "play_time", "playtime",
            "balance", "balance"
    );

    /** Cron обновления основного кэша (для reloadConfig). */
    private volatile ScheduledFuture<?> cacheUpdaterTask;
    /** Cron обновления top-1 (для reloadConfig). */
    private volatile ScheduledFuture<?> top1UpdaterTask;

    public TopCache(PlayerStatsPlugin plugin, LeaderboardDAO leaderboardDAO,
                    int serverId, int limit) {
        this.plugin = plugin;
        this.leaderboardDAO = leaderboardDAO;
        this.serverId = serverId;
        this.limit = limit;

        startCacheUpdater();
        startTop1Updater();
    }

    // ==================== CRON ====================

    private void startCacheUpdater() {
        cacheUpdaterTask = plugin.getThreadManager().runAsyncRepeating(() -> {
            try {
                refreshAll();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка обновления кэша топов", e);
            }
        }, INITIAL_DELAY_SECONDS, CACHE_REFRESH_SECONDS);
    }

    private void startTop1Updater() {
        var cm = plugin.getConfigManager();
        if (!cm.main().modules().topChips() || cm.topChips() == null) {
            plugin.getLogger().info("Top-chips отключены в config.yml.");
            return;
        }
        long refreshSeconds = cm.topChips().refreshSeconds();
        plugin.getLogger().info("Top-chips: обновление раз в " + refreshSeconds + " сек.");
        top1UpdaterTask = plugin.getThreadManager().runAsyncRepeating(() -> {
            try { refreshAllTop1(); }
            catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка обновления top-1", e);
            }
        }, INITIAL_DELAY_SECONDS, refreshSeconds);
    }

    public void reloadConfig() {
        ScheduledFuture<?> c = cacheUpdaterTask;
        if (c != null && !c.isCancelled()) c.cancel(false);
        ScheduledFuture<?> t = top1UpdaterTask;
        if (t != null && !t.isCancelled()) t.cancel(false);
        cacheUpdaterTask = null;
        top1UpdaterTask = null;

        invalidateAll();
        inFlight.clear();

        startCacheUpdater();
        startTop1Updater();

        plugin.getLogger().info("TopCache: конфиг перечитан, cron'ы перезапущены.");
    }

    // ==================== REFRESH (async only) ====================

    /**
     * Обновить весь кэш. Вызывается из cron (async).
     */
    public void refreshAll() {
        for (String stat : CACHED_STATS) {
            refresh(stat);
        }
        lastUpdate = System.currentTimeMillis();
    }

    public void refreshAllTop1() {
        for (String stat : TOP_CHIP_CATEGORIES.keySet()) {
            refreshTop1(stat);
        }
    }

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
     * Синхронный refresh — <b>только для вызова из async</b>.
     * Не вызывать из main-потока.
     */
    public void refresh(String statName) {
        try {
            List<LeaderboardEntry> top = leaderboardDAO.getTop(statName, serverId, limit);
            cache.put(statName, top);
            cacheTimestamps.put(statName, System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка обновления топа '" + statName + "'", e);
        }
    }

    /**
     * Асинхронный refresh с throttle: если по этой стате запрос уже идёт или
     * недавно (меньше {@link #REFRESH_THROTTLE_MS}) выполнялся — пропускаем.
     */
    public void scheduleRefresh(String statName) {
        Long ts = cacheTimestamps.get(statName);
        if (ts != null && (System.currentTimeMillis() - ts) < REFRESH_THROTTLE_MS) {
            return;
        }
        if (inFlight.putIfAbsent(statName, Boolean.TRUE) != null) {
            return; // уже в полёте
        }
        plugin.getThreadManager().runAsync(() -> {
            try {
                refresh(statName);
            } finally {
                inFlight.remove(statName);
            }
        });
    }

    // ==================== TOP-1 (chips) ====================

    public UUID getTop1Uuid(String statName) {
        return top1Cache.get(statName);
    }

    public boolean isTop1(UUID uuid, String statName) {
        UUID top1 = top1Cache.get(statName);
        return top1 != null && top1.equals(uuid);
    }

    public Set<String> getTopChipCategories() {
        return TOP_CHIP_CATEGORIES.keySet();
    }

    public String getCategoryForStat(String statName) {
        return TOP_CHIP_CATEGORIES.getOrDefault(statName, statName);
    }

    // ==================== GET / INVALIDATE ====================

    /**
     * Возвращает текущий снапшот топа из памяти.
     *
     * <p><b>Никогда не блокирует main-поток.</b> Если данных нет —
     * возвращает пустой список и асинхронно запускает refresh
     * (throttled). Следующий вызов (через ~1–2 сек) вернёт данные.</p>
     */
    public List<LeaderboardEntry> getTop(String statName) {
        List<LeaderboardEntry> cached = cache.get(statName);
        if (cached == null) {
            scheduleRefresh(statName);
            return Collections.emptyList();
        }
        // Даже если данные «старые» — отдаём их. Cron всё равно обновит.
        return cached;
    }

    public void invalidateAll() {
        cache.clear();
        cacheTimestamps.clear();
        lastUpdate = 0;
    }

    public void invalidate(String statName) {
        cache.remove(statName);
        cacheTimestamps.remove(statName);
    }

    // ==================== STATS ====================

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