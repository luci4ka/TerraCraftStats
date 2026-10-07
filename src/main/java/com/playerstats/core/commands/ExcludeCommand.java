package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExcludeCommand {

    private final Main plugin;
    private final LanguageManager lang;

    private final Set<UUID> excludedPlayers = ConcurrentHashMap.newKeySet();

    public ExcludeCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstats.exclude")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
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
                    sender.sendMessage(getComponent(sender, "exclude.help"));
                    return;
                }
                executeRemove(sender, args[1]);
            }
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage(getComponent(sender, "exclude.help"));
                    return;
                }
                executeAdd(sender, args[1]);
            }
            default -> executeAdd(sender, args[0]);
        }
    }

    // ==================== ADD ====================

    private void executeAdd(CommandSender sender, String playerName) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    sendSyncMessage(sender, getComponent(sender,
                            "exclude.never-played", "player", playerName));
                    return;
                }

                UUID uuid = target.getUniqueId();

                if (excludedPlayers.contains(uuid)) {
                    sendSyncMessage(sender, getComponent(sender,
                            "exclude.already-excluded", "player", playerName));
                    return;
                }

                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                String name = target.getName() != null ? target.getName() : playerName;
                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                statsDAO.setStatString(playerId, plugin.getServerId(), "excluded", "true");
                excludedPlayers.add(uuid);

                plugin.getThreadManager().runSync(() -> {
                    Component line = getComponent(sender, "exclude.added",
                            "player", name);

                    // Кнопка «Отменить» — на языке отправителя
                    Component undoButton = getComponentFromString(sender,
                            getRaw(sender, "exclude.separator-bracket-left") +
                                    "<yellow>" + getRaw(sender, "exclude.button-undo") +
                                    getRaw(sender, "exclude.separator-bracket-right"))
                            .clickEvent(ClickEvent.runCommand("/exclude remove " + name))
                            .hoverEvent(HoverEvent.showText(
                                    getComponent(sender, "exclude.button-undo-hover",
                                            "player", name)));

                    // Кнопка «Статистика» — на языке отправителя
                    Component statsButton = getComponentFromString(sender,
                            getRaw(sender, "exclude.separator-bracket-left-short") +
                                    "<aqua>" + getRaw(sender, "exclude.button-stats") +
                                    getRaw(sender, "exclude.separator-bracket-right"))
                            .clickEvent(ClickEvent.runCommand("/stats " + name))
                            .hoverEvent(HoverEvent.showText(
                                    getComponent(sender, "exclude.button-stats-hover",
                                            "player", name)));

                    sender.sendMessage(line);
                    sender.sendMessage(undoButton.append(statsButton));
                });

                plugin.getLogger().info(sender.getName()
                        + " исключил игрока " + name + " из статистики.");

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при исключении игрока "
                        + playerName + ": " + e.getMessage());
                e.printStackTrace();
                sendSyncMessage(sender, getComponent(sender, "error"));
            }
        });
    }

    // ==================== REMOVE ====================

    private void executeRemove(CommandSender sender, String playerName) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                UUID uuid = target.getUniqueId();

                if (!excludedPlayers.contains(uuid)) {
                    sendSyncMessage(sender, getComponent(sender,
                            "exclude.not-excluded", "player", playerName));
                    return;
                }

                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();

                String name = target.getName() != null ? target.getName() : playerName;
                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                statsDAO.setStatString(playerId, plugin.getServerId(), "excluded", "false");
                excludedPlayers.remove(uuid);

                plugin.getThreadManager().runSync(() -> {
                    Component line = getComponent(sender, "exclude.removed",
                            "player", name);

                    // Кнопка «Статистика» — на языке отправителя
                    Component statsButton = getComponentFromString(sender,
                            getRaw(sender, "exclude.separator-bracket-left") +
                                    "<aqua>" + getRaw(sender, "exclude.button-stats") +
                                    getRaw(sender, "exclude.separator-bracket-right"))
                            .clickEvent(ClickEvent.runCommand("/stats " + name))
                            .hoverEvent(HoverEvent.showText(
                                    getComponent(sender, "exclude.button-stats-hover",
                                            "player", name)));

                    sender.sendMessage(line);
                    sender.sendMessage(statsButton);
                });

                plugin.getLogger().info(sender.getName()
                        + " вернул игрока " + name + " в статистику.");

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при возвращении игрока "
                        + playerName + ": " + e.getMessage());
                e.printStackTrace();
                sendSyncMessage(sender, getComponent(sender, "error"));
            }
        });
    }

    // ==================== LIST ====================

    private void executeList(CommandSender sender) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                List<String> excludedNames = loadExcludedNamesFromDb();

                plugin.getThreadManager().runSync(() -> {
                    if (excludedNames.isEmpty()) {
                        sender.sendMessage(getComponent(sender, "exclude.list-empty"));
                        return;
                    }

                    sender.sendMessage(getComponent(sender,
                            "exclude.list-header", "count", excludedNames.size()));

                    for (String name : excludedNames) {
                        Component line = getComponentFromString(sender,
                                getRaw(sender, "exclude.bullet") +
                                        "<red>" + name +
                                        "  " +
                                        getRaw(sender, "exclude.separator-bracket-left") +
                                        "<yellow>" + getRaw(sender, "exclude.button-undo-short") +
                                        getRaw(sender, "exclude.separator-bracket-right") +
                                        getRaw(sender, "exclude.separator-bracket-left-short") +
                                        "<aqua>" + getRaw(sender, "exclude.button-stats-short") +
                                        getRaw(sender, "exclude.separator-bracket-right"))
                                .clickEvent(ClickEvent.runCommand("/exclude remove " + name))
                                .hoverEvent(HoverEvent.showText(
                                        getComponent(sender, "exclude.button-undo-short-hover",
                                                "player", name)));

                        // Кнопка «Отменить» и «Статистика» — раздельно
                        Component undoPart = getComponentFromString(sender,
                                getRaw(sender, "exclude.separator-bracket-left") +
                                        "<yellow>" + getRaw(sender, "exclude.button-undo-short") +
                                        getRaw(sender, "exclude.separator-bracket-right"))
                                .clickEvent(ClickEvent.runCommand("/exclude remove " + name))
                                .hoverEvent(HoverEvent.showText(
                                        getComponent(sender, "exclude.button-undo-short-hover",
                                                "player", name)));

                        Component statsPart = getComponentFromString(sender,
                                getRaw(sender, "exclude.separator-bracket-left-short") +
                                        "<aqua>" + getRaw(sender, "exclude.button-stats-short") +
                                        getRaw(sender, "exclude.separator-bracket-right"))
                                .clickEvent(ClickEvent.runCommand("/stats " + name))
                                .hoverEvent(HoverEvent.showText(
                                        getComponent(sender, "exclude.button-stats-short-hover",
                                                "player", name)));

                        Component finalLine = getComponentFromString(sender,
                                getRaw(sender, "exclude.bullet") + "<red>" + name)
                                .append(undoPart)
                                .append(statsPart);

                        sender.sendMessage(finalLine);
                    }

                    sender.sendMessage(getComponent(sender, "exclude.list-footer"));
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка при получении списка исключённых: "
                        + e.getMessage());
                e.printStackTrace();
                sendSyncMessage(sender, getComponent(sender, "error"));
            }
        });
    }

    // ==================== LOAD ====================

    public void loadExcludedPlayers() {
        try {
            List<String> names = loadExcludedNamesFromDb();
            plugin.getLogger().info("Загружено " + excludedPlayers.size()
                    + " исключённых игроков (" + names.size() + " записей).");
        } catch (SQLException e) {
            plugin.getLogger().warning("Не удалось загрузить список исключённых игроков: "
                    + e.getMessage());
        }
    }

    private List<String> loadExcludedNamesFromDb() throws SQLException {
        List<String> names = new ArrayList<>();

        String sql = """
            SELECT p.name, p.uuid
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.value_string = 'true'
            AND ps.stat_type_id = (SELECT id FROM stat_types WHERE name = 'excluded')
            AND ps.server_id = ?
            """;

        try (Connection conn = plugin.getConnection().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, plugin.getServerId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    names.add(name);
                    excludedPlayers.add(uuid);
                }
            }
        }

        return names;
    }

    // ==================== PUBLIC API ====================

    public boolean isExcluded(UUID uuid) {
        return excludedPlayers.contains(uuid);
    }

    public boolean isExcluded(UUID uuid, int playerId) throws SQLException {
        if (excludedPlayers.contains(uuid)) {
            return true;
        }

        PlayerStatsDAO statsDAO = plugin.getStatsDAO();
        String excluded = statsDAO.getStatString(playerId, plugin.getServerId(), "excluded");
        boolean isExcluded = "true".equals(excluded);

        if (isExcluded) {
            excludedPlayers.add(uuid);
        }

        return isExcluded;
    }

    public void clearCache() {
        excludedPlayers.clear();
    }

    // ==================== UTIL ====================

    private void showHelp(CommandSender sender) {
        sender.sendMessage(getComponent(sender, "exclude.help"));
    }

    private void sendSyncMessage(CommandSender sender, Component message) {
        plugin.getThreadManager().runSync(() -> sender.sendMessage(message));
    }

    // ==================== LANG HELPERS ====================

    private Component getComponent(CommandSender sender, String key, Object... replacements) {
        if (sender instanceof Player player) {
            return lang.getComponent(player, key, replacements);
        }
        return lang.getComponent(key, replacements);
    }

    private String getRaw(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            return lang.getRaw(player, key);
        }
        return lang.getRaw(key);
    }

    private Component getComponentFromString(CommandSender sender, String message, Object... replacements) {
        return lang.getComponentFromString(message, replacements);
    }
}