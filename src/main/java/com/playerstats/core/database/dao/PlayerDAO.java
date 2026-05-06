package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class PlayerDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;

    public PlayerDAO(DatabaseConnection db, boolean isMySQL) {
        this.db = db;
        this.isMySQL = isMySQL;
    }

    // ==================== Создание игрока ====================

    /**
     * Создать игрока, если не существует (без возврата ID).
     * Автоматически обновляет имя, если игрок сменил ник.
     *
     * @deprecated Используйте {@link #getOrCreatePlayerId(UUID, String)} — один запрос вместо двух.
     */
    @Deprecated
    public void createPlayerIfNotExists(UUID uuid, String name) throws SQLException {
        // Пробуем вставить
        String insertSQL = isMySQL
                ? "INSERT IGNORE INTO players (uuid, name, first_join) VALUES (?, ?, NOW())"
                : "INSERT OR IGNORE INTO players (uuid, name, first_join) VALUES (?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement stmt = db.getConnection().prepareStatement(insertSQL)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        }

        // Обновляем имя, если изменилось
        updatePlayerName(uuid, name);
    }

    // ==================== Получение или создание ID ====================

    /**
     * Получить ID игрока или создать, если не существует.
     * Обновляет имя при каждом вызове (на случай смены ника).
     * Один запрос на получение + один на вставку (если нужно).
     *
     * @param uuid UUID игрока
     * @param name текущий ник
     * @return ID игрока
     * @throws SQLException если БД недоступна
     */
    public int getOrCreatePlayerId(UUID uuid, String name) throws SQLException {
        // 1. Пробуем получить существующий ID
        String selectSQL = "SELECT id, name FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(selectSQL)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String oldName = rs.getString("name");

                    // Обновляем имя, если изменилось
                    if (!name.equals(oldName)) {
                        updatePlayerNameById(id, name);
                    }
                    return id;
                }
            }
        }

        // 2. Игрока нет — создаём
        return insertPlayer(uuid, name);
    }

    /**
     * Вставить нового игрока и вернуть его ID.
     */
    private int insertPlayer(UUID uuid, String name) throws SQLException {
        String insertSQL;
        boolean returnGeneratedKeys = true;

        if (isMySQL) {
            insertSQL = "INSERT INTO players (uuid, name, first_join) VALUES (?, ?, NOW())";
        } else {
            // SQLite: INSERT OR IGNORE — если уже существует, просто игнорируем
            insertSQL = "INSERT OR IGNORE INTO players (uuid, name, first_join) VALUES (?, ?, CURRENT_TIMESTAMP)";
            returnGeneratedKeys = false; // INSERT OR IGNORE не возвращает ключи
        }

        if (returnGeneratedKeys) {
            try (PreparedStatement stmt = db.getConnection().prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("Duplicate entry")) {
                    return handleDuplicatePlayer(uuid, name);
                }
                throw e;
            }
        } else {
            // SQLite: INSERT OR IGNORE
            try (PreparedStatement stmt = db.getConnection().prepareStatement(insertSQL)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();
            }
        }

        // В любом случае получаем ID (мог быть создан или уже существовать)
        return handleExistingPlayer(uuid, name);
    }

    /**
     * Обработать ситуацию, когда игрок уже существует.
     */
    private int handleExistingPlayer(UUID uuid, String name) throws SQLException {
        int id = getPlayerId(uuid);
        if (id != -1) {
            updatePlayerName(uuid, name);
            return id;
        }
        throw new SQLException("Не удалось найти или создать игрока: " + name);
    }

    /**
     * Обработать дубликат при вставке (для MySQL).
     */
    private int handleDuplicatePlayer(UUID uuid, String name) throws SQLException {
        int id = getPlayerId(uuid);
        if (id != -1) {
            updatePlayerName(uuid, name);
            return id;
        }
        throw new SQLException("Не удалось получить ID после дубликата: " + name);
    }

    // ==================== Получение ID ====================

    /**
     * Получить ID игрока по UUID.
     *
     * @param uuid UUID игрока
     * @return ID игрока или -1, если не найден
     */
    public int getPlayerId(UUID uuid) throws SQLException {
        String sql = "SELECT id FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
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
     *
     * @param uuid UUID игрока
     * @return имя игрока или null, если не найден
     */
    public String getPlayerName(UUID uuid) throws SQLException {
        String sql = "SELECT name FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
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
     *
     * @param playerId ID игрока
     * @return имя игрока или null, если не найден
     */
    public String getPlayerName(int playerId) throws SQLException {
        String sql = "SELECT name FROM players WHERE id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
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
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
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
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setInt(2, playerId);
            stmt.setString(3, newName);
            stmt.executeUpdate();
        }
    }
}