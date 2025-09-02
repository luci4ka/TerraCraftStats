package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerDAO {

    private final DatabaseConnection db;
    private final boolean isMySQL;

    public PlayerDAO(DatabaseConnection db, boolean isMySQL) {
        this.db = db;
        this.isMySQL = isMySQL;
    }

    public void createPlayerIfNotExists(UUID uuid, String name) throws SQLException {
        String query = isMySQL
                ? "INSERT IGNORE INTO players (uuid, name, first_join) VALUES (?, ?, NOW())"
                : "INSERT OR IGNORE INTO players (uuid, name, first_join) VALUES (?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        }
    }

    // Получаем ID игрока по UUID
    public int getPlayerId(UUID uuid) throws SQLException {
        try (PreparedStatement stmt = db.getConnection().prepareStatement(
                "SELECT id FROM players WHERE uuid = ?"
        )) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        return -1;
    }
}
