package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ExcludeCommand {

    private final Main plugin;

    // Кэш исключённых игроков (UUID -> true)
    private final Set<UUID> excludedPlayers = new HashSet<>();

    public ExcludeCommand(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Выполнить команду /exclude.
     *
     * /exclude <игрок>         — исключить игрока из статистики
     * /exclude <игрок> remove  — вернуть игрока в статистику
     * /exclude list            — показать список исключённых
     */
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstats.exclude")) {
            sendMessage(sender, Component.text("У вас нет прав для этой команды.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> executeList(sender);
            case "remove" -> {
                if (args.length < 2) {
                    sendMessage(sender, Component.text("Использование: /exclude remove <игрок>", NamedTextColor.RED));
                    return;
                }
                executeRemove(sender, args[1]);
            }
            case "add" -> {
                if (args.length < 2) {
                    sendMessage(sender, Component.text("Использование: /exclude add <игрок>", NamedTextColor.RED));
                    return;
                }
                executeAdd(sender, args[1]);
            }
            default -> {
                // По умолчанию — исключаем игрока (для обратной совместимости)
                executeAdd(sender, args[0]);
            }
        }
    }

    /**
     * Исключить игрока из статистики.
     */
    private void executeAdd(CommandSender sender, String playerName) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    sendSyncMessage(sender, Component.text("Игрок '" + playerName + "' никогда не играл на сервере.", NamedTextColor.RED));
                    return;
                }

                UUID uuid = target.getUniqueId();

                // Проверяем, не исключён ли уже
                if (excludedPlayers.contains(uuid)) {
                    sendSyncMessage(sender, Component.text("Игрок '" + playerName + "' уже исключён из статистики.", NamedTextColor.YELLOW));
                    return;
                }

                // Помечаем как исключённого в БД
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                int playerId = playerDAO.getOrCreatePlayerId(uuid, target.getName() != null ? target.getName() : playerName);

                // Устанавливаем флаг excluded в статистике
                statsDAO.setStatString(playerId, plugin.getServerId(), "excluded", "true");

                // Добавляем в кэш
                excludedPlayers.add(uuid);

                sendSyncMessage(sender, Component.text()
                        .append(Component.text("✅ Игрок ", NamedTextColor.GREEN))
                        .append(Component.text(playerName, NamedTextColor.YELLOW))
                        .append(Component.text(" исключён из статистики.", NamedTextColor.GREEN))
                        .build());

                // Логируем
                plugin.getLogger().info(sender.getName() + " исключил игрока " + playerName + " из статистики.");

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при исключении игрока " + playerName + ": " + e.getMessage());
                e.printStackTrace();
                sendSyncMessage(sender, Component.text("Произошла ошибка при исключении игрока.", NamedTextColor.RED));
            }
        });
    }

    /**
     * Вернуть игрока в статистику.
     */
    private void executeRemove(CommandSender sender, String playerName) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                UUID uuid = target.getUniqueId();

                // Проверяем, исключён ли
                if (!excludedPlayers.contains(uuid)) {
                    sendSyncMessage(sender, Component.text("Игрок '" + playerName + "' не исключён из статистики.", NamedTextColor.YELLOW));
                    return;
                }

                // Убираем флаг excluded
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                int playerId = playerDAO.getOrCreatePlayerId(uuid, target.getName() != null ? target.getName() : playerName);
                statsDAO.setStatString(playerId, plugin.getServerId(), "excluded", "false");

                // Удаляем из кэша
                excludedPlayers.remove(uuid);

                sendSyncMessage(sender, Component.text()
                        .append(Component.text("✅ Игрок ", NamedTextColor.GREEN))
                        .append(Component.text(playerName, NamedTextColor.YELLOW))
                        .append(Component.text(" возвращён в статистику.", NamedTextColor.GREEN))
                        .build());

                plugin.getLogger().info(sender.getName() + " вернул игрока " + playerName + " в статистику.");

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при возвращении игрока " + playerName + ": " + e.getMessage());
                e.printStackTrace();
                sendSyncMessage(sender, Component.text("Произошла ошибка.", NamedTextColor.RED));
            }
        });
    }

    /**
     * Показать список исключённых игроков.
     */
    private void executeList(CommandSender sender) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();
                PlayerDAO playerDAO = plugin.getPlayerDAO();

                // Ищем всех игроков с excluded = "true"
                // Это нужно делать через прямой SQL-запрос, т.к. в PlayerStatsDAO нет такого метода
                java.sql.Connection conn = plugin.getConnection().getConnection();
                String sql = """
                    SELECT p.name, p.uuid
                    FROM player_stats ps
                    JOIN players p ON ps.player_id = p.id
                    WHERE ps.value_string = 'true'
                    AND ps.stat_type_id = (SELECT id FROM stat_types WHERE name = 'excluded')
                    AND ps.server_id = ?
                    """;

                java.util.List<String> excludedNames = new java.util.ArrayList<>();
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, plugin.getServerId());
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String name = rs.getString("name");
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            excludedNames.add(name);
                            excludedPlayers.add(uuid); // Обновляем кэш
                        }
                    }
                }

                if (excludedNames.isEmpty()) {
                    sendSyncMessage(sender, Component.text("Нет исключённых игроков.", NamedTextColor.YELLOW));
                    return;
                }

                Component header = Component.text()
                        .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY))
                        .append(Component.text("🚫 Исключённые игроки (", NamedTextColor.GOLD))
                        .append(Component.text(String.valueOf(excludedNames.size()), NamedTextColor.YELLOW))
                        .append(Component.text("):\n", NamedTextColor.GOLD))
                        .build();

                sendSyncMessage(sender, header);

                for (String name : excludedNames) {
                    Component playerLine = Component.text()
                            .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(name, NamedTextColor.RED))
                            .build();
                    sendSyncMessage(sender, playerLine);
                }

                Component footer = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);
                sendSyncMessage(sender, footer);

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при получении списка исключённых: " + e.getMessage());
                e.printStackTrace();
                sendSyncMessage(sender, Component.text("Произошла ошибка.", NamedTextColor.RED));
            }
        });
    }

    /**
     * Проверить, исключён ли игрок из статистики.
     */
    public boolean isExcluded(UUID uuid) {
        return excludedPlayers.contains(uuid);
    }

    /**
     * Проверить, исключён ли игрок из статистики (с проверкой в БД при необходимости).
     */
    public boolean isExcluded(UUID uuid, int playerId) throws SQLException {
        // Проверяем кэш
        if (excludedPlayers.contains(uuid)) {
            return true;
        }

        // Проверяем БД
        PlayerStatsDAO statsDAO = plugin.getStatsDAO();
        String excluded = statsDAO.getStatString(playerId, plugin.getServerId(), "excluded");
        boolean isExcluded = "true".equals(excluded);

        if (isExcluded) {
            excludedPlayers.add(uuid);
        }

        return isExcluded;
    }

    /**
     * Загрузить список исключённых игроков из БД при старте плагина.
     */
    public void loadExcludedPlayers() {
        try {
            java.sql.Connection conn = plugin.getConnection().getConnection();
            String sql = """
                SELECT p.uuid
                FROM player_stats ps
                JOIN players p ON ps.player_id = p.id
                WHERE ps.value_string = 'true'
                AND ps.stat_type_id = (SELECT id FROM stat_types WHERE name = 'excluded')
                AND ps.server_id = ?
                """;

            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, plugin.getServerId());
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        excludedPlayers.add(uuid);
                    }
                }
            }

            plugin.getLogger().info("Загружено " + excludedPlayers.size() + " исключённых игроков.");

        } catch (SQLException e) {
            plugin.getLogger().warning("Не удалось загрузить список исключённых игроков: " + e.getMessage());
        }
    }

    /**
     * Очистить кэш исключённых игроков.
     */
    public void clearCache() {
        excludedPlayers.clear();
    }

    private void showHelp(CommandSender sender) {
        sendMessage(sender, Component.text()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY))
                .append(Component.text("🚫 Управление исключениями:\n", NamedTextColor.GOLD))
                .append(Component.text("/exclude <игрок>", NamedTextColor.YELLOW))
                .append(Component.text(" — исключить из статистики\n", NamedTextColor.GRAY))
                .append(Component.text("/exclude remove <игрок>", NamedTextColor.YELLOW))
                .append(Component.text(" — вернуть в статистику\n", NamedTextColor.GRAY))
                .append(Component.text("/exclude list", NamedTextColor.YELLOW))
                .append(Component.text(" — список исключённых\n", NamedTextColor.GRAY))
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
                .build());
    }

    private void sendMessage(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    private void sendSyncMessage(CommandSender sender, Component message) {
        plugin.getThreadManager().runSync(() -> sender.sendMessage(message));
    }
}