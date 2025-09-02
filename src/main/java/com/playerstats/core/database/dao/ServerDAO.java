package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ServerDAO {
    private final DatabaseConnection db;
    private final Map<String, Integer> serverCache = new HashMap<>();

    public ServerDAO(DatabaseConnection db) {
        this.db = db;
    }

    /**
     * Получить ID сервера, если не существует — создать.
     */
    public int createOrGetServer(String name) throws SQLException {
        if (serverCache.containsKey(name)) {
            return serverCache.get(name);
        }

        try (PreparedStatement select = db.getConnection().prepareStatement(
                "SELECT id FROM servers WHERE name = ?")) {
            select.setString(1, name);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    serverCache.put(name, id);
                    return id;
                }
            }
        }

        try (PreparedStatement insert = db.getConnection().prepareStatement(
                "INSERT INTO servers (name) VALUES (?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    serverCache.put(name, id);
                    return id;
                }
            }
        }

        throw new SQLException("Не удалось создать или получить сервер: " + name);
    }

    /**
     * Получить ID сервера, не создавая новый. Если не найден — вернёт -1.
     */
    public int getServerId(String name) throws SQLException {
        if (serverCache.containsKey(name)) {
            return serverCache.get(name);
        }

        try (PreparedStatement select = db.getConnection().prepareStatement(
                "SELECT id FROM servers WHERE name = ?")) {
            select.setString(1, name);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    serverCache.put(name, id);
                    return id;
                }
            }
        }
        return -1; // вместо null
    }

    /**
     * Удаление сервера (например, при отладке).
     */
    public void deleteServer(String name) throws SQLException {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "DELETE FROM servers WHERE name = ?")) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
        serverCache.remove(name);
    }
}
