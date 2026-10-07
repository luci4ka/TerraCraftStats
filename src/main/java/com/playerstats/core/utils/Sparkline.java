package com.playerstats.core.utils;

import java.util.List;

/**
 * Генератор ASCII-sparkline.
 */
public class Sparkline {

    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    /**
     * Построить sparkline из списка значений.
     */
    public static String generate(List<Integer> values) {
        if (values == null || values.isEmpty()) return "";
        if (values.size() == 1) return String.valueOf(BARS[3]);

        int min = values.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = values.stream().mapToInt(Integer::intValue).max().orElse(0);

        if (min == max) {
            return String.valueOf(BARS[3]).repeat(values.size());
        }

        StringBuilder sb = new StringBuilder();
        for (int v : values) {
            int level = (int) Math.round((double) (v - min) / (max - min) * (BARS.length - 1));
            level = Math.max(0, Math.min(BARS.length - 1, level));
            sb.append(BARS[level]);
        }
        return sb.toString();
    }

    /**
     * Построить sparkline с указанием min/max в скобках.
     */
    public static String generatePretty(List<Integer> values) {
        if (values == null || values.isEmpty()) return "—";
        int first = values.get(0);
        int last = values.get(values.size() - 1);
        return generate(values) + " (" + first + " → " + last + ")";
    }
}