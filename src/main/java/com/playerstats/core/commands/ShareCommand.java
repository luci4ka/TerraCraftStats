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
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;

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

    public void execute(Player player) {
        if (!player.hasPermission("playerstats.share")) {
            player.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        String playerName = player.getName();
        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                int playerId = playerDAO.getOrCreatePlayerId(player.getUniqueId(), playerName);

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

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    Component message = buildShareMessage(
                            player, group, playTimeSec, totalPlayTime,
                            mobsKilled, bossesKilled, totalMobs,
                            blocksPlaced, kills, deaths, balance
                    );

                    Bukkit.broadcast(message);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка в ShareCommand для " + playerName + ": " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(lang.getComponent("error"));
                    }
                });
            }
        });
    }

    public void execute(Player sender, Player target) {
        if (!sender.hasPermission("playerstats.share")) {
            sender.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        if (target == null) {
            sender.sendMessage(lang.getComponent("player-not-found", "player", "?"));
            return;
        }

        if (target.equals(sender)) {
            sender.sendMessage(lang.getComponent("share.self"));
            return;
        }

        String playerName = sender.getName();
        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                int playerId = playerDAO.getOrCreatePlayerId(sender.getUniqueId(), playerName);

                int playTimeSec = statsDAO.getStat(playerId, serverId, "play_time");
                int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
                int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");
                int kills = statsDAO.getStat(playerId, serverId, "kills");
                int deaths = statsDAO.getStat(playerId, serverId, "deaths");
                double balance = statsDAO.getStatDouble(playerId, serverId, "balance");
                String group = groupService.getPlayerGroup(sender, playerId);

                Map<String, Integer> totalStats = statsDAO.getTotalStatsForPlayer(playerId);
                int totalPlayTime = totalStats.getOrDefault("play_time", 0);

                plugin.getThreadManager().runSync(() -> {
                    if (!sender.isOnline()) return;

                    Component message = buildPrivateShareMessage(
                            sender, group, playTimeSec, totalPlayTime,
                            mobsKilled, bossesKilled, kills, deaths, balance
                    );

                    sender.sendMessage(lang.getComponent("share.private-sender",
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
                        sender.sendMessage(lang.getComponent("error"));
                    }
                });
            }
        });
    }

    /**
     * Построить сообщение для шаринга в общий чат.
     */
    private Component buildShareMessage(Player player, String group,
                                        int playTime, int totalPlayTime,
                                        int mobs, int bosses, int totalMobs,
                                        int blocks, int kills, int deaths,
                                        double balance) {
        String serverDisplay = config.getServerDisplayName(plugin.getServerName());

        Component header = lang.getComponent("share.broadcast",
                "player", player.getName(),
                "server", serverDisplay,
                "group", group);

        // Клик и hover на имени игрока
        header = header.clickEvent(ClickEvent.suggestCommand("/stats"))
                .hoverEvent(HoverEvent.showText(lang.getComponent("share.broadcast-hover")));

        Component stats = lang.getComponent("share.stats",
                "time", formatTime(playTime),
                "total-time", formatTime(totalPlayTime),
                "mobs", mobs,
                "bosses", bosses,
                "total-mobs", totalMobs,
                "blocks", blocks,
                "kills", kills,
                "deaths", deaths,
                "kd", formatKD(kills, deaths),
                "balance", formatBalance(balance));

        return header.append(stats);
    }

    /**
     * Построить сообщение для отправки в личные сообщения.
     */
    private Component buildPrivateShareMessage(Player sender, String group,
                                               int playTime, int totalPlayTime,
                                               int mobs, int bosses,
                                               int kills, int deaths, double balance) {
        String serverDisplay = config.getServerDisplayName(plugin.getServerName());

        return lang.getComponent("share.private-target",
                        "player", sender.getName(),
                        "server", serverDisplay,
                        "group", group,
                        "time", formatTime(playTime),
                        "total-time", formatTime(totalPlayTime),
                        "mobs", mobs,
                        "bosses", bosses,
                        "kills", kills,
                        "deaths", deaths,
                        "kd", formatKD(kills, deaths),
                        "balance", formatBalance(balance))
                .hoverEvent(HoverEvent.showText(lang.getComponent("share.private-hover",
                        "player", sender.getName())));
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return lang.get("time.hours", "hours", hours, "minutes", minutes);
        }
        return lang.get("time.minutes", "minutes", minutes);
    }

    private String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
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
}