package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class PlayerStatsDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;
    private final StatTypeDAO statTypeDAO;

    public PlayerStatsDAO(DatabaseConnection db, boolean isMySQL, StatTypeDAO statTypeDAO) {
        this.db = db;
        this.isMySQL = isMySQL;
        this.statTypeDAO = statTypeDAO;
    }

    /** ====== Int ====== */
    public void setStat(int playerId, int serverId, int statTypeId, int value) throws SQLException {
        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_int = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_int = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setInt(4, value);
            stmt.setInt(5, value);
            stmt.executeUpdate();
        }
    }

    public int getStat(int playerId, int serverId, int statTypeId) throws SQLException {
        String query = "SELECT value_int FROM player_stats WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
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

    public void setStat(int playerId, int serverId, String statName, int value) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        setStat(playerId, serverId, statTypeId, value);
    }

    public int getStat(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        return getStat(playerId, serverId, statTypeId);
    }

    /** ====== Double ====== */
    public void setStatDouble(int playerId, int serverId, String statName, double value) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_double = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setDouble(4, value);
            stmt.setDouble(5, value);
            stmt.executeUpdate();
        }
    }

    public double getStatDouble(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        String query = "SELECT value_double FROM player_stats WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("value_double");
            }
        }
        return 0.0;
    }

    /** ====== String ====== */
    public void setStatString(int playerId, int serverId, String statName, String value) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_string) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value_string = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_string) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_string = ?";
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
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        String query = "SELECT value_string FROM player_stats WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
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

    /** ====== Дополнительно ====== */

    // Все статы игрока на конкретном сервере
    public Map<String, Integer> getAllStatsForPlayer(int playerId, int serverId) throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        String query = "SELECT s.name, ps.value_int FROM player_stats ps " +
                "JOIN stat_types s ON ps.stat_type_id = s.id " +
                "WHERE ps.player_id = ? AND ps.server_id = ?";
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

    // Суммарные статы игрока по всем серверам
    public Map<String, Integer> getTotalStatsForPlayer(int playerId) throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        String query = "SELECT s.name, SUM(ps.value_int) as total FROM player_stats ps " +
                "JOIN stat_types s ON ps.stat_type_id = s.id " +
                "WHERE ps.player_id = ? GROUP BY s.name";
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
     * Получаем топ игроков по типу статистики (int или double).
     * @param statName имя статистики ("play_time", "mobs_killed", "blocks_placed", "balance")
     * @param limit количество игроков в топе
     * @return список пар "имя игрока" → "значение"
     */
    public List<Map.Entry<String, Number>> getTopPlayers(String statName, int limit) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);

        String valueColumn = (statName.equals("balance")) ? "value_double" : "value_int";
        String sql = isMySQL
                ? "SELECT p.name, ps." + valueColumn + " AS value " +
                "FROM player_stats ps " +
                "JOIN players p ON ps.player_id = p.id " +
                "WHERE ps.stat_type_id = ? " +
                "ORDER BY ps." + valueColumn + " DESC " +
                "LIMIT ?"
                : "SELECT p.name, ps." + valueColumn + " AS value " +
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
                    Number value = (statName.equals("balance")) ? rs.getDouble("value") : rs.getInt("value");
                    topList.add(new AbstractMap.SimpleEntry<>(playerName, value));
                }
            }
        }
        return topList;
    }
}
