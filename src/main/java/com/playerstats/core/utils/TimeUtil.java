package com.playerstats.core.utils;

import com.playerstats.core.config.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Утилита для форматирования времени (с поддержкой мультиязычности).
 */
public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Форматировать время для игрока (на его языке).
     *
     * @param lang   менеджер языков
     * @param player игрок (для определения языка)
     * @param totalSeconds секунды
     */
    public static String formatTime(LanguageManager lang, Player player, int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;

        if (totalSeconds < 60) {
            return lang.get(player, "time.seconds",
                    "seconds", totalSeconds);
        }

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        if (hours > 0) {
            return lang.get(player, "time.hours",
                    "hours", hours,
                    "minutes", minutes);
        }

        return lang.get(player, "time.minutes",
                "minutes", minutes);
    }

    /**
     * Форматировать время для консоли (defaultLang из config.yml).
     */
    public static String formatTime(LanguageManager lang, int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;

        if (totalSeconds < 60) {
            return lang.get("time.seconds", "seconds", totalSeconds);
        }

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        if (hours > 0) {
            return lang.get("time.hours", "hours", hours, "minutes", minutes);
        }

        return lang.get("time.minutes", "minutes", minutes);
    }

    /**
     * Форматировать время для CommandSender — если игрок, то на его языке,
     * иначе — на defaultLang.
     */
    public static String formatTime(LanguageManager lang, CommandSender sender, int totalSeconds) {
        if (sender instanceof Player player) {
            return formatTime(lang, player, totalSeconds);
        }
        return formatTime(lang, totalSeconds);
    }

    /**
     * Форматировать время для double (для CompareCommand).
     */
    public static String formatTime(LanguageManager lang, Player player, double totalSeconds) {
        return formatTime(lang, player, (int) totalSeconds);
    }

    /**
     * Форматировать время для double + CommandSender.
     */
    public static String formatTime(LanguageManager lang, CommandSender sender, double totalSeconds) {
        return formatTime(lang, sender, (int) totalSeconds);
    }
}