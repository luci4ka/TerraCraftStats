package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TotalServerDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final StatTypeDAO statTypeDAO;

    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public TotalServerDAO(Main plugin, DatabaseConnection db, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.db = db;
        this.statTypeDAO = statTypeDAO;
    }

    // ==================== Статистика конкретного сервера ====================

    public Map<String, Number> getServerStats(int serverId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getServerStats(serverId=" + serverId + ")");
        }

        Map<String, Number> stats = new LinkedHashMap<>();
        stats.put("uniquePlayers", getUniquePlayerCount(serverId));

        String sql = """
            SELECT st.name,
                   SUM(CASE
                       WHEN ps.value_double IS NOT NULL THEN ps.value_double
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       ELSE 0
                   END) AS total_value,
                   MAX(CASE WHEN ps.value_double IS NOT NULL THEN 1 ELSE 0 END) AS has_double
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            WHERE ps.server_id = ?
            GROUP BY st.name
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double value = rs.getDouble("total_value");
                    boolean hasDouble = rs.getInt("has_double") == 1;

                    if (hasDouble) {
                        stats.put(name, value);
                    } else {
                        stats.put(name, (int) value);
                    }
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getServerStats → "
                    + stats.size() + " записей");
        }

        return stats;
    }

    public int getUniquePlayerCount(int serverId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT player_id) FROM player_stats WHERE server_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    // ==================== Общая статистика по всем серверам ====================

    public Map<String, Number> getTotalStats() throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTotalStats()");
        }

        Map<String, Number> stats = new LinkedHashMap<>();
        stats.put("totalUniquePlayers", getTotalUniquePlayerCount());

        String sql = """
            SELECT st.name,
                   SUM(CASE
                       WHEN ps.value_double IS NOT NULL THEN ps.value_double
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       ELSE 0
                   END) AS total_value,
                   MAX(CASE WHEN ps.value_double IS NOT NULL THEN 1 ELSE 0 END) AS has_double
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            GROUP BY st.name
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                double value = rs.getDouble("total_value");
                boolean hasDouble = rs.getInt("has_double") == 1;

                if (hasDouble) {
                    stats.put(name, value);
                } else {
                    stats.put(name, (int) value);
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTotalStats → "
                    + stats.size() + " записей");
        }

        return stats;
    }

    public int getTotalUniquePlayerCount() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT player_id) FROM player_stats";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int getServerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM servers";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int getTotalPlayerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM players";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    // ==================== Статистика конкретного игрока ====================

    public Map<String, Number> getPlayerTotalStats(int playerId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getPlayerTotalStats(playerId="
                    + playerId + ")");
        }

        Map<String, Number> stats = new LinkedHashMap<>();

        String sql = """
            SELECT st.name,
                   SUM(CASE
                       WHEN ps.value_double IS NOT NULL THEN ps.value_double
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       ELSE 0
                   END) AS total_value,
                   MAX(CASE WHEN ps.value_double IS NOT NULL THEN 1 ELSE 0 END) AS has_double
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            WHERE ps.player_id = ?
            GROUP BY st.name
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double value = rs.getDouble("total_value");
                    boolean hasDouble = rs.getInt("has_double") == 1;

                    if (hasDouble) {
                        stats.put(name, value);
                    } else {
                        stats.put(name, (int) value);
                    }
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getPlayerTotalStats → "
                    + stats.size() + " записей");
        }

        return stats;
    }

    public int getPlayerServerCount(int playerId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT server_id) FROM player_stats WHERE player_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    // ==================== Топ по конкретной статистике ====================

    public Map<String, Number> getTopPlayers(String statName, int limit) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTopPlayers(statName="
                    + statName + ", limit=" + limit + ")");
        }

        Map<String, Number> top = new LinkedHashMap<>();
        int statTypeId = getOrCacheStatTypeId(statName);

        String sql = """
            SELECT p.name,
                   SUM(CASE
                       WHEN ps.value_double IS NOT NULL THEN ps.value_double
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       ELSE 0
                   END) AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ?
            GROUP BY p.id, p.name
            ORDER BY total_value DESC
            LIMIT ?
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, Math.min(limit, 100));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double value = rs.getDouble("total_value");
                    top.put(name, value);
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getTopPlayers → "
                    + top.size() + " записей");
        }

        return top;
    }

    // ==================== Быстрые методы для конкретных статов ====================

    public int getTotalPlayTime() throws SQLException {
        return getSingleTotalInt("play_time");
    }

    public int getTotalMobsKilled() throws SQLException {
        return getSingleTotalInt("mobs_killed");
    }

    public int getTotalBossesKilled() throws SQLException {
        return getSingleTotalInt("bosses_killed");
    }

    public int getTotalBlocksPlaced() throws SQLException {
        return getSingleTotalInt("blocks_placed");
    }

    public int getTotalBlocksBroken() throws SQLException {
        return getSingleTotalInt("blocks_broken");
    }

    public int getTotalKills() throws SQLException {
        return getSingleTotalInt("kills");
    }

    public int getTotalDeaths() throws SQLException {
        return getSingleTotalInt("deaths");
    }

    public double getSingleTotalDouble(String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = """
            SELECT SUM(CASE
                WHEN value_double IS NOT NULL THEN value_double
                WHEN value_int IS NOT NULL THEN value_int
                ELSE 0
            END)
            FROM player_stats WHERE stat_type_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    // ==================== Приватные методы ====================

    private int getSingleTotalInt(String statName) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getSingleTotalInt(statName="
                    + statName + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = """
            SELECT SUM(CASE
                WHEN value_int IS NOT NULL THEN value_int
                WHEN value_double IS NOT NULL THEN CAST(value_double AS INT)
                ELSE 0
            END)
            FROM player_stats WHERE stat_type_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int result = rs.getInt(1);
                    if (isVerboseDebug()) {
                        plugin.getLogger().info("[DEBUG] SQL: getSingleTotalInt → "
                                + result);
                    }
                    return result;
                }
            }
        }
        return 0;
    }

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) return cached;

        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);
        return id;
    }

    public void clearCache() {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Очистка кэша TotalServerDAO ("
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