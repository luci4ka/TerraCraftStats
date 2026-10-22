package ru.terracraft.stats.core.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.config.model.MainConfig;
import ru.terracraft.stats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import ru.terracraft.stats.core.services.TopCache;
import ru.terracraft.stats.core.utils.FormatUtil;
import ru.terracraft.stats.core.utils.SpriteUtil;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TopCommand {

    private static final int DEFAULT_TOP_LIMIT = 10;

    private static final List<String> SUGGESTED_STATS = List.of(
            "mobs_killed", "bosses_killed", "kills", "deaths",
            "play_time", "blocks_placed", "blocks_broken", "balance",
            "killstreak", "best_killstreak", "distance_walked",
            "damage_dealt", "damage_taken", "items_crafted",
            "food_consumed", "arrows_shot", "fish_caught",
            "entities_tamed", "entities_bred", "teleports",
            "portal_uses", "logins"
    );

    /** Спрайты для заголовков категорий /top. */
    private static final Map<String, Component> CATEGORY_SPRITES = Map.of(
            "top.category-time",    SpriteUtil.item("minecraft:item/clock", "⏳"),
            "top.category-mobs",    SpriteUtil.item("minecraft:item/zombie_head", "☠"),
            "top.category-blocks",  SpriteUtil.block("minecraft:bricks", "⛏"),
            "top.category-pvp",     SpriteUtil.item("minecraft:item/netherite_sword", "⚔"),
            "top.category-balance", SpriteUtil.item("minecraft:item/gold_ingot", "💰")
    );

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;

    public TopCommand(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    private MainConfig mainCfg() { return plugin.getConfigManager().main(); }

    public void register() {
        new CommandAPICommand("top")
                .withPermission("playerstats.top")
                .executesPlayer((player, args) -> {
                    execute(player);
                    return 0;
                })
                .withSubcommand(new CommandAPICommand("pvp")
                        .withArguments(new StringArgument("player"))
                        .executesPlayer((player, args) -> {
                            String targetName = (String) args.get("player");
                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                            if (target == null || target.getUniqueId() == null
                                    || (!target.hasPlayedBefore() && !target.isOnline())) {
                                player.sendMessage(lang.getComponent(player,
                                        "commands.player-not-found", "player", targetName));
                                return 0;
                            }
                            executePvp(player, target.getUniqueId(), targetName);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("stat")
                        .withArguments(new StringArgument("stat")
                                .replaceSuggestions(ArgumentSuggestions.strings(SUGGESTED_STATS)))
                        .executesPlayer((player, args) -> {
                            String statName = (String) args.get("stat");
                            execute(player, statName);
                            return 0;
                        }))
                .register();
    }

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
                        mainCfg().statsClickCommands().time(), StatFormat.TIME,
                        DEFAULT_TOP_LIMIT, "play_time");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-mobs", topMobs,
                        mainCfg().statsClickCommands().pve(), StatFormat.INTEGER,
                        DEFAULT_TOP_LIMIT, "mobs_killed");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-blocks", topBlocks,
                        mainCfg().statsClickCommands().blocks(), StatFormat.INTEGER,
                        DEFAULT_TOP_LIMIT, "blocks_placed");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-pvp", topKills,
                        mainCfg().statsClickCommands().pvp(), StatFormat.INTEGER,
                        DEFAULT_TOP_LIMIT, "kills");

                player.sendMessage(Component.empty());

                sendTopCategory(player, "top.category-balance", topBalance,
                        mainCfg().statsClickCommands().balance(), StatFormat.BALANCE,
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
                        Component line = formatPlayerEntry(
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
                        Component line = formatPlayerEntry(
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

    private enum StatFormat {
        TIME, INTEGER, BALANCE
    }

    private void sendTopCategory(Player player, String langKey,
                                 List<LeaderboardEntry> entries,
                                 String clickCommand,
                                 StatFormat format,
                                 int limit,
                                 String statName) {
        Component text = lang.getComponent(player, langKey);
        Component sprite = CATEGORY_SPRITES.get(langKey);
        Component header = (sprite != null ? sprite.append(Component.text(" ")) : Component.empty())
                .append(text)
                .decorate(TextDecoration.BOLD);
        player.sendMessage(header);

        if (entries.isEmpty()) {
            player.sendMessage(lang.getComponent(player, "top.no-data"));
            return;
        }

        int count = Math.min(limit, entries.size());

        if (count <= 5) {
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

        String chips = getChipsForPlayer(entry.uuid());
        Component chipsComponent = chips.isEmpty()
                ? Component.empty()
                : Component.text(chips + " ").color(NamedTextColor.WHITE);

        Component entryComponent = Component.text("#" + rank + " ")
                .color(NamedTextColor.DARK_GRAY)
                .append(chipsComponent)
                .append(Component.text(name).color(color))
                .append(Component.text(" " + value).color(NamedTextColor.DARK_GRAY));

        Component hoverText = lang.getComponent(viewer, "top.entry-hover",
                "player", name,
                "rank", rank,
                "value", value);
        entryComponent = entryComponent.hoverEvent(HoverEvent.showText(hoverText));

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

    private String getChipsForPlayer(UUID uuid) {
        TopCache topCache = plugin.getTopCache();
        if (topCache == null) return "";
        var cm = plugin.getConfigManager();
        if (!cm.main().modules().topChips() || cm.topChips() == null) return "";

        StringBuilder sb = new StringBuilder();
        for (String category : cm.topChips().order()) {
            String statName = mapCategoryToStat(category);
            if (statName == null) continue;
            if (topCache.isTop1(uuid, statName)) {
                sb.append(cm.topChips().chips().getOrDefault(category, ""));
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
            case INTEGER -> FormatUtil.formatInteger((int) value);
            case BALANCE -> FormatUtil.formatBalance(plugin, lang, viewer, value);
        };
    }

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

        String directKey = "top.stats." + stat;
        if (lang.hasKey(viewer, directKey)) {
            return lang.get(viewer, directKey);
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
            String name = plugin.getPlayerDAO().getPlayerName(uuid);
            if (name != null && !name.isEmpty()) return name;
            return uuidStr.substring(0, 8) + "…";
        } catch (Exception e) {
            return uuidStr;
        }
    }
}