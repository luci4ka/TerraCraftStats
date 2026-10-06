package com.playerstats.core.pvp;

import com.playerstats.Main;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит cooldown'ы и дневные счётчики по парам killer→victim
 * для анти-абуза PvP.
 *
 * <p>Две независимые проверки:</p>
 * <ul>
 *   <li><b>Cooldown</b> — не засчитывать убийство одного и того же игрока
 *       повторно в течение N секунд.</li>
 *   <li><b>Дневной лимит</b> — не более N засчитанных убийств одного игрока
 *       в сутки.</li>
 * </ul>
 *
 * <p>Если хотя бы одна проверка не прошла — {@code kills} не растёт,
 * но {@code pvp_kill_<uuid>} пишется всегда.</p>
 */
public class PvPTracker {

    private record Pair(UUID killer, UUID victim) {}

    private static final class DailyCounter {
        LocalDate day;
        int count;

        DailyCounter(LocalDate day, int count) {
            this.day = day;
            this.count = count;
        }
    }

    private final Main plugin;
    private final Map<Pair, Long> lastCounted = new ConcurrentHashMap<>();
    private final Map<Pair, DailyCounter> dailyCounters = new ConcurrentHashMap<>();

    private final long cooldownMillis;
    private final int dailyLimit;

    public PvPTracker(Main plugin) {
        this.plugin = plugin;
        long seconds = plugin.getConfigHandler().getPvpAntifarmCooldownSeconds();
        this.cooldownMillis = seconds * 1000L;
        this.dailyLimit = plugin.getConfigHandler().getPvpDailyLimitPerVictim();

        // Периодическая очистка (каждые 5 минут)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::cleanup, 20L * 60 * 5, 20L * 60 * 5);
    }

    /**
     * Комплексная проверка: можно ли засчитать полноценное убийство
     * (kills +1). Если да — счётчики обновляются.
     *
     * @return true, если убийство можно засчитать
     */
    public boolean shouldCount(UUID killer, UUID victim) {
        // 1. Cooldown
        if (!checkCooldown(killer, victim)) {
            return false;
        }

        // 2. Дневной лимит
        if (dailyLimit > 0 && !checkDailyLimit(killer, victim)) {
            return false;
        }

        // Обе проверки прошли — фиксируем
        markCounted(killer, victim);
        return true;
    }

    /**
     * Проверка cooldown (без обновления счётчика).
     */
    private boolean checkCooldown(UUID killer, UUID victim) {
        if (cooldownMillis <= 0) return true;

        Pair key = new Pair(killer, victim);
        Long last = lastCounted.get(key);
        if (last == null) return true;

        return (System.currentTimeMillis() - last) >= cooldownMillis;
    }

    /**
     * Проверка дневного лимита.
     * Если день сменился — счётчик сбрасывается.
     */
    private boolean checkDailyLimit(UUID killer, UUID victim) {
        Pair key = new Pair(killer, victim);
        LocalDate today = LocalDate.now();

        DailyCounter counter = dailyCounters.get(key);
        if (counter == null || !counter.day.equals(today)) {
            // Новый день — сбрасываем
            counter = new DailyCounter(today, 0);
            dailyCounters.put(key, counter);
        }

        return counter.count < dailyLimit;
    }

    /**
     * Зафиксировать факт засчитанного убийства:
     * обновить cooldown и увеличить дневной счётчик.
     */
    public void markCounted(UUID killer, UUID victim) {
        Pair key = new Pair(killer, victim);
        long now = System.currentTimeMillis();

        // Cooldown
        lastCounted.put(key, now);

        // Дневной счётчик
        if (dailyLimit > 0) {
            LocalDate today = LocalDate.now();
            DailyCounter counter = dailyCounters.computeIfAbsent(
                    key, k -> new DailyCounter(today, 0));

            if (!counter.day.equals(today)) {
                counter.day = today;
                counter.count = 0;
            }
            counter.count++;
        }
    }

    /**
     * Периодическая очистка старых записей.
     */
    private void cleanup() {
        long now = System.currentTimeMillis();

        // Cooldown — удаляем всё, что старше 2× cooldown
        if (cooldownMillis > 0) {
            lastCounted.entrySet().removeIf(
                    e -> (now - e.getValue()) > cooldownMillis * 2);
        }

        // Дневные счётчики — удаляем всё, что не за сегодня
        LocalDate today = LocalDate.now();
        dailyCounters.entrySet().removeIf(
                e -> !e.getValue().day.equals(today));
    }

    /**
     * Полная очистка (при выключении плагина).
     */
    public void clear() {
        lastCounted.clear();
        dailyCounters.clear();
    }

    /**
     * @return текущий дневной счётчик для пары (для отладки/статистики)
     */
    public int getDailyCount(UUID killer, UUID victim) {
        DailyCounter counter = dailyCounters.get(new Pair(killer, victim));
        if (counter == null || !counter.day.equals(LocalDate.now())) {
            return 0;
        }
        return counter.count;
    }
}