package com.playerstats.hooks;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
            plugin.getLogger().info("PlaceholderAPI плейсхолдеры зарегистрированы (с кэшем).");
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

    public void invalidate(UUID uuid) {
        if (expansion != null) {
            expansion.invalidate(uuid);
        }
    }

    /**
     * Внутренний класс для плейсхолдеров с кэшем.
     */
    private static class PlayerStatsExpansion extends PlaceholderExpansion {

        private final Main plugin;

        /**
         * Кэш: UUID игрока → Map<ключ статистики, значение>.
         */
        private final ConcurrentHashMap<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();

        /**
         * Кэш динамических плейсхолдеров (mob_killed_*) для игроков.
         * Обновляется по запросу, не в фоновом режиме.
         */
        private final ConcurrentHashMap<UUID, Map<String, String>> dynamicCache = new ConcurrentHashMap<>();

        private final ConcurrentHashMap<UUID, Long> cacheTimestamps = new ConcurrentHashMap<>();

        private static final long CACHE_TTL_MS = 5_000L;

        public PlayerStatsExpansion(Main plugin) {
            this.plugin = plugin;
            startCacheUpdater();
        }

        private void startCacheUpdater() {
            plugin.getThreadManager().runAsyncRepeating(() -> {
                if (!isRegistered()) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        refreshCache(player);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Ошибка обновления кэша для " + player.getName(), e);
                    }
                }
            }, 10, 15);
        }

        /**
         * Обновляет базовый кэш (стандартные статистики).
         */
        private void refreshCache(Player player) throws SQLException {
            PlayerStatsDAO statsDAO = plugin.getStatsDAO();
            int serverId = plugin.getServerId();
            UUID uuid = player.getUniqueId();

            int playerId = plugin.getPlayerDAO().getOrCreatePlayerId(uuid, player.getName());

            Map<String, String> data = new HashMap<>();
            data.put("play_time", formatTime(statsDAO.getStat(playerId, serverId, "play_time")));
            data.put("play_time_raw", String.valueOf(statsDAO.getStat(playerId, serverId, "play_time")));
            data.put("blocks_placed", String.valueOf(statsDAO.getStat(playerId, serverId, "blocks_placed")));
            data.put("blocks_broken", String.valueOf(statsDAO.getStat(playerId, serverId, "blocks_broken")));
            data.put("mobs_killed", String.valueOf(statsDAO.getStat(playerId, serverId, "mobs_killed")));
            data.put("bosses_killed", String.valueOf(statsDAO.getStat(playerId, serverId, "bosses_killed")));
            data.put("kills", String.valueOf(statsDAO.getStat(playerId, serverId, "kills")));
            data.put("deaths", String.valueOf(statsDAO.getStat(playerId, serverId, "deaths")));
            data.put("kdr", formatKDR(
                    statsDAO.getStat(playerId, serverId, "kills"),
                    statsDAO.getStat(playerId, serverId, "deaths")));
            data.put("balance", String.format("%.2f",
                    statsDAO.getStatDouble(playerId, serverId, "balance")));

            String group = statsDAO.getStatString(playerId, serverId, "group");
            data.put("group", (group != null && !group.isEmpty()) ? group : "default");

            cache.put(uuid, Collections.unmodifiableMap(data));
            cacheTimestamps.put(uuid, System.currentTimeMillis());
        }

        /**
         * Обновляет динамический кэш (mob_killed_*) для игрока.
         * Вызывается только при запросе динамического плейсхолдера.
         */
        /**
         * Обновляет динамический кэш (mob_killed_*) для игрока.
         * ОДИН SQL-запрос вместо 80.
         */
        private void refreshDynamicCache(Player player) throws SQLException {
            PlayerStatsDAO statsDAO = plugin.getStatsDAO();
            int serverId = plugin.getServerId();
            UUID uuid = player.getUniqueId();

            int playerId = plugin.getPlayerDAO().getOrCreatePlayerId(uuid, player.getName());

            Map<String, String> data = new HashMap<>();

            // Один запрос на каждый префикс
            for (String prefix : new String[]{"mob_killed_", "block_placed_", "block_broken_"}) {
                Map<String, Integer> stats = statsDAO.getStatsByPrefix(playerId, serverId, prefix);
                for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                    data.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            dynamicCache.put(uuid, Collections.unmodifiableMap(data));
        }

        public void invalidate(UUID uuid) {
            cache.remove(uuid);
            dynamicCache.remove(uuid);
            cacheTimestamps.remove(uuid);
        }

        @Override
        public @NotNull String getIdentifier() {
            return "playerstats";
        }

        @Override
        public @NotNull String getAuthor() {
            return String.join(", ", plugin.getPluginMeta().getAuthors());
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String identifier) {
            if (player == null) return "";

            UUID uuid = player.getUniqueId();
            String key = identifier.toLowerCase();

            // ==================== ДИНАМИЧЕСКИЕ ПЛЕЙСХОЛДЕРЫ (mob_killed_*) ====================
            if (key.startsWith("mob_killed_")
                    || key.startsWith("block_placed_")
                    || key.startsWith("block_broken_")) {
                return handleMobPlaceholder(player, uuid, key);
            }

            // ==================== СТАНДАРТНЫЕ ПЛЕЙСХОЛДЕРЫ ====================
            Map<String, String> data = cache.get(uuid);
            Long timestamp = cacheTimestamps.get(uuid);

            if (data == null || timestamp == null
                    || (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS) {
                try {
                    refreshCache(player);
                    data = cache.get(uuid);
                } catch (SQLException e) {
                    plugin.getLogger().warning("PlaceholderAPI: ошибка для "
                            + identifier + ": " + e.getMessage());
                    return "N/A";
                }
            }

            if (data == null) return "N/A";

            String value = data.get(key);
            return value != null ? value : "";
        }

        /**
         * Обработка плейсхолдера mob_killed_<mob>.
         * Читает напрямую из БД (не кэширует постоянно).
         */
        private String handleMobPlaceholder(Player player, UUID uuid, String key) {
            // Проверяем динамический кэш
            Map<String, String> dynamicData = dynamicCache.get(uuid);

            if (dynamicData == null) {
                try {
                    refreshDynamicCache(player);
                    dynamicData = dynamicCache.get(uuid);
                } catch (SQLException e) {
                    plugin.getLogger().warning("PlaceholderAPI: ошибка для "
                            + key + ": " + e.getMessage());
                    return "0";
                }
            }

            if (dynamicData == null) return "0";

            String value = dynamicData.get(key);
            return value != null ? value : "0";
        }

        private String formatTime(int seconds) {
            if (seconds < 0) seconds = 0;
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