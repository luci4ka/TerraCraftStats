package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardDAO {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 10;

    /**
     * Универсальное выражение для "эффективного значения" статы.
     * Приоритет: value_int (если не NULL и не 0), иначе value_double.
     * Это соответствует логике PlayerStatsDAO / ServerStatsDAO.
     */
    private static final String EFFECTIVE_VALUE =
            "CASE " +
                    "  WHEN ps.value_int IS NOT NULL AND ps.value_int != 0 THEN ps.value_int " +
                    "  WHEN ps.value_double IS NOT NULL THEN ps.value_double " +
                    "  ELSE 0 " +
                    "END";

    private final Main plugin;
    private final DatabaseConnection db;
    private final StatTypeDAO statTypeDAO;

    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public LeaderboardDAO(Main plugin, DatabaseConnection db, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.db = db;
        this.statTypeDAO = statTypeDAO;
    }

    public record LeaderboardEntry(UUID uuid, String playerName, double value) {
        @Override
        public String toString() {
            return playerName + " — " + String.format("%.2f", value);
        }
    }

    // ==================== Топ по конкретному серверу ====================

    public List<LeaderboardEntry> getTop(String statName, int serverId, int limit) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTop(statName=" + statName
                    + ", serverId=" + serverId + ", limit=" + limit + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        limit = clampLimit(limit);

        String query = """
            SELECT p.uuid, p.name,
                   %s AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ? AND ps.server_id = ?
            ORDER BY total_value DESC
            LIMIT ?
            """.formatted(EFFECTIVE_VALUE);

        List<LeaderboardEntry> result = executeTopQuery(query, statTypeId, serverId, limit);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTop(" + statName
                    + ") → " + result.size() + " записей");
        }

        return result;
    }

    public List<LeaderboardEntry> getTop(String statName, int serverId) throws SQLException {
        return getTop(statName, serverId, DEFAULT_LIMIT);
    }

    // ==================== Топ по всем серверам (суммарно) ====================

    public List<LeaderboardEntry> getTopGlobal(String statName, int limit) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTopGlobal(statName=" + statName
                    + ", limit=" + limit + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        limit = clampLimit(limit);

        String query = """
            SELECT p.uuid, p.name,
                   SUM(%s) AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ?
            GROUP BY p.id, p.uuid, p.name
            ORDER BY total_value DESC
            LIMIT ?
            """.formatted(EFFECTIVE_VALUE);

        List<LeaderboardEntry> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    double value = rs.getDouble("total_value");
                    result.add(new LeaderboardEntry(uuid, name, value));
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTopGlobal(" + statName
                    + ") → " + result.size() + " записей");
        }

        return result;
    }

    // ==================== Топ игрока относительно других ====================

    public int getPlayerRank(String statName, int serverId, int playerId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getPlayerRank(statName=" + statName
                    + ", serverId=" + serverId + ", playerId=" + playerId + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);

        String checkQuery = """
            SELECT %s AS player_value
            FROM player_stats ps
            WHERE ps.stat_type_id = ? AND ps.server_id = ? AND ps.player_id = ?
            """.formatted(EFFECTIVE_VALUE);

        double playerValue = 0;
        boolean hasRecord = false;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkQuery)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    playerValue = rs.getDouble("player_value");
                    hasRecord = true;
                }
            }
        }

        if (!hasRecord) {
            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: getPlayerRank → нет записи (-1)");
            }
            return -1;
        }

        String rankQuery = """
            SELECT COUNT(*) + 1 AS rank
            FROM player_stats ps
            WHERE ps.stat_type_id = ? AND ps.server_id = ?
              AND %s > ?
            """.formatted(EFFECTIVE_VALUE);

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(rankQuery)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setDouble(3, playerValue);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int rank = rs.getInt("rank");

                    if (isVerboseDebug()) {
                        plugin.getLogger().info("[DEBUG] SQL: getPlayerRank → #"
                                + rank + " (value=" + playerValue + ")");
                    }

                    return rank;
                }
            }
        }
        return -1;
    }

    // ==================== Удобные методы ====================

    public List<LeaderboardEntry> getTopPlayTime(int serverId, int limit) throws SQLException {
        return getTop("play_time", serverId, limit);
    }

    public List<LeaderboardEntry> getTopMobsKilled(int serverId, int limit) throws SQLException {
        return getTop("mobs_killed", serverId, limit);
    }

    public List<LeaderboardEntry> getTopBlocksPlaced(int serverId, int limit) throws SQLException {
        return getTop("blocks_placed", serverId, limit);
    }

    public List<LeaderboardEntry> getTopBalance(int serverId, int limit) throws SQLException {
        return getTop("balance", serverId, limit);
    }

    public List<LeaderboardEntry> getTopKills(int serverId, int limit) throws SQLException {
        return getTop("kills", serverId, limit);
    }

    public List<LeaderboardEntry> getTopDeaths(int serverId, int limit) throws SQLException {
        return getTop("deaths", serverId, limit);
    }

    public List<LeaderboardEntry> getTopBossesKilled(int serverId, int limit) throws SQLException {
        return getTop("bosses_killed", serverId, limit);
    }

    // ==================== Приватные методы ====================

    private List<LeaderboardEntry> executeTopQuery(String query, int statTypeId,
                                                   int serverId, int limit)
            throws SQLException {
        List<LeaderboardEntry> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    double value = rs.getDouble("total_value");
                    result.add(new LeaderboardEntry(uuid, name, value));
                }
            }
        }
        return result;
    }

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) {
            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] Кэш stat_type: " + statName
                        + " → " + cached);
            }
            return cached;
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Создание stat_type: " + statName);
        }

        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Создан stat_type: " + statName
                    + " → " + id);
        }

        return id;
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        if (limit > MAX_LIMIT) return MAX_LIMIT;
        return limit;
    }

    public void clearCache() {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Очистка кэша LeaderboardDAO ("
                    + statTypeCache.size() + " записей)");
        }
        statTypeCache.clear();
    }

    /**
     * Проверить, включён ли verbose debug.
     */
    private boolean isVerboseDebug() {
        try {
            return plugin.getConfigHandler().isVerboseDebug();
        } catch (Exception e) {
            return false;
        }
    }
}