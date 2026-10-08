package com.playerstats.core.commands;

import com.playerstats.Main;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

/**
 * /stats rollback <player> <stat> <value>
 * /stats rollback <player> --all <value>
 * /stats rollback <player> --from <date> <stat>
 */
public class RollbackCommand {

    private final Main plugin;

    public RollbackCommand(Main plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstats.rollback")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(getComponent(sender, "rollback.usage"));
            return;
        }

        // args[0] = player
        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(getComponent(sender, "commands.player-not-found",
                    "player", targetName));
            return;
        }

        // args[1] = stat | --all | --from
        String mode = args[1];

        if (mode.equals("--all")) {
            if (args.length < 3) {
                sender.sendMessage(getComponent(sender, "rollback.usage"));
                return;
            }
            int value;
            try {
                value = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getComponent(sender, "rollback.invalid-value",
                        "value", args[2]));
                return;
            }
            rollbackAll(sender, target, value);
            return;
        }

        if (mode.equals("--from")) {
            if (args.length < 4) {
                sender.sendMessage(getComponent(sender, "rollback.usage"));
                return;
            }
            String dateStr = args[2];
            String statName = args[3];
            rollbackFrom(sender, target, statName, dateStr);
            return;
        }

        // Обычный режим: <stat> <value>
        if (args.length < 3) {
            sender.sendMessage(getComponent(sender, "rollback.usage"));
            return;
        }

        String statName = args[1];
        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(getComponent(sender, "rollback.invalid-value",
                    "value", args[2]));
            return;
        }

        rollbackOne(sender, target, statName, value);
    }

    // ==================== ОДНА СТАТА ====================

    private void rollbackOne(CommandSender sender, OfflinePlayer target,
                             String statName, int value) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                int playerId = plugin.getPlayerDAO()
                        .getPlayerId(target.getUniqueId());
                if (playerId == -1) {
                    plugin.getThreadManager().runSync(() ->
                            sender.sendMessage(getComponent(sender,
                                    "rollback.never-played",
                                    "player", target.getName())));
                    return;
                }

                plugin.getStatsDAO().setStat(playerId, plugin.getServerId(),
                        statName, value);

                // Lock
                plugin.getRollbackLockService().lock(target.getUniqueId(), statName);

                // Notify
                notifyPlayer(target, statName, value);
                log(sender, target, statName, value);

                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.done",
                                "player", target.getName(),
                                "stat", statName,
                                "value", value)));
            } catch (SQLException e) {
                plugin.getLogger().warning("Rollback error: " + e.getMessage());
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.error")));
            }
        });
    }

    // ==================== ВСЕ ====================

    private void rollbackAll(CommandSender sender, OfflinePlayer target, int value) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                int playerId = plugin.getPlayerDAO()
                        .getPlayerId(target.getUniqueId());
                if (playerId == -1) {
                    plugin.getThreadManager().runSync(() ->
                            sender.sendMessage(getComponent(sender,
                                    "rollback.never-played",
                                    "player", target.getName())));
                    return;
                }

                int updated = plugin.getStatsDAO()
                        .resetAllStats(playerId, plugin.getServerId());

                // Lock all
                plugin.getRollbackLockService().lockAll(target.getUniqueId());

                notifyPlayerAll(target, value);
                log(sender, target, "--all", value);

                final int count = updated;
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.done-all",
                                "player", target.getName(),
                                "count", count)));
            } catch (SQLException e) {
                plugin.getLogger().warning("Rollback --all error: " + e.getMessage());
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.error")));
            }
        });
    }

    // ==================== ОТ ДАТЫ ====================

    private void rollbackFrom(CommandSender sender, OfflinePlayer target,
                              String statName, String dateStr) {
        long since;
        try {
            since = parseDate(dateStr);
        } catch (Exception e) {
            sender.sendMessage(getComponent(sender, "rollback.invalid-date",
                    "date", dateStr));
            return;
        }

        plugin.getThreadManager().runAsync(() -> {
            try {
                int playerId = plugin.getPlayerDAO()
                        .getPlayerId(target.getUniqueId());
                if (playerId == -1) {
                    plugin.getThreadManager().runSync(() ->
                            sender.sendMessage(getComponent(sender,
                                    "rollback.never-played",
                                    "player", target.getName())));
                    return;
                }

                int current = plugin.getStatsDAO()
                        .getStat(playerId, plugin.getServerId(), statName);
                int deltaSince = plugin.getStatsDAO()
                        .sumDeltasSince(playerId, statName, since);

                int newValue = Math.max(0, current - deltaSince);

                plugin.getStatsDAO().setStat(playerId, plugin.getServerId(),
                        statName, newValue);
                plugin.getRollbackLockService().lock(target.getUniqueId(), statName);

                notifyPlayer(target, statName, newValue);
                log(sender, target, statName + " (from " + dateStr + ")", newValue);

                final int nv = newValue;
                final int ds = deltaSince;
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.done-from",
                                "player", target.getName(),
                                "stat", statName,
                                "delta", ds,
                                "value", nv)));
            } catch (SQLException e) {
                plugin.getLogger().warning("Rollback --from error: " + e.getMessage());
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.error")));
            }
        });
    }

    // ==================== УТИЛИТЫ ====================

    /**
     * Парсит дату: "2026-01-01", "2026-01-01_12-00", "1d", "2h", "30m".
     */
    private long parseDate(String s) {
        // Относительное: 1d, 2h, 30m, 60s
        if (s.matches("\\d+[smhd]")) {
            long now = System.currentTimeMillis();
            int num = Integer.parseInt(s.substring(0, s.length() - 1));
            char unit = s.charAt(s.length() - 1);
            return switch (unit) {
                case 's' -> now - num * 1000L;
                case 'm' -> now - num * 60 * 1000L;
                case 'h' -> now - num * 3600 * 1000L;
                case 'd' -> now - num * 86400 * 1000L;
                default -> now;
            };
        }

        // Абсолютное — через SimpleDateFormat
        for (String pattern : new String[]{
                "yyyy-MM-dd_HH-mm",
                "yyyy-MM-dd_HH:mm",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        }) {
            try {
                var fmt = new java.text.SimpleDateFormat(pattern);
                fmt.setLenient(false);
                return fmt.parse(s).getTime();
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Invalid date: " + s);
    }

    private void notifyPlayer(OfflinePlayer target, String statName, int value) {
        if (!plugin.getConfigHandler().isRollbackNotifyPlayer()) return;
        Player online = target.getPlayer();
        if (online == null) return;

        plugin.getThreadManager().runSync(() ->
                online.sendMessage(getComponent(online, "rollback.notify",
                        "stat", statName,
                        "value", value)));
    }

    private void notifyPlayerAll(OfflinePlayer target, int value) {
        if (!plugin.getConfigHandler().isRollbackNotifyPlayer()) return;
        Player online = target.getPlayer();
        if (online == null) return;

        plugin.getThreadManager().runSync(() ->
                online.sendMessage(getComponent(online, "rollback.notify-all",
                        "value", value)));
    }

    private void log(CommandSender sender, OfflinePlayer target,
                     String statName, int value) {
        if (!plugin.getConfigHandler().isRollbackLogToConsole()) return;
        plugin.getLogger().info("[Rollback] " + sender.getName()
                + " → " + target.getName()
                + " stat=" + statName + " value=" + value);
    }

    private Component getComponent(CommandSender sender, String key, Object... repl) {
        if (sender instanceof Player player) {
            return plugin.getLanguageManager().getComponent(player, key, repl);
        }
        return plugin.getLanguageManager().getComponent(key, repl);
    }
}