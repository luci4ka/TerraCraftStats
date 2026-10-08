package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * DAO для таблицы {@code player_milestones}.
 *
 * <p>Хранит информацию о выданных milestones, чтобы избежать
 * повторной выдачи после рестарта сервера.</p>
 */
public class PlayerMilestoneDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;

    public PlayerMilestoneDAO(Main plugin, DatabaseConnection db, boolean isMySQL) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
    }

    // ==================== CHECK ====================

    /**
     * Проверить, была ли уже выдана награда за milestone.
     *
     * @param playerId     ID игрока
     * @param milestoneKey ключ milestone (например, {@code "mobs_killed:100"})
     * @return true, если уже выдана
     */
    public boolean isAwarded(int playerId, String milestoneKey) throws SQLException {
        String sql = "SELECT 1 FROM player_milestones "
                + "WHERE player_id = ? AND milestone_key = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setString(2, milestoneKey);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Получить все выданные milestones для игрока.
     */
    public Set<String> getAwardedKeys(int playerId) throws SQLException {
        Set<String> result = new HashSet<>();
        String sql = "SELECT milestone_key FROM player_milestones WHERE player_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("milestone_key"));
                }
            }
        }
        return result;
    }

    // ==================== AWARD ====================

    /**
     * Отметить milestone как выданный.
     *
     * @param playerId     ID игрока
     * @param milestoneKey ключ milestone
     * @return true, если запись создана; false — если уже была
     */
    public boolean markAwarded(int playerId, String milestoneKey) throws SQLException {
        String query = isMySQL
                ? "INSERT IGNORE INTO player_milestones "
                + "(player_id, milestone_key, awarded_at) VALUES (?, ?, ?)"
                : "INSERT OR IGNORE INTO player_milestones "
                + "(player_id, milestone_key, awarded_at) VALUES (?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setString(2, milestoneKey);
            stmt.setLong(3, System.currentTimeMillis());

            int affected = stmt.executeUpdate();
            return affected > 0;
        }
    }

    // ==================== COUNT ====================

    /**
     * Получить количество выданных milestones для игрока.
     */
    public int countAwarded(int playerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM player_milestones WHERE player_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}