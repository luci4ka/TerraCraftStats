package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.*;
import java.util.UUID;

public class PlayerDAO {

    private final PlayerStatsPlugin plugin;

    public PlayerDAO(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public int getOrCreatePlayerId(UUID uuid, String name) throws SQLException {
        String selectSQL = "SELECT id, name FROM players WHERE uuid = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String oldName = rs.getString("name");
                    if (!name.equals(oldName)) {
                        updatePlayerNameById(id, name);
                    }
                    return id;
                }
            }
        }
        return insertPlayer(uuid, name);
    }

    private int insertPlayer(UUID uuid, String name) throws SQLException {
        String insertSQL = "INSERT INTO players (uuid, name, first_join) VALUES (?, ?, NOW())";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // race — игрок уже создан, читаем
        }
        return handleExistingPlayer(uuid, name);
    }

    public UUID getUuidByName(String name) {
        String sql = "SELECT uuid FROM players WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getUuidByName error: " + e.getMessage());
        }
        return null;
    }

    private int handleExistingPlayer(UUID uuid, String name) throws SQLException {
        int id = getPlayerId(uuid);
        if (id != -1) {
            updatePlayerName(uuid, name);
            return id;
        }
        throw new SQLException("Не удалось найти или создать игрока: " + name);
    }

    public int getPlayerId(UUID uuid) throws SQLException {
        String sql = "SELECT id FROM players WHERE uuid = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        return -1;
    }

    public String getPlayerName(UUID uuid) throws SQLException {
        String sql = "SELECT name FROM players WHERE uuid = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        }
        return null;
    }

    public String getPlayerName(int playerId) throws SQLException {
        String sql = "SELECT name FROM players WHERE id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        }
        return null;
    }

    public boolean playerExists(UUID uuid) throws SQLException {
        return getPlayerId(uuid) != -1;
    }

    private void updatePlayerName(UUID uuid, String newName) throws SQLException {
        String sql = "UPDATE players SET name = ? WHERE uuid = ? AND name != ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, newName);
            stmt.executeUpdate();
        }
    }

    private void updatePlayerNameById(int playerId, String newName) throws SQLException {
        String sql = "UPDATE players SET name = ? WHERE id = ? AND name != ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setInt(2, playerId);
            stmt.setString(3, newName);
            stmt.executeUpdate();
        }
    }
}