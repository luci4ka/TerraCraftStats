package ru.terracraft.stats.core.pvp;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Учёт PvP-киллов с анти-фармом.
 *
 * <p><b>Потокобезопасность:</b> {@link #shouldCount} атомарно проверяет кулдаун
 * и дневной лимит, и сразу же их обновляет, через {@link ConcurrentHashMap#compute}.
 * Это исключает двойной зачёт одного и того же убийства.</p>
 *
 * <p><b>Cleanup:</b> раз в 5 минут удаляются устаревшие записи:
 * {@code lastCounted} — старше 2× кулдауна (или суток, если кулдаун выключен),
 * {@code dailyCounters} — за прошлые дни.</p>
 */
public class PvPTracker {

    /** Период cleanup: 5 минут. */
    private static final long CLEANUP_PERIOD_SECONDS = 5L * 60;

    private record Pair(UUID killer, UUID victim) {}

    private record DailyCounter(LocalDate day, int count) {}

    private final PlayerStatsPlugin plugin;

    private final Map<Pair, Long> lastCounted = new ConcurrentHashMap<>();
    private final Map<Pair, DailyCounter> dailyCounters = new ConcurrentHashMap<>();

    private final long cooldownMillis;
    private final int dailyLimit;

    public PvPTracker(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        var pvp = plugin.getConfigManager().main().pvp();
        this.cooldownMillis = pvp.antifarmCooldownSeconds() * 1000L;
        this.dailyLimit = pvp.dailyLimitPerVictim();

        plugin.getThreadManager().runAsyncRepeating(this::cleanup,
                CLEANUP_PERIOD_SECONDS, CLEANUP_PERIOD_SECONDS);
    }

    /**
     * Атомарно проверяет и обновляет состояние.
     *
     * <p>Возвращает {@code true}, если kill нужно засчитать
     * (и сразу же помечает его как засчитанный).</p>
     */
    public boolean shouldCount(UUID killer, UUID victim) {
        Pair key = new Pair(killer, victim);
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        if (cooldownMillis > 0) {
            final boolean[] allowed = {false};
            lastCounted.compute(key, (k, prev) -> {
                if (prev != null && (now - prev) < cooldownMillis) {
                    allowed[0] = false;
                    return prev;
                }
                allowed[0] = true;
                return now;
            });
            if (!allowed[0]) return false;
        } else {
            lastCounted.put(key, now);
        }

        if (dailyLimit > 0) {
            final boolean[] allowed = {false};
            dailyCounters.compute(key, (k, counter) -> {
                if (counter == null || !counter.day().equals(today)) {
                    counter = new DailyCounter(today, 0);
                }
                if (counter.count() >= dailyLimit) {
                    allowed[0] = false;
                    return counter;
                }
                allowed[0] = true;
                return new DailyCounter(today, counter.count() + 1);
            });
            if (!allowed[0]) return false;
        }

        return true;
    }

    /**
     * @deprecated {@link #shouldCount} теперь сам обновляет состояние.
     * Оставлен только для обратной совместимости.
     */
    @Deprecated
    public void markCounted(UUID killer, UUID victim) {
        // no-op
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        if (cooldownMillis > 0) {
            lastCounted.entrySet().removeIf(e -> (now - e.getValue()) > cooldownMillis * 2);
        } else {
            lastCounted.entrySet().removeIf(e -> (now - e.getValue()) > 24L * 3600 * 1000);
        }
        LocalDate today = LocalDate.now();
        dailyCounters.entrySet().removeIf(e -> !e.getValue().day().equals(today));
    }

    public void clear() {
        lastCounted.clear();
        dailyCounters.clear();
    }

    public int getDailyCount(UUID killer, UUID victim) {
        DailyCounter counter = dailyCounters.get(new Pair(killer, victim));
        if (counter == null || !counter.day().equals(LocalDate.now())) return 0;
        return counter.count();
    }
}