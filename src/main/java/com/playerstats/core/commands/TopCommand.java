package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.dao.LeaderboardDAO;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.List;

public class TopCommand extends BukkitCommand {

    private final Main plugin;
    private final ConfigHandler config;

    public TopCommand(Main plugin) {
        super("top", "Показ топ игроков", "/top", List.of());
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду может использовать только игрок.");
            return true;
        }

        LeaderboardDAO leaderboardDAO = new LeaderboardDAO(
                plugin.getConnection(),
                plugin.getStatTypeDAO()
        );
        int serverId = plugin.getServerId();

        plugin.getThreadManager().runAsync(() -> {
            try {
                // Используем LeaderboardDAO с учётом serverId
                List<LeaderboardEntry> topTime = leaderboardDAO.getTop("play_time", serverId, 5);
                List<LeaderboardEntry> topMobs = leaderboardDAO.getTop("mobs_killed", serverId, 5);
                List<LeaderboardEntry> topBlocks = leaderboardDAO.getTop("blocks_placed", serverId, 5);
                List<LeaderboardEntry> topBalance = leaderboardDAO.getTop("balance", serverId, 5);

                plugin.getThreadManager().runSync(() -> {
                    Component header = Component.text("━━━━━━━━━━ §6Топы сервера§r ━━━━━━━━━━")
                            .color(NamedTextColor.DARK_GRAY);
                    Component footer = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            .color(NamedTextColor.DARK_GRAY);

                    player.sendMessage(header);
                    sendTopCategory(player, "⏳ Время игры", topTime,
                            config.getStatsClickCommand("time"), StatFormat.TIME);
                    sendTopCategory(player, "☠ Убито мобов", topMobs,
                            config.getStatsClickCommand("pve"), StatFormat.INTEGER);
                    sendTopCategory(player, "⛏ Поставлено блоков", topBlocks,
                            config.getStatsClickCommand("blocks"), StatFormat.INTEGER);
                    sendTopCategory(player, "💰 Баланс", topBalance,
                            config.getStatsClickCommand("balance"), StatFormat.BALANCE);
                    player.sendMessage(footer);

                    // Кнопка "Обновить"
                    Component refresh = Component.text("[Обновить]")
                            .color(NamedTextColor.GRAY)
                            .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы обновить топ")))
                            .clickEvent(ClickEvent.runCommand("/top"));
                    player.sendMessage(refresh);
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при загрузке топов: " + e.getMessage());
                e.printStackTrace();
                plugin.getThreadManager().runSync(() ->
                        player.sendMessage(Component.text("Произошла ошибка при загрузке топов.", NamedTextColor.RED)));
            }
        });

        return true;
    }

    /**
     * Формат отображения значения.
     */
    private enum StatFormat {
        TIME,       // "10ч 30м"
        INTEGER,    // "1,234"
        BALANCE     // "1,234.00$"
    }

    /**
     * Отправка одной категории топа.
     */
    private void sendTopCategory(Player player, String title,
                                 List<LeaderboardEntry> entries,
                                 String clickCommand,
                                 StatFormat format) {
        Component categoryHeader = Component.text("▸ " + title)
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD);
        player.sendMessage(categoryHeader);

        if (entries.isEmpty()) {
            player.sendMessage(Component.text("   Нет данных", NamedTextColor.GRAY));
            return;
        }

        // Строка с топ-3
        Component topLine = Component.empty();
        for (int i = 0; i < Math.min(3, entries.size()); i++) {
            LeaderboardEntry entry = entries.get(i);
            topLine = topLine.append(formatPlayerEntry(i + 1, entry, clickCommand, format));
            if (i < 2 && i < entries.size() - 1) {
                topLine = topLine.append(Component.text("  "));
            }
        }
        player.sendMessage(topLine);

        // Строка с 4-5 местами (если есть)
        if (entries.size() > 3) {
            Component bottomLine = Component.empty();
            for (int i = 3; i < Math.min(5, entries.size()); i++) {
                LeaderboardEntry entry = entries.get(i);
                bottomLine = bottomLine.append(formatPlayerEntry(i + 1, entry, clickCommand, format));
                if (i < 4 && i < entries.size() - 1) {
                    bottomLine = bottomLine.append(Component.text("  "));
                }
            }
            player.sendMessage(bottomLine);
        }
    }

    /**
     * Форматирование одной записи в топе.
     */
    private Component formatPlayerEntry(int rank, LeaderboardEntry entry,
                                        String clickCommand, StatFormat format) {
        String name = entry.playerName();
        String value = formatValue(entry.value(), format);
        String hoverText = "Игрок: " + name + "\nЗначение: " + value + "\nМесто: #" + rank;

        // Цвет для позиции
        NamedTextColor color = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };

        // Заменяем плейсхолдер %player% в команде
        String finalCommand = clickCommand.replace("%player%", name);

        return Component.text("#" + rank + " " + name + " §8" + value)
                .color(color)
                .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
                .clickEvent(ClickEvent.suggestCommand(finalCommand));
    }

    /**
     * Форматирование значения в зависимости от типа.
     */
    private String formatValue(double value, StatFormat format) {
        return switch (format) {
            case TIME -> formatTime((int) value);
            case INTEGER -> formatInteger((int) value);
            case BALANCE -> formatBalance(value);
        };
    }

    /**
     * Форматирование времени: "10ч 30м" или "45м" или "30с".
     */
    private String formatTime(int totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + "с";
        }
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "ч " + minutes + "м";
        } else {
            return minutes + "м";
        }
    }

    /**
     * Форматирование целого числа с разделителями: "1,234".
     */
    private String formatInteger(int value) {
        return String.format("%,d", value);
    }

    /**
     * Форматирование баланса: "1,234.56$" или "0$".
     */
    private String formatBalance(double value) {
        if (value == (long) value) {
            return String.format("%,d$", (long) value);
        }
        return new DecimalFormat("#,###.##$").format(value);
    }
}