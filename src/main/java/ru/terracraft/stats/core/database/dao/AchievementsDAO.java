package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AchievementsDAO {

    private final PlayerStatsPlugin plugin;
    private final ConcurrentHashMap<UUID, Set<String>> receivedCache = new ConcurrentHashMap<>();

    public AchievementsDAO(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public Set<String> loadReceived(UUID uuid) throws SQLException {
        Set<String> cached = receivedCache.get(uuid);
        if (cached != null) return cached;

        Set<String> result = new HashSet<>();
        String sql = "SELECT achievement_id FROM achievements_received WHERE uuid = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString("achievement_id"));
            }
        }
        receivedCache.put(uuid, result);
        return result;
    }

    public void saveReceived(UUID uuid, String achievementId) throws SQLException {
        String sql = "INSERT INTO achievements_received (uuid, achievement_id, date) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE date = VALUES(date)";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, achievementId);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }
        receivedCache.computeIfAbsent(uuid, k -> new HashSet<>()).add(achievementId);
    }

    public void removeAll(UUID uuid) throws SQLException {
        String sql = "DELETE FROM achievements_received WHERE uuid = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        }
        receivedCache.remove(uuid);
    }

    public void removeOne(UUID uuid, String achievementId) throws SQLException {
        String sql = "DELETE FROM achievements_received WHERE uuid = ? AND achievement_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, achievementId);
            stmt.executeUpdate();
        }
        Set<String> cached = receivedCache.get(uuid);
        if (cached != null) cached.remove(achievementId);
    }

    public void unloadCache(UUID uuid) {
        receivedCache.remove(uuid);
    }

    public int countReceived(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM achievements_received WHERE uuid = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }
}