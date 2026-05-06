package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
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
    private final PlayerGroupService groupService;

    public ShareCommand(Main plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.groupService = groupService;
    }

    /**
     * Поделиться статистикой в общий чат.
     */
    public void execute(Player player) {
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
                double balance = statsDAO.getStatDouble(playerId, serverId, "balance");
                String group = groupService.getPlayerGroup(player, playerId);

                Map<String, Integer> totalStats = statsDAO.getTotalStatsForPlayer(playerId);
                int totalPlayTime = totalStats.getOrDefault("play_time", 0);
                int totalMobs = totalStats.getOrDefault("mobs_killed", 0);

                plugin.getThreadManager().runSync(() -> {
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
                plugin.getThreadManager().runSync(() ->
                        player.sendMessage(Component.text("Произошла ошибка при получении статистики.", NamedTextColor.RED)));
            }
        });
    }

    /**
     * Отправить свою статистику конкретному игроку в личные сообщения.
     */
    public void execute(Player sender, Player target) {
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return;
        }

        if (target.equals(sender)) {
            sender.sendMessage(Component.text("Нельзя поделиться статистикой с самим собой. Используйте /stats.", NamedTextColor.RED));
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
                    Component message = buildPrivateShareMessage(
                            sender, group, playTimeSec, totalPlayTime,
                            mobsKilled, bossesKilled, kills, deaths, balance
                    );

                    sender.sendMessage(Component.text("Вы поделились статистикой с ", NamedTextColor.GREEN)
                            .append(Component.text(target.getName(), NamedTextColor.YELLOW)));

                    target.sendMessage(message);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка в ShareCommand: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(Component.text("Произошла ошибка.", NamedTextColor.RED)));
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
        String serverName = config.getServerDisplayName(config.getServerName());

        Component header = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY)
                .append(Component.text("📊 ", NamedTextColor.GOLD))
                .append(Component.text(player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" поделился статистикой!\n", NamedTextColor.GOLD))
                .append(Component.text("Сервер: ", NamedTextColor.GRAY))
                .append(Component.text(serverName, NamedTextColor.AQUA))
                .append(Component.text(" | Группа: ", NamedTextColor.GRAY))
                .append(Component.text(group, NamedTextColor.LIGHT_PURPLE));

        Component stats = Component.text()
                .append(Component.text("\n⏳ Время: ", NamedTextColor.GREEN))
                .append(Component.text(formatTime(playTime), NamedTextColor.WHITE))
                .append(Component.text(" (всего: " + formatTime(totalPlayTime) + ")", NamedTextColor.GRAY))

                .append(Component.text("\n☠ PvE: ", NamedTextColor.GREEN))
                .append(Component.text("Мобы " + mobs + " | Боссы " + bosses, NamedTextColor.WHITE))
                .append(Component.text(" (всего мобов: " + totalMobs + ")", NamedTextColor.GRAY))

                .append(Component.text("\n⛏ Блоки: ", NamedTextColor.GREEN))
                .append(Component.text("Поставлено " + blocks, NamedTextColor.WHITE))

                .append(Component.text("\n⚔ PvP: ", NamedTextColor.GREEN))
                .append(Component.text("Убийств " + kills + " | Смертей " + deaths, NamedTextColor.WHITE))
                .append(Component.text(" | K/D: " + formatKD(kills, deaths), NamedTextColor.GRAY))

                .append(Component.text("\n💰 Баланс: ", NamedTextColor.GREEN))
                .append(Component.text(String.format("%.0f₽", balance), NamedTextColor.GOLD))

                .append(Component.text("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
                .build();

        return header.append(stats)
                .hoverEvent(HoverEvent.showText(Component.text("Нажмите /stats, чтобы посмотреть свою статистику")))
                .clickEvent(ClickEvent.suggestCommand("/stats"));
    }

    /**
     * Построить сообщение для отправки в личные сообщения.
     */
    private Component buildPrivateShareMessage(Player sender, String group,
                                               int playTime, int totalPlayTime,
                                               int mobs, int bosses,
                                               int kills, int deaths, double balance) {
        String serverName = config.getServerDisplayName(config.getServerName());

        return Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY)
                .append(Component.text("📊 ", NamedTextColor.GOLD))
                .append(Component.text(sender.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" поделился с вами статистикой:\n", NamedTextColor.GOLD))
                .append(Component.text("Сервер: ", NamedTextColor.GRAY))
                .append(Component.text(serverName, NamedTextColor.AQUA))
                .append(Component.text(" | Группа: ", NamedTextColor.GRAY))
                .append(Component.text(group, NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("\n⏳ Время: " + formatTime(playTime) + " (всего: " + formatTime(totalPlayTime) + ")", NamedTextColor.WHITE))
                .append(Component.text("\n☠ PvE: Мобы " + mobs + " | Боссы " + bosses, NamedTextColor.WHITE))
                .append(Component.text("\n⚔ PvP: Убийств " + kills + " | Смертей " + deaths + " | K/D: " + formatKD(kills, deaths), NamedTextColor.WHITE))
                .append(Component.text("\n💰 Баланс: ", NamedTextColor.WHITE))
                .append(Component.text(String.format("%.0f₽", balance), NamedTextColor.GOLD))
                .append(Component.text("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("Статистика игрока " + sender.getName())));
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "ч " + minutes + "м";
        return minutes + "м";
    }

    private String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
    }
}