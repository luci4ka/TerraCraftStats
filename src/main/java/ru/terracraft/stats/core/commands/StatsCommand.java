package ru.terracraft.stats.core.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.config.model.MainConfig;
import ru.terracraft.stats.core.database.dao.PlayerDAO;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;
import ru.terracraft.stats.core.services.PlayerGroupService;
import ru.terracraft.stats.core.services.TopCache;
import ru.terracraft.stats.core.utils.FormatUtil;
import ru.terracraft.stats.core.utils.Sparkline;
import ru.terracraft.stats.core.utils.SpriteUtil;
import ru.terracraft.stats.core.utils.TimeUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatsCommand {

    private static final int PAGE_SIZE_PVP = 20;
    private static final int PAGE_SIZE_MOB = 30;
    private static final int PAGE_SIZE_BLOCK = 15;

    /** Спрайты для строк /stats. */
    private static final Map<String, Component> STAT_SPRITES = Map.of(
            "stats.blocks",  SpriteUtil.block("minecraft:bricks", "🧱"),
            "stats.pve",     SpriteUtil.item("minecraft:item/zombie_head", "☠"),
            "stats.time",    SpriteUtil.item("minecraft:item/clock", "⏳"),
            "stats.pvp",     SpriteUtil.item("minecraft:item/netherite_sword", "⚔"),
            "stats.balance", SpriteUtil.item("minecraft:item/gold_ingot", "💰"),
            "stats.group",   SpriteUtil.item("minecraft:item/name_tag", "🎖")
    );

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public StatsCommand(PlayerStatsPlugin plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.groupService = groupService;
    }

    private MainConfig cfg() { return plugin.getConfigManager().main(); }

    public void register() {
        new CommandAPICommand("stats")
                .withPermission("playerstats.stats")
                .executesPlayer((player, args) -> {
                    execute(player);
                    return 0;
                })
                .withSubcommand(new CommandAPICommand("player")
                        .withArguments(new StringArgument("player"))
                        .withPermission("playerstats.stats.others")
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
                        }))
                .withSubcommand(new CommandAPICommand("pvp")
                        .executesPlayer((player, args) -> {
                            executePvp(player, player, 0);
                            return 0;
                        })
                        .withSubcommand(new CommandAPICommand("player")
                                .withArguments(new StringArgument("player"))
                                .executesPlayer((player, args) -> {
                                    String targetName = (String) args.get("player");
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                    executePvp(player, target, 0);
                                    return 0;
                                })
                                .withSubcommand(new CommandAPICommand("page")
                                        .withArguments(new IntegerArgument("page", 0))
                                        .executesPlayer((player, args) -> {
                                            String targetName = (String) args.get("player");
                                            int page = (int) args.get("page");
                                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                            executePvp(player, target, page);
                                            return 0;
                                        }))))
                .withSubcommand(new CommandAPICommand("mob")
                        .executesPlayer((player, args) -> {
                            executeMob(player, player, 0);
                            return 0;
                        })
                        .withSubcommand(new CommandAPICommand("player")
                                .withArguments(new StringArgument("player"))
                                .executesPlayer((player, args) -> {
                                    String targetName = (String) args.get("player");
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                    executeMob(player, target, 0);
                                    return 0;
                                })
                                .withSubcommand(new CommandAPICommand("page")
                                        .withArguments(new IntegerArgument("page", 0))
                                        .executesPlayer((player, args) -> {
                                            String targetName = (String) args.get("player");
                                            int page = (int) args.get("page");
                                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                            executeMob(player, target, page);
                                            return 0;
                                        }))))
                .withSubcommand(new CommandAPICommand("block")
                        .executesPlayer((player, args) -> {
                            executeBlock(player, player, 0);
                            return 0;
                        })
                        .withSubcommand(new CommandAPICommand("player")
                                .withArguments(new StringArgument("player"))
                                .executesPlayer((player, args) -> {
                                    String targetName = (String) args.get("player");
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                    executeBlock(player, target, 0);
                                    return 0;
                                })
                                .withSubcommand(new CommandAPICommand("page")
                                        .withArguments(new IntegerArgument("page", 0))
                                        .executesPlayer((player, args) -> {
                                            String targetName = (String) args.get("player");
                                            int page = (int) args.get("page");
                                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                            executeBlock(player, target, page);
                                            return 0;
                                        }))))
                .withSubcommand(new CommandAPICommand("gui")
                        .executesPlayer((player, args) -> {
                            plugin.getStatsGui().open(player);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("rollback")
                        .withPermission("playerstats.rollback")
                        .withArguments(new GreedyStringArgument("args"))
                        .executes((sender, args) -> {
                            String[] parts = ((String) args.get("args")).split("\\s+");
                            new RollbackCommand(plugin).execute(sender, parts);
                            return 0;
                        }))
                .register();
    }

    public void execute(Player player) {
        if (!player.hasPermission("playerstats.stats")) {
            player.sendMessage(lang.getComponent(player, "no-permission"));
            return;
        }
        loadAndSend(player, player, player.getName(), player.getUniqueId(), true, "main", 0);
    }

    public void execute(Player viewer, OfflinePlayer target) {
        if (!viewer.hasPermission("playerstats.stats.others")) {
            viewer.sendMessage(lang.getComponent(viewer, "no-permission"));
            return;
        }
        if (target == null) {
            viewer.sendMessage(lang.getComponent(viewer, "player-not-found", "player", "?"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "main", 0);
    }

    public void executePvp(Player viewer, OfflinePlayer target, int page) {
        if (!viewer.hasPermission("playerstats.stats.others") && !target.equals(viewer)) {
            viewer.sendMessage(lang.getComponent(viewer, "no-permission"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "pvp", page);
    }

    public void executeMob(Player viewer, OfflinePlayer target, int page) {
        if (!viewer.hasPermission("playerstats.stats.others") && !target.equals(viewer)) {
            viewer.sendMessage(lang.getComponent(viewer, "no-permission"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "mob", page);
    }

    public void executeBlock(Player viewer, OfflinePlayer target, int page) {
        if (!viewer.hasPermission("playerstats.stats.others") && !target.equals(viewer)) {
            viewer.sendMessage(lang.getComponent(viewer, "no-permission"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "block", page);
    }

    private void loadAndSend(Player viewer, OfflinePlayer target,
                             String targetName, UUID targetUuid,
                             boolean selfStats, String mode, int page) {
        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                int playerId;
                if (selfStats) {
                    playerId = playerDAO.getOrCreatePlayerId(targetUuid, targetName);
                } else {
                    playerId = playerDAO.getPlayerId(targetUuid);
                    if (playerId == -1) {
                        plugin.getThreadManager().runSync(() -> {
                            if (viewer.isOnline()) {
                                viewer.sendMessage(lang.getComponent(viewer,
                                        "player-not-found", "player", targetName));
                            }
                        });
                        return;
                    }
                }

                final int finalPlayerId = playerId;

                switch (mode) {
                    case "pvp" -> loadAndSendPvp(viewer, targetName, selfStats,
                            finalPlayerId, serverId, statsDAO, page);
                    case "mob" -> loadAndSendMob(viewer, targetName, selfStats,
                            finalPlayerId, serverId, statsDAO, page);
                    case "block" -> loadAndSendBlock(viewer, targetName, selfStats,
                            finalPlayerId, serverId, statsDAO, page);
                    default -> loadAndSendMain(viewer, target, targetName, selfStats,
                            finalPlayerId, serverId, statsDAO);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка статистики для " + targetName + ": " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage(lang.getComponent(viewer, "error"));
                    }
                });
            }
        });
    }

    private void loadAndSendMain(Player viewer, OfflinePlayer target,
                                 String targetName, boolean selfStats,
                                 int playerId, int serverId,
                                 PlayerStatsDAO statsDAO) throws SQLException {
        int blocksPlaced = statsDAO.getStat(playerId, serverId, "blocks_placed");
        int blocksBroken = statsDAO.getStat(playerId, serverId, "blocks_broken");
        int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
        int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");
        int playTimeSec = statsDAO.getStat(playerId, serverId, "play_time");
        int kills = statsDAO.getStat(playerId, serverId, "kills");
        int deaths = statsDAO.getStat(playerId, serverId, "deaths");

        Map<String, Double> totalDoubleStats = statsDAO.getTotalStatsDoubleForPlayer(playerId);
        double balance = totalDoubleStats.getOrDefault("balance", 0.0);
        Map<String, Integer> totalStats = statsDAO.getTotalStatsForPlayer(playerId);

        String group;
        if (target instanceof Player onlineTarget) {
            group = groupService.getPlayerGroup(onlineTarget, playerId);
        } else {
            group = groupService.getPlayerGroup(target.getUniqueId());
        }

        String localTime = TimeUtil.formatTime(lang, viewer, playTimeSec);
        String totalTime = TimeUtil.formatTime(lang, viewer,
                totalStats.getOrDefault("play_time", 0));

        final String groupFinal = group;
        final UUID targetUuid = target.getUniqueId();

        plugin.getThreadManager().runSync(() -> {
            if (!viewer.isOnline()) return;
            sendMainStats(viewer, targetName, targetUuid, playerId, selfStats,
                    blocksPlaced, blocksBroken,
                    totalStats.getOrDefault("blocks_placed", 0),
                    totalStats.getOrDefault("blocks_broken", 0),
                    mobsKilled, bossesKilled,
                    totalStats.getOrDefault("mobs_killed", 0),
                    totalStats.getOrDefault("bosses_killed", 0),
                    localTime, totalTime, kills, deaths,
                    totalStats.getOrDefault("kills", 0),
                    totalStats.getOrDefault("deaths", 0),
                    balance, groupFinal);

            viewer.sendMessage(lang.getComponent(viewer, "stats.header"));
            viewer.sendMessage(lang.getComponent(viewer, "stats.hint-subcommands"));
            viewer.sendMessage(lang.getComponent(viewer, "stats.header"));

            if (selfStats && cfg().enableStatSharing()) {
                Component shareButton = lang.getComponent(viewer, "stats.share-button")
                        .clickEvent(ClickEvent.runCommand("/share"))
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent(viewer, "stats.share-hover")));
                viewer.sendMessage(Component.empty());
                viewer.sendMessage(shareButton);
            }
        });
    }

    private void loadAndSendPvp(Player viewer, String targetName, boolean selfStats,
                                int playerId, int serverId,
                                PlayerStatsDAO statsDAO, int page) throws SQLException {
        Map<String, Integer> pvpStats = statsDAO.getStatsByPrefix(playerId, serverId, "pvp_kill_");
        int kills = statsDAO.getStat(playerId, serverId, "kills");
        int deaths = statsDAO.getStat(playerId, serverId, "deaths");

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(pvpStats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Map<String, String> victimNames = new java.util.HashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            String victimUuid = entry.getKey().substring("pvp_kill_".length());
            victimNames.put(victimUuid, resolvePlayerNameAsync(victimUuid));
        }

        plugin.getThreadManager().runSync(() -> {
            if (!viewer.isOnline()) return;
            Component separator = lang.getComponent(viewer, "stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.pvp-title-self" : "stats.pvp-title-other";
            viewer.sendMessage(lang.getComponent(viewer, titleKey,
                    "player", targetName, "kills", kills, "deaths", deaths,
                    "kd", formatKD(viewer, kills, deaths)));
            viewer.sendMessage(separator);

            if (sorted.isEmpty()) {
                viewer.sendMessage(lang.getComponent(viewer, "stats.pvp-empty"));
                viewer.sendMessage(separator);
                return;
            }

            viewer.sendMessage(lang.getComponent(viewer, "stats.pvp-header",
                    "count", sorted.size()));

            int totalPages = (int) Math.ceil((double) sorted.size() / PAGE_SIZE_PVP);
            int currentPage = Math.max(0, Math.min(page, totalPages - 1));
            int start = currentPage * PAGE_SIZE_PVP;
            int end = Math.min(start + PAGE_SIZE_PVP, sorted.size());

            for (int i = start; i < end; i++) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                String victimUuid = entry.getKey().substring("pvp_kill_".length());
                String victimName = victimNames.getOrDefault(victimUuid, victimUuid);
                int count = entry.getValue();

                Component line = lang.getComponent(viewer, "stats.pvp-entry",
                                "player", victimName, "count", count,
                                "times", pluralize(viewer, count))
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent(viewer, "stats.pvp-entry-hover",
                                        "player", victimName, "uuid", victimUuid)))
                        .clickEvent(ClickEvent.runCommand("/stats pvp " + victimName));
                viewer.sendMessage(line);
            }

            sendPaginationFooter(viewer, currentPage, totalPages,
                    "/stats pvp player " + targetName + " page");
            viewer.sendMessage(separator);
        });
    }

    private void loadAndSendMob(Player viewer, String targetName, boolean selfStats,
                                int playerId, int serverId,
                                PlayerStatsDAO statsDAO, int page) throws SQLException {
        Map<String, Integer> mobStats = statsDAO.getStatsByPrefix(playerId, serverId, "mob_killed_");
        int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
        int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(mobStats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        plugin.getThreadManager().runSync(() -> {
            if (!viewer.isOnline()) return;
            Component separator = lang.getComponent(viewer, "stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.mob-title-self" : "stats.mob-title-other";
            viewer.sendMessage(lang.getComponent(viewer, titleKey,
                    "player", targetName, "mobs", mobsKilled, "bosses", bossesKilled));
            viewer.sendMessage(separator);

            if (sorted.isEmpty()) {
                viewer.sendMessage(lang.getComponent(viewer, "stats.mob-empty"));
                viewer.sendMessage(separator);
                return;
            }

            viewer.sendMessage(lang.getComponent(viewer, "stats.mob-header",
                    "count", sorted.size()));

            int totalPages = (int) Math.ceil((double) sorted.size() / PAGE_SIZE_MOB);
            int currentPage = Math.max(0, Math.min(page, totalPages - 1));
            int start = currentPage * PAGE_SIZE_MOB;
            int end = Math.min(start + PAGE_SIZE_MOB, sorted.size());
            String unit = lang.getRaw(viewer, "stats.unit-pieces");

            for (int i = start; i < end; i++) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                String mobName = entry.getKey().substring("mob_killed_".length());
                viewer.sendMessage(lang.getComponent(viewer, "stats.mob-entry",
                        "name", prettify(mobName), "count", entry.getValue(), "unit", unit));
            }

            sendPaginationFooter(viewer, currentPage, totalPages,
                    "/stats mob player " + targetName + " page");
            viewer.sendMessage(separator);
        });
    }

    private void loadAndSendBlock(Player viewer, String targetName, boolean selfStats,
                                  int playerId, int serverId,
                                  PlayerStatsDAO statsDAO, int page) throws SQLException {
        Map<String, Integer> placedStats = statsDAO.getStatsByPrefix(playerId, serverId, "block_placed_");
        Map<String, Integer> brokenStats = statsDAO.getStatsByPrefix(playerId, serverId, "block_broken_");
        int blocksPlaced = statsDAO.getStat(playerId, serverId, "blocks_placed");
        int blocksBroken = statsDAO.getStat(playerId, serverId, "blocks_broken");

        List<Map.Entry<String, Integer>> sortedPlaced = new ArrayList<>(placedStats.entrySet());
        sortedPlaced.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Map.Entry<String, Integer>> sortedBroken = new ArrayList<>(brokenStats.entrySet());
        sortedBroken.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        plugin.getThreadManager().runSync(() -> {
            if (!viewer.isOnline()) return;
            Component separator = lang.getComponent(viewer, "stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.block-title-self" : "stats.block-title-other";
            viewer.sendMessage(lang.getComponent(viewer, titleKey,
                    "player", targetName, "placed", blocksPlaced, "broken", blocksBroken));
            viewer.sendMessage(separator);

            String unit = lang.getRaw(viewer, "stats.unit-pieces");

            viewer.sendMessage(lang.getComponent(viewer, "stats.block-placed-header",
                    "count", sortedPlaced.size()));
            if (sortedPlaced.isEmpty()) {
                viewer.sendMessage(lang.getComponent(viewer, "stats.block-empty"));
            } else {
                int totalPages = (int) Math.ceil((double) sortedPlaced.size() / PAGE_SIZE_BLOCK);
                int currentPage = Math.max(0, Math.min(page, totalPages - 1));
                int start = currentPage * PAGE_SIZE_BLOCK;
                int end = Math.min(start + PAGE_SIZE_BLOCK, sortedPlaced.size());
                for (int i = start; i < end; i++) {
                    Map.Entry<String, Integer> entry = sortedPlaced.get(i);
                    String blockName = entry.getKey().substring("block_placed_".length());
                    viewer.sendMessage(lang.getComponent(viewer, "stats.block-placed-entry",
                            "name", prettify(blockName), "count", entry.getValue(), "unit", unit));
                }
                sendPaginationFooter(viewer, currentPage, totalPages,
                        "/stats block player " + targetName + " page");
            }

            viewer.sendMessage(lang.getComponent(viewer, "stats.block-broken-header",
                    "count", sortedBroken.size()));
            if (sortedBroken.isEmpty()) {
                viewer.sendMessage(lang.getComponent(viewer, "stats.block-empty"));
            } else {
                int totalPages = (int) Math.ceil((double) sortedBroken.size() / PAGE_SIZE_BLOCK);
                int currentPage = Math.max(0, Math.min(page, totalPages - 1));
                int start = currentPage * PAGE_SIZE_BLOCK;
                int end = Math.min(start + PAGE_SIZE_BLOCK, sortedBroken.size());
                for (int i = start; i < end; i++) {
                    Map.Entry<String, Integer> entry = sortedBroken.get(i);
                    String blockName = entry.getKey().substring("block_broken_".length());
                    viewer.sendMessage(lang.getComponent(viewer, "stats.block-broken-entry",
                            "name", prettify(blockName), "count", entry.getValue(), "unit", unit));
                }
                sendPaginationFooter(viewer, currentPage, totalPages,
                        "/stats block player " + targetName + " page");
            }

            viewer.sendMessage(separator);
        });
    }

    private void sendPaginationFooter(Player viewer, int currentPage, int totalPages, String baseCommand) {
        if (totalPages <= 1) return;
        var nav = Component.text();
        if (currentPage > 0) {
            nav.append(Component.text("◀ ", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(baseCommand + " " + (currentPage - 1)))
                    .hoverEvent(HoverEvent.showText(lang.getComponent(viewer,
                            "stats.pagination-prev-hover", "page", currentPage))));
        } else {
            nav.append(Component.text("◀ ", NamedTextColor.DARK_GRAY));
        }
        nav.append(Component.text(" [ ", NamedTextColor.GRAY))
                .append(Component.text((currentPage + 1) + " / " + totalPages, NamedTextColor.YELLOW))
                .append(Component.text(" ] ", NamedTextColor.GRAY));
        if (currentPage < totalPages - 1) {
            nav.append(Component.text("▶", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(baseCommand + " " + (currentPage + 1)))
                    .hoverEvent(HoverEvent.showText(lang.getComponent(viewer,
                            "stats.pagination-next-hover", "page", currentPage + 2))));
        } else {
            nav.append(Component.text("▶", NamedTextColor.DARK_GRAY));
        }
        viewer.sendMessage(nav.build());
    }

    private void sendMainStats(Player player, String targetName, UUID targetUuid,
                               int playerId, boolean selfStats,
                               int blocksPlaced, int blocksBroken,
                               int totalBlocksPlaced, int totalBlocksBroken,
                               int mobsKilled, int bossesKilled,
                               int totalMobsKilled, int totalBossesKilled,
                               String localTime, String totalTime,
                               int kills, int deaths,
                               int totalKills, int totalDeaths,
                               double balance, String group) {
        Component separator = lang.getComponent(player, "stats.header");
        String serverDisplay = plugin.getServerName();
        String chips = getChipsForPlayer(targetUuid);

        Component header;
        if (selfStats) {
            header = lang.getComponent(player, "stats.title-self",
                    "server", serverDisplay, "chips", chips);
        } else {
            header = lang.getComponent(player, "stats.title-other",
                    "player", targetName, "server", serverDisplay, "chips", chips);
        }

        String serverCmd = cfg().statsClickCommands().server();
        if (serverCmd != null && !serverCmd.isEmpty()) {
            header = header.clickEvent(ClickEvent.runCommand(serverCmd));
        }

        player.sendMessage(separator);
        player.sendMessage(header);
        player.sendMessage(separator);

        sendStatLine(player, "stats.blocks", "blocks",
                "placed", blocksPlaced, "broken", blocksBroken,
                "total-placed", totalBlocksPlaced, "total-broken", totalBlocksBroken);
        sendSparkline(player, playerId, "blocks_placed");

        sendStatLine(player, "stats.pve", "pve",
                "mobs", mobsKilled, "bosses", bossesKilled,
                "total-mobs", totalMobsKilled, "total-bosses", totalBossesKilled);
        sendSparkline(player, playerId, "mobs_killed");

        sendStatLine(player, "stats.time", "time", "time", localTime, "total-time", totalTime);
        sendSparkline(player, playerId, "play_time");

        sendStatLine(player, "stats.pvp", "pvp",
                "kills", kills, "deaths", deaths, "kd", formatKD(player, kills, deaths),
                "total-kills", totalKills, "total-deaths", totalDeaths);
        sendSparkline(player, playerId, "kills");

        String balanceStr = FormatUtil.formatBalance(plugin, lang, player, balance);
        sendStatLineSimple(player, "stats.balance", "balance", balanceStr,
                lang.getRaw(player, "stats.balance-hover"));
        sendSparkline(player, playerId, "balance");

        sendStatLineSimple(player, "stats.group", "group", group,
                lang.getRaw(player, "stats.group-hover"));
    }

    private void sendSparkline(Player player, int playerId, String statName) {
        var cm = plugin.getConfigManager();
        if (!cm.main().modules().sparkline() || cm.sparkline() == null) return;
        if (!cm.sparkline().showInStats()) return;

        String category = mapStatToCategory(statName);
        if (category == null) return;
        if (cm.sparkline().categories() != null
                && !cm.sparkline().categories().getOrDefault(category, true)) return;

        plugin.getThreadManager().runAsync(() -> {
            try {
                int statTypeId = plugin.getStatTypeDAO().createOrGetStatType(statName);
                List<Integer> history = plugin.getStatHistoryDAO().getHistory(
                        playerId, statTypeId, plugin.getServerId(),
                        cm.sparkline().days());
                if (history.size() < 2) return;
                String graph = Sparkline.generatePretty(history);
                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;
                    player.sendMessage(lang.getComponent(player, "stats.sparkline",
                            "days", cm.sparkline().days(), "graph", graph));
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка sparkline: " + e.getMessage());
            }
        });
    }

    private void sendStatLine(Player player, String langKey, String configKey, Object... placeholders) {
        Component line = lang.getComponent(player, langKey, placeholders);
        Component sprite = STAT_SPRITES.get(langKey);
        if (sprite != null) line = sprite.append(Component.text(" ")).append(line);
        line = applyClickEvent(line, configKey);
        player.sendMessage(line);
    }

    private void sendStatLineSimple(Player player, String langKey, String configKey,
                                    String mainValue, String hoverValue) {
        Component line = lang.getComponent(player, langKey, "value", mainValue);
        Component sprite = STAT_SPRITES.get(langKey);
        if (sprite != null) line = sprite.append(Component.text(" ")).append(line);
        line = line.hoverEvent(HoverEvent.showText(Component.text(hoverValue)));
        String clickCommand = clickCommand(configKey);
        if (clickCommand != null && !clickCommand.isEmpty()) {
            line = line.clickEvent(ClickEvent.runCommand(clickCommand));
        }
        player.sendMessage(line);
    }

    private Component applyClickEvent(Component line, String configKey) {
        String clickCommand = clickCommand(configKey);
        if (clickCommand != null && !clickCommand.isEmpty()) {
            line = line.clickEvent(ClickEvent.runCommand(clickCommand));
        }
        return line;
    }

    private String clickCommand(String key) {
        if (key == null) return null;
        var c = cfg().statsClickCommands();
        return switch (key.toLowerCase()) {
            case "server" -> c.server();
            case "time" -> c.time();
            case "pve" -> c.pve();
            case "blocks" -> c.blocks();
            case "pvp" -> c.pvp();
            case "balance" -> c.balance();
            default -> null;
        };
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
        if (sb.length() > 0) sb.append(" ");
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

    private String mapStatToCategory(String statName) {
        return switch (statName) {
            case "kills" -> "kills";
            case "deaths" -> "deaths";
            case "mobs_killed" -> "mobs";
            case "blocks_placed" -> "blocks";
            case "play_time" -> "playtime";
            case "balance" -> "balance";
            default -> null;
        };
    }

    private String formatKD(Player viewer, int kills, int deaths) {
        return FormatUtil.formatKD(lang, viewer, kills, deaths);
    }

    private String pluralize(Player viewer, int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        String key;
        if (mod10 == 1 && mod100 != 11) key = "time.times.one";
        else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) key = "time.times.few";
        else key = "time.times.many";
        return lang.getRaw(viewer, key);
    }

    private String resolvePlayerNameAsync(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            String name = plugin.getPlayerDAO().getPlayerName(uuid);
            if (name != null && !name.isEmpty()) return name;
            return uuidStr.substring(0, 8) + "…";
        } catch (Exception e) {
            return uuidStr;
        }
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