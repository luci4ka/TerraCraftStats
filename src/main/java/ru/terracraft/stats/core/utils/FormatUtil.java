package ru.terracraft.stats.core.utils;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FormatUtil {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
    private static final DecimalFormat BALANCE_FMT = new DecimalFormat("#,##0.00", SYMBOLS);

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};

    private static final String FB_DAYS = "d";
    private static final String FB_HOURS = "h";
    private static final String FB_MINUTES = "m";
    private static final String FB_LESS_THAN_MINUTE = "<1m";
    private static final String FB_INFINITE = "∞";
    private static final String FB_ZERO_KD = "0.00";
    private static final String FB_KD_FORMAT = "%.2f";

    private FormatUtil() {}

    // ==================== Баланс ====================

    public static String formatBalance(PlayerStatsPlugin plugin, LanguageManager lang,
                                       Player viewer, double balance) {
        if (plugin.getHooksManager() != null
                && plugin.getHooksManager().getVaultHook() != null
                && plugin.getHooksManager().getVaultHook().isEnabled()) {
            try {
                return plugin.getHooksManager().getVaultHook().format(balance);
            } catch (Exception ignored) {}
        }
        return formatBalance(lang, viewer, balance);
    }

    public static String formatBalance(PlayerStatsPlugin plugin, LanguageManager lang, double balance) {
        if (plugin.getHooksManager() != null
                && plugin.getHooksManager().getVaultHook() != null
                && plugin.getHooksManager().getVaultHook().isEnabled()) {
            try {
                return plugin.getHooksManager().getVaultHook().format(balance);
            } catch (Exception ignored) {}
        }
        return formatBalance(lang, balance);
    }

    /** Баланс без Vault (только lang, для игрока). */
    public static String formatBalance(LanguageManager lang, Player viewer, double balance) {
        if (lang != null && lang.hasKey(viewer, "currency.format")) {
            return lang.getRaw(viewer, "currency.format")
                    .replace("{value}", String.format(Locale.ROOT, "%.2f", balance));
        }
        return String.format(Locale.ROOT, "%.2f", balance);
    }

    /** Баланс без Vault (только lang, для консоли). */
    public static String formatBalance(LanguageManager lang, double balance) {
        if (lang != null && lang.hasKey("currency.format")) {
            return lang.getRaw("currency.format")
                    .replace("{value}", String.format(Locale.ROOT, "%.2f", balance));
        }
        return String.format(Locale.ROOT, "%.2f", balance);
    }

    /** Баланс без lang и Vault (просто число). */
    public static String formatBalancePlain(double value) {
        return BALANCE_FMT.format(value);
    }

    // ==================== Целые числа ====================

    /** Целое число с разделителями: 1234567 -> "1,234,567". */
    public static String formatInteger(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String formatInteger(int value) {
        return formatInteger((long) value);
    }

    // ==================== Сокращённые числа (K/M/B) ====================

    public static String formatNumber(int value) {
        return formatNumber((long) value);
    }

    public static String formatNumber(long value) {
        if (value < 0) return "-" + formatNumber(-value);
        if (value < 1000) return String.valueOf(value);

        int idx = 0;
        double v = value;
        while (v >= 999.95 && idx < SUFFIXES.length - 1) {
            v /= 1000.0;
            idx++;
        }

        String num = (Math.abs(v - Math.floor(v)) < 0.05)
                ? String.valueOf((long) Math.round(v))
                : String.format(Locale.ROOT, "%.1f", v);
        return num + SUFFIXES[idx];
    }

    // ==================== Время ====================

    public static String formatTimeFull(LanguageManager lang, CommandSender sender, int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;

        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        String d = suffix(lang, sender, "time.suffix.days", FB_DAYS);
        String h = suffix(lang, sender, "time.suffix.hours", FB_HOURS);
        String m = suffix(lang, sender, "time.suffix.minutes", FB_MINUTES);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(d).append(" ");
        if (hours > 0) sb.append(hours).append(h).append(" ");
        if (minutes > 0 || sb.length() == 0) sb.append(minutes).append(m);
        return sb.toString().trim();
    }

    public static String formatTimeShort(LanguageManager lang, CommandSender sender, int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        String h = suffix(lang, sender, "time.suffix.hours", FB_HOURS);
        String m = suffix(lang, sender, "time.suffix.minutes", FB_MINUTES);

        if (hours > 0) return hours + h + " " + minutes + m;
        return minutes + m;
    }

    public static String formatDurationCompact(LanguageManager lang, CommandSender sender, long millis) {
        String lessThanMinute = suffix(lang, sender, "time.less-than-minute", FB_LESS_THAN_MINUTE);
        if (millis <= 0) return lessThanMinute;

        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        String d = suffix(lang, sender, "time.suffix.days", FB_DAYS);
        String h = suffix(lang, sender, "time.suffix.hours", FB_HOURS);
        String m = suffix(lang, sender, "time.suffix.minutes", FB_MINUTES);

        if (days > 0) {
            return hours > 0 ? days + d + " " + hours + h : days + d;
        }
        if (hours > 0) {
            return minutes > 0 ? hours + h + " " + minutes + m : hours + h;
        }
        if (minutes > 0) return minutes + m;
        return lessThanMinute;
    }

    // ==================== K/D ====================

    public static String formatKD(LanguageManager lang, CommandSender sender, int kills, int deaths) {
        if (deaths == 0) {
            if (kills > 0) {
                return suffix(lang, sender, "kd.infinite", FB_INFINITE);
            }
            return suffix(lang, sender, "kd.zero", FB_ZERO_KD);
        }
        String fmt = suffix(lang, sender, "kd.format", FB_KD_FORMAT);
        try {
            return String.format(Locale.ROOT, fmt, (double) kills / deaths);
        } catch (Exception e) {
            return String.format(Locale.ROOT, FB_KD_FORMAT, (double) kills / deaths);
        }
    }

    public static String formatKD(LanguageManager lang, int kills, int deaths) {
        if (deaths == 0) {
            if (kills > 0) return suffix(lang, null, "kd.infinite", FB_INFINITE);
            return suffix(lang, null, "kd.zero", FB_ZERO_KD);
        }
        String fmt = suffix(lang, null, "kd.format", FB_KD_FORMAT);
        try {
            return String.format(Locale.ROOT, fmt, (double) kills / deaths);
        } catch (Exception e) {
            return String.format(Locale.ROOT, FB_KD_FORMAT, (double) kills / deaths);
        }
    }

    private static String suffix(LanguageManager lang, CommandSender sender,
                                 String key, String fallback) {
        if (lang == null) return fallback;
        try {
            if (sender instanceof Player player) {
                return lang.hasKey(player, key) ? lang.getRaw(player, key) : fallback;
            }
            return lang.hasKey(key) ? lang.getRaw(key) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}