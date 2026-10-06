package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.ServerDAO;
import com.playerstats.core.database.dao.ServerStatsDAO;
import com.playerstats.core.database.dao.TotalServerDAO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

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
            sender.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                TotalServerDAO totalServerDAO = new TotalServerDAO(
                        plugin, plugin.getConnection(), plugin.getStatTypeDAO());

                Map<String, Number> stats = totalServerDAO.getServerStats(serverId);

                plugin.getThreadManager().runSync(() -> {
                    sendServerStats(sender, stats);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка статистики сервера: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(lang.getComponent("error")));
            }
        });
    }

    private void sendServerStats(CommandSender sender, Map<String, Number> stats) {
        Component separator = lang.getComponent("stats.header");
        String serverDisplay = config.getServerDisplayName(plugin.getServerName());

        sender.sendMessage(separator);
        sender.sendMessage(lang.getComponent("serverstats.title",
                "server", serverDisplay));
        sender.sendMessage(separator);

        // Уникальные игроки
        Number uniquePlayers = stats.getOrDefault("uniquePlayers", 0);
        sender.sendMessage(lang.getComponent("serverstats.unique-players",
                "value", uniquePlayers));

        // Остальные статы
        for (Map.Entry<String, Number> entry : stats.entrySet()) {
            String key = entry.getKey();
            if (key.equals("uniquePlayers")) continue;

            Number value = entry.getValue();
            String display = formatStatName(key);

            if (key.equals("play_time")) {
                sender.sendMessage(lang.getComponent("serverstats.line-time",
                        "name", display,
                        "value", formatTime(value.intValue())));
            } else if (key.equals("balance")) {
                sender.sendMessage(lang.getComponent("serverstats.line-balance",
                        "name", display,
                        "value", String.format("%.2f", value.doubleValue())));
            } else {
                sender.sendMessage(lang.getComponent("serverstats.line",
                        "name", display,
                        "value", String.format("%,d", value.longValue())));
            }
        }

        sender.sendMessage(separator);
    }

    private String formatStatName(String key) {
        return switch (key) {
            case "play_time" -> "Общее время игры";
            case "blocks_placed" -> "Поставлено блоков";
            case "blocks_broken" -> "Сломано блоков";
            case "mobs_killed" -> "Убито мобов";
            case "bosses_killed" -> "Убито боссов";
            case "kills" -> "Убийства игроков";
            case "deaths" -> "Смерти";
            case "balance" -> "Общий баланс";
            default -> prettify(key);
        };
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "ч " + minutes + "м";
        return minutes + "м";
    }

    private String prettify(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] parts = input.split("_");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase())
                    .append(" ");
        }

        return sb.toString().trim();
    }
}