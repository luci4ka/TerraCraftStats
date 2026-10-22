package ru.terracraft.stats.api.model;

import java.util.Map;
import java.util.UUID;

/**
 * Иммутабельный снимок всех стат игрока, полученный одним запросом.
 *
 * <p><b>Семантика:</b></p>
 * <ul>
 *   <li>{@link #intStats()} — все int-статы сервера, включая нули,
 *       если они записаны в БД;</li>
 *   <li>{@link #doubleStats()} — все double-статы (например, {@code balance}),
 *       включая нули;</li>
 *   <li>{@link #stringStats()} — только не-{@code null} строки.</li>
 * </ul>
 *
 * <p><b>Отсутствие ключа</b> означает, что стата не записана в БД,
 * а не что значение 0. Для чтения используйте {@code getOrDefault(key, 0)},
 * а не {@code get(key)} — иначе получите {@code null}/{@code NullPointerException}.</p>
 *
 * @param uuid        UUID игрока
 * @param name        имя игрока на момент снимка ({@code null}, если не найдено)
 * @param intStats    int-статы (иммутабельно)
 * @param doubleStats double-статы (иммутабельно)
 * @param stringStats string-статы (иммутабельно)
 */
public record PlayerStatsSnapshot(
        UUID uuid,
        String name,
        Map<String, Integer> intStats,
        Map<String, Double> doubleStats,
        Map<String, String> stringStats
) {}