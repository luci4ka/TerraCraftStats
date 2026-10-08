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
        // PRAGMA на уровне соединения
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout = 5000");
            stmt.execute("PRAGMA synchronous = NORMAL");
        }

        DatabaseMigrator migrator = new DatabaseMigrator(
                plugin,
                false,
                plugin.getDatabaseBackupTask()   // ← ДОБАВИТЬ
        );

        try (Connection conn = getConnection()) {
            if (migrator.hasFailed(conn)) {
                throw new SQLException("Не удалось применить миграции БД (SQLite).");
            }
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