package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.UUID;

public class PlayerDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;

    public PlayerDAO(Main plugin, DatabaseConnection db, boolean isMySQL) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
    }

    // ==================== Создание игрока ====================

    /**
     * @deprecated Используйте {@link #getOrCreatePlayerId(UUID, String)}.
     */
    @Deprecated
    public void createPlayerIfNotExists(UUID uuid, String name) throws SQLException {
        getOrCreatePlayerId(uuid, name);
    }

    // ==================== Получение или создание ID ====================

    /**
     * Получить ID игрока или создать, если не существует.
     * Обновляет имя при смене ника.
     */
    public int getOrCreatePlayerId(UUID uuid, String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getOrCreatePlayerId(uuid="
                    + uuid + ", name=" + name + ")");
        }

        // 1. Пробуем получить существующий ID
        String selectSQL = "SELECT id, name FROM players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String oldName = rs.getString("name");

                    // Обновляем имя только если изменилось
                    if (!name.equals(oldName)) {
                        if (isVerboseDebug()) {
                            plugin.getLogger().info("[DEBUG] SQL: смена имени "
                                    + oldName + " → " + name + " (ID=" + id + ")");
                        }
                        updatePlayerNameById(id, name);
                    }

                    if (isVerboseDebug()) {
                        plugin.getLogger().info("[DEBUG] SQL: getOrCreatePlayerId → "
                                + id + " (найден)");
                    }

                    return id;
                }
            }
        }

        // 2. Игрока нет — создаём
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: игрок не найден, создание...");
        }

        return insertPlayer(uuid, name);
    }

    /**
     * Вставить нового игрока и вернуть его ID.
     */
    private int insertPlayer(UUID uuid, String name) throws SQLException {
        if (isMySQL) {
            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: INSERT players (MySQL) uuid="
                        + uuid + ", name=" + name);
            }

            String insertSQL = "INSERT INTO players (uuid, name, first_join) VALUES (?, ?, NOW())";
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        if (isVerboseDebug()) {
                            plugin.getLogger().info("[DEBUG] SQL: insertPlayer → "
                                    + id + " (создан)");
                        }
                        return id;
                    }
                }
            } catch (SQLIntegrityConstraintViolationException e) {
                if (isVerboseDebug()) {
                    plugin.getLogger().info("[DEBUG] SQL: race condition — игрок уже существует");
                }
                return handleExistingPlayer(uuid, name);
            }
        } else {
            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: UPSERT players (SQLite) uuid="
                        + uuid + ", name=" + name);
            }

            // SQLite: UPSERT — вставка или обновление имени одним запросом
            String upsertSQL = """
                INSERT INTO players (uuid, name, first_join)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(upsertSQL)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();
            }
        }

        // Получаем ID (мог быть создан или уже существовать)
        return handleExistingPlayer(uuid, name);
    }

    /**
     * Получить ID существующего игрока и обновить имя.
     */
    private int handleExistingPlayer(UUID uuid, String name) throws SQLException {
        int id = getPlayerId(uuid);
        if (id != -1) {
            updatePlayerName(uuid, name);

            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: handleExistingPlayer → "
                        + id);
            }

            return id;
        }
        throw new SQLException("Не удалось найти или создать игрока: " + name);
    }

    // ==================== Получение ID ====================

    /**
     * Получить ID игрока по UUID.
     *
     * @return ID игрока или -1, если не найден
     */
    public int getPlayerId(UUID uuid) throws SQLException {
        String sql = "SELECT id FROM players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    /**
     * Получить имя игрока по UUID.
     */
    public String getPlayerName(UUID uuid) throws SQLException {
        String sql = "SELECT name FROM players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        }
        return null;
    }

    /**
     * Получить имя игрока по ID.
     */
    public String getPlayerName(int playerId) throws SQLException {
        String sql = "SELECT name FROM players WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        }
        return null;
    }

    /**
     * Проверить, существует ли игрок в БД.
     */
    public boolean playerExists(UUID uuid) throws SQLException {
        return getPlayerId(uuid) != -1;
    }

    // ==================== Обновление имени ====================

    /**
     * Обновить имя игрока, если оно изменилось.
     */
    private void updatePlayerName(UUID uuid, String newName) throws SQLException {
        String sql = "UPDATE players SET name = ? WHERE uuid = ? AND name != ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, newName);
            stmt.executeUpdate();
        }
    }

    /**
     * Обновить имя игрока по ID.
     */
    private void updatePlayerNameById(int playerId, String newName) throws SQLException {
        String sql = "UPDATE players SET name = ? WHERE id = ? AND name != ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setInt(2, playerId);
            stmt.setString(3, newName);
            stmt.executeUpdate();
        }
    }

    /**
     * Проверить, включён ли verbose debug.
     */
    private boolean isVerboseDebug() {
        try {
            return plugin.getConfigHandler().isVerboseDebug();
        } catch (Exception e) {
            return false;
        }
    }
}