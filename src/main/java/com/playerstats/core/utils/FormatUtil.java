package com.playerstats.core.utils;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Утилита для форматирования чисел, валюты, времени.
 */
public final class FormatUtil {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
    private static final DecimalFormat BALANCE_FMT = new DecimalFormat("#,##0.00", SYMBOLS);

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};

    private FormatUtil() {}

    // ==================== BALANCE ====================

    /**
     * Формат баланса.
     *
     * <p>Приоритет:</p>
     * <ol>
     *   <li>Vault — если подключён, берём его формат</li>
     *   <li>lang.yml → currency.format — fallback</li>
     *   <li>%.2f — если нет ключа</li>
     * </ol>
     */
    public static String formatBalance(Main plugin, LanguageManager lang,
                                       Player viewer, double balance) {
        // 1. Vault
        if (plugin.getHooksManager() != null
                && plugin.getHooksManager().getVaultHook() != null
                && plugin.getHooksManager().getVaultHook().isEnabled()) {
            try {
                return plugin.getHooksManager().getVaultHook().format(balance);
            } catch (Exception e) {
                // fallback
            }
        }

        // 2. lang.yml → currency.format
        String format = lang.getRaw(viewer, "currency.format");
        if (!format.startsWith("<red>Missing key")) {
            return format.replace("{value}", String.format("%.2f", balance));
        }

        // 3. Fallback
        return String.format("%.2f", balance);
    }

    /**
     * Формат баланса для консоли.
     */
    public static String formatBalance(Main plugin, LanguageManager lang, double balance) {
        if (plugin.getHooksManager() != null
                && plugin.getHooksManager().getVaultHook() != null
                && plugin.getHooksManager().getVaultHook().isEnabled()) {
            try {
                return plugin.getHooksManager().getVaultHook().format(balance);
            } catch (Exception e) {
                // fallback
            }
        }

        String format = lang.getRaw("currency.format");
        if (!format.startsWith("<red>Missing key")) {
            return format.replace("{value}", String.format("%.2f", balance));
        }

        return String.format("%.2f", balance);
    }

    /**
     * Баланс с разделителями тысяч: 1234.5 → "1,234.50".
     */
    public static String formatBalancePlain(double value) {
        return BALANCE_FMT.format(value);
    }

    // ==================== NUMBERS ====================

    /**
     * Сокращение чисел: 1234 → "1.2K", 1_500_000 → "1.5M".
     */
    public static String formatNumber(int value) {
        return formatNumber((long) value);
    }

    /**
     * Сокращение чисел (long): 1234 → "1.2K", 1_500_000_000 → "1.5B".
     */
    public static String formatNumber(long value) {
        if (value < 0) return "-" + formatNumber(-value);
        if (value < 1000) return String.valueOf(value);

        int idx = 0;
        double v = value;
        while (v >= 1000 && idx < SUFFIXES.length - 1) {
            v /= 1000.0;
            idx++;
        }
        String num = (v == Math.floor(v))
                ? String.valueOf((long) v)
                : String.format(Locale.US, "%.1f", v);
        return num + SUFFIXES[idx];
    }

    /**
     * K/D.
     */
    public static String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
    }

    // ==================== TIME ====================

    /**
     * Полный формат времени: "2д 3ч 5м" (без lang).
     * Для PAPI `_formatted`-плейсхолдеров.
     */
    public static String formatTimeFull(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;

        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0) sb.append(hours).append("ч ");
        if (minutes > 0 || sb.length() == 0) sb.append(minutes).append("м");
        return sb.toString().trim();
    }

    /**
     * Короткий формат времени: "2ч 5м" или "5м".
     */
    public static String formatTimeShort(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "ч " + minutes + "м";
        return minutes + "м";
    }

    /**
     * Компактный формат оставшегося времени (для GUI/PAPI).
     * Не показывает нулевые хвосты:
     * <ul>
     *   <li>{@code 1д 1ч} — есть дни и часы</li>
     *   <li>{@code 1д} — ровно день</li>
     *   <li>{@code 5ч 12м} — есть часы и минуты</li>
     *   <li>{@code 5ч} — ровно часы</li>
     *   <li>{@code 42м} — только минуты</li>
     *   <li>{@code <1м} — меньше минуты или отрицательное</li>
     * </ul>
     */
    public static String formatDurationCompact(long millis) {
        if (millis <= 0) return "<1м";

        long seconds = millis / 1000;
        long days    = seconds / 86400;
        long hours   = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return hours > 0 ? days + "д " + hours + "ч" : days + "д";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + "ч " + minutes + "м" : hours + "ч";
        }
        if (minutes > 0) return minutes + "м";
        return "<1м";
    }
}