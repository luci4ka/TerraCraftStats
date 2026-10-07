package com.playerstats.hooks;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.services.TopCache;
import com.playerstats.core.utils.Sparkline;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

        private final ConcurrentHashMap<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
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
            data.put("distance_walked", String.valueOf(statsDAO.getStat(playerId, serverId, "distance_walked")));
            data.put("damage_dealt", String.valueOf(statsDAO.getStat(playerId, serverId, "damage_dealt")));
            data.put("damage_taken", String.valueOf(statsDAO.getStat(playerId, serverId, "damage_taken")));
            data.put("items_crafted", String.valueOf(statsDAO.getStat(playerId, serverId, "items_crafted")));
            data.put("food_consumed", String.valueOf(statsDAO.getStat(playerId, serverId, "food_consumed")));
            data.put("arrows_shot", String.valueOf(statsDAO.getStat(playerId, serverId, "arrows_shot")));
            data.put("fish_caught", String.valueOf(statsDAO.getStat(playerId, serverId, "fish_caught")));
            data.put("entities_tamed", String.valueOf(statsDAO.getStat(playerId, serverId, "entities_tamed")));
            data.put("entities_bred", String.valueOf(statsDAO.getStat(playerId, serverId, "entities_bred")));
            data.put("teleports", String.valueOf(statsDAO.getStat(playerId, serverId, "teleports")));
            data.put("portal_uses", String.valueOf(statsDAO.getStat(playerId, serverId, "portal_uses")));
            data.put("logins", String.valueOf(statsDAO.getStat(playerId, serverId, "logins")));
            data.put("killstreak", String.valueOf(statsDAO.getStat(playerId, serverId, "killstreak")));
            data.put("best_killstreak", String.valueOf(statsDAO.getStat(playerId, serverId, "best_killstreak")));
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
         * Обновляет динамический кэш (mob_killed_*, block_*, pvp_kill_*).
         */
        private void refreshDynamicCache(Player player) throws SQLException {
            PlayerStatsDAO statsDAO = plugin.getStatsDAO();
            int serverId = plugin.getServerId();
            UUID uuid = player.getUniqueId();

            int playerId = plugin.getPlayerDAO().getOrCreatePlayerId(uuid, player.getName());

            Map<String, String> data = new HashMap<>();

            for (String prefix : new String[]{"mob_killed_", "block_placed_", "block_broken_", "pvp_kill_"}) {
                Map<String, Integer> stats = statsDAO.getStatsByPrefix(playerId, serverId, prefix);
                for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                    data.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            // Суммы PvP
            Map<String, Integer> pvpStats = statsDAO.getStatsByPrefix(playerId, serverId, "pvp_kill_");
            data.put("pvp_total", String.valueOf(
                    pvpStats.values().stream().mapToInt(Integer::intValue).sum()));
            data.put("pvp_unique", String.valueOf(pvpStats.size()));

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

            // ==================== TOP-CHIPS ====================
            if (key.startsWith("top_")) {
                return handleTopPlaceholder(uuid, key);
            }

            if (key.startsWith("sparkline_")) {
                return handleSparklinePlaceholder(player, uuid, key);
            }

            // ==================== ДИНАМИЧЕСКИЕ ====================
            if (key.startsWith("mob_killed_")
                    || key.startsWith("block_placed_")
                    || key.startsWith("block_broken_")
                    || key.startsWith("pvp_kill_")
                    || key.equals("pvp_total")
                    || key.equals("pvp_unique")) {
                return handleDynamicPlaceholder(player, uuid, key);
            }

            // ==================== СТАНДАРТНЫЕ ====================
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
         * Обработка top_*-плейсхолдеров.
         * Значки берутся из config.yml (top-chips.chips.*).
         */
        private String handleTopPlaceholder(UUID uuid, String key) {
            TopCache topCache = plugin.getTopCache();
            if (topCache == null) return "";
            if (!plugin.getConfigHandler().isTopChipsEnabled()) return "";

            return switch (key) {
                case "top_kills" -> topCache.isTop1(uuid, "kills")
                        ? plugin.getConfigHandler().getTopChip("kills") : "";
                case "top_mobs" -> topCache.isTop1(uuid, "mobs_killed")
                        ? plugin.getConfigHandler().getTopChip("mobs") : "";
                case "top_blocks" -> topCache.isTop1(uuid, "blocks_placed")
                        ? plugin.getConfigHandler().getTopChip("blocks") : "";
                case "top_playtime" -> topCache.isTop1(uuid, "play_time")
                        ? plugin.getConfigHandler().getTopChip("playtime") : "";
                case "top_balance" -> topCache.isTop1(uuid, "balance")
                        ? plugin.getConfigHandler().getTopChip("balance") : "";

                case "top_chips" -> buildChips(uuid, topCache, true);
                case "top_chips_short" -> buildChips(uuid, topCache, false);
                case "top_count" -> String.valueOf(countChips(uuid, topCache));

                default -> "";
            };
        }

        /**
         * Собрать все значки игрока в одну строку.
         *
         * @param withSpace добавлять ли пробел в конце
         */
        private String buildChips(UUID uuid, TopCache topCache, boolean withSpace) {
            StringBuilder sb = new StringBuilder();
            List<String> order = plugin.getConfigHandler().getTopChipsOrder();

            for (String category : order) {
                String statName = mapCategoryToStat(category);
                if (statName == null) continue;
                if (topCache.isTop1(uuid, statName)) {
                    sb.append(plugin.getConfigHandler().getTopChip(category));
                }
            }

            if (withSpace && sb.length() > 0) {
                sb.append(" ");
            }
            return sb.toString();
        }

        /**
         * Посчитать количество значков у игрока.
         */
        private int countChips(UUID uuid, TopCache topCache) {
            int count = 0;
            for (String category : plugin.getConfigHandler().getTopChipsOrder()) {
                String statName = mapCategoryToStat(category);
                if (statName != null && topCache.isTop1(uuid, statName)) {
                    count++;
                }
            }
            return count;
        }

        private String handleSparklinePlaceholder(Player player, UUID uuid, String key) {
            String statName = key.substring("sparkline_".length());
            try {
                int playerId = plugin.getPlayerDAO().getOrCreatePlayerId(uuid, player.getName());
                int statTypeId = plugin.getStatTypeDAO().createOrGetStatType(statName);
                List<Integer> history = plugin.getStatHistoryDAO().getHistory(
                        playerId, statTypeId, plugin.getServerId(), 7);
                return Sparkline.generatePretty(history);
            } catch (SQLException e) {
                return "—";
            }
        }

        /**
         * Маппинг категории (kills, mobs, blocks, playtime, balance) → statName в БД.
         */
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

        /**
         * Обработка динамических плейсхолдеров (mob_killed_*, block_*, pvp_kill_*).
         */
        private String handleDynamicPlaceholder(Player player, UUID uuid, String key) {
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