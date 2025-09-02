package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class TopCommand implements CommandExecutor {

    private final Main plugin;
    private final ConfigHandler config;

    public TopCommand(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду может использовать только игрок.");
            return true;
        }

        PlayerStatsDAO statsDAO = plugin.getStatsDAO();
        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                List<Map.Entry<String, Number>> topTime = statsDAO.getTopPlayers("play_time", 5);
                List<Map.Entry<String, Number>> topMobs = statsDAO.getTopPlayers("mobs_killed", 5);
                List<Map.Entry<String, Number>> topBlocks = statsDAO.getTopPlayers("blocks_placed", 5);
                List<Map.Entry<String, Number>> topBalance = statsDAO.getTopPlayers("balance", 5);

                plugin.getThreadManager().runSync(() -> {
                    Component separator = Component.text("§8§m-----------------------------");

                    player.sendMessage(separator);
                    sendTopTable(player, "⏳ Время", topTime, config.getStatsClickCommand("time"), true);
                    sendTopTable(player, "⚔️ PvE", topMobs, config.getStatsClickCommand("pve"), false);
                    sendTopTable(player, "🧱 Блоки", topBlocks, config.getStatsClickCommand("blocks"), false);
                    sendTopTable(player, "💰 Баланс", topBalance, config.getStatsClickCommand("balance"), false);
                    player.sendMessage(separator);
                });
            } catch (SQLException e) {
                e.printStackTrace();
                plugin.getThreadManager().runSync(() ->
                        player.sendMessage("§cПроизошла ошибка при получении топа."));
            }
        });

        return true;
    }

    /**
     * Отправляет топ в виде мини-таблицы (2 строки по 3 игрока)
     * @param player игрок
     * @param title название категории
     * @param topList топ-5 игроков
     * @param clickCommand команда при клике
     * @param isTime true, если это время, чтобы форматировать
     */
    private void sendTopTable(Player player, String title, List<Map.Entry<String, Number>> topList, String clickCommand, boolean isTime) {
        Component header = Component.text(title + ": ").color(NamedTextColor.YELLOW);
        player.sendMessage(header);

        // Две строки, максимум 3 игрока на строку
        for (int row = 0; row < 2; row++) {
            Component line = Component.empty();
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                if (idx >= topList.size()) break;

                Map.Entry<String, Number> entry = topList.get(idx);
                String name = entry.getKey();
                String value;

                if (isTime) {
                    int seconds = entry.getValue().intValue();
                    int hours = seconds / 3600;
                    int minutes = (seconds % 3600) / 60;
                    value = hours + "ч " + minutes + "м";
                } else {
                    value = entry.getValue().toString();
                }

                NamedTextColor color;
                switch (idx) {
                    case 0 -> color = NamedTextColor.GOLD;
                    case 1 -> color = NamedTextColor.GRAY;
                    case 2 -> color = NamedTextColor.DARK_RED;
                    default -> color = NamedTextColor.WHITE;
                }

                Component playerComp = Component.text((idx + 1) + "." + name + "|" + value)
                        .color(color)
                        .hoverEvent(HoverEvent.showText(Component.text("Игрок: " + name + "\nЗначение: " + value)))
                        .clickEvent(ClickEvent.runCommand(clickCommand));

                line = line.append(playerComp);

                if (col < 2) line = line.append(Component.text("   ")); // пробел между игроками
            }
            player.sendMessage(line);
        }
    }
}
