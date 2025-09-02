package com.playerstats.core.database.dao;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TotalServerDAO {
    private final Connection conn;

    public TotalServerDAO(Connection conn) {
        this.conn = conn;
    }

    // Статистика по конкретному серверу
    public Map<String, Integer> getServerStats(int serverId) throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(DISTINCT player_id) AS totalPlayers, " +
                        "SUM(blocks_placed) AS blocksPlaced, SUM(blocks_broken) AS blocksBroken, " +
                        "SUM(mobs_killed) AS mobsKilled, SUM(bosses_killed) AS bossesKilled " +
                        "FROM player_stats WHERE server_id = ?")) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalPlayers", rs.getInt("totalPlayers"));
                    stats.put("blocksPlaced", rs.getInt("blocksPlaced"));
                    stats.put("blocksBroken", rs.getInt("blocksBroken"));
                    stats.put("mobsKilled", rs.getInt("mobsKilled"));
                    stats.put("bossesKilled", rs.getInt("bossesKilled"));
                }
            }
        }
        return stats;
    }

    // Общая статистика по всем серверам
    public Map<String, Integer> getTotalStats() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(DISTINCT player_id) AS totalPlayers, " +
                             "SUM(blocks_placed) AS blocksPlaced, SUM(blocks_broken) AS blocksBroken, " +
                             "SUM(mobs_killed) AS mobsKilled, SUM(bosses_killed) AS bossesKilled " +
                             "FROM player_stats")) {
            if (rs.next()) {
                stats.put("totalPlayers", rs.getInt("totalPlayers"));
                stats.put("blocksPlaced", rs.getInt("blocksPlaced"));
                stats.put("blocksBroken", rs.getInt("blocksBroken"));
                stats.put("mobsKilled", rs.getInt("mobsKilled"));
                stats.put("bossesKilled", rs.getInt("bossesKilled"));
            }
        }
        return stats;
    }

    // Статистика конкретного игрока по всем серверам
    public Map<String, Number> getPlayerTotalStats(int playerId) throws SQLException {
        Map<String, Number> stats = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT SUM(blocks_placed) AS blocksPlaced, SUM(blocks_broken) AS blocksBroken, " +
                        "SUM(mobs_killed) AS mobsKilled, SUM(bosses_killed) AS bossesKilled, " +
                        "SUM(play_time) AS playTime, SUM(deaths) AS deaths, SUM(kills) AS kills " +
                        "FROM player_stats WHERE player_id = ?")) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("blocksPlaced", rs.getInt("blocksPlaced"));
                    stats.put("blocksBroken", rs.getInt("blocksBroken"));
                    stats.put("mobsKilled", rs.getInt("mobsKilled"));
                    stats.put("bossesKilled", rs.getInt("bossesKilled"));
                    stats.put("playTime", rs.getInt("playTime"));
                    stats.put("deaths", rs.getInt("deaths"));
                    stats.put("kills", rs.getInt("kills"));
                }
            }
        }
        return stats;
    }

    // Топ игроков по конкретной статистике
    public Map<String, Number> getTopPlayers(String stat, int limit) throws SQLException {
        Map<String, Number> top = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT p.name, s." + stat + " FROM player_stats s " +
                        "JOIN players p ON p.id = s.player_id " +
                        "ORDER BY s." + stat + " DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    top.put(rs.getString("name"), rs.getInt(stat));
                }
            }
        }
        return top;
    }
}
