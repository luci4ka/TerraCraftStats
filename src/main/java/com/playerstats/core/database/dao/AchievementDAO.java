package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;

public class AchievementDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public AchievementDAO(Main plugin, DatabaseConnection db, boolean isMySQL) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
    }

    /**
     * Проверить, разблокировано ли достижение.
     */
    public boolean isUnlocked(int playerId, String achievementId) throws SQLException {
        String sql = "SELECT 1 FROM player_achievements WHERE player_id = ? AND achievement_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setString(2, achievementId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Разблокировать достижение.
     */
    public void unlock(int playerId, String achievementId) throws SQLException {
        String query = isMySQL
                ? "INSERT IGNORE INTO player_achievements (player_id, achievement_id) VALUES (?, ?)"
                : "INSERT OR IGNORE INTO player_achievements (player_id, achievement_id) VALUES (?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setString(2, achievementId);
            stmt.executeUpdate();
        }
    }

    /**
     * Получить все разблокированные достижения игрока.
     */
    public Set<String> getUnlockedAchievements(int playerId) throws SQLException {
        Set<String> result = new HashSet<>();
        String sql = "SELECT achievement_id FROM player_achievements WHERE player_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("achievement_id"));
                }
            }
        }
        return result;
    }

    /**
     * Получить дату разблокировки (millis).
     *
     * @return -1, если не разблокировано
     */
    public long getUnlockTime(int playerId, String achievementId) throws SQLException {
        String sql = "SELECT unlocked_at FROM player_achievements WHERE player_id = ? AND achievement_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setString(2, achievementId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("unlocked_at");
                    return ts != null ? ts.getTime() : -1;
                }
            }
        }
        return -1;
    }

    /**
     * Получить форматированную дату разблокировки.
     */
    public String getUnlockDate(int playerId, String achievementId) throws SQLException {
        long time = getUnlockTime(playerId, achievementId);
        if (time == -1) return null;
        return dateFormat.format(new Date(time));
    }

    /**
     * Получить все разблокированные достижения с датами.
     */
    public Map<String, Long> getUnlockedWithDates(int playerId) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        String sql = "SELECT achievement_id, unlocked_at FROM player_achievements WHERE player_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("unlocked_at");
                    result.put(rs.getString("achievement_id"),
                            ts != null ? ts.getTime() : -1);
                }
            }
        }
        return result;
    }
}