package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StatTypeDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;

    public StatTypeDAO(DatabaseConnection db, boolean isMySQL) {
        this.db = db;
        this.isMySQL = isMySQL;
    }

    public int createOrGetStatType(String name) throws SQLException {
        // Сначала пытаемся получить ID
        int existingId = getStatTypeId(name);
        if (existingId != -1) return existingId;

        // Если нет, создаём
        String query = isMySQL
                ? "INSERT INTO stat_types (name) VALUES (?) ON DUPLICATE KEY UPDATE name = VALUES(name)"
                : "INSERT OR IGNORE INTO stat_types (name) VALUES (?)";

        try (PreparedStatement insert = db.getConnection().prepareStatement(
                query,
                PreparedStatement.RETURN_GENERATED_KEYS
        )) {
            insert.setString(1, name);
            insert.executeUpdate();

            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        // На случай SQLite (IGNORE) получаем ID ещё раз
        return getStatTypeId(name);
    }

    public int getStatTypeId(String name) throws SQLException {
        try (PreparedStatement stmt = db.getConnection().prepareStatement("SELECT id FROM stat_types WHERE name = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        return -1;
    }
}
