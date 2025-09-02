package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ServerStatsDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;
    private final StatTypeDAO statTypeDAO;

    public ServerStatsDAO(DatabaseConnection db, boolean isMySQL, StatTypeDAO statTypeDAO) {
        this.db = db;
        this.isMySQL = isMySQL;
        this.statTypeDAO = statTypeDAO;
    }

    public int getStat(int serverId, String statName) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT value_int FROM server_stats WHERE server_id = ? AND stat_type_id = ?")) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("value_int");
            }
        }
        return 0;
    }

    public void setStat(int serverId, String statName, int value) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
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

    public void incrementStat(int serverId, String statName, int increment) throws SQLException {
        int current = getStat(serverId, statName);
        setStat(serverId, statName, current + increment);
    }
}
