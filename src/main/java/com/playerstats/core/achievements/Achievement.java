package com.playerstats.core.achievements;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Достижение.
 *
 * @param id           уникальный ID
 * @param name         отображаемое имя
 * @param description  описание
 * @param icon         Material для GUI
 * @param stat         статистика, которая проверяется (например, "kills")
 * @param threshold    порог (например, 100)
 * @param commands     команды для выполнения (от консоли)
 * @param broadcast    сообщать ли в чат
 * @param category     категория ("pvp", "pve", "blocks", ...)
 * @param reset        период сброса: null | "daily" | "weekly" | "monthly"
 * @param repeatReward выдавать ли награду повторно после сброса
 */
public record Achievement(
        String id,
        String name,
        String description,
        String icon,
        String stat,
        int threshold,
        List<String> commands,
        boolean broadcast,
        String category,
        @Nullable String reset,
        boolean repeatReward
) {

    /** Временное ли достижение (есть reset). */
    public boolean isTemporary() {
        return reset != null && !reset.isEmpty();
    }

    /** Длительность периода в миллисекундах. -1 — если не временное. */
    public long resetIntervalMs() {
        if (!isTemporary()) return -1L;
        return switch (reset.toLowerCase()) {
            case "daily"   -> 24L * 3600 * 1000;
            case "weekly"  -> 7L * 24 * 3600 * 1000;
            case "monthly" -> 30L * 24 * 3600 * 1000;
            default        -> -1L;
        };
    }
}