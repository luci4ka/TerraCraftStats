package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.services.PlayerGroupService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class CompareCommand {

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public CompareCommand(Main plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
        this.groupService = groupService;
    }

    /**
     * Сравнить статистику двух игроков.
     */
    public void execute(Player viewer, OfflinePlayer target) {
        if (!viewer.hasPermission("playerstats.compare")) {
            viewer.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        if (target == null) {
            viewer.sendMessage(lang.getComponent("player-not-found", "player", "?"));
            return;
        }

        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            viewer.sendMessage(lang.getComponent("compare.self"));
            return;
        }

        String targetName = target.getName() != null ? target.getName() : "Unknown";

        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();
                int serverId = plugin.getServerId();

                int viewerId = playerDAO.getOrCreatePlayerId(
                        viewer.getUniqueId(), viewer.getName());
                int targetId = playerDAO.getPlayerId(target.getUniqueId());

                if (targetId == -1) {
                    plugin.getThreadManager().runSync(() -> {
                        if (viewer.isOnline()) {
                            viewer.sendMessage(lang.getComponent(
                                    "player-not-found", "player", targetName));
                        }
                    });
                    return;
                }

                // Загружаем статистику обоих
                ComparisonData viewerData = loadData(statsDAO, viewerId, serverId);
                ComparisonData targetData = loadData(statsDAO, targetId, serverId);

                plugin.getThreadManager().runSync(() -> {
                    if (!viewer.isOnline()) return;
                    sendComparison(viewer, viewer.getName(), targetName, viewerData, targetData);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка в CompareCommand: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage(lang.getComponent("error"));
                    }
                });
            }
        });
    }

    private ComparisonData loadData(PlayerStatsDAO statsDAO, int playerId, int serverId)
            throws SQLException {
        int blocksPlaced = statsDAO.getStat(playerId, serverId, "blocks_placed");
        int blocksBroken = statsDAO.getStat(playerId, serverId, "blocks_broken");
        int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
        int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");
        int playTime = statsDAO.getStat(playerId, serverId, "play_time");
        int kills = statsDAO.getStat(playerId, serverId, "kills");
        int deaths = statsDAO.getStat(playerId, serverId, "deaths");
        double balance = statsDAO.getStatDouble(playerId, serverId, "balance");

        return new ComparisonData(
                blocksPlaced, blocksBroken, mobsKilled, bossesKilled,
                playTime, kills, deaths, balance
        );
    }

    private void sendComparison(Player viewer, String name1, String name2,
                                ComparisonData d1, ComparisonData d2) {
        Component separator = lang.getComponent("compare.header");

        viewer.sendMessage(separator);
        viewer.sendMessage(lang.getComponent("compare.title",
                "player1", name1,
                "player2", name2));
        viewer.sendMessage(separator);

        sendCompareLine(viewer, "stats.blocks",
                d1.blocksPlaced(), d2.blocksPlaced(), Format.INTEGER);
        sendCompareLine(viewer, "stats.pve",
                d1.mobsKilled(), d2.mobsKilled(), Format.INTEGER);
        sendCompareLine(viewer, "compare.time",
                d1.playTime(), d2.playTime(), Format.TIME);
        sendCompareLine(viewer, "stats.pvp",
                d1.kills(), d2.kills(), Format.INTEGER);
        sendCompareLine(viewer, "compare.balance",
                d1.balance(), d2.balance(), Format.BALANCE);

        viewer.sendMessage(separator);
    }

    private void sendCompareLine(Player viewer, String langKey,
                                 double value1, double value2, Format format) {
        String v1 = formatValue(value1, format);
        String v2 = formatValue(value2, format);

        double diff = value1 - value2;

        // Определяем, кто выигрывает
        String diffText;
        NamedTextColor diffColor;

        if (diff > 0) {
            diffText = "+" + formatValue(diff, format);
            diffColor = NamedTextColor.GREEN;
        } else if (diff < 0) {
            diffText = formatValue(diff, format);
            diffColor = NamedTextColor.RED;
        } else {
            diffText = "=";
            diffColor = NamedTextColor.GRAY;
        }

        Component line = lang.getComponent(langKey + ".line")
                .append(Component.text("  ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(v1).color(NamedTextColor.WHITE))
                .append(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(v2).color(NamedTextColor.WHITE))
                .append(Component.text("  (").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(diffText).color(diffColor))
                .append(Component.text(")").color(NamedTextColor.DARK_GRAY));

        viewer.sendMessage(line);
    }

    private String formatValue(double value, Format format) {
        return switch (format) {
            case INTEGER -> String.format("%,d", (long) value);
            case TIME -> formatTime((int) value);
            case BALANCE -> String.format("%.2f₽", value);
        };
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "ч " + minutes + "м";
        return minutes + "м";
    }

    private enum Format {
        INTEGER, TIME, BALANCE
    }

    private record ComparisonData(
            int blocksPlaced, int blocksBroken,
            int mobsKilled, int bossesKilled,
            int playTime, int kills, int deaths,
            double balance
    ) {}
}