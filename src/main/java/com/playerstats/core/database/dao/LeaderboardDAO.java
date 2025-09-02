package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardDAO {

    private final DatabaseConnection db;
    private final StatTypeDAO statTypeDAO;

    public LeaderboardDAO(DatabaseConnection db, StatTypeDAO statTypeDAO) {
        this.db = db;
        this.statTypeDAO = statTypeDAO;
    }

    public record LeaderboardEntry(String playerName, double value) {}

    /**
     * Получаем топ по любому типу статистики
     * @param statName имя статистики: play_time, mobs_killed, blocks_placed, balance
     * @param limit количество топов
     * @return список игроков с их значениями
     */
    public List<LeaderboardEntry> getTop(String statName, int limit) throws SQLException {
        int statTypeId = statTypeDAO.createOrGetStatType(statName);
        String query = """
                SELECT p.name, ps.value_int AS int_val, ps.value_double AS double_val
                FROM player_stats ps
                JOIN players p ON ps.player_id = p.id
                WHERE ps.stat_type_id = ?
                ORDER BY 
                CASE WHEN ps.value_double IS NOT NULL THEN ps.value_double ELSE ps.value_int END DESC
                LIMIT ?
                """;

        List<LeaderboardEntry> topList = new ArrayList<>();
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double value = rs.getDouble("double_val");
                    if (rs.wasNull()) {
                        value = rs.getInt("int_val");
                    }
                    topList.add(new LeaderboardEntry(name, value));
                }
            }
        }
        return topList;
    }

    // Можно добавить удобные методы для конкретных топов:

    public List<LeaderboardEntry> getTopPlayTime(int limit) throws SQLException {
        return getTop("play_time", limit);
    }

    public List<LeaderboardEntry> getTopMobsKilled(int limit) throws SQLException {
        return getTop("mobs_killed", limit);
    }

    public List<LeaderboardEntry> getTopBlocksPlaced(int limit) throws SQLException {
        return getTop("blocks_placed", limit);
    }

    public List<LeaderboardEntry> getTopBalance(int limit) throws SQLException {
        return getTop("balance", limit);
    }
}
