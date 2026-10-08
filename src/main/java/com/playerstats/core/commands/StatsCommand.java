package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.services.PlayerGroupService;
import com.playerstats.core.services.TopCache;
import com.playerstats.core.utils.FormatUtil;
import com.playerstats.core.utils.Sparkline;
import com.playerstats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatsCommand {

    private static final int PAGE_SIZE_PVP = 20;
    private static final int PAGE_SIZE_MOB = 30;
    private static final int PAGE_SIZE_BLOCK = 15;

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public StatsCommand(Main plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
        this.groupService = groupService;
    }

    // ==================== ENTRY POINTS ====================

    public void execute(Player player) {
        if (!player.hasPermission("playerstats.stats")) {
            player.sendMessage(lang.getComponent(player, "no-permission"));
            return;
        }
        loadAndSend(player, player, player.getName(), player.getUniqueId(),
                true, "main", 0);
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

    // ==================== ОСНОВНАЯ ЛОГИКА ====================

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
                                viewer.sendMessage(lang.getComponent(
                                        viewer, "player-not-found",
                                        "player", targetName));
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
                plugin.getLogger().severe("Ошибка при получении статистики для "
                        + targetName + ": " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage(lang.getComponent(viewer, "error"));
                    }
                });
            }
        });
    }

    // ==================== MAIN ====================

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
                    localTime, totalTime,
                    kills, deaths,
                    totalStats.getOrDefault("kills", 0),
                    totalStats.getOrDefault("deaths", 0),
                    balance, groupFinal);

            viewer.sendMessage(lang.getComponent(viewer, "stats.header"));
            viewer.sendMessage(lang.getComponent(viewer, "stats.hint-subcommands"));
            viewer.sendMessage(lang.getComponent(viewer, "stats.header"));

            if (selfStats && config.isStatSharingEnabled()) {
                Component shareButton = lang.getComponent(viewer, "stats.share-button")
                        .clickEvent(ClickEvent.runCommand("/share"))
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent(viewer, "stats.share-hover")));
                viewer.sendMessage(Component.empty());
                viewer.sendMessage(shareButton);
            }
        });
    }

    // ==================== PVP ====================

    private void loadAndSendPvp(Player viewer, String targetName, boolean selfStats,
                                int playerId, int serverId,
                                PlayerStatsDAO statsDAO, int page) throws SQLException {

        Map<String, Integer> pvpStats = statsDAO.getStatsByPrefix(
                playerId, serverId, "pvp_kill_");

        int kills = statsDAO.getStat(playerId, serverId, "kills");
        int deaths = statsDAO.getStat(playerId, serverId, "deaths");

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(pvpStats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        plugin.getThreadManager().runSync(() -> {
            if (!viewer.isOnline()) return;

            Component separator = lang.getComponent(viewer, "stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.pvp-title-self" : "stats.pvp-title-other";
            Component title = lang.getComponent(viewer, titleKey,
                    "player", targetName,
                    "kills", kills,
                    "deaths", deaths,
                    "kd", formatKD(kills, deaths));
            viewer.sendMessage(title);
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
                String victimName = resolvePlayerName(victimUuid);
                int count = entry.getValue();

                Component line = lang.getComponent(viewer, "stats.pvp-entry",
                                "player", victimName,
                                "count", count,
                                "times", pluralize(viewer, count))
                        .hoverEvent(HoverEvent.showText(
                                lang.getComponent(viewer, "stats.pvp-entry-hover",
                                        "player", victimName,
                                        "uuid", victimUuid)))
                        .clickEvent(ClickEvent.runCommand("/stats pvp " + victimName));
                viewer.sendMessage(line);
            }

            sendPaginationFooter(viewer, currentPage, totalPages,
                    "/stats pvp " + targetName);

            viewer.sendMessage(separator);
        });
    }

    // ==================== MOB ====================

    private void loadAndSendMob(Player viewer, String targetName, boolean selfStats,
                                int playerId, int serverId,
                                PlayerStatsDAO statsDAO, int page) throws SQLException {

        Map<String, Integer> mobStats = statsDAO.getStatsByPrefix(
                playerId, serverId, "mob_killed_");
        int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
        int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(mobStats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        plugin.getThreadManager().runSync(() -> {
            if (!viewer.isOnline()) return;

            Component separator = lang.getComponent(viewer, "stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.mob-title-self" : "stats.mob-title-other";
            Component title = lang.getComponent(viewer, titleKey,
                    "player", targetName,
                    "mobs", mobsKilled,
                    "bosses", bossesKilled);
            viewer.sendMessage(title);
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
                int count = entry.getValue();

                Component line = lang.getComponent(viewer, "stats.mob-entry",
                        "name", prettify(mobName),
                        "count", count,
                        "unit", unit);
                viewer.sendMessage(line);
            }

            sendPaginationFooter(viewer, currentPage, totalPages,
                    "/stats mob " + targetName);

            viewer.sendMessage(separator);
        });
    }

    // ==================== BLOCK ====================

    private void loadAndSendBlock(Player viewer, String targetName, boolean selfStats,
                                  int playerId, int serverId,
                                  PlayerStatsDAO statsDAO, int page) throws SQLException {

        Map<String, Integer> placedStats = statsDAO.getStatsByPrefix(
                playerId, serverId, "block_placed_");
        Map<String, Integer> brokenStats = statsDAO.getStatsByPrefix(
                playerId, serverId, "block_broken_");

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
            Component title = lang.getComponent(viewer, titleKey,
                    "player", targetName,
                    "placed", blocksPlaced,
                    "broken", blocksBroken);
            viewer.sendMessage(title);
            viewer.sendMessage(separator);

            String unit = lang.getRaw(viewer, "stats.unit-pieces");

            // Поставленные
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
                    Component line = lang.getComponent(viewer, "stats.block-placed-entry",
                            "name", prettify(blockName),
                            "count", entry.getValue(),
                            "unit", unit);
                    viewer.sendMessage(line);
                }

                sendPaginationFooter(viewer, currentPage, totalPages,
                        "/stats block " + targetName);
            }

            // Сломанные
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
                    Component line = lang.getComponent(viewer, "stats.block-broken-entry",
                            "name", prettify(blockName),
                            "count", entry.getValue(),
                            "unit", unit);
                    viewer.sendMessage(line);
                }

                sendPaginationFooter(viewer, currentPage, totalPages,
                        "/stats block " + targetName);
            }

            viewer.sendMessage(separator);
        });
    }

    // ==================== ПАГИНАЦИЯ ====================

    private void sendPaginationFooter(Player viewer, int currentPage,
                                      int totalPages, String baseCommand) {
        if (totalPages <= 1) return;

        var nav = Component.text();

        if (currentPage > 0) {
            nav.append(Component.text("◀ ", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(baseCommand + " " + (currentPage - 1)))
                    .hoverEvent(HoverEvent.showText(
                            lang.getComponent(viewer, "stats.pagination-prev-hover",
                                    "page", currentPage))));
        } else {
            nav.append(Component.text("◀ ", NamedTextColor.DARK_GRAY));
        }

        nav.append(Component.text(" [ ", NamedTextColor.GRAY))
                .append(Component.text((currentPage + 1) + " / " + totalPages,
                        NamedTextColor.YELLOW))
                .append(Component.text(" ] ", NamedTextColor.GRAY));

        if (currentPage < totalPages - 1) {
            nav.append(Component.text("▶", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(baseCommand + " " + (currentPage + 1)))
                    .hoverEvent(HoverEvent.showText(
                            lang.getComponent(viewer, "stats.pagination-next-hover",
                                    "page", currentPage + 2))));
        } else {
            nav.append(Component.text("▶", NamedTextColor.DARK_GRAY));
        }

        viewer.sendMessage(nav.build());
    }

    // ==================== MAIN STATS ====================

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

        String serverName = plugin.getServerName();
        String serverDisplay = config.getServerDisplayName(serverName);

        String chips = getChipsForPlayer(targetUuid);

        Component header;
        if (selfStats) {
            header = lang.getComponent(player, "stats.title-self",
                    "server", serverDisplay,
                    "chips", chips);
        } else {
            header = lang.getComponent(player, "stats.title-other",
                    "player", targetName,
                    "server", serverDisplay,
                    "chips", chips);
        }

        String serverCmd = config.getStatsClickCommand("server");
        if (serverCmd != null && !serverCmd.isEmpty()) {
            header = header.clickEvent(ClickEvent.runCommand(serverCmd));
        }

        player.sendMessage(separator);
        player.sendMessage(header);
        player.sendMessage(separator);

        // Блоки
        sendStatLine(player, "stats.blocks", "blocks",
                "placed", blocksPlaced,
                "broken", blocksBroken,
                "total-placed", totalBlocksPlaced,
                "total-broken", totalBlocksBroken);
        sendSparkline(player, playerId, "blocks_placed");

        // PvE
        sendStatLine(player, "stats.pve", "pve",
                "mobs", mobsKilled,
                "bosses", bossesKilled,
                "total-mobs", totalMobsKilled,
                "total-bosses", totalBossesKilled);
        sendSparkline(player, playerId, "mobs_killed");

        // Время
        sendStatLine(player, "stats.time", "time",
                "time", localTime,
                "total-time", totalTime);
        sendSparkline(player, playerId, "play_time");

        // PvP
        sendStatLine(player, "stats.pvp", "pvp",
                "kills", kills,
                "deaths", deaths,
                "kd", formatKD(kills, deaths),
                "total-kills", totalKills,
                "total-deaths", totalDeaths);
        sendSparkline(player, playerId, "kills");

        // Баланс
        String balanceStr = FormatUtil.formatBalance(plugin, lang, player, balance);
        sendStatLineSimple(player, "stats.balance", "balance",
                balanceStr,
                lang.getRaw(player, "stats.balance-hover"));
        sendSparkline(player, playerId, "balance");

        // Группа
        sendStatLineSimple(player, "stats.group", "group",
                group,
                lang.getRaw(player, "stats.group-hover"));
    }

    private void sendSparkline(Player player, int playerId, String statName) {
        if (!config.isSparklineEnabled()) return;
        if (!config.isSparklineShowInStats()) return;

        String category = mapStatToCategory(statName);
        if (category == null) return;
        if (!config.isSparklineCategoryEnabled(category)) return;

        plugin.getThreadManager().runAsync(() -> {
            try {
                int statTypeId = plugin.getStatTypeDAO().createOrGetStatType(statName);
                List<Integer> history = plugin.getStatHistoryDAO().getHistory(
                        playerId, statTypeId, plugin.getServerId(),
                        config.getSparklineDays());

                if (history.size() < 2) return;

                String graph = Sparkline.generatePretty(history);

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;
                    Component line = lang.getComponent(player, "stats.sparkline",
                            "days", config.getSparklineDays(),
                            "graph", graph);
                    player.sendMessage(line);
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка sparkline: " + e.getMessage());
            }
        });
    }

    private void sendStatLine(Player player, String langKey, String configKey,
                              Object... placeholders) {
        Component line = lang.getComponent(player, langKey, placeholders);
        line = applyClickEvent(line, configKey);
        player.sendMessage(line);
    }

    private void sendStatLineSimple(Player player, String langKey, String configKey,
                                    String mainValue, String hoverValue) {
        Component line = lang.getComponent(player, langKey, "value", mainValue);
        line = line.hoverEvent(HoverEvent.showText(Component.text(hoverValue)));

        String clickCommand = config.getStatsClickCommand(configKey);
        if (clickCommand != null && !clickCommand.isEmpty()) {
            line = line.clickEvent(ClickEvent.runCommand(clickCommand));
        }

        player.sendMessage(line);
    }

    private Component applyClickEvent(Component line, String configKey) {
        String clickCommand = config.getStatsClickCommand(configKey);
        if (clickCommand != null && !clickCommand.isEmpty()) {
            line = line.clickEvent(ClickEvent.runCommand(clickCommand));
        }
        return line;
    }

    // ==================== Утилиты ====================

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

    private String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
    }

    /**
     * Склонение по числу — из lang.yml (time.times.*).
     * one / few / many — 3 формы для языков со склонениями.
     * Для en/zh — все три ключа одинаковы.
     */
    private String pluralize(Player viewer, int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;

        String key;
        if (mod10 == 1 && mod100 != 11) {
            key = "time.times.one";
        } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
            key = "time.times.few";
        } else {
            key = "time.times.many";
        }
        return lang.getRaw(viewer, key);
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