package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import com.playerstats.core.services.TopCache;
import com.playerstats.core.utils.TimeUtil;
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
            player.sendMessage(lang.getComponent(player, "no-permission"));
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

                player.sendMessage(lang.getComponent(player, "top.header"));

                sendTopCategory(player, "top.category-time", topTime,
                        config.getStatsClickCommand("time"), StatFormat.TIME,
                        DEFAULT_TOP_LIMIT, "play_time");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-mobs", topMobs,
                        config.getStatsClickCommand("pve"), StatFormat.INTEGER,
                        DEFAULT_TOP_LIMIT, "mobs_killed");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-blocks", topBlocks,
                        config.getStatsClickCommand("blocks"), StatFormat.INTEGER,
                        DEFAULT_TOP_LIMIT, "blocks_placed");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-pvp", topKills,
                        config.getStatsClickCommand("pvp"), StatFormat.INTEGER,
                        DEFAULT_TOP_LIMIT, "kills");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-balance", topBalance,
                        config.getStatsClickCommand("balance"), StatFormat.BALANCE,
                        DEFAULT_TOP_LIMIT, "balance");

                player.sendMessage(lang.getComponent(player, "top.footer"));

                Component refresh = lang.getComponent(player, "top.refresh")
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent(player, "top.refresh-hover")))
                        .clickEvent(ClickEvent.runCommand("/top"));
                player.sendMessage(refresh);
            });
        });
    }

    // ==================== /top <statName> ====================

    public void execute(Player player, String statName) {
        if (!player.hasPermission("playerstats.top")) {
            player.sendMessage(lang.getComponent(player, "no-permission"));
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
                            player.sendMessage(lang.getComponent(player, "error"));
                        }
                    });
                    return;
                }
            }

            final List<LeaderboardEntry> finalEntries = entries;

            plugin.getThreadManager().runSync(() -> {
                if (!player.isOnline()) return;

                player.sendMessage(lang.getComponent(player, "top.header"));
                player.sendMessage(lang.getComponent(player, "top.category-custom",
                        "stat", formatStatName(player, stat)));

                if (finalEntries.isEmpty()) {
                    player.sendMessage(lang.getComponent(player, "top.no-data"));
                } else {
                    for (int i = 0; i < finalEntries.size(); i++) {
                        LeaderboardEntry entry = finalEntries.get(i);
                        Component line = formatPlayerEntrySingle(
                                player, i + 1, entry, null, StatFormat.INTEGER, stat);
                        player.sendMessage(line);
                    }
                }

                player.sendMessage(lang.getComponent(player, "top.footer"));

                Component refresh = lang.getComponent(player, "top.refresh")
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent(player, "top.refresh-hover")))
                        .clickEvent(ClickEvent.runCommand("/top " + stat));
                player.sendMessage(refresh);
            });
        });
    }

    // ==================== /top pvp <ник> ====================

    public void executePvp(Player player, UUID victimUuid, String victimName) {
        if (!player.hasPermission("playerstats.top")) {
            player.sendMessage(lang.getComponent(player, "no-permission"));
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
                        player.sendMessage(lang.getComponent(player, "error"));
                    }
                });
                return;
            }

            final List<LeaderboardEntry> finalEntries = entries;

            plugin.getThreadManager().runSync(() -> {
                if (!player.isOnline()) return;

                player.sendMessage(lang.getComponent(player, "top.header"));
                player.sendMessage(lang.getComponent(player, "top.category-pvp-victim",
                        "victim", victimName));

                if (finalEntries.isEmpty()) {
                    player.sendMessage(lang.getComponent(player, "top.no-data"));
                } else {
                    for (int i = 0; i < finalEntries.size(); i++) {
                        LeaderboardEntry entry = finalEntries.get(i);
                        Component line = formatPlayerEntrySingle(
                                player, i + 1, entry, null, StatFormat.INTEGER, stat);
                        player.sendMessage(line);
                    }
                }

                player.sendMessage(lang.getComponent(player, "top.footer"));

                Component refresh = lang.getComponent(player, "top.refresh")
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent(player, "top.refresh-hover")))
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
                                 int limit,
                                 String statName) {
        Component categoryHeader = lang.getComponent(player, langKey)
                .decorate(TextDecoration.BOLD);
        player.sendMessage(categoryHeader);

        if (entries.isEmpty()) {
            player.sendMessage(lang.getComponent(player, "top.no-data"));
            return;
        }

        int count = Math.min(limit, entries.size());

        if (count <= 5) {
            // Одна строка
            Component line = Component.empty();
            for (int i = 0; i < count; i++) {
                LeaderboardEntry entry = entries.get(i);
                line = line.append(formatPlayerEntry(
                        player, i + 1, entry, clickCommand, format, statName));
                if (i < count - 1) {
                    line = line.append(Component.text("  "));
                }
            }
            player.sendMessage(line);
        } else {
            // Две строки по 5
            int firstLine = Math.min(5, count);
            Component line1 = Component.empty();
            for (int i = 0; i < firstLine; i++) {
                LeaderboardEntry entry = entries.get(i);
                line1 = line1.append(formatPlayerEntry(
                        player, i + 1, entry, clickCommand, format, statName));
                if (i < firstLine - 1) {
                    line1 = line1.append(Component.text("  "));
                }
            }
            player.sendMessage(line1);

            Component line2 = Component.empty();
            for (int i = 5; i < count; i++) {
                LeaderboardEntry entry = entries.get(i);
                line2 = line2.append(formatPlayerEntry(
                        player, i + 1, entry, clickCommand, format, statName));
                if (i < count - 1) {
                    line2 = line2.append(Component.text("  "));
                }
            }
            player.sendMessage(line2);
        }
    }

    /**
     * Формат одной записи в топе.
     */
    private Component formatPlayerEntrySingle(Player viewer, int rank,
                                              LeaderboardEntry entry,
                                              String clickCommand,
                                              StatFormat format,
                                              String statName) {
        return formatPlayerEntry(viewer, rank, entry, clickCommand, format, statName);
    }

    /**
     * Формат записи в топе.
     */
    private Component formatPlayerEntry(Player viewer, int rank,
                                        LeaderboardEntry entry,
                                        String clickCommand,
                                        StatFormat format,
                                        String statName) {
        String name = entry.playerName();
        String value = formatValue(viewer, entry.value(), format);

        NamedTextColor color = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };

        // Значки топ-1
        String chips = getChipsForPlayer(entry.uuid());
        Component chipsComponent = chips.isEmpty()
                ? Component.empty()
                : Component.text(chips + " ").color(NamedTextColor.WHITE);

        // #N + значки + ник + значение
        Component entryComponent = Component.text("#" + rank + " ")
                .color(NamedTextColor.DARK_GRAY)
                .append(chipsComponent)
                .append(Component.text(name).color(color))
                .append(Component.text(" " + value).color(NamedTextColor.DARK_GRAY));

        // Hover — из lang.yml
        Component hoverText = lang.getComponent(viewer, "top.entry-hover",
                "player", name,
                "rank", rank,
                "value", value);
        entryComponent = entryComponent.hoverEvent(HoverEvent.showText(hoverText));

        // Клик
        if (clickCommand != null && !clickCommand.isEmpty()) {
            String finalCommand = clickCommand.replace("%player%", name);
            entryComponent = entryComponent.clickEvent(
                    ClickEvent.runCommand(finalCommand));
        } else {
            entryComponent = entryComponent.clickEvent(
                    ClickEvent.runCommand("/stats " + name));
        }

        return entryComponent;
    }

    /**
     * Значки топ-1.
     */
    private String getChipsForPlayer(UUID uuid) {
        TopCache topCache = plugin.getTopCache();
        if (topCache == null) return "";
        if (!config.isTopChipsEnabled()) return "";

        StringBuilder sb = new StringBuilder();
        for (String category : config.getTopChipsOrder()) {
            String statName = mapCategoryToStat(category);
            if (statName == null) continue;
            if (topCache.isTop1(uuid, statName)) {
                sb.append(config.getTopChip(category));
            }
        }
        return sb.toString();
    }

    private String mapCategoryToStat(String category) {
        return switch (category) {
            case "kills" -> "kills";
            case "mobs" -> "mobs_killed";
            case "blocks" -> "blocks_placed";
            case "playtime" -> "play_time";
            case "balance" -> "balance";
            default -> null;
        };
    }

    private String formatValue(Player viewer, double value, StatFormat format) {
        return switch (format) {
            case TIME -> TimeUtil.formatTime(lang, viewer, (int) value);
            case INTEGER -> formatInteger((int) value);
            case BALANCE -> formatBalance(viewer, value);
        };
    }

    private String formatInteger(int value) {
        return String.format("%,d", value);
    }

    /**
     * Баланс — из lang.yml (currency.format).
     */
    private String formatBalance(Player viewer, double value) {
        String fmt = lang.getRaw(viewer, "currency.format");
        if (fmt.startsWith("<red>Missing key")) {
            fmt = "{value}₽";
        }
        return fmt.replace("{value}", String.format("%,.2f", value));
    }

    /**
     * Название стата — из lang.yml (top.stats.* / top.stat-prefixes.*).
     */
    private String formatStatName(Player viewer, String stat) {
        if (stat.startsWith("pvp_kill_")) {
            String uuid = stat.substring("pvp_kill_".length());
            return lang.get(viewer, "top.stat-prefixes.pvp_kill",
                    "name", resolvePlayerName(uuid));
        }
        if (stat.startsWith("mob_killed_")) {
            String mob = stat.substring("mob_killed_".length());
            return lang.get(viewer, "top.stat-prefixes.mob_killed",
                    "name", prettify(mob));
        }
        if (stat.startsWith("block_placed_")) {
            String block = stat.substring("block_placed_".length());
            return lang.get(viewer, "top.stat-prefixes.block_placed",
                    "name", prettify(block));
        }
        if (stat.startsWith("block_broken_")) {
            String block = stat.substring("block_broken_".length());
            return lang.get(viewer, "top.stat-prefixes.block_broken",
                    "name", prettify(block));
        }

        // Прямой ключ top.stats.<stat>
        String direct = lang.get(viewer, "top.stats." + stat);
        if (!direct.startsWith("<red>Missing key")) {
            return direct;
        }

        return prettify(stat);
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