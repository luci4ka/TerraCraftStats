package com.playerstats.hooks;

import com.playerstats.Main;
import com.playerstats.core.achievements.Achievement;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.services.TopCache;
import com.playerstats.core.utils.FormatUtil;
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

/**
 * PAPI-экспансия. Префикс: %ps_...%.
 *
 * <p>Сырые значения:
 * <ul>
 *   <li>{@code %ps_kills%} → 1234</li>
 *   <li>{@code %ps_play_time_raw%} → 183600</li>
 *   <li>{@code %ps_balance%} → 1234.50</li>
 * </ul>
 *
 * <p>Форматированные:
 * <ul>
 *   <li>{@code %ps_kills_formatted%} → 1.2K</li>
 *   <li>{@code %ps_play_time%} → 2ч 5м</li>
 *   <li>{@code %ps_play_time_formatted%} → 2д 2ч 5м</li>
 *   <li>{@code %ps_balance_formatted%} → 1,234.50</li>
 * </ul>
 */
public class PlayerStatsExpansion extends PlaceholderExpansion {

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
     * Обновляет базовый кэш (стандартные статистики + formatted).
     */
    private void refreshCache(Player player) throws SQLException {
        PlayerStatsDAO statsDAO = plugin.getStatsDAO();
        int serverId = plugin.getServerId();
        UUID uuid = player.getUniqueId();

        int playerId = plugin.getPlayerDAO().getOrCreatePlayerId(uuid, player.getName());

        Map<String, String> data = new HashMap<>();

        // Сырые значения
        int playTime = statsDAO.getStat(playerId, serverId, "play_time");
        int kills = statsDAO.getStat(playerId, serverId, "kills");
        int deaths = statsDAO.getStat(playerId, serverId, "deaths");
        int blocksPlaced = statsDAO.getStat(playerId, serverId, "blocks_placed");
        int blocksBroken = statsDAO.getStat(playerId, serverId, "blocks_broken");
        int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
        int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");
        int distanceWalked = statsDAO.getStat(playerId, serverId, "distance_walked");
        int damageDealt = statsDAO.getStat(playerId, serverId, "damage_dealt");
        int damageTaken = statsDAO.getStat(playerId, serverId, "damage_taken");
        int itemsCrafted = statsDAO.getStat(playerId, serverId, "items_crafted");
        int foodConsumed = statsDAO.getStat(playerId, serverId, "food_consumed");
        int arrowsShot = statsDAO.getStat(playerId, serverId, "arrows_shot");
        int fishCaught = statsDAO.getStat(playerId, serverId, "fish_caught");
        int entitiesTamed = statsDAO.getStat(playerId, serverId, "entities_tamed");
        int entitiesBred = statsDAO.getStat(playerId, serverId, "entities_bred");
        int teleports = statsDAO.getStat(playerId, serverId, "teleports");
        int portalUses = statsDAO.getStat(playerId, serverId, "portal_uses");
        int logins = statsDAO.getStat(playerId, serverId, "logins");
        int killstreak = statsDAO.getStat(playerId, serverId, "killstreak");
        int bestKillstreak = statsDAO.getStat(playerId, serverId, "best_killstreak");

        double balance = statsDAO.getStatDouble(playerId, serverId, "balance");

        // ===== raw =====
        data.put("play_time_raw", String.valueOf(playTime));
        data.put("kills", String.valueOf(kills));
        data.put("deaths", String.valueOf(deaths));
        data.put("blocks_placed", String.valueOf(blocksPlaced));
        data.put("blocks_broken", String.valueOf(blocksBroken));
        data.put("mobs_killed", String.valueOf(mobsKilled));
        data.put("bosses_killed", String.valueOf(bossesKilled));
        data.put("distance_walked", String.valueOf(distanceWalked));
        data.put("damage_dealt", String.valueOf(damageDealt));
        data.put("damage_taken", String.valueOf(damageTaken));
        data.put("items_crafted", String.valueOf(itemsCrafted));
        data.put("food_consumed", String.valueOf(foodConsumed));
        data.put("arrows_shot", String.valueOf(arrowsShot));
        data.put("fish_caught", String.valueOf(fishCaught));
        data.put("entities_tamed", String.valueOf(entitiesTamed));
        data.put("entities_bred", String.valueOf(entitiesBred));
        data.put("teleports", String.valueOf(teleports));
        data.put("portal_uses", String.valueOf(portalUses));
        data.put("logins", String.valueOf(logins));
        data.put("killstreak", String.valueOf(killstreak));
        data.put("best_killstreak", String.valueOf(bestKillstreak));
        data.put("balance", String.format("%.2f", balance));

        // ===== formatted =====
        data.put("kills_formatted", FormatUtil.formatNumber(kills));
        data.put("deaths_formatted", FormatUtil.formatNumber(deaths));
        data.put("blocks_placed_formatted", FormatUtil.formatNumber(blocksPlaced));
        data.put("blocks_broken_formatted", FormatUtil.formatNumber(blocksBroken));
        data.put("mobs_killed_formatted", FormatUtil.formatNumber(mobsKilled));
        data.put("bosses_killed_formatted", FormatUtil.formatNumber(bossesKilled));
        data.put("distance_walked_formatted", FormatUtil.formatNumber(distanceWalked));
        data.put("damage_dealt_formatted", FormatUtil.formatNumber(damageDealt));
        data.put("damage_taken_formatted", FormatUtil.formatNumber(damageTaken));
        data.put("items_crafted_formatted", FormatUtil.formatNumber(itemsCrafted));
        data.put("food_consumed_formatted", FormatUtil.formatNumber(foodConsumed));
        data.put("arrows_shot_formatted", FormatUtil.formatNumber(arrowsShot));
        data.put("fish_caught_formatted", FormatUtil.formatNumber(fishCaught));
        data.put("entities_tamed_formatted", FormatUtil.formatNumber(entitiesTamed));
        data.put("entities_bred_formatted", FormatUtil.formatNumber(entitiesBred));
        data.put("teleports_formatted", FormatUtil.formatNumber(teleports));
        data.put("portal_uses_formatted", FormatUtil.formatNumber(portalUses));
        data.put("logins_formatted", FormatUtil.formatNumber(logins));
        data.put("killstreak_formatted", FormatUtil.formatNumber(killstreak));
        data.put("best_killstreak_formatted", FormatUtil.formatNumber(bestKillstreak));
        data.put("balance_formatted", FormatUtil.formatBalancePlain(balance));

        // ===== время =====
        data.put("play_time", FormatUtil.formatTimeShort(playTime));
        data.put("play_time_formatted", FormatUtil.formatTimeFull(playTime));

        // ===== KDR =====
        data.put("kdr", FormatUtil.formatKD(kills, deaths));

        // ===== группа =====
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
        return "ps";
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

        // ===== TOP-CHIPS =====
        if (key.startsWith("top_")) {
            return handleTopPlaceholder(uuid, key);
        }

        // ==================== ACHIEVEMENT RESET IN ====================
        if (key.startsWith("achievement_reset_in_")) {
            String achId = key.substring("achievement_reset_in_".length());
            return handleResetIn(player, uuid, achId);
        }

        if (key.startsWith("sparkline_")) {
            return handleSparklinePlaceholder(player, uuid, key);
        }

        // ===== ДИНАМИЧЕСКИЕ =====
        if (key.startsWith("mob_killed_")
                || key.startsWith("block_placed_")
                || key.startsWith("block_broken_")
                || key.startsWith("pvp_kill_")
                || key.equals("pvp_total")
                || key.equals("pvp_unique")) {
            return handleDynamicPlaceholder(player, uuid, key);
        }

        // ===== СТАНДАРТНЫЕ =====
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

    // ==================== TOP-CHIPS ====================

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

    // ==================== SPARKLINE ====================

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

    private String handleResetIn(Player player, UUID uuid, String achievementId) {
        Achievement ac = plugin.getAchievementManager().get(achievementId);
        if (ac == null || !ac.isTemporary()) return "—";

        try {
            int playerId = plugin.getPlayerDAO()
                    .getOrCreatePlayerId(uuid, player.getName());

            long lastReset = plugin.getAchievementDAO().getLastReset(playerId, achievementId);
            long interval = ac.resetIntervalMs();

            if (lastReset == -1) return "—"; // ещё не начал

            long elapsed = System.currentTimeMillis() - lastReset;
            long remaining = interval - elapsed;

            if (remaining <= 0) return "сброшено";

            return formatDuration(remaining);

        } catch (SQLException e) {
            return "—";
        }
    }

    /** 12345678 мс → "3ч 25м" */
    private String formatDuration(long millis) {
        return FormatUtil.formatDurationCompact(millis);
    }

    // ==================== ДИНАМИЧЕСКИЕ ====================

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

    // ==================== MAPPING ====================

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
}