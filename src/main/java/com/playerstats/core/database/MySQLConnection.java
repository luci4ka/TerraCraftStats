package com.playerstats.core.database;

import com.playerstats.Main;
import com.playerstats.core.config.TrackedBlocks;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MySQLConnection implements DatabaseConnection {

    private final Main plugin;
    private final HikariDataSource dataSource;

    public MySQLConnection(Main plugin, String host, int port, String database,
                           String username, String password) {
        this.plugin = plugin;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setConnectionTimeout(5000);
        config.setPoolName("PlayerStats-MySQL");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void connect() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Пробное подключение
        }
    }

    @Override
    public void disconnect() throws SQLException {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public void initTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) UNIQUE,
                    name VARCHAR(16),
                    first_join DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS servers (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) UNIQUE,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stat_types (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) UNIQUE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_id INT NOT NULL,
                    server_id INT NOT NULL,
                    stat_type_id INT NOT NULL,
                    value_int INT DEFAULT 0,
                    value_double DECIMAL(20, 2) DEFAULT 0,
                    value_string VARCHAR(255),
                    PRIMARY KEY(player_id, server_id, stat_type_id),
                    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                    FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS server_stats (
                    server_id INT NOT NULL,
                    stat_type_id INT NOT NULL,
                    value_int INT DEFAULT 0,
                    value_double DECIMAL(20, 2) DEFAULT 0,
                    value_string VARCHAR(255),
                    PRIMARY KEY(server_id, stat_type_id),
                    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                    FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS leaderboards (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    stat_type_id INT NOT NULL,
                    server_id INT DEFAULT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE,
                    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS leaderboard_entries (
                    leaderboard_id INT NOT NULL,
                    player_id INT NOT NULL,
                    rank INT NOT NULL,
                    value_int INT DEFAULT 0,
                    value_double DECIMAL(20, 2) DEFAULT 0,
                    PRIMARY KEY(leaderboard_id, player_id),
                    FOREIGN KEY (leaderboard_id) REFERENCES leaderboards(id) ON DELETE CASCADE,
                    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS stat_history (
            id INT AUTO_INCREMENT PRIMARY KEY,
            player_id INT NOT NULL,
            stat_type_id INT NOT NULL,
            server_id INT NOT NULL,
            value INT NOT NULL,
            date DATE NOT NULL,
            UNIQUE KEY unique_snapshot (player_id, stat_type_id, server_id, date),
            INDEX idx_player_stat (player_id, stat_type_id, server_id)
        );
""");

// ← НОВОЕ
            stmt.executeUpdate("""
        CREATE TABLE IF NOT EXISTS player_achievements (
            player_id INT NOT NULL,
            achievement_id VARCHAR(64) NOT NULL,
            unlocked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY(player_id, achievement_id),
            FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
            INDEX idx_player (player_id),
            INDEX idx_achievement (achievement_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
""");
        }

        insertDefaultStatTypes();
    }

    private void insertDefaultStatTypes() throws SQLException {
        List<String> types = new ArrayList<>(List.of(
                "excluded", "play_time", "blocks_placed", "blocks_broken",
                "kills", "deaths", "mobs_killed", "bosses_killed",
                "balance", "group",
                "distance_walked", "damage_dealt", "damage_taken",
                "items_crafted", "food_consumed", "arrows_shot",
                "fish_caught", "entities_tamed", "entities_bred",
                "teleports", "portal_uses", "logins",
                "killstreak", "best_killstreak"
        ));

        // Типы мобов
        for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
            if (type.isAlive() && !type.equals(org.bukkit.entity.EntityType.PLAYER)) {
                types.add("mob_killed_" + type.name().toLowerCase());
            }
        }

        // Ценные блоки из конфига
        TrackedBlocks tracked = plugin.getConfigHandler().getTrackedBlocks();
        if (tracked != null) {
            types.addAll(tracked.toStatTypes());
        }

        String insertSQL = "INSERT IGNORE INTO stat_types (name) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            for (String type : types) {
                pstmt.setString(1, type);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        plugin.getLogger().info("Предсоздано " + types.size() + " типов статистики (MySQL).");
    }
}