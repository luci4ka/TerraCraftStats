package ru.terracraft.stats.core.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.database.dao.PlayerDAO;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;
import ru.terracraft.stats.core.services.PlayerGroupService;
import ru.terracraft.stats.core.services.TopCache;
import ru.terracraft.stats.core.utils.FormatUtil;
import ru.terracraft.stats.core.utils.SpriteUtil;
import ru.terracraft.stats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class ShareCommand {

    /** Спрайт для заголовка share.broadcast. */
    private static final Component SPRITE_HEADER =
            SpriteUtil.item("minecraft:item/writable_book", "📊");

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public ShareCommand(PlayerStatsPlugin plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.groupService = groupService;
    }

    public void register() {
        new CommandAPICommand("share")
                .withPermission("playerstats.share")
                .executesPlayer((player, args) -> {
                    execute(player);
                    return 0;
                })
                .withSubcommand(new CommandAPICommand("player")
                        .withArguments(new StringArgument("player"))
                        .executesPlayer((player, args) -> {
                            String targetName = (String) args.get("player");
                            Player target = Bukkit.getPlayer(targetName);
                            execute(player, target);
                            return 0;
                        }))
                .register();
    }

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
                final double balanceFinal = balance;

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        Component message = buildShareMessage(
                                online, player, uuid, groupFinal,
                                playTimeSec, totalPlayTime,
                                mobsKilled, bossesKilled, totalMobs,
                                blocksPlaced, kills, deaths, balanceFinal
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

                Map<String, Double> totalDoubleStats = statsDAO.getTotalStatsDoubleForPlayer(playerId);
                double balance = totalDoubleStats.getOrDefault("balance", 0.0);

                String group = groupService.getPlayerGroup(sender, playerId);

                Map<String, Integer> totalStats = statsDAO.getTotalStatsForPlayer(playerId);
                int totalPlayTime = totalStats.getOrDefault("play_time", 0);

                final String groupFinal = group;
                final double balanceFinal = balance;

                plugin.getThreadManager().runSync(() -> {
                    if (!sender.isOnline()) return;

                    Component message = buildPrivateShareMessage(
                            target, sender, uuid, groupFinal,
                            playTimeSec, totalPlayTime,
                            mobsKilled, bossesKilled,
                            kills, deaths, balanceFinal
                    );

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

    private Component buildShareMessage(Player receiver, Player sender,
                                        UUID uuid, String group,
                                        int playTime, int totalPlayTime,
                                        int mobs, int bosses, int totalMobs,
                                        int blocks, int kills, int deaths,
                                        double balance) {
        String serverDisplay = plugin.getServerName();
        String chips = getChipsForPlayer(uuid);

        // Заголовок — со спрайтом (fallback — эмодзи).
        Component header = SPRITE_HEADER.append(Component.text(" "))
                .append(lang.getComponent(receiver, "share.broadcast",
                        "player", sender.getName(),
                        "chips", chips,
                        "server", serverDisplay,
                        "group", group));

        Component hoverText = lang.getComponent(receiver, "share.broadcast-hover",
                "player", sender.getName(),
                "server", serverDisplay,
                "group", group,
                "kd", formatKD(receiver, kills, deaths));

        header = header.clickEvent(ClickEvent.runCommand("/stats " + sender.getName()))
                .hoverEvent(HoverEvent.showText(hoverText));

        Component stats = lang.getComponent(receiver, "share.stats",
                "time", TimeUtil.formatTime(lang, receiver, playTime),
                "total-time", TimeUtil.formatTime(lang, receiver, totalPlayTime),
                "mobs", mobs,
                "bosses", bosses,
                "total-mobs", totalMobs,
                "blocks", blocks,
                "kills", kills,
                "deaths", deaths,
                "kd", formatKD(receiver, kills, deaths),
                "balance", FormatUtil.formatBalance(plugin, lang, receiver, balance));

        return header.append(stats);
    }

    private Component buildPrivateShareMessage(Player receiver, Player sender,
                                               UUID uuid, String group,
                                               int playTime, int totalPlayTime,
                                               int mobs, int bosses,
                                               int kills, int deaths, double balance) {
        String serverDisplay = plugin.getServerName();
        String chips = getChipsForPlayer(uuid);

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
                "kd", formatKD(receiver, kills, deaths),
                "balance", FormatUtil.formatBalance(plugin, lang, receiver, balance));

        Component hoverText = lang.getComponent(receiver, "share.private-hover",
                "player", sender.getName(),
                "kd", formatKD(receiver, kills, deaths));

        return message.clickEvent(ClickEvent.runCommand("/stats " + sender.getName()))
                .hoverEvent(HoverEvent.showText(hoverText));
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

    private String formatKD(Player viewer, int kills, int deaths) {
        return FormatUtil.formatKD(lang, viewer, kills, deaths);
    }
}