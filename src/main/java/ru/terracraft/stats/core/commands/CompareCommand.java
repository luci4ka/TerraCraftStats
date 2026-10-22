package ru.terracraft.stats.core.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.database.dao.PlayerDAO;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;
import ru.terracraft.stats.core.services.PlayerGroupService;
import ru.terracraft.stats.core.utils.FormatUtil;
import ru.terracraft.stats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Locale;

public class CompareCommand {

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public CompareCommand(PlayerStatsPlugin plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.groupService = groupService;
    }

    public void register() {
        new CommandAPICommand("compare")
                .withPermission("playerstats.compare")
                .withArguments(new StringArgument("player"))
                .executesPlayer((player, args) -> {
                    String targetName = (String) args.get("player");
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                        player.sendMessage(lang.getComponent(player,
                                "commands.player-not-found", "player", targetName));
                        return 0;
                    }
                    execute(player, target);
                    return 0;
                })
                .register();
    }

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

    private ComparisonData loadData(PlayerStatsDAO statsDAO, int playerId, int serverId)
            throws SQLException {
        return new ComparisonData(
                statsDAO.getStat(playerId, serverId, "blocks_placed"),
                statsDAO.getStat(playerId, serverId, "mobs_killed"),
                statsDAO.getStat(playerId, serverId, "play_time"),
                statsDAO.getStat(playerId, serverId, "kills"),
                statsDAO.getStat(playerId, serverId, "deaths"),
                statsDAO.getStatDouble(playerId, serverId, "balance")
        );
    }

    private void sendComparison(Player viewer, String name1, String name2,
                                ComparisonData d1, ComparisonData d2,
                                int wins1, int wins2) {
        Component separator = lang.getComponent(viewer, "compare.header");
        viewer.sendMessage(separator);

        viewer.sendMessage(lang.getComponent(viewer, "compare.title",
                "player1", name1,
                "player2", name2));

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

        sendCompareLine(viewer, "compare.line-blocks",
                d1.blocksPlaced(), d2.blocksPlaced(), Format.INTEGER);
        sendCompareLine(viewer, "compare.line-pve",
                d1.mobsKilled(), d2.mobsKilled(), Format.INTEGER);
        sendCompareLine(viewer, "compare.line-time",
                d1.playTime(), d2.playTime(), Format.TIME);
        sendCompareLine(viewer, "compare.line-pvp",
                d1.kills(), d2.kills(), Format.INTEGER);

        sendCompareKDLine(viewer, d1, d2);

        sendCompareLine(viewer, "compare.line-balance",
                d1.balance(), d2.balance(), Format.BALANCE);

        viewer.sendMessage(separator);

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

        String diffText;
        NamedTextColor diffColor;

        if (diff > 0) {
            diffText = "+" + formatValue(viewer, diff, format);
            diffColor = NamedTextColor.GREEN;
        } else if (diff < 0) {
            // Для отрицательной разницы берём abs и ставим "-" вручную —
            // иначе TimeUtil.formatTime(отрицательное) вернёт "0м".
            diffText = "-" + formatValue(viewer, Math.abs(diff), format);
            diffColor = NamedTextColor.RED;
        } else {
            diffText = "=";
            diffColor = NamedTextColor.DARK_GRAY;
        }

        Component hover = lang.getComponentFromString(
                lang.get(viewer, "compare.hover-value-1", "value", v1) + "\n" +
                        lang.get(viewer, "compare.hover-value-2", "value", v2) + "\n" +
                        lang.get(viewer, "compare.hover-diff", "diff", diffText));

        Component line = Component.text()
                .append(Component.text(label + " ", NamedTextColor.YELLOW))
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

    private void sendCompareKDLine(Player viewer, ComparisonData d1, ComparisonData d2) {
        String label = lang.getRaw(viewer, "compare.line-kd");

        String v1 = formatKDString(viewer, d1.kills(), d1.deaths());
        String v2 = formatKDString(viewer, d2.kills(), d2.deaths());

        int cmp = compareKD(d1.kills(), d1.deaths(), d2.kills(), d2.deaths());

        boolean wins1 = cmp > 0;
        boolean wins2 = cmp < 0;

        String marker1 = wins1 ? "✓" : (wins2 ? "✗" : "=");
        String marker2 = wins2 ? "✓" : (wins1 ? "✗" : "=");

        NamedTextColor color1 = wins1 ? NamedTextColor.GREEN
                : (wins2 ? NamedTextColor.GRAY : NamedTextColor.WHITE);
        NamedTextColor color2 = wins2 ? NamedTextColor.GREEN
                : (wins1 ? NamedTextColor.GRAY : NamedTextColor.WHITE);

        String diffText;
        NamedTextColor diffColor;
        if (cmp > 0) {
            diffText = ">";
            diffColor = NamedTextColor.GREEN;
        } else if (cmp < 0) {
            diffText = "<";
            diffColor = NamedTextColor.RED;
        } else {
            diffText = "=";
            diffColor = NamedTextColor.DARK_GRAY;
        }

        Component hover = lang.getComponentFromString(
                lang.get(viewer, "compare.hover-value-1", "value", v1) + "\n" +
                        lang.get(viewer, "compare.hover-value-2", "value", v2));

        Component line = Component.text()
                .append(Component.text(label + " ", NamedTextColor.YELLOW))
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

    private int countWins(ComparisonData d1, ComparisonData d2) {
        int wins = 0;
        if (d1.blocksPlaced() > d2.blocksPlaced()) wins++;
        if (d1.mobsKilled() > d2.mobsKilled()) wins++;
        if (d1.playTime() > d2.playTime()) wins++;
        if (d1.kills() > d2.kills()) wins++;
        if (d1.balance() > d2.balance()) wins++;

        if (compareKD(d1.kills(), d1.deaths(), d2.kills(), d2.deaths()) > 0) wins++;

        return wins;
    }

    private String formatValue(Player viewer, double value, Format format) {
        return switch (format) {
            case INTEGER -> FormatUtil.formatInteger((long) value);
            case TIME -> TimeUtil.formatTime(lang, viewer, (int) value);
            case BALANCE -> FormatUtil.formatBalance(plugin, lang, viewer, value);
            case DECIMAL -> String.format(Locale.ROOT, "%.2f", value);
        };
    }

    private String formatKDString(Player viewer, int kills, int deaths) {
        return FormatUtil.formatKD(lang, viewer, kills, deaths);
    }

    private int compareKD(int k1, int d1, int k2, int d2) {
        if (d1 == 0 && d2 == 0) {
            return Integer.compare(k1, k2);
        }
        if (d1 == 0) {
            if (k1 == 0) {
                return Double.compare(0.0, (double) k2 / d2);
            }
            return 1;
        }
        if (d2 == 0) {
            if (k2 == 0) {
                return Double.compare((double) k1 / d1, 0.0);
            }
            return -1;
        }
        return Double.compare((double) k1 / d1, (double) k2 / d2);
    }

    private enum Format {
        INTEGER, TIME, BALANCE, DECIMAL
    }

    private record ComparisonData(
            int blocksPlaced,
            int mobsKilled,
            int playTime,
            int kills,
            int deaths,
            double balance
    ) {}
}