package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatTypeDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;

    // Кэш: name → id и id → name
    private final Map<String, Integer> nameToIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> idToNameCache = new ConcurrentHashMap<>();

    public StatTypeDAO(DatabaseConnection db, boolean isMySQL) {
        this.db = db;
        this.isMySQL = isMySQL;
    }

    // ==================== Создание / Получение ====================

    /**
     * Получить ID типа статистики, если не существует — создать.
     * Потокобезопасен: атомарная вставка.
     *
     * @param name название типа статистики
     * @return ID типа статистики
     * @throws SQLException если БД недоступна
     */
    public int createOrGetStatType(String name) throws SQLException {
        // Проверяем кэш
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        // Атомарная вставка
        int id = insertOrGetStatType(name);

        // Кэшируем
        nameToIdCache.put(name, id);
        idToNameCache.put(id, name);

        return id;
    }

    /**
     * Атомарная вставка или получение ID типа статистики.
     * Без гонки потоков!
     */
    private int insertOrGetStatType(String name) throws SQLException {
        if (isMySQL) {
            return insertOrGetStatTypeMySQL(name);
        } else {
            return insertOrGetStatTypeSQLite(name);
        }
    }

    /**
     * MySQL: INSERT ... ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
     * LAST_INSERT_ID(id) возвращает СУЩЕСТВУЮЩИЙ ID без выделения нового AUTO_INCREMENT.
     */
    private int insertOrGetStatTypeMySQL(String name) throws SQLException {
        String sql = "INSERT INTO stat_types (name) VALUES (?) " +
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
        // Если не получили ключ (маловероятно) — SELECT
        return getStatTypeIdDirect(name);
    }

    /**
     * SQLite: INSERT OR IGNORE, затем SELECT.
     */
    private int insertOrGetStatTypeSQLite(String name) throws SQLException {
        String insertSQL = "INSERT OR IGNORE INTO stat_types (name) VALUES (?)";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(insertSQL)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        // В любом случае (вставлен или уже существовал) читаем ID
        return getStatTypeIdDirect(name);
    }

    /**
     * Прямой SELECT ID (без кэша).
     */
    private int getStatTypeIdDirect(String name) throws SQLException {
        String sql = "SELECT id FROM stat_types WHERE name = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        throw new SQLException("Не удалось создать или найти тип статистики: " + name);
    }

    // ==================== Получение ID ====================

    /**
     * Получить ID типа статистики. Если не найден — вернёт -1.
     */
    public int getStatTypeId(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "SELECT id FROM stat_types WHERE name = ?";
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
     * Получить название типа статистики по ID.
     */
    public String getStatTypeName(int statTypeId) throws SQLException {
        String cached = idToNameCache.get(statTypeId);
        if (cached != null) return cached;

        String sql = "SELECT name FROM stat_types WHERE id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    idToNameCache.put(statTypeId, name);
                    nameToIdCache.put(name, statTypeId);
                    return name;
                }
            }
        }
        return null;
    }

    // ==================== Список всех типов ====================

    /**
     * Получить все типы статистики и их ID.
     *
     * @return Map<ID, Название>
     */
    public Map<Integer, String> getAllStatTypes() throws SQLException {
        Map<Integer, String> types = new LinkedHashMap<>();
        String sql = "SELECT id, name FROM stat_types ORDER BY id";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                types.put(id, name);
                // Прогреваем кэш
                idToNameCache.put(id, name);
                nameToIdCache.put(name, id);
            }
        }
        return types;
    }

    /**
     * Проверить, существует ли тип статистики.
     */
    public boolean statTypeExists(String name) throws SQLException {
        return getStatTypeId(name) != -1;
    }

    // ==================== Удаление ====================

    /**
     * Удалить тип статистики (осторожно: каскадное удаление связанных записей!).
     */
    public void deleteStatType(String name) throws SQLException {
        String sql = "DELETE FROM stat_types WHERE name = ?";
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