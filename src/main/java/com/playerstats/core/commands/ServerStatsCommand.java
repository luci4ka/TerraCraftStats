package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.TotalServerDAO;
import com.playerstats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;

public class ServerStatsCommand {

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;

    public ServerStatsCommand(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
    }

    public void execute(CommandSender sender) {
        if (!sender.hasPermission("playerstats.serverstats")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                TotalServerDAO totalServerDAO = new TotalServerDAO(
                        plugin, plugin.getConnection(), plugin.getStatTypeDAO());

                Map<String, Number> stats = totalServerDAO.getServerStats(serverId);

                plugin.getThreadManager().runSync(() -> sendServerStats(sender, stats));

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка статистики сервера: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "error")));
            }
        });
    }

    // ==================== SEND ====================

    private void sendServerStats(CommandSender sender, Map<String, Number> stats) {
        Component separator = getComponent(sender, "stats.header");
        String serverDisplay = config.getServerDisplayName(plugin.getServerName());

        sender.sendMessage(separator);
        sender.sendMessage(getComponent(sender, "serverstats.title",
                "server", serverDisplay));
        sender.sendMessage(separator);

        // ==================== ИГРОКИ ====================
        sender.sendMessage(getComponent(sender, "serverstats.section-players"));

        Number uniquePlayers = stats.getOrDefault("uniquePlayers", 0);
        sendStatLine(sender, "serverstats.unique-players",
                String.format("%,d", uniquePlayers.longValue()),
                null, null);

        // ==================== АКТИВНОСТЬ ====================
        sender.sendMessage(getComponent(sender, "serverstats.section-activity"));

        sendStat(sender, stats, "play_time",
                "play_time",
                "serverstats.stat-playtime-hover");

        sendStat(sender, stats, "mobs_killed",
                "mobs_killed",
                "serverstats.stat-mobs-hover");

        sendStat(sender, stats, "bosses_killed",
                "bosses_killed",
                "serverstats.stat-bosses-hover");

        sendStat(sender, stats, "kills",
                "kills",
                "serverstats.stat-kills-hover");

        sendStat(sender, stats, "deaths",
                "deaths",
                "serverstats.stat-deaths-hover");

        // ==================== БЛОКИ ====================
        sender.sendMessage(getComponent(sender, "serverstats.section-blocks"));

        sendStat(sender, stats, "blocks_placed",
                "blocks_placed",
                "serverstats.stat-blocks-placed-hover");

        sendStat(sender, stats, "blocks_broken",
                "blocks_broken",
                "serverstats.stat-blocks-broken-hover");

        // ==================== ЭКОНОМИКА ====================
        sender.sendMessage(getComponent(sender, "serverstats.section-economy"));

        Number balance = stats.getOrDefault("balance", 0);
        sendStatLine(sender, "serverstats.line-balance",
                formatBalance(balance.doubleValue()),
                "serverstats.stat-balance-hover",
                "balance");

        sender.sendMessage(separator);
    }

    // ==================== LINE ====================

    /**
     * Отправить строку статистики с hover и (опционально) кликом.
     *
     * @param hoverLangKey ключ в lang.yml для hover (может быть null)
     * @param statKey     ключ статистики для /top (может быть null)
     */
    private void sendStat(CommandSender sender, Map<String, Number> stats,
                          String key, String statKey,
                          String hoverLangKey) {
        Number value = stats.get(key);
        if (value == null) return;

        String formatted;
        if (key.equals("play_time")) {
            formatted = TimeUtil.formatTime(lang, sender, value.intValue());
        } else if (key.equals("balance")) {
            formatted = formatBalance(value.doubleValue());
        } else {
            formatted = String.format("%,d", value.longValue());
        }

        sendStatLine(sender, null, formatted, hoverLangKey, statKey);
    }

    /**
     * Отправить строку.
     */
    private void sendStatLine(CommandSender sender, String langKey,
                              String formatted,
                              String hoverLangKey, String statKey) {
        Component line;

        if (langKey != null) {
            line = getComponent(sender, langKey, "value", formatted);
        } else {
            // Для sendStat — собираем как serverstats.line
            line = getComponent(sender, "serverstats.line",
                    "value", formatted);
        }

        // Hover
        if (hoverLangKey != null) {
            String hoverText = getRaw(sender, hoverLangKey);
            Component hoverComponent = getComponentFromString(sender,
                    hoverText + (statKey != null
                            ? "\n<dark_gray>Нажмите для просмотра /top " + statKey
                            : ""));
            line = line.hoverEvent(HoverEvent.showText(hoverComponent));
        }

        // Клик
        if (statKey != null) {
            line = line.clickEvent(ClickEvent.runCommand("/top " + statKey));
        }

        sender.sendMessage(line);
    }

    // ==================== FORMAT ====================

    private String formatBalance(double value) {
        return String.format("%,.2f₽", value);
    }

    // ==================== LANG HELPERS ====================

    /**
     * Получить Component для sender (игрок → его язык, консоль → defaultLang).
     */
    private Component getComponent(CommandSender sender, String key, Object... replacements) {
        if (sender instanceof Player player) {
            return lang.getComponent(player, key, replacements);
        }
        return lang.getComponent(key, replacements);
    }

    /**
     * Получить сырую строку для sender.
     */
    private String getRaw(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            return lang.getRaw(player, key);
        }
        return lang.getRaw(key);
    }

    /**
     * Получить Component из готовой строки для sender.
     */
    private Component getComponentFromString(CommandSender sender, String message, Object... replacements) {
        return lang.getComponentFromString(message, replacements);
    }
}