package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.services.PlayerGroupService;
import com.playerstats.core.services.TopCache;
import com.playerstats.core.utils.FormatUtil;
import com.playerstats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class ShareCommand {

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public ShareCommand(Main plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
        this.groupService = groupService;
    }

    // ==================== /share ====================

    public void execute(Player player) {
        if (!player.hasPermission("playerstats.share")) {
            player.sendMessage(lang.getComponent(player, "no-permission"));
            return;
        }

        String playerName = player.getName();
        UUID uuid = player.getUniqueId();
        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                int playerId = playerDAO.getOrCreatePlayerId(uuid, playerName);

                int playTimeSec = statsDAO.getStat(playerId, serverId, "play_time");
                int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
                int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");
                int blocksPlaced = statsDAO.getStat(playerId, serverId, "blocks_placed");
                int kills = statsDAO.getStat(playerId, serverId, "kills");
                int deaths = statsDAO.getStat(playerId, serverId, "deaths");
                Map<String, Double> totalDoubleStats = statsDAO.getTotalStatsDoubleForPlayer(playerId);
                double balance = totalDoubleStats.getOrDefault("balance", 0.0);
                String group = groupService.getPlayerGroup(player, playerId);

                Map<String, Integer> totalStats = statsDAO.getTotalStatsForPlayer(playerId);
                int totalPlayTime = totalStats.getOrDefault("play_time", 0);
                int totalMobs = totalStats.getOrDefault("mobs_killed", 0);

                final String groupFinal = group;

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    // Для каждого онлайн-игрока — на его языке
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        Component message = buildShareMessage(
                                online, player, uuid, groupFinal,
                                playTimeSec, totalPlayTime,
                                mobsKilled, bossesKilled, totalMobs,
                                blocksPlaced, kills, deaths, balance
                        );
                        online.sendMessage(message);
                    }
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка в ShareCommand для " + playerName + ": " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(lang.getComponent(player, "error"));
                    }
                });
            }
        });
    }

    // ==================== /share <player> ====================

    public void execute(Player sender, Player target) {
        if (!sender.hasPermission("playerstats.share")) {
            sender.sendMessage(lang.getComponent(sender, "no-permission"));
            return;
        }

        if (target == null) {
            sender.sendMessage(lang.getComponent(sender, "player-not-found", "player", "?"));
            return;
        }

        if (target.equals(sender)) {
            sender.sendMessage(lang.getComponent(sender, "share.self"));
            return;
        }

        String playerName = sender.getName();
        UUID uuid = sender.getUniqueId();
        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                int playerId = playerDAO.getOrCreatePlayerId(uuid, playerName);

                int playTimeSec = statsDAO.getStat(playerId, serverId, "play_time");
                int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
                int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");
                int kills = statsDAO.getStat(playerId, serverId, "kills");
                int deaths = statsDAO.getStat(playerId, serverId, "deaths");
                double balance = statsDAO.getStatDouble(playerId, serverId, "balance");
                String group = groupService.getPlayerGroup(sender, playerId);

                Map<String, Integer> totalStats = statsDAO.getTotalStatsForPlayer(playerId);
                int totalPlayTime = totalStats.getOrDefault("play_time", 0);

                final String groupFinal = group;

                plugin.getThreadManager().runSync(() -> {
                    if (!sender.isOnline()) return;

                    // Получателю — на его языке
                    Component message = buildPrivateShareMessage(
                            target, sender, uuid, groupFinal,
                            playTimeSec, totalPlayTime,
                            mobsKilled, bossesKilled,
                            kills, deaths, balance
                    );

                    // Отправителю — на его языке
                    sender.sendMessage(lang.getComponent(sender, "share.private-sender",
                            "target", target.getName()));

                    if (target.isOnline()) {
                        target.sendMessage(message);
                    }
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка в ShareCommand: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (sender.isOnline()) {
                        sender.sendMessage(lang.getComponent(sender, "error"));
                    }
                });
            }
        });
    }

    // ==================== BUILD MESSAGES ====================

    /**
     * Сообщение для шаринга в общий чат.
     *
     * @param receiver игрок, который УВИДИТ сообщение (для языка)
     * @param sender   игрок, чью статистику показываем
     */
    private Component buildShareMessage(Player receiver, Player sender,
                                        UUID uuid, String group,
                                        int playTime, int totalPlayTime,
                                        int mobs, int bosses, int totalMobs,
                                        int blocks, int kills, int deaths,
                                        double balance) {
        String serverDisplay = config.getServerDisplayName(plugin.getServerName());
        String chips = getChipsForPlayer(uuid);

        // Заголовок — на языке ПОЛУЧАТЕЛЯ
        Component header = lang.getComponent(receiver, "share.broadcast",
                "player", sender.getName(),
                "chips", chips,
                "server", serverDisplay,
                "group", group);

        // Hover — на языке ПОЛУЧАТЕЛЯ
        Component hoverText = lang.getComponentFromString(
                "<gray>Нажмите, чтобы открыть статистику\n" +
                        "<gray>Игрок: <white>" + sender.getName() + "\n" +
                        "<gray>Сервер: <white>" + serverDisplay + "\n" +
                        "<gray>Группа: " + group + "\n" +
                        "<gray>K/D: <white>" + formatKD(kills, deaths));

        header = header.clickEvent(ClickEvent.runCommand("/stats " + sender.getName()))
                .hoverEvent(HoverEvent.showText(hoverText));

        // Статистика — на языке ПОЛУЧАТЕЛЯ
        Component stats = lang.getComponent(receiver, "share.stats",
                "time", TimeUtil.formatTime(lang, receiver, playTime),
                "total-time", TimeUtil.formatTime(lang, receiver, totalPlayTime),
                "mobs", mobs,
                "bosses", bosses,
                "total-mobs", totalMobs,
                "blocks", blocks,
                "kills", kills,
                "deaths", deaths,
                "kd", formatKD(kills, deaths),
                "balance", FormatUtil.formatBalance(plugin, lang, receiver, balance));

        return header.append(stats);
    }

    /**
     * Сообщение для ЛС.
     *
     * @param receiver игрок, который УВИДИТ сообщение (для языка)
     * @param sender   игрок, чью статистику показываем
     */
    private Component buildPrivateShareMessage(Player receiver, Player sender,
                                               UUID uuid, String group,
                                               int playTime, int totalPlayTime,
                                               int mobs, int bosses,
                                               int kills, int deaths, double balance) {
        String serverDisplay = config.getServerDisplayName(plugin.getServerName());
        String chips = getChipsForPlayer(uuid);

        // Сообщение — на языке ПОЛУЧАТЕЛЯ
        Component message = lang.getComponent(receiver, "share.private-target",
                "player", sender.getName(),
                "chips", chips,
                "server", serverDisplay,
                "group", group,
                "time", TimeUtil.formatTime(lang, receiver, playTime),
                "total-time", TimeUtil.formatTime(lang, receiver, totalPlayTime),
                "mobs", mobs,
                "bosses", bosses,
                "kills", kills,
                "deaths", deaths,
                "kd", formatKD(kills, deaths),
                "balance", FormatUtil.formatBalance(plugin, lang, receiver, balance));

        Component hoverText = lang.getComponentFromString(
                "<gray>Нажмите, чтобы открыть статистику\n" +
                        "<gray>Игрок: <white>" + sender.getName() + "\n" +
                        "<gray>K/D: <white>" + formatKD(kills, deaths));

        return message.clickEvent(ClickEvent.runCommand("/stats " + sender.getName()))
                .hoverEvent(HoverEvent.showText(hoverText));
    }

    // ==================== УТИЛИТЫ ====================

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

    private String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
    }

}