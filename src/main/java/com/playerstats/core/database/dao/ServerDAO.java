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

public class ServerDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;

    private final Map<String, Integer> nameToIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> idToNameCache = new ConcurrentHashMap<>();

    public ServerDAO(Main plugin, DatabaseConnection db, boolean isMySQL) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
    }

    // ==================== Создание / Получение ====================

    public int createOrGetServer(String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: createOrGetServer(name=" + name + ")");
        }

        // Проверяем кэш
        Integer cached = nameToIdCache.get(name);
        if (cached != null) {
            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: createOrGetServer → кэш "
                        + cached);
            }
            return cached;
        }

        // Вставляем или получаем
        int id = insertOrGetServer(name);

        // Кэшируем оба направления
        nameToIdCache.put(name, id);
        idToNameCache.put(id, name);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: createOrGetServer → "
                    + id + " (создан/найден)");
        }

        return id;
    }

    private int insertOrGetServer(String name) throws SQLException {
        if (isMySQL) {
            return insertOrGetServerMySQL(name);
        } else {
            return insertOrGetServerSQLite(name);
        }
    }

    private int insertOrGetServerMySQL(String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: INSERT servers (MySQL) name=" + name);
        }

        String sql = "INSERT INTO servers (name) VALUES (?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        return getServerIdDirect(name);
    }

    private int insertOrGetServerSQLite(String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: INSERT servers (SQLite) name=" + name);
        }

        String insertSQL = "INSERT OR IGNORE INTO servers (name) VALUES (?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        return getServerIdDirect(name);
    }

    private int getServerIdDirect(String name) throws SQLException {
        String sql = "SELECT id FROM servers WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public int getServerId(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "SELECT id FROM servers WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    nameToIdCache.put(name, id);
                    idToNameCache.put(id, name);

                    if (isVerboseDebug()) {
                        plugin.getLogger().info("[DEBUG] SQL: getServerId("
                                + name + ") → " + id);
                    }

                    return id;
                }
            }
        }
        return -1;
    }

    // ==================== Получение имени ====================

    public String getServerName(int serverId) throws SQLException {
        String cached = idToNameCache.get(serverId);
        if (cached != null) return cached;

        String sql = "SELECT name FROM servers WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public Map<Integer, String> getAllServers() throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getAllServers()");
        }

        Map<Integer, String> servers = new LinkedHashMap<>();
        String sql = "SELECT id, name FROM servers ORDER BY id";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                servers.put(id, name);
                idToNameCache.put(id, name);
                nameToIdCache.put(name, id);
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getAllServers → "
                    + servers.size() + " серверов");
        }

        return servers;
    }

    public int getServerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM servers";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    // ==================== Удаление ====================

    public void deleteServer(String name) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: deleteServer(name=" + name + ")");
        }

        String sql = "DELETE FROM servers WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            int deleted = stmt.executeUpdate();

            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: deleteServer → удалено "
                        + deleted + " записей");
            }
        }
        Integer id = nameToIdCache.remove(name);
        if (id != null) {
            idToNameCache.remove(id);
        }
    }

    public void deleteServer(int serverId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: deleteServer(serverId="
                    + serverId + ")");
        }

        String sql = "DELETE FROM servers WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            int deleted = stmt.executeUpdate();

            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] SQL: deleteServer → удалено "
                        + deleted + " записей");
            }
        }
        String name = idToNameCache.remove(serverId);
        if (name != null) {
            nameToIdCache.remove(name);
        }
    }

    public boolean serverExists(String name) throws SQLException {
        return getServerId(name) != -1;
    }

    public boolean serverExists(int serverId) throws SQLException {
        return getServerName(serverId) != null;
    }

    // ==================== Кэш ====================

    public void clearCache() {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Очистка кэша ServerDAO ("
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