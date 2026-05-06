package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerStatsDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;
    private final StatTypeDAO statTypeDAO;

    // Кэш stat_name → stat_type_id
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public ServerStatsDAO(DatabaseConnection db, boolean isMySQL, StatTypeDAO statTypeDAO) {
        this.db = db;
        this.isMySQL = isMySQL;
        this.statTypeDAO = statTypeDAO;
    }

    // ==================== Int ====================

    /**
     * Получить целочисленную статистику сервера.
     */
    public int getStat(int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT value_int FROM server_stats WHERE server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("value_int");
            }
        }
        return 0;
    }

    /**
     * Установить целочисленную статистику сервера.
     */
    public void setStat(int serverId, String statName, int value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_int) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_int = ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_int) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_int = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, value);
            stmt.setInt(4, value);
            stmt.executeUpdate();
        }
    }

    /**
     * Атомарно увеличить целочисленную статистику сервера.
     * Без гонки потоков!
     */
    public void incrementStat(int serverId, String statName, int increment) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        // Сначала пробуем обновить существующую запись
        String updateSQL = "UPDATE server_stats SET value_int = value_int + ? " +
                "WHERE server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(updateSQL)) {
            stmt.setInt(1, increment);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            int rowsUpdated = stmt.executeUpdate();

            // Если запись не существует — создаём
            if (rowsUpdated == 0) {
                String insertSQL = isMySQL
                        ? "INSERT INTO server_stats (server_id, stat_type_id, value_int) " +
                        "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value_int = value_int + ?"
                        : "INSERT INTO server_stats (server_id, stat_type_id, value_int) " +
                        "VALUES (?, ?, ?) ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_int = value_int + ?";
                try (PreparedStatement insertStmt = db.getConnection().prepareStatement(insertSQL)) {
                    insertStmt.setInt(1, serverId);
                    insertStmt.setInt(2, statTypeId);
                    insertStmt.setInt(3, increment);
                    insertStmt.setInt(4, increment);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    // ==================== Double ====================

    /**
     * Получить дробную статистику сервера.
     */
    public double getStatDouble(int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT COALESCE(value_double, value_int, 0) AS value FROM server_stats " +
                "WHERE server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("value");
            }
        }
        return 0.0;
    }

    /**
     * Установить дробную статистику сервера.
     */
    public void setStatDouble(int serverId, String statName, double value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_double) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_double) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_double = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setDouble(3, value);
            stmt.setDouble(4, value);
            stmt.executeUpdate();
        }
    }

    /**
     * Атомарно увеличить дробную статистику сервера.
     */
    public void incrementStatDouble(int serverId, String statName, double increment) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String updateSQL = "UPDATE server_stats SET value_double = value_double + ? " +
                "WHERE server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(updateSQL)) {
            stmt.setDouble(1, increment);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated == 0) {
                String insertSQL = isMySQL
                        ? "INSERT INTO server_stats (server_id, stat_type_id, value_double) " +
                        "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value_double = value_double + ?"
                        : "INSERT INTO server_stats (server_id, stat_type_id, value_double) " +
                        "VALUES (?, ?, ?) ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_double = value_double + ?";
                try (PreparedStatement insertStmt = db.getConnection().prepareStatement(insertSQL)) {
                    insertStmt.setInt(1, serverId);
                    insertStmt.setInt(2, statTypeId);
                    insertStmt.setDouble(3, increment);
                    insertStmt.setDouble(4, increment);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    // ==================== String ====================

    /**
     * Получить строковую статистику сервера.
     */
    public String getStatString(int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT value_string FROM server_stats WHERE server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("value_string");
            }
        }
        return null;
    }

    /**
     * Установить строковую статистику сервера.
     */
    public void setStatString(int serverId, String statName, String value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO server_stats (server_id, stat_type_id, value_string) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_string = ?"
                : "INSERT INTO server_stats (server_id, stat_type_id, value_string) VALUES (?, ?, ?) " +
                "ON CONFLICT(server_id, stat_type_id) DO UPDATE SET value_string = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            stmt.setString(3, value);
            stmt.setString(4, value);
            stmt.executeUpdate();
        }
    }

    // ==================== Сводные данные ====================

    /**
     * Получить все статистики сервера (int + double).
     */
    public Map<String, Number> getAllStats(int serverId) throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();
        String sql = """
            SELECT s.name, ps.value_int, ps.value_double
            FROM server_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.server_id = ?
            """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double doubleVal = rs.getDouble("value_double");
                    if (rs.wasNull() || doubleVal == 0.0) {
                        stats.put(name, rs.getInt("value_int"));
                    } else {
                        stats.put(name, doubleVal);
                    }
                }
            }
        }
        return stats;
    }

    /**
     * Получить детальную статистику сервера (все три типа значений).
     */
    public Map<String, StatValue> getAllStatsDetailed(int serverId) throws SQLException {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        String sql = """
            SELECT s.name, ps.value_int, ps.value_double, ps.value_string
            FROM server_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.server_id = ?
            """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    int intVal = rs.getInt("value_int");
                    double doubleVal = rs.getDouble("value_double");
                    String strVal = rs.getString("value_string");
                    stats.put(name, new StatValue(intVal, doubleVal, strVal));
                }
            }
        }
        return stats;
    }

    /**
     * Контейнер для значений статистики.
     */
    public record StatValue(int valueInt, double valueDouble, String valueString) {
        public Number getNumberValue() {
            if (valueDouble != 0.0 || valueInt == 0) {
                return valueDouble;
            }
            return valueInt;
        }
    }

    // ==================== Сброс статистики ====================

    /**
     * Сбросить конкретную статистику сервера в 0.
     */
    public void resetStat(int serverId, String statName) throws SQLException {
        setStat(serverId, statName, 0);
    }

    /**
     * Сбросить все статистики сервера.
     * Осторожно: удаляет все записи сервера из server_stats!
     */
    public void resetAllStats(int serverId) throws SQLException {
        String sql = "DELETE FROM server_stats WHERE server_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.executeUpdate();
        }
    }

    // ==================== Кэширование ====================

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) return cached;

        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);
        return id;
    }

    /**
     * Очистить кэш stat_type_id.
     */
    public void clearCache() {
        statTypeCache.clear();
    }
}