package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatTypeDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;

    private final Map<String, Integer> nameToIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> idToNameCache = new ConcurrentHashMap<>();

    public StatTypeDAO(Main plugin, DatabaseConnection db, boolean isMySQL) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
    }

    // ==================== Создание / Получение ====================

    /**
     * Получить ID типа статистики, если не существует — создать.
     */
    public int createOrGetStatType(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) {
            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] StatType: кэш " + name + " → " + cached);
            }
            return cached;
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] StatType: создание " + name);
        }

        int id = insertOrGetStatType(name);

        nameToIdCache.put(name, id);
        idToNameCache.put(id, name);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] StatType: создан " + name + " → " + id);
        }

        return id;
    }

    private int insertOrGetStatType(String name) throws SQLException {
        if (isMySQL) {
            return insertOrGetStatTypeMySQL(name);
        } else {
            return insertOrGetStatTypeSQLite(name);
        }
    }

    private int insertOrGetStatTypeMySQL(String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: INSERT IGNORE stat_types (MySQL) name=" + name);
        }

        String sql = "INSERT INTO stat_types (name) VALUES (?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        return getStatTypeIdDirect(name);
    }

    private int insertOrGetStatTypeSQLite(String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: INSERT IGNORE stat_types (SQLite) name=" + name);
        }

        String insertSQL = "INSERT OR IGNORE INTO stat_types (name) VALUES (?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        return getStatTypeIdDirect(name);
    }

    private int getStatTypeIdDirect(String name) throws SQLException {
        String sql = "SELECT id FROM stat_types WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        throw new SQLException("Не удалось создать или найти тип статистики: " + name);
    }

    // ==================== Получение ID ====================

    public int getStatTypeId(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "SELECT id FROM stat_types WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    nameToIdCache.put(name, id);
                    idToNameCache.put(id, name);

                    if (isVerboseDebug()) {
                        plugin.getLogger().info("[DEBUG] StatType: загружен из БД "
                                + name + " → " + id);
                    }

                    return id;
                }
            }
        }
        return -1;
    }

    // ==================== Получение имени ====================

    public String getStatTypeName(int statTypeId) throws SQLException {
        String cached = idToNameCache.get(statTypeId);
        if (cached != null) return cached;

        String sql = "SELECT name FROM stat_types WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public Map<Integer, String> getAllStatTypes() throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getAllStatTypes()");
        }

        Map<Integer, String> types = new LinkedHashMap<>();
        String sql = "SELECT id, name FROM stat_types ORDER BY id";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                types.put(id, name);
                idToNameCache.put(id, name);
                nameToIdCache.put(name, id);
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getAllStatTypes → "
                    + types.size() + " типов");
        }

        return types;
    }

    public boolean statTypeExists(String name) throws SQLException {
        return getStatTypeId(name) != -1;
    }

    // ==================== Удаление ====================

    public void deleteStatType(String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: deleteStatType(name=" + name + ")");
        }

        String sql = "DELETE FROM stat_types WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            int deleted = stmt.executeUpdate();

            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: deleteStatType → удалено "
                        + deleted + " записей");
            }
        }
        Integer id = nameToIdCache.remove(name);
        if (id != null) {
            idToNameCache.remove(id);
        }
    }

    // ==================== Кэш ====================

    public void clearCache() {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Очистка кэша StatTypeDAO ("
                    + nameToIdCache.size() + " записей)");
        }
        nameToIdCache.clear();
        idToNameCache.clear();
    }

    public int getCacheSize() {
        return nameToIdCache.size();
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