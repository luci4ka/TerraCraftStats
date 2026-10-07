package com.playerstats.core.utils;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import org.bukkit.entity.Player;

/**
 * Утилита для форматирования чисел, валюты.
 */
public final class FormatUtil {

    private FormatUtil() {}

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
     * Сокращение чисел: 1.5K, 2.3M.
     */
    public static String formatNumber(int value) {
        if (value < 1000) return String.valueOf(value);
        if (value < 1_000_000) return String.format("%.1fK", value / 1000.0);
        return String.format("%.1fM", value / 1_000_000.0);
    }

    /**
     * K/D.
     */
    public static String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
    }
}