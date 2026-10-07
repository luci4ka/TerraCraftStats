package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;

public class StatHistoryDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;

    public StatHistoryDAO(Main plugin, DatabaseConnection db, boolean isMySQL) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
    }

    public void saveSnapshot(int playerId, int statTypeId, int serverId, int value)
            throws SQLException {
        LocalDate today = LocalDate.now();

        String query = isMySQL
                ? """
                  INSERT INTO stat_history (player_id, stat_type_id, server_id, value, date)
                  VALUES (?, ?, ?, ?, ?)
                  ON DUPLICATE KEY UPDATE value = ?
                  """
                : """
                  INSERT INTO stat_history (player_id, stat_type_id, server_id, value, date)
                  VALUES (?, ?, ?, ?, ?)
                  ON CONFLICT(player_id, stat_type_id, server_id, date)
                  DO UPDATE SET value = excluded.value
                  """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, serverId);
            stmt.setInt(4, value);
            stmt.setDate(5, java.sql.Date.valueOf(today));
            if (isMySQL) stmt.setInt(6, value);
            stmt.executeUpdate();
        }
    }

    public List<Integer> getHistory(int playerId, int statTypeId,
                                    int serverId, int days) throws SQLException {
        LocalDate from = LocalDate.now().minusDays(days - 1);

        String sql = """
                SELECT value, date FROM stat_history
                WHERE player_id = ? AND stat_type_id = ? AND server_id = ?
                  AND date >= ?
                ORDER BY date ASC
                """;

        List<Integer> history = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, serverId);
            stmt.setDate(4, java.sql.Date.valueOf(from));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(rs.getInt("value"));
                }
            }
        }
        return history;
    }

    /**
     * Сделать снапшот для всех игроков по всем категориям.
     */
    public void snapshotAllPlayers() throws SQLException {
        String sql = """
                SELECT player_id, stat_type_id, server_id, value_int
                FROM player_stats
                WHERE stat_type_id IN (
                    SELECT id FROM stat_types
                    WHERE name IN ('play_time', 'kills', 'deaths', 'mobs_killed',
                                   'blocks_placed', 'blocks_broken', 'bosses_killed')
                )
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                saveSnapshot(
                        rs.getInt("player_id"),
                        rs.getInt("stat_type_id"),
                        rs.getInt("server_id"),
                        rs.getInt("value_int")
                );
            }
        }
    }
}