package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.services.PlayerGroupService;
import com.playerstats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;

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

    // ==================== EXECUTE ====================

    public void execute(Player viewer, OfflinePlayer target) {
        if (!viewer.hasPermission("playerstats.compare")) {
            viewer.sendMessage(lang.getComponent(viewer, "no-permission"));
            return;
        }

        if (target == null) {
            viewer.sendMessage(lang.getComponent(viewer, "player-not-found",
                    "player", "?"));
            return;
        }

        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            viewer.sendMessage(lang.getComponent(viewer, "compare.self"));
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
                            viewer.sendMessage(lang.getComponent(viewer,
                                    "player-not-found", "player", targetName));
                        }
                    });
                    return;
                }

                ComparisonData d1 = loadData(statsDAO, viewerId, serverId);
                ComparisonData d2 = loadData(statsDAO, targetId, serverId);

                int wins1 = countWins(d1, d2);
                int wins2 = countWins(d2, d1);

                final int finalWins1 = wins1;
                final int finalWins2 = wins2;

                plugin.getThreadManager().runSync(() -> {
                    if (!viewer.isOnline()) return;
                    sendComparison(viewer, viewer.getName(), targetName,
                            d1, d2, finalWins1, finalWins2);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка в CompareCommand: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage(lang.getComponent(viewer, "error"));
                    }
                });
            }
        });
    }

    // ==================== LOAD ====================

    private ComparisonData loadData(PlayerStatsDAO statsDAO, int playerId, int serverId)
            throws SQLException {
        return new ComparisonData(
                statsDAO.getStat(playerId, serverId, "blocks_placed"),
                statsDAO.getStat(playerId, serverId, "blocks_broken"),
                statsDAO.getStat(playerId, serverId, "mobs_killed"),
                statsDAO.getStat(playerId, serverId, "bosses_killed"),
                statsDAO.getStat(playerId, serverId, "play_time"),
                statsDAO.getStat(playerId, serverId, "kills"),
                statsDAO.getStat(playerId, serverId, "deaths"),
                statsDAO.getStatDouble(playerId, serverId, "balance")
        );
    }

    // ==================== SEND ====================

    private void sendComparison(Player viewer, String name1, String name2,
                                ComparisonData d1, ComparisonData d2,
                                int wins1, int wins2) {
        Component separator = lang.getComponent(viewer, "compare.header");
        viewer.sendMessage(separator);

        // Заголовок
        viewer.sendMessage(lang.getComponent(viewer, "compare.title",
                "player1", name1,
                "player2", name2));

        // Победитель / Ничья
        if (wins1 != wins2) {
            String winner = wins1 > wins2 ? name1 : name2;
            int maxWins = Math.max(wins1, wins2);
            int minWins = Math.min(wins1, wins2);

            Component winnerLine = lang.getComponent(viewer, "compare.winner",
                    "winner", winner,
                    "wins1", maxWins,
                    "wins2", minWins);

            winnerLine = winnerLine.decorate(TextDecoration.BOLD);
            viewer.sendMessage(winnerLine);
        } else {
            viewer.sendMessage(lang.getComponent(viewer, "compare.draw",
                    "wins1", wins1,
                    "wins2", wins2));
        }

        viewer.sendMessage(separator);

        // Строки сравнения
        sendCompareLine(viewer, "compare.line-blocks",
                d1.blocksPlaced(), d2.blocksPlaced(), Format.INTEGER);
        sendCompareLine(viewer, "compare.line-pve",
                d1.mobsKilled(), d2.mobsKilled(), Format.INTEGER);
        sendCompareLine(viewer, "compare.line-time",
                d1.playTime(), d2.playTime(), Format.TIME);
        sendCompareLine(viewer, "compare.line-pvp",
                d1.kills(), d2.kills(), Format.INTEGER);

        // K/D
        double kd1 = formatKD(d1.kills(), d1.deaths());
        double kd2 = formatKD(d2.kills(), d2.deaths());
        sendCompareLine(viewer, "compare.line-kd", kd1, kd2, Format.DECIMAL);

        sendCompareLine(viewer, "compare.line-balance",
                d1.balance(), d2.balance(), Format.BALANCE);

        viewer.sendMessage(separator);

        // Кнопки перехода на статистику — из lang.yml
        Component p1Button = lang.getComponent(viewer, "compare.button",
                        "player", name1)
                .clickEvent(ClickEvent.runCommand("/stats " + name1))
                .hoverEvent(HoverEvent.showText(
                        lang.getComponent(viewer, "compare.button-hover",
                                "player", name1)));

        Component p2Button = lang.getComponent(viewer, "compare.button",
                        "player", name2)
                .clickEvent(ClickEvent.runCommand("/stats " + name2))
                .hoverEvent(HoverEvent.showText(
                        lang.getComponent(viewer, "compare.button-hover",
                                "player", name2)));

        viewer.sendMessage(Component.text("   ")
                .append(p1Button)
                .append(Component.text(" "))
                .append(p2Button));

        viewer.sendMessage(separator);
    }

    // ==================== LINE ====================

    private void sendCompareLine(Player viewer, String labelKey,
                                 double value1, double value2, Format format) {
        String label = lang.getRaw(viewer, labelKey);
        String v1 = formatValue(viewer, value1, format);
        String v2 = formatValue(viewer, value2, format);

        double diff = value1 - value2;

        boolean wins1 = diff > 0;
        boolean wins2 = diff < 0;

        String marker1 = wins1 ? "✓" : (wins2 ? "✗" : "=");
        String marker2 = wins2 ? "✓" : (wins1 ? "✗" : "=");

        NamedTextColor color1 = wins1 ? NamedTextColor.GREEN
                : (wins2 ? NamedTextColor.GRAY : NamedTextColor.WHITE);
        NamedTextColor color2 = wins2 ? NamedTextColor.GREEN
                : (wins1 ? NamedTextColor.GRAY : NamedTextColor.WHITE);

        // Разница
        String diffText;
        NamedTextColor diffColor;

        if (diff > 0) {
            diffText = "+" + formatValue(viewer, diff, format);
            diffColor = NamedTextColor.GREEN;
        } else if (diff < 0) {
            diffText = formatValue(viewer, diff, format);
            diffColor = NamedTextColor.RED;
        } else {
            diffText = "=";
            diffColor = NamedTextColor.DARK_GRAY;
        }

        // Hover — из lang.yml
        Component hover = lang.getComponentFromString(
                lang.get(viewer, "compare.hover-value-1", "value", v1) + "\n" +
                        lang.get(viewer, "compare.hover-value-2", "value", v2) + "\n" +
                        lang.get(viewer, "compare.hover-diff", "diff", diffText));

        Component line = Component.text()
                .append(Component.text(String.format("%-16s", label), NamedTextColor.YELLOW))
                .append(Component.text(marker1 + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(v1, color1))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(marker2 + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(v2, color2))
                .append(Component.text("  (" + diffText + ")", diffColor))
                .hoverEvent(HoverEvent.showText(hover))
                .build();

        viewer.sendMessage(line);
    }

    // ==================== WIN COUNT ====================

    private int countWins(ComparisonData d1, ComparisonData d2) {
        int wins = 0;
        if (d1.blocksPlaced() > d2.blocksPlaced()) wins++;
        if (d1.mobsKilled() > d2.mobsKilled()) wins++;
        if (d1.playTime() > d2.playTime()) wins++;
        if (d1.kills() > d2.kills()) wins++;
        if (d1.balance() > d2.balance()) wins++;

        double kd1 = formatKD(d1.kills(), d1.deaths());
        double kd2 = formatKD(d2.kills(), d2.deaths());
        if (kd1 > kd2) wins++;

        return wins;
    }

    // ==================== FORMAT ====================

    private String formatValue(Player viewer, double value, Format format) {
        return switch (format) {
            case INTEGER -> String.format("%,d", (long) value);
            case TIME -> TimeUtil.formatTime(lang, viewer, (int) value);
            case BALANCE -> formatBalance(viewer, value);
            case DECIMAL -> String.format("%.2f", value);
        };
    }

    /**
     * Баланс — из lang.yml (currency.format).
     */
    private String formatBalance(Player viewer, double value) {
        String fmt = lang.getRaw(viewer, "currency.format");
        if (fmt.startsWith("<red>Missing key")) fmt = "{value}₽";
        return fmt.replace("{value}", String.format("%,.2f", value));
    }

    private double formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? 999.0 : 0.0;
        return (double) kills / deaths;
    }

    private enum Format {
        INTEGER, TIME, BALANCE, DECIMAL
    }

    private record ComparisonData(
            int blocksPlaced, int blocksBroken,
            int mobsKilled, int bossesKilled,
            int playTime, int kills, int deaths,
            double balance
    ) {}
}