package ru.terracraft.stats.core.commands;

import ru.terracraft.stats.PlayerStatsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

/**
 * /stats rollback <player> <stat> <value>
 * /stats rollback <player> --all
 * /stats rollback <player> --from <date> <stat>
 *
 * <p><b>--from</b> требует, чтобы в {@code config.yml} было
 * {@code rollback.track-deltas: true} — иначе {@code stat_deltas} не
 * пополняется и команда вернёт текущее значение (no-op).</p>
 */
public class RollbackCommand {

    private final PlayerStatsPlugin plugin;

    public RollbackCommand(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstats.rollback")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(getComponent(sender, "rollback.usage"));
            return;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(getComponent(sender, "commands.player-not-found",
                    "player", targetName));
            return;
        }

        String mode = args[1];

        if (mode.equals("--all")) {
            rollbackAll(sender, target);
            return;
        }

        if (mode.equals("--from")) {
            if (args.length < 4) {
                sender.sendMessage(getComponent(sender, "rollback.usage"));
                return;
            }
            rollbackFrom(sender, target, args[3], args[2]);
            return;
        }

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

        rollbackOne(sender, target, args[1], value);
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

                plugin.getRollbackLockService().lock(target.getUniqueId(), statName);

                // balance — double-стата, остальное — int
                if ("balance".equalsIgnoreCase(statName)) {
                    plugin.getStatsDAO().setStatDouble(playerId, plugin.getServerId(),
                            statName, value);
                } else {
                    plugin.getStatsDAO().setStat(playerId, plugin.getServerId(),
                            statName, value);
                }

                notifyPlayer(target, statName, value);
                log(sender, target, statName, value);

                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.done",
                                "player", target.getName(),
                                "stat", statName,
                                "value", value)));
            } catch (SQLException e) {
                plugin.getLogger().severe("Rollback error: " + e.getMessage());
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.error")));
            }
        });
    }

    // ==================== --all ====================

    private void rollbackAll(CommandSender sender, OfflinePlayer target) {
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

                plugin.getRollbackLockService().lockAll(target.getUniqueId());

                int updated = plugin.getStatsDAO()
                        .resetAllStats(playerId, plugin.getServerId());

                notifyPlayerAll(target);
                log(sender, target, "--all", 0);

                final int count = updated;
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.done-all",
                                "player", target.getName(),
                                "count", count)));
            } catch (SQLException e) {
                plugin.getLogger().severe("Rollback --all error: " + e.getMessage());
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

        // Предупреждаем, если дельты не пишутся — команда будет no-op
        if (!plugin.getConfigManager().main().rollback().trackDeltas()) {
            sender.sendMessage(getComponent(sender, "rollback.from-disabled"));
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

                plugin.getRollbackLockService().lock(target.getUniqueId(), statName);
                plugin.getStatsDAO().setStat(playerId, plugin.getServerId(),
                        statName, newValue);

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
                plugin.getLogger().severe("Rollback --from error: " + e.getMessage());
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "rollback.error")));
            }
        });
    }

    // ==================== УТИЛИТЫ ====================

    private long parseDate(String s) {
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
        if (!plugin.getConfigManager().main().rollback().notifyPlayer()) return;
        Player online = target.getPlayer();
        if (online == null) return;

        plugin.getThreadManager().runSync(() ->
                online.sendMessage(getComponent(online, "rollback.notify",
                        "stat", statName,
                        "value", value)));
    }

    private void notifyPlayerAll(OfflinePlayer target) {
        if (!plugin.getConfigManager().main().rollback().notifyPlayer()) return;
        Player online = target.getPlayer();
        if (online == null) return;

        plugin.getThreadManager().runSync(() ->
                online.sendMessage(getComponent(online, "rollback.notify-all")));
    }

    private void log(CommandSender sender, OfflinePlayer target,
                     String statName, int value) {
        if (!plugin.getConfigManager().main().rollback().logToConsole()) return;
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