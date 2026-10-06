package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.services.PlayerGroupService;
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

    // ==================== Константы ====================

    /** Сколько строк показывать на одной странице */
    private static final int PAGE_SIZE_PVP = 20;
    private static final int PAGE_SIZE_MOB = 30;
    private static final int PAGE_SIZE_BLOCK = 15;

    // ==================== Поля ====================

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
            player.sendMessage(lang.getComponent("no-permission"));
            return;
        }
        loadAndSend(player, player, player.getName(), player.getUniqueId(),
                true, "main", 0);
    }

    public void execute(Player viewer, OfflinePlayer target) {
        if (!viewer.hasPermission("playerstats.stats.others")) {
            viewer.sendMessage(lang.getComponent("no-permission"));
            return;
        }
        if (target == null) {
            viewer.sendMessage(lang.getComponent("player-not-found", "player", "?"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "main", 0);
    }

    /**
     * /stats pvp [player] [page]
     */
    public void executePvp(Player viewer, OfflinePlayer target, int page) {
        if (!viewer.hasPermission("playerstats.stats.others") && !target.equals(viewer)) {
            viewer.sendMessage(lang.getComponent("no-permission"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "pvp", page);
    }

    /**
     * /stats mob [player] [page]
     */
    public void executeMob(Player viewer, OfflinePlayer target, int page) {
        if (!viewer.hasPermission("playerstats.stats.others") && !target.equals(viewer)) {
            viewer.sendMessage(lang.getComponent("no-permission"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "mob", page);
    }

    /**
     * /stats block [player] [page]
     */
    public void executeBlock(Player viewer, OfflinePlayer target, int page) {
        if (!viewer.hasPermission("playerstats.stats.others") && !target.equals(viewer)) {
            viewer.sendMessage(lang.getComponent("no-permission"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        loadAndSend(viewer, target, targetName, target.getUniqueId(),
                target.equals(viewer), "block", page);
    }

    // ==================== ОСНОВНАЯ ЛОГИКА ====================

    /**
     * @param mode "main" | "pvp" | "mob" | "block"
     * @param page номер страницы (0-based)
     */
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
                plugin.getLogger().severe("Ошибка при получении статистики для "
                        + targetName + ": " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage(lang.getComponent("error"));
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

        TimeFormat localTime = formatTime(playTimeSec);
        TimeFormat totalTime = formatTime(totalStats.getOrDefault("play_time", 0));

        final String groupFinal = group;

        plugin.getThreadManager().runSync(() -> {
            if (!viewer.isOnline()) return;

            sendMainStats(viewer, targetName, selfStats,
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

            // Подсказка о подкомандах
            viewer.sendMessage(lang.getComponent("stats.header"));
            viewer.sendMessage(lang.getComponent("stats.hint-subcommands"));
            viewer.sendMessage(lang.getComponent("stats.header"));
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

            Component separator = lang.getComponent("stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.pvp-title-self" : "stats.pvp-title-other";
            Component title = lang.getComponent(titleKey,
                    "player", targetName,
                    "kills", kills,
                    "deaths", deaths,
                    "kd", formatKD(kills, deaths));
            viewer.sendMessage(title);
            viewer.sendMessage(separator);

            if (sorted.isEmpty()) {
                viewer.sendMessage(lang.getComponent("stats.pvp-empty"));
                viewer.sendMessage(separator);
                return;
            }

            viewer.sendMessage(lang.getComponent("stats.pvp-header",
                    "count", sorted.size()));

            // Пагинация
            int totalPages = (int) Math.ceil((double) sorted.size() / PAGE_SIZE_PVP);
            int currentPage = Math.max(0, Math.min(page, totalPages - 1));
            int start = currentPage * PAGE_SIZE_PVP;
            int end = Math.min(start + PAGE_SIZE_PVP, sorted.size());

            for (int i = start; i < end; i++) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                String victimUuid = entry.getKey().substring("pvp_kill_".length());
                String victimName = resolvePlayerName(victimUuid);

                Component line = Component.text()
                        .append(Component.text("  ☠ ", NamedTextColor.RED))
                        .append(Component.text(victimName, NamedTextColor.YELLOW))
                        .append(Component.text(" — ", NamedTextColor.GRAY))
                        .append(Component.text(entry.getValue() + " раз",
                                NamedTextColor.WHITE))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "§7Нажмите, чтобы посмотреть статистику " + victimName
                                        + "\n§7UUID: §8" + victimUuid)))
                        .clickEvent(ClickEvent.runCommand("/stats pvp " + victimName))
                        .build();
                viewer.sendMessage(line);
            }

            // Навигация
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

            Component separator = lang.getComponent("stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.mob-title-self" : "stats.mob-title-other";
            Component title = lang.getComponent(titleKey,
                    "player", targetName,
                    "mobs", mobsKilled,
                    "bosses", bossesKilled);
            viewer.sendMessage(title);
            viewer.sendMessage(separator);

            if (sorted.isEmpty()) {
                viewer.sendMessage(lang.getComponent("stats.mob-empty"));
                viewer.sendMessage(separator);
                return;
            }

            viewer.sendMessage(lang.getComponent("stats.mob-header",
                    "count", sorted.size()));

            // Пагинация
            int totalPages = (int) Math.ceil((double) sorted.size() / PAGE_SIZE_MOB);
            int currentPage = Math.max(0, Math.min(page, totalPages - 1));
            int start = currentPage * PAGE_SIZE_MOB;
            int end = Math.min(start + PAGE_SIZE_MOB, sorted.size());

            for (int i = start; i < end; i++) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                String mobName = entry.getKey().substring("mob_killed_".length());

                Component line = Component.text()
                        .append(Component.text("  ☠ ", NamedTextColor.DARK_RED))
                        .append(Component.text(prettify(mobName), NamedTextColor.YELLOW))
                        .append(Component.text(" — ", NamedTextColor.GRAY))
                        .append(Component.text(entry.getValue() + " шт",
                                NamedTextColor.WHITE))
                        .build();
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

            Component separator = lang.getComponent("stats.header");
            viewer.sendMessage(separator);

            String titleKey = selfStats ? "stats.block-title-self" : "stats.block-title-other";
            Component title = lang.getComponent(titleKey,
                    "player", targetName,
                    "placed", blocksPlaced,
                    "broken", blocksBroken);
            viewer.sendMessage(title);
            viewer.sendMessage(separator);

            // ==================== ПОСТАВЛЕННЫЕ ====================
            viewer.sendMessage(lang.getComponent("stats.block-placed-header",
                    "count", sortedPlaced.size()));

            if (sortedPlaced.isEmpty()) {
                viewer.sendMessage(lang.getComponent("stats.block-empty"));
            } else {
                int totalPages = (int) Math.ceil((double) sortedPlaced.size() / PAGE_SIZE_BLOCK);
                int currentPage = Math.max(0, Math.min(page, totalPages - 1));
                int start = currentPage * PAGE_SIZE_BLOCK;
                int end = Math.min(start + PAGE_SIZE_BLOCK, sortedPlaced.size());

                for (int i = start; i < end; i++) {
                    Map.Entry<String, Integer> entry = sortedPlaced.get(i);
                    String blockName = entry.getKey().substring("block_placed_".length());
                    Component line = Component.text()
                            .append(Component.text("  🏗 ", NamedTextColor.GREEN))
                            .append(Component.text(prettify(blockName), NamedTextColor.YELLOW))
                            .append(Component.text(" — ", NamedTextColor.GRAY))
                            .append(Component.text(entry.getValue() + " шт",
                                    NamedTextColor.WHITE))
                            .build();
                    viewer.sendMessage(line);
                }

                sendPaginationFooter(viewer, currentPage, totalPages,
                        "/stats block " + targetName);
            }

            // ==================== СЛОМАННЫЕ ====================
            viewer.sendMessage(lang.getComponent("stats.block-broken-header",
                    "count", sortedBroken.size()));

            if (sortedBroken.isEmpty()) {
                viewer.sendMessage(lang.getComponent("stats.block-empty"));
            } else {
                int totalPages = (int) Math.ceil((double) sortedBroken.size() / PAGE_SIZE_BLOCK);
                int currentPage = Math.max(0, Math.min(page, totalPages - 1));
                int start = currentPage * PAGE_SIZE_BLOCK;
                int end = Math.min(start + PAGE_SIZE_BLOCK, sortedBroken.size());

                for (int i = start; i < end; i++) {
                    Map.Entry<String, Integer> entry = sortedBroken.get(i);
                    String blockName = entry.getKey().substring("block_broken_".length());
                    Component line = Component.text()
                            .append(Component.text("  ⛏ ", NamedTextColor.GOLD))
                            .append(Component.text(prettify(blockName), NamedTextColor.YELLOW))
                            .append(Component.text(" — ", NamedTextColor.GRAY))
                            .append(Component.text(entry.getValue() + " шт",
                                    NamedTextColor.WHITE))
                            .build();
                    viewer.sendMessage(line);
                }

                // Для сломанных — вторая навигация (та же страница)
                sendPaginationFooter(viewer, currentPage, totalPages,
                        "/stats block " + targetName);
            }

            viewer.sendMessage(separator);
        });
    }

    // ==================== ПАГИНАЦИЯ ====================

    /**
     * Отправить футер с навигацией по страницам.
     *
     * @param baseCommand команда без номера страницы (например "/stats mob Steve")
     */
    private void sendPaginationFooter(Player viewer, int currentPage,
                                      int totalPages, String baseCommand) {
        if (totalPages <= 1) return;

        // ← БЫЛО: Component nav = Component.text();
        // ← СТАЛО: var nav = Component.text();  (тип выводится как TextComponent.Builder)
        var nav = Component.text();

        // ◀ Предыдущая
        if (currentPage > 0) {
            nav.append(Component.text("◀ ", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(baseCommand + " " + (currentPage - 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("§7Страница " + currentPage))));
        } else {
            nav.append(Component.text("◀ ", NamedTextColor.DARK_GRAY));
        }

        // Текущая / всего
        nav.append(Component.text(" [ ", NamedTextColor.GRAY))
                .append(Component.text((currentPage + 1) + " / " + totalPages,
                        NamedTextColor.YELLOW))
                .append(Component.text(" ] ", NamedTextColor.GRAY));

        // Следующая ▶
        if (currentPage < totalPages - 1) {
            nav.append(Component.text("▶", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(baseCommand + " " + (currentPage + 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("§7Страница " + (currentPage + 2)))));
        } else {
            nav.append(Component.text("▶", NamedTextColor.DARK_GRAY));
        }

        viewer.sendMessage(nav.build());
    }

    // ==================== MAIN STATS ====================

    private void sendMainStats(Player player, String targetName, boolean selfStats,
                               int blocksPlaced, int blocksBroken,
                               int totalBlocksPlaced, int totalBlocksBroken,
                               int mobsKilled, int bossesKilled,
                               int totalMobsKilled, int totalBossesKilled,
                               TimeFormat localTime, TimeFormat totalTime,
                               int kills, int deaths,
                               int totalKills, int totalDeaths,
                               double balance, String group) {
        Component separator = lang.getComponent("stats.header");

        String serverName = plugin.getServerName();
        String serverDisplay = config.getServerDisplayName(serverName);

        Component header;
        if (selfStats) {
            header = lang.getComponent("stats.title-self", "server", serverDisplay);
        } else {
            header = lang.getComponent("stats.title-other",
                    "player", targetName,
                    "server", serverDisplay);
        }

        String serverCmd = config.getStatsClickCommand("server");
        if (serverCmd != null && !serverCmd.isEmpty()) {
            header = header.clickEvent(ClickEvent.runCommand(serverCmd));
        }

        player.sendMessage(separator);
        player.sendMessage(header);
        player.sendMessage(separator);

        sendStatLine(player, "stats.blocks", "blocks",
                "placed", blocksPlaced,
                "broken", blocksBroken,
                "total-placed", totalBlocksPlaced,
                "total-broken", totalBlocksBroken);

        sendStatLine(player, "stats.pve", "pve",
                "mobs", mobsKilled,
                "bosses", bossesKilled,
                "total-mobs", totalMobsKilled,
                "total-bosses", totalBossesKilled);

        sendStatLine(player, "stats.time", "time",
                "time", localTime.toString(),
                "total-time", totalTime.toString());

        sendStatLine(player, "stats.pvp", "pvp",
                "kills", kills,
                "deaths", deaths,
                "total-kills", totalKills,
                "total-deaths", totalDeaths);

        String balanceStr = formatBalance(balance);
        sendStatLineSimple(player, "stats.balance", "balance",
                balanceStr,
                lang.getRaw("stats.balance-hover"));

        sendStatLineSimple(player, "stats.group", "group",
                group,
                lang.getRaw("stats.group-hover"));
    }

    private void sendStatLine(Player player, String langKey, String configKey,
                              Object... placeholders) {
        Component line = lang.getComponent(langKey, placeholders);
        line = applyClickEvent(line, configKey, placeholders);
        player.sendMessage(line);
    }

    private void sendStatLineSimple(Player player, String langKey, String configKey,
                                    String mainValue, String hoverValue) {
        Component line = lang.getComponent(langKey, "value", mainValue);
        line = line.hoverEvent(HoverEvent.showText(Component.text(hoverValue)));

        String clickCommand = config.getStatsClickCommand(configKey);
        if (clickCommand != null && !clickCommand.isEmpty()) {
            line = line.clickEvent(ClickEvent.runCommand(clickCommand));
        }

        player.sendMessage(line);
    }

    private Component applyClickEvent(Component line, String configKey, Object... placeholders) {
        String clickCommand = config.getStatsClickCommand(configKey);
        if (clickCommand != null && !clickCommand.isEmpty()) {
            line = line.clickEvent(ClickEvent.runCommand(clickCommand));
        }
        return line;
    }

    // ==================== Утилиты ====================

    private TimeFormat formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        return new TimeFormat(hours, minutes);
    }

    private record TimeFormat(int hours, int minutes) {
        @Override
        public String toString() {
            if (hours > 0) {
                return hours + " ч " + minutes + " мин";
            }
            return minutes + " мин";
        }
    }

    private String formatBalance(double balance) {
        if (plugin.getHooksManager() != null
                && plugin.getHooksManager().getVaultHook() != null
                && plugin.getHooksManager().getVaultHook().isEnabled()) {
            try {
                return plugin.getHooksManager().getVaultHook().format(balance);
            } catch (Exception e) {
                // fallback
            }
        }
        return String.format("%.2f₽", balance);
    }

    private String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
    }

    /**
     * Резолв UUID → имя игрока.
     */
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

    /**
     * "diamond_ore" → "Diamond Ore".
     */
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