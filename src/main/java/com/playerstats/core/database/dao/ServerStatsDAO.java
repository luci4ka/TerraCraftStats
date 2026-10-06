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

public class ServerStatsDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;
    private final StatTypeDAO statTypeDAO;

    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public ServerStatsDAO(Main plugin, DatabaseConnection db, boolean isMySQL, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
        this.statTypeDAO = statTypeDAO;
    }

    // ==================== Int ====================

    public int getStat(int serverId, String statName) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.getStat(serverId="
                    + serverId + ", statName=" + statName + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT value_int FROM server_stats WHERE server_id = ? AND stat_type_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int result = rs.getInt("value_int");
                    if (isVerboseDebug()) {
                        plugin.getLogger().info("[DEBUG] SQL: getStat → " + result);
                    }
                    return result;
                }
            }
        }
        return 0;
    }

    public void setStat(int serverId, String statName, int value) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.setStat(serverId="
                    + serverId + ", statName=" + statName + ", value=" + value + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_int) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_int = ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_int) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_int = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, value);
            stmt.setInt(4, value);
            stmt.executeUpdate();
        }
    }

    public void incrementStat(int serverId, String statName, int increment) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.incrementStat(serverId="
                    + serverId + ", statName=" + statName
                    + ", increment=" + increment + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_int) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_int = value_int + ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_int) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_int = value_int + ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, increment);
            stmt.setInt(4, increment);
            stmt.executeUpdate();
        }
    }

    // ==================== Double ====================

    /**
     * Получить double-статистику сервера.
     * Читает value_double; если NULL — value_int.
     */
    public double getStatDouble(int serverId, String statName) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.getStatDouble(serverId="
                    + serverId + ", statName=" + statName + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT CASE " +
                "WHEN value_double IS NOT NULL THEN value_double " +
                "WHEN value_int IS NOT NULL THEN CAST(value_int AS DOUBLE) " +
                "ELSE 0 END AS value " +
                "FROM server_stats " +
                "WHERE server_id = ? AND stat_type_id = ?";
        try (Connection conn = db.getConnection();
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
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.setStatDouble(serverId="
                    + serverId + ", statName=" + statName + ", value=" + value + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_double) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_double) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_double = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setDouble(3, value);
            stmt.setDouble(4, value);
            stmt.executeUpdate();
        }
    }

    public void incrementStatDouble(int serverId, String statName, double increment) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.incrementStatDouble(serverId="
                    + serverId + ", statName=" + statName
                    + ", increment=" + increment + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_double) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = value_double + ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_double) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_double = value_double + ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setDouble(3, increment);
            stmt.setDouble(4, increment);
            stmt.executeUpdate();
        }
    }

    // ==================== String ====================

    public String getStatString(int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT value_string FROM server_stats WHERE server_id = ? AND stat_type_id = ?";
        try (Connection conn = db.getConnection();
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
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.setStatString(serverId="
                    + serverId + ", statName=" + statName + ", value=" + value + ")");
        }

        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_string) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_string = ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_string) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_string = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setString(3, value);
            stmt.setString(4, value);
            stmt.executeUpdate();
        }
    }

    // ==================== Сводные данные ====================

    /**
     * Получить все статистики сервера.
     * Приоритет: value_int; если NULL — value_double.
     */
    public Map<String, Number> getAllStats(int serverId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.getAllStats(serverId="
                    + serverId + ")");
        }

        Map<String, Number> stats = new LinkedHashMap<>();
        String sql = """
            SELECT s.name, ps.value_int, ps.value_double
            FROM server_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.server_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");

                    int intVal = rs.getInt("value_int");
                    boolean intNull = rs.wasNull();

                    double doubleVal = rs.getDouble("value_double");
                    boolean doubleNull = rs.wasNull();

                    // Приоритет: value_int (для kills, blocks_placed и т.д.)
                    if (!intNull && intVal != 0) {
                        stats.put(name, intVal);
                    } else if (!doubleNull) {
                        stats.put(name, doubleVal);
                    } else {
                        stats.put(name, 0);
                    }
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getAllStats → " + stats.size() + " записей");
        }

        return stats;
    }

    public Map<String, StatValue> getAllStatsDetailed(int serverId) throws SQLException {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        String sql = """
            SELECT s.name, ps.value_int, ps.value_double, ps.value_string
            FROM server_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.server_id = ?
            """;
        try (Connection conn = db.getConnection();
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

                    stats.put(name, new StatValue(
                            intNull ? 0 : intVal,
                            doubleNull ? 0.0 : doubleVal,
                            strVal
                    ));
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

    // ==================== Сброс статистики ====================

    public void resetStat(int serverId, String statName) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.resetStat(serverId="
                    + serverId + ", statName=" + statName + ")");
        }
        setStat(serverId, statName, 0);
    }

    public void resetAllStats(int serverId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: ServerStatsDAO.resetAllStats(serverId="
                    + serverId + ")");
        }

        String sql = "DELETE FROM server_stats WHERE server_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            int deleted = stmt.executeUpdate();

            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: resetAllStats → удалено "
                        + deleted + " записей");
            }
        }
    }

    // ==================== Кэширование ====================

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

    public void clearCache() {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Очистка кэша ServerStatsDAO ("
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