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

public class SQLiteConnection implements DatabaseConnection {

    private final Main plugin;
    private final HikariDataSource dataSource;

    public SQLiteConnection(Main plugin, String filePath) {
        this.plugin = plugin;

        HikariConfig config = new HikariConfig();

        // SQLite поддерживает только ОДНОГО писателя
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);

        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + filePath);

        // Внешние ключи для КАЖДОГО соединения
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");

        config.setIdleTimeout(0);
        config.setMaxLifetime(0);
        config.setConnectionTimeout(5000);
        config.setPoolName("PlayerStats-SQLite");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void connect() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (conn == null) {
                throw new SQLException("Не удалось получить соединение с SQLite.");
            }
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

            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout = 5000");
            stmt.execute("PRAGMA synchronous = NORMAL");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT UNIQUE,
                    name TEXT,
                    first_join DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS servers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // UNIQUE на name уже создаёт индекс — CREATE INDEX НЕ НУЖЕН!
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stat_types (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_id INTEGER NOT NULL,
                    server_id INTEGER NOT NULL,
                    stat_type_id INTEGER NOT NULL,
                    value_int INTEGER DEFAULT 0,
                    value_double REAL DEFAULT 0,
                    value_string TEXT,
                    PRIMARY KEY(player_id, server_id, stat_type_id),
                    FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE,
                    FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE CASCADE,
                    FOREIGN KEY(stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS server_stats (
                    server_id INTEGER NOT NULL,
                    stat_type_id INTEGER NOT NULL,
                    value_int INTEGER DEFAULT 0,
                    value_double REAL DEFAULT 0,
                    value_string TEXT,
                    PRIMARY KEY(server_id, stat_type_id),
                    FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE CASCADE,
                    FOREIGN KEY(stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS leaderboards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    stat_type_id INTEGER NOT NULL,
                    server_id INTEGER DEFAULT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE,
                    FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE SET NULL
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS leaderboard_entries (
                    leaderboard_id INTEGER NOT NULL,
                    player_id INTEGER NOT NULL,
                    rank INTEGER NOT NULL,
                    value_int INTEGER DEFAULT 0,
                    value_double REAL DEFAULT 0,
                    PRIMARY KEY(leaderboard_id, player_id),
                    FOREIGN KEY(leaderboard_id) REFERENCES leaderboards(id) ON DELETE CASCADE,
                    FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE
                );
            """);
            stmt.executeUpdate("""
    CREATE TABLE IF NOT EXISTS stat_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        player_id INTEGER NOT NULL,
        stat_type_id INTEGER NOT NULL,
        server_id INTEGER NOT NULL,
        value INTEGER NOT NULL,
        date DATE NOT NULL,
        UNIQUE(player_id, stat_type_id, server_id, date)
    );
""");

            stmt.executeUpdate("""
    CREATE INDEX IF NOT EXISTS idx_stat_history_player
    ON stat_history(player_id, stat_type_id, server_id);
""");

// ← НОВОЕ
            stmt.executeUpdate("""
    CREATE TABLE IF NOT EXISTS player_achievements (
        player_id INTEGER NOT NULL,
        achievement_id TEXT NOT NULL,
        unlocked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY(player_id, achievement_id),
        FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE
    );
""");

            stmt.executeUpdate("""
    CREATE INDEX IF NOT EXISTS idx_achievements_player
    ON player_achievements(player_id);
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

        String insertSQL = "INSERT OR IGNORE INTO stat_types (name) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            for (String type : types) {
                pstmt.setString(1, type);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        plugin.getLogger().info("Предсоздано " + types.size() + " типов статистики (SQLite).");
    }
}