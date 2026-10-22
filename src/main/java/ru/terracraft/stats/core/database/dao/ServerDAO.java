package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerDAO {

    private final PlayerStatsPlugin plugin;
    private final Map<String, Integer> nameToIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> idToNameCache = new ConcurrentHashMap<>();

    public ServerDAO(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public int createOrGetServer(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "INSERT INTO servers (name) VALUES (?) ON DUPLICATE KEY UPDATE name = VALUES(name)";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }

        int id = getServerIdDirect(name);
        nameToIdCache.put(name, id);
        idToNameCache.put(id, name);
        return id;
    }

    private int getServerIdDirect(String name) throws SQLException {
        String sql = "SELECT id FROM servers WHERE name = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        throw new SQLException("Не удалось создать или найти сервер: " + name);
    }

    public int getServerId(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "SELECT id FROM servers WHERE name = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public String getServerName(int serverId) throws SQLException {
        String cached = idToNameCache.get(serverId);
        if (cached != null) return cached;

        String sql = "SELECT name FROM servers WHERE id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
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

    public Map<Integer, String> getAllServers() throws SQLException {
        Map<Integer, String> servers = new LinkedHashMap<>();
        String sql = "SELECT id, name FROM servers ORDER BY id";
        try (Connection conn = plugin.getSqlManager().getConnection();
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
        return servers;
    }

    public int getServerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM servers";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public void deleteServer(String name) throws SQLException {
        String sql = "DELETE FROM servers WHERE name = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        Integer id = nameToIdCache.remove(name);
        if (id != null) idToNameCache.remove(id);
    }

    public void deleteServer(int serverId) throws SQLException {
        String sql = "DELETE FROM servers WHERE id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.executeUpdate();
        }
        String name = idToNameCache.remove(serverId);
        if (name != null) nameToIdCache.remove(name);
    }

    public boolean serverExists(String name) throws SQLException {
        return getServerId(name) != -1;
    }

    public boolean serverExists(int serverId) throws SQLException {
        return getServerName(serverId) != null;
    }

    public void clearCache() {
        nameToIdCache.clear();
        idToNameCache.clear();
    }

    public int getCacheSize() {
        return nameToIdCache.size();
    }
}