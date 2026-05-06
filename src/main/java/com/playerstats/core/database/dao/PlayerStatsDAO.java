package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStatsDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;
    private final StatTypeDAO statTypeDAO;

    // Кэш stat_name → stat_type_id
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public PlayerStatsDAO(DatabaseConnection db, boolean isMySQL, StatTypeDAO statTypeDAO) {
        this.db = db;
        this.isMySQL = isMySQL;
        this.statTypeDAO = statTypeDAO;
    }

    // ==================== Int ====================

    public void setStat(int playerId, int serverId, int statTypeId, int value) throws SQLException {
        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_int = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_int = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setInt(4, value);
            stmt.setInt(5, value);
            stmt.executeUpdate();
        }
    }

    public void setStat(int playerId, int serverId, String statName, int value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        setStat(playerId, serverId, statTypeId, value);
    }

    public int getStat(int playerId, int serverId, int statTypeId) throws SQLException {
        String query = "SELECT value_int FROM player_stats " +
                "WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("value_int");
            }
        }
        return 0;
    }

    public int getStat(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        return getStat(playerId, serverId, statTypeId);
    }

    // ==================== Double ====================

    public void setStatDouble(int playerId, int serverId, String statName, double value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_double = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_double = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setDouble(4, value);
            stmt.setDouble(5, value);
            stmt.executeUpdate();
        }
    }

    /**
     * Получить double-значение статистики.
     * Если value_double is NULL, пробует value_int (приведение).
     */
    public double getStatDouble(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "SELECT COALESCE(value_double, value_int, 0) AS value FROM player_stats " +
                "WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("value");
            }
        }
        return 0.0;
    }

    // ==================== String ====================

    public void setStatString(int playerId, int serverId, String statName, String value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_string) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_string = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_string) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_string = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setString(4, value);
            stmt.setString(5, value);
            stmt.executeUpdate();
        }
    }

    public String getStatString(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "SELECT value_string FROM player_stats " +
                "WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("value_string");
            }
        }
        return null;
    }

    // ==================== Сводные данные ====================

    /**
     * Все статы игрока на конкретном сервере.
     * Возвращает int-значения (для double-статов используйте {@link #getAllStatsDetailed}).
     */
    public Map<String, Integer> getAllStatsForPlayer(int playerId, int serverId) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name, ps.value_int
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ? AND ps.server_id = ?
            """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getInt("value_int"));
                }
            }
        }
        return stats;
    }

    /**
     * Суммарные статы игрока по всем серверам (только int).
     * Использует COALESCE для учёта double-значений.
     */
    public Map<String, Integer> getTotalStatsForPlayer(int playerId) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name, 
                   SUM(COALESCE(ps.value_double, ps.value_int, 0)) AS total
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ?
            GROUP BY s.name
            """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getInt("total"));
                }
            }
        }
        return stats;
    }

    /**
     * Детальная статистика игрока на конкретном сервере.
     * Возвращает все три типа значений.
     */
    public Map<String, StatValue> getAllStatsDetailed(int playerId, int serverId) throws SQLException {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name, ps.value_int, ps.value_double, ps.value_string
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ? AND ps.server_id = ?
            """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
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

    // ==================== Кэширование ====================

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) return cached;

        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);
        return id;
    }

    /**
     * Очистить кэш stat_type_id (при перезагрузке конфига, например).
     */
    public void clearCache() {
        statTypeCache.clear();
    }

    // ==================== ДЕПРЕКЕЙТЕД: перенесено в LeaderboardDAO ====================

    /**
     * @deprecated Используйте {@link LeaderboardDAO#getTop(String, int, int)}
     */
    @Deprecated
    public List<Map.Entry<String, Number>> getTopPlayers(String statName, int limit) throws SQLException {
        // Делегируем в LeaderboardDAO, НО с serverId = -1 (некорректно для мультисерверности!)
        // Оставлено только для обратной совместимости
        int statTypeId = getOrCacheStatTypeId(statName);
        String valueColumn = statName.equals("balance") ? "value_double" : "value_int";
        String sql = "SELECT p.name, ps." + valueColumn + " AS value " +
                "FROM player_stats ps " +
                "JOIN players p ON ps.player_id = p.id " +
                "WHERE ps.stat_type_id = ? " +
                "ORDER BY ps." + valueColumn + " DESC " +
                "LIMIT ?";

        List<Map.Entry<String, Number>> topList = new ArrayList<>();
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String playerName = rs.getString("name");
                    Number value = statName.equals("balance")
                            ? rs.getDouble("value")
                            : rs.getInt("value");
                    topList.add(new AbstractMap.SimpleEntry<>(playerName, value));
                }
            }
        }
        return topList;
    }
}