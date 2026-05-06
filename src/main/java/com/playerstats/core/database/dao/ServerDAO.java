package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;

    // Потокобезопасный кэш: name → id
    private final Map<String, Integer> nameToIdCache = new ConcurrentHashMap<>();
    // Потокобезопасный кэш: id → name
    private final Map<Integer, String> idToNameCache = new ConcurrentHashMap<>();

    public ServerDAO(DatabaseConnection db, boolean isMySQL) {
        this.db = db;
        this.isMySQL = isMySQL;
    }

    // ==================== Создание / Получение ====================

    /**
     * Получить ID сервера, если не существует — создать.
     * Потокобезопасен: использует атомарную вставку.
     *
     * @param name имя сервера
     * @return ID сервера
     * @throws SQLException если БД недоступна
     */
    public int createOrGetServer(String name) throws SQLException {
        // Проверяем кэш
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        // Пробуем атомарную вставку
        int id = insertOrGetServer(name);

        // Кэшируем оба направления
        nameToIdCache.put(name, id);
        idToNameCache.put(id, name);

        return id;
    }

    /**
     * Атомарная вставка или получение ID сервера.
     * Без гонки потоков!
     */
    private int insertOrGetServer(String name) throws SQLException {
        if (isMySQL) {
            return insertOrGetServerMySQL(name);
        } else {
            return insertOrGetServerSQLite(name);
        }
    }

    private int insertOrGetServerMySQL(String name) throws SQLException {
        // MySQL: INSERT ... ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)
        // LAST_INSERT_ID(id) вернёт существующий ID без изменения записи
        String sql = "INSERT INTO servers (name) VALUES (?) " +
                "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        // Если не получили ключ, делаем SELECT
        return getServerIdDirect(name);
    }

    private int insertOrGetServerSQLite(String name) throws SQLException {
        // SQLite: INSERT OR IGNORE
        String insertSQL = "INSERT OR IGNORE INTO servers (name) VALUES (?)";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(insertSQL)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        // В любом случае (вставлен или уже существовал) читаем ID
        return getServerIdDirect(name);
    }

    /**
     * Прямой SELECT ID сервера (без кэша).
     */
    private int getServerIdDirect(String name) throws SQLException {
        String sql = "SELECT id FROM servers WHERE name = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Не удалось создать или найти сервер: " + name);
    }

    // ==================== Получение ID ====================

    /**
     * Получить ID сервера по имени. Если не найден — вернёт -1.
     */
    public int getServerId(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "SELECT id FROM servers WHERE name = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    nameToIdCache.put(name, id);
                    idToNameCache.put(id, name);
                    return id;
                }
            }
        }
        return -1;
    }

    // ==================== Получение имени ====================

    /**
     * Получить имя сервера по ID. Если не найден — вернёт null.
     */
    public String getServerName(int serverId) throws SQLException {
        String cached = idToNameCache.get(serverId);
        if (cached != null) return cached;

        String sql = "SELECT name FROM servers WHERE id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    idToNameCache.put(serverId, name);
                    nameToIdCache.put(name, serverId);
                    return name;
                }
            }
        }
        return null;
    }

    // ==================== Список серверов ====================

    /**
     * Получить список всех серверов.
     *
     * @return Map<ID, Имя>
     */
    public Map<Integer, String> getAllServers() throws SQLException {
        Map<Integer, String> servers = new ConcurrentHashMap<>();
        String sql = "SELECT id, name FROM servers ORDER BY id";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                servers.put(id, name);
                // Заодно прогреваем кэш
                idToNameCache.put(id, name);
                nameToIdCache.put(name, id);
            }
        }
        return servers;
    }

    /**
     * Получить количество серверов в БД.
     */
    public int getServerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM servers";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    // ==================== Удаление ====================

    /**
     * Удалить сервер по имени (осторожно: каскадное удаление!).
     */
    public void deleteServer(String name) throws SQLException {
        String sql = "DELETE FROM servers WHERE name = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        // Очищаем кэш
        Integer id = nameToIdCache.remove(name);
        if (id != null) {
            idToNameCache.remove(id);
        }
    }

    /**
     * Удалить сервер по ID (осторожно: каскадное удаление!).
     */
    public void deleteServer(int serverId) throws SQLException {
        String sql = "DELETE FROM servers WHERE id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.executeUpdate();
        }
        String name = idToNameCache.remove(serverId);
        if (name != null) {
            nameToIdCache.remove(name);
        }
    }

    /**
     * Проверить, существует ли сервер в БД.
     */
    public boolean serverExists(String name) throws SQLException {
        return getServerId(name) != -1;
    }

    /**
     * Проверить, существует ли сервер в БД.
     */
    public boolean serverExists(int serverId) throws SQLException {
        return getServerName(serverId) != null;
    }

    // ==================== Кэш ====================

    /**
     * Полностью очистить кэш.
     */
    public void clearCache() {
        nameToIdCache.clear();
        idToNameCache.clear();
    }

    /**
     * Размер кэша (для дебага).
     */
    public int getCacheSize() {
        return nameToIdCache.size();
    }
}