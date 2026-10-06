package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import com.playerstats.core.services.TopCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class TopCommand {

    private static final int DEFAULT_TOP_LIMIT = 10;

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;

    public TopCommand(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
    }

    // ==================== /top ====================

    public void execute(Player player) {
        if (!player.hasPermission("playerstats.top")) {
            player.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        plugin.getThreadManager().runAsync(() -> {
            TopCache topCache = plugin.getTopCache();
            List<LeaderboardEntry> topTime = topCache.getTop("play_time");
            List<LeaderboardEntry> topMobs = topCache.getTop("mobs_killed");
            List<LeaderboardEntry> topBlocks = topCache.getTop("blocks_placed");
            List<LeaderboardEntry> topKills = topCache.getTop("kills");
            List<LeaderboardEntry> topBalance = topCache.getTop("balance");

            plugin.getThreadManager().runSync(() -> {
                if (!player.isOnline()) return;

                player.sendMessage(lang.getComponent("top.header"));

                sendTopCategory(player, "top.category-time", topTime,
                        config.getStatsClickCommand("time"), StatFormat.TIME, DEFAULT_TOP_LIMIT);
                sendTopCategory(player, "top.category-mobs", topMobs,
                        config.getStatsClickCommand("pve"), StatFormat.INTEGER, DEFAULT_TOP_LIMIT);
                sendTopCategory(player, "top.category-blocks", topBlocks,
                        config.getStatsClickCommand("blocks"), StatFormat.INTEGER, DEFAULT_TOP_LIMIT);
                sendTopCategory(player, "top.category-pvp", topKills,
                        config.getStatsClickCommand("pvp"), StatFormat.INTEGER, DEFAULT_TOP_LIMIT);
                sendTopCategory(player, "top.category-balance", topBalance,
                        config.getStatsClickCommand("balance"), StatFormat.BALANCE, DEFAULT_TOP_LIMIT);

                player.sendMessage(lang.getComponent("top.footer"));

                Component refresh = lang.getComponent("top.refresh")
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent("top.refresh-hover")))
                        .clickEvent(ClickEvent.runCommand("/top"));
                player.sendMessage(refresh);
            });
        });
    }

    // ==================== /top <statName> ====================

    public void execute(Player player, String statName) {
        if (!player.hasPermission("playerstats.top")) {
            player.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        if (statName == null || statName.isEmpty()) {
            execute(player);
            return;
        }

        final String stat = statName.toLowerCase();

        plugin.getThreadManager().runAsync(() -> {
            TopCache topCache = plugin.getTopCache();
            List<LeaderboardEntry> entries;

            if (topCache.isCached(stat)) {
                entries = topCache.getTop(stat);
            } else {
                try {
                    entries = plugin.getLeaderboardDAO()
                            .getTop(stat, plugin.getServerId(), DEFAULT_TOP_LIMIT);
                } catch (SQLException e) {
                    plugin.getLogger().severe("Ошибка топа для "
                            + stat + ": " + e.getMessage());
                    plugin.getThreadManager().runSync(() -> {
                        if (player.isOnline()) {
                            player.sendMessage(lang.getComponent("error"));
                        }
                    });
                    return;
                }
            }

            final List<LeaderboardEntry> finalEntries = entries;

            plugin.getThreadManager().runSync(() -> {
                if (!player.isOnline()) return;

                player.sendMessage(lang.getComponent("top.header"));
                player.sendMessage(lang.getComponent("top.category-custom",
                        "stat", formatStatName(stat)));

                if (finalEntries.isEmpty()) {
                    player.sendMessage(lang.getComponent("top.no-data"));
                } else {
                    for (int i = 0; i < finalEntries.size(); i++) {
                        LeaderboardEntry entry = finalEntries.get(i);
                        Component line = formatPlayerEntry(i + 1, entry, null, StatFormat.INTEGER);
                        player.sendMessage(line);
                    }
                }

                player.sendMessage(lang.getComponent("top.footer"));

                Component refresh = lang.getComponent("top.refresh")
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent("top.refresh-hover")))
                        .clickEvent(ClickEvent.runCommand("/top " + stat));
                player.sendMessage(refresh);
            });
        });
    }

    // ==================== /top pvp <ник> ====================

    /**
     * Топ: кто больше всех убивал конкретного игрока.
     */
    public void executePvp(Player player, UUID victimUuid, String victimName) {
        if (!player.hasPermission("playerstats.top")) {
            player.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        String stat = "pvp_kill_" + victimUuid;

        plugin.getThreadManager().runAsync(() -> {
            List<LeaderboardEntry> entries;
            try {
                entries = plugin.getLeaderboardDAO()
                        .getTop(stat, plugin.getServerId(), DEFAULT_TOP_LIMIT);
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка топа PvP для "
                        + victimName + ": " + e.getMessage());
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(lang.getComponent("error"));
                    }
                });
                return;
            }

            final List<LeaderboardEntry> finalEntries = entries;

            plugin.getThreadManager().runSync(() -> {
                if (!player.isOnline()) return;

                player.sendMessage(lang.getComponent("top.header"));
                player.sendMessage(lang.getComponent("top.category-pvp-victim",
                        "victim", victimName));

                if (finalEntries.isEmpty()) {
                    player.sendMessage(lang.getComponent("top.no-data"));
                } else {
                    for (int i = 0; i < finalEntries.size(); i++) {
                        LeaderboardEntry entry = finalEntries.get(i);
                        Component line = formatPlayerEntry(i + 1, entry, null, StatFormat.INTEGER);
                        player.sendMessage(line);
                    }
                }

                player.sendMessage(lang.getComponent("top.footer"));

                Component refresh = lang.getComponent("top.refresh")
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent("top.refresh-hover")))
                        .clickEvent(ClickEvent.runCommand("/top pvp " + victimName));
                player.sendMessage(refresh);
            });
        });
    }

    // ==================== Внутреннее ====================

    private enum StatFormat {
        TIME, INTEGER, BALANCE
    }

    private void sendTopCategory(Player player, String langKey,
                                 List<LeaderboardEntry> entries,
                                 String clickCommand,
                                 StatFormat format,
                                 int limit) {
        Component categoryHeader = lang.getComponent(langKey)
                .decorate(TextDecoration.BOLD);
        player.sendMessage(categoryHeader);

        if (entries.isEmpty()) {
            player.sendMessage(lang.getComponent("top.no-data"));
            return;
        }

        int count = Math.min(limit, entries.size());

        if (count <= 5) {
            Component line = Component.empty();
            for (int i = 0; i < count; i++) {
                LeaderboardEntry entry = entries.get(i);
                line = line.append(formatPlayerEntry(i + 1, entry, clickCommand, format));
                if (i < count - 1) {
                    line = line.append(Component.text("  "));
                }
            }
            player.sendMessage(line);
        } else {
            int firstLine = Math.min(5, count);
            Component line1 = Component.empty();
            for (int i = 0; i < firstLine; i++) {
                LeaderboardEntry entry = entries.get(i);
                line1 = line1.append(formatPlayerEntry(i + 1, entry, clickCommand, format));
                if (i < firstLine - 1) {
                    line1 = line1.append(Component.text("  "));
                }
            }
            player.sendMessage(line1);

            Component line2 = Component.empty();
            for (int i = 5; i < count; i++) {
                LeaderboardEntry entry = entries.get(i);
                line2 = line2.append(formatPlayerEntry(i + 1, entry, clickCommand, format));
                if (i < count - 1) {
                    line2 = line2.append(Component.text("  "));
                }
            }
            player.sendMessage(line2);
        }
    }

    private Component formatPlayerEntry(int rank, LeaderboardEntry entry,
                                        String clickCommand, StatFormat format) {
        String name = entry.playerName();
        String value = formatValue(entry.value(), format);

        NamedTextColor color = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };

        Component entryComponent = Component.text("#" + rank + " " + name)
                .color(color)
                .append(Component.text(" " + value).color(NamedTextColor.DARK_GRAY));

        if (clickCommand != null && !clickCommand.isEmpty()) {
            String finalCommand = clickCommand.replace("%player%", name);
            entryComponent = entryComponent.clickEvent(ClickEvent.suggestCommand(finalCommand));
        }

        return entryComponent;
    }

    private String formatValue(double value, StatFormat format) {
        return switch (format) {
            case TIME -> formatTime((int) value);
            case INTEGER -> formatInteger((int) value);
            case BALANCE -> formatBalance(value);
        };
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 60) {
            return lang.get("time.seconds", "seconds", totalSeconds);
        }
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return lang.get("time.hours", "hours", hours, "minutes", minutes);
        } else {
            return lang.get("time.minutes", "minutes", minutes);
        }
    }

    private String formatInteger(int value) {
        return String.format("%,d", value);
    }

    private String formatBalance(double value) {
        return String.format("%,.2f$", value);
    }

    /**
     * Красивое имя статистики для заголовка.
     */
    private String formatStatName(String stat) {
        // --- PvP по конкретной жертве ---
        if (stat.startsWith("pvp_kill_")) {
            String uuid = stat.substring("pvp_kill_".length());
            return "Убийства: " + resolvePlayerName(uuid);
        }

        // --- Мобы ---
        if (stat.startsWith("mob_killed_")) {
            String mob = stat.substring("mob_killed_".length());
            return "Убийства: " + prettify(mob);
        }

        // --- Поставленные блоки ---
        if (stat.startsWith("block_placed_")) {
            String block = stat.substring("block_placed_".length());
            return "Поставлено: " + prettify(block);
        }

        // --- Сломанные блоки ---
        if (stat.startsWith("block_broken_")) {
            String block = stat.substring("block_broken_".length());
            return "Сломано: " + prettify(block);
        }

        // --- Стандартные ---
        return switch (stat) {
            case "mobs_killed" -> "Убито мобов";
            case "bosses_killed" -> "Убито боссов";
            case "kills" -> "Убийства игроков";
            case "deaths" -> "Смерти";
            case "play_time" -> "Время игры";
            case "blocks_placed" -> "Поставлено блоков";
            case "blocks_broken" -> "Сломано блоков";
            case "balance" -> "Баланс";
            default -> prettify(stat);
        };
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

    private String resolvePlayerName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName();
            if (name != null && !name.isEmpty()) return name;
            return uuidStr.substring(0, 8) + "…";
        } catch (IllegalArgumentException e) {
            return uuidStr;
        }
    }
}