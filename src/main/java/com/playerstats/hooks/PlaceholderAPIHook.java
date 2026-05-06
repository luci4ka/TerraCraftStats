package com.playerstats.hooks;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.logging.Level;

public class PlaceholderAPIHook implements Hook {

    private final Main plugin;
    private PlayerStatsExpansion expansion;
    private boolean enabled = false;

    public PlaceholderAPIHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.getLogger().info("PlaceholderAPI не обнаружен — интеграция отключена.");
            return;
        }

        try {
            this.expansion = new PlayerStatsExpansion(plugin);
            this.expansion.register();
            this.enabled = true;
            plugin.getLogger().info("PlaceholderAPI плейсхолдеры зарегистрированы.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось зарегистрировать PlaceholderAPI.", e);
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        if (expansion != null) {
            expansion.unregister();
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled && expansion != null;
    }

    @Override
    public String getPluginName() {
        return "PlaceholderAPI";
    }

    /**
     * Внутренний класс для плейсхолдеров.
     */
    private static class PlayerStatsExpansion extends PlaceholderExpansion {

        private final Main plugin;

        public PlayerStatsExpansion(Main plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "playerstats";
        }

        @Override
        public @NotNull String getAuthor() {
            return String.join(", ", plugin.getDescription().getAuthors());
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String identifier) {
            if (player == null) return "";

            PlayerStatsDAO statsDAO = plugin.getStatsDAO();
            int serverId = plugin.getServerId();

            try {
                int playerId = plugin.getPlayerDAO().getOrCreatePlayerId(
                        player.getUniqueId(), player.getName());

                return switch (identifier.toLowerCase()) {
                    case "play_time" -> formatTime(statsDAO.getStat(playerId, serverId, "play_time"));
                    case "play_time_raw" -> String.valueOf(statsDAO.getStat(playerId, serverId, "play_time"));
                    case "blocks_placed" -> String.valueOf(statsDAO.getStat(playerId, serverId, "blocks_placed"));
                    case "blocks_broken" -> String.valueOf(statsDAO.getStat(playerId, serverId, "blocks_broken"));
                    case "mobs_killed" -> String.valueOf(statsDAO.getStat(playerId, serverId, "mobs_killed"));
                    case "bosses_killed" -> String.valueOf(statsDAO.getStat(playerId, serverId, "bosses_killed"));
                    case "kills" -> String.valueOf(statsDAO.getStat(playerId, serverId, "kills"));
                    case "deaths" -> String.valueOf(statsDAO.getStat(playerId, serverId, "deaths"));
                    case "kdr" -> formatKDR(
                            statsDAO.getStat(playerId, serverId, "kills"),
                            statsDAO.getStat(playerId, serverId, "deaths"));
                    case "balance" -> String.format("%.2f", statsDAO.getStatDouble(playerId, serverId, "balance"));
                    case "group" -> statsDAO.getStatString(playerId, serverId, "group");
                    default -> null;
                };
            } catch (SQLException e) {
                plugin.getLogger().warning("PlaceholderAPI: ошибка для " + identifier + ": " + e.getMessage());
                return "N/A";
            }
        }

        private String formatTime(int seconds) {
            int hours = seconds / 3600;
            int minutes = (seconds % 3600) / 60;
            if (hours > 0) return hours + "ч " + minutes + "м";
            return minutes + "м";
        }

        private String formatKDR(int kills, int deaths) {
            if (deaths == 0) return kills > 0 ? "∞" : "0.00";
            return String.format("%.2f", (double) kills / deaths);
        }
    }
}