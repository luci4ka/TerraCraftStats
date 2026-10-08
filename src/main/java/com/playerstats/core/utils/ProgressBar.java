package com.playerstats.core.utils;

/**
 * Строит прогресс-бар для GUI/чата.
 */
public final class ProgressBar {

    private ProgressBar() {}

    /**
     * @param current текущее
     * @param max     максимум
     * @param length  длина полосы (кол-во символов)
     * @param filled  символ заполнения
     * @param empty   символ пустоты
     * @param color   MiniMessage-цвет заполнения (например, {@code <green>})
     * @param emptyColor MiniMessage-цвет пустоты
     */
    public static String build(int current, int max, int length,
                               char filled, char empty,
                               String color, String emptyColor) {
        if (max <= 0) max = 1;
        if (length <= 0) length = 1;

        double ratio = Math.min(1.0, (double) current / max);
        int filledCount = (int) Math.round(ratio * length);
        int emptyCount = length - filledCount;

        StringBuilder sb = new StringBuilder(length * 8);
        sb.append(color);
        for (int i = 0; i < filledCount; i++) sb.append(filled);
        sb.append(emptyColor);
        for (int i = 0; i < emptyCount; i++) sb.append(empty);
        return sb.toString();
    }

    /** Цвет по проценту: >=100 зелёный, >=50 жёлтый, >=25 золотой, иначе красный. */
    public static String colorForPercent(int percent) {
        if (percent >= 100) return "<green>";
        if (percent >= 50)  return "<yellow>";
        if (percent >= 25)  return "<gold>";
        return "<red>";
    }

    /**
     * Полоса с цветом, зависящим от процента.
     */
    public static String buildAuto(int current, int max, int length,
                                   char filled, char empty) {
        int percent = max > 0 ? Math.min(100, (current * 100) / max) : 0;
        return build(current, max, length, filled, empty,
                colorForPercent(percent), "<dark_gray>");
    }
}