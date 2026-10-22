package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerStatsDAO {

    private final PlayerStatsPlugin plugin;
    private final StatTypeDAO statTypeDAO;
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public ServerStatsDAO(PlayerStatsPlugin plugin, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.statTypeDAO = statTypeDAO;
    }

    public int getStat(int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT value_int FROM server_stats WHERE server_id = ? AND stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt("value_int");
                    return rs.wasNull() ? 0 : v;
                }
            }
        }
        return 0;
    }

    public void setStat(int serverId, String statName, int value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "INSERT INTO server_stats (server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE value_int = ?, value_double = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, value);
            stmt.setInt(4, value);
            stmt.executeUpdate();
        }
    }

    public void incrementStat(int serverId, String statName, int increment) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "INSERT INTO server_stats (server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE value_int = COALESCE(value_int, 0) + ?, value_double = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, increment);
            stmt.setInt(4, increment);
            stmt.executeUpdate();
        }
    }

    public double getStatDouble(int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT CASE WHEN value_double IS NOT NULL THEN value_double " +
                "WHEN value_int IS NOT NULL THEN CAST(value_int AS DOUBLE) ELSE 0 END AS value " +
                "FROM server_stats WHERE server_id = ? AND stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("value");
            }
        }
        return 0.0;
    }

    public void setStatDouble(int serverId, String statName, double value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "INSERT INTO server_stats (server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, NULL, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = ?, value_int = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setDouble(3, value);
            stmt.setDouble(4, value);
            stmt.executeUpdate();
        }
    }

    public void incrementStatDouble(int serverId, String statName, double increment) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "INSERT INTO server_stats (server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, NULL, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = COALESCE(value_double, 0) + ?, value_int = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setDouble(3, increment);
            stmt.setDouble(4, increment);
            stmt.executeUpdate();
        }
    }

    public String getStatString(int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT value_string FROM server_stats WHERE server_id = ? AND stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("value_string");
            }
        }
        return null;
    }

    public void setStatString(int serverId, String statName, String value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "INSERT INTO server_stats (server_id, stat_type_id, value_string) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_string = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setString(3, value);
            stmt.setString(4, value);
            stmt.executeUpdate();
        }
    }

    public Map<String, Number> getAllStats(int serverId) throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();
        String sql = """
            SELECT s.name, ps.value_int, ps.value_double
            FROM server_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.server_id = ?
            """;
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    int intVal = rs.getInt("value_int");
                    boolean intNull = rs.wasNull();
                    double doubleVal = rs.getDouble("value_double");
                    boolean doubleNull = rs.wasNull();
                    if (!doubleNull) stats.put(name, doubleVal);
                    else if (!intNull) stats.put(name, intVal);
                    else stats.put(name, 0);
                }
            }
        }
        return stats;
    }

    public record StatValue(int valueInt, double valueDouble, String valueString) {
        public Number getNumberValue() {
            if (valueDouble != 0.0) return valueDouble;
            return valueInt;
        }
    }

    public Map<String, StatValue> getAllStatsDetailed(int serverId) throws SQLException {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        String sql = """
            SELECT s.name, ps.value_int, ps.value_double, ps.value_string
            FROM server_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.server_id = ?
            """;
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    int intVal = rs.getInt("value_int");
                    boolean intNull = rs.wasNull();
                    double doubleVal = rs.getDouble("value_double");
                    boolean doubleNull = rs.wasNull();
                    String strVal = rs.getString("value_string");
                    stats.put(name, new StatValue(intNull ? 0 : intVal,
                            doubleNull ? 0.0 : doubleVal, strVal));
                }
            }
        }
        return stats;
    }

    public void resetStat(int serverId, String statName) throws SQLException {
        setStat(serverId, statName, 0);
    }

    public void resetAllStats(int serverId) throws SQLException {
        String sql = "DELETE FROM server_stats WHERE server_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.executeUpdate();
        }
    }

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) return cached;
        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);
        return id;
    }

    public void clearCache() {
        statTypeCache.clear();
    }
}