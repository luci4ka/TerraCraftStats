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
        DatabaseMigrator migrator = new DatabaseMigrator(
                plugin,
                true,
                plugin.getDatabaseBackupTask()   // ← ДОБАВИТЬ
        );

        try (Connection conn = getConnection()) {
            if (migrator.hasFailed(conn)) {
                throw new SQLException("Не удалось применить миграции БД (MySQL).");
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