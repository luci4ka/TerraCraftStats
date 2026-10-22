package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatTypeDAO {

    private final PlayerStatsPlugin plugin;
    private final Map<String, Integer> nameToIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> idToNameCache = new ConcurrentHashMap<>();

    public StatTypeDAO(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public int createOrGetStatType(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "INSERT INTO stat_types (name) VALUES (?) ON DUPLICATE KEY UPDATE name = VALUES(name)";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }

        int id = getStatTypeIdDirect(name);
        nameToIdCache.put(name, id);
        idToNameCache.put(id, name);
        return id;
    }

    private int getStatTypeIdDirect(String name) throws SQLException {
        String sql = "SELECT id FROM stat_types WHERE name = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        throw new SQLException("Не удалось создать или найти тип статистики: " + name);
    }

    public int getStatTypeId(String name) throws SQLException {
        Integer cached = nameToIdCache.get(name);
        if (cached != null) return cached;

        String sql = "SELECT id FROM stat_types WHERE name = ?";
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

    public String getStatTypeName(int statTypeId) throws SQLException {
        String cached = idToNameCache.get(statTypeId);
        if (cached != null) return cached;

        String sql = "SELECT name FROM stat_types WHERE id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
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

    public Map<Integer, String> getAllStatTypes() throws SQLException {
        Map<Integer, String> types = new LinkedHashMap<>();
        String sql = "SELECT id, name FROM stat_types ORDER BY id";
        try (Connection conn = plugin.getSqlManager().getConnection();
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
        return types;
    }

    public boolean statTypeExists(String name) throws SQLException {
        return getStatTypeId(name) != -1;
    }

    public void deleteStatType(String name) throws SQLException {
        String sql = "DELETE FROM stat_types WHERE name = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        Integer id = nameToIdCache.remove(name);
        if (id != null) idToNameCache.remove(id);
    }

    public void clearCache() {
        nameToIdCache.clear();
        idToNameCache.clear();
    }

    public int getCacheSize() {
        return nameToIdCache.size();
    }
}