package com.playerstats.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLConnection implements DatabaseConnection {

    private final HikariDataSource dataSource;
    private final String host, database, username, password;
    private final int port;

    public MySQLConnection(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setConnectionTimeout(5000);
        // Проверка валидности соединения перед использованием
        config.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void connect() throws SQLException {
        // HikariCP сам управляет соединениями, просто проверяем
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
        // Используем try-with-resources для автоматического возврата соединения в пул
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Таблица игроков
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) UNIQUE,
                    name VARCHAR(16),
                    first_join DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // Таблица серверов
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS servers (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) UNIQUE,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // Таблица типов статистики
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stat_types (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) UNIQUE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // Таблица статистики игрока
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_id INT NOT NULL,
                    server_id INT NOT NULL,
                    stat_type_id INT NOT NULL,
                    value_int INT DEFAULT 0,
                    value_double DOUBLE DEFAULT 0,
                    value_string VARCHAR(255),
                    PRIMARY KEY(player_id, server_id, stat_type_id),
                    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                    FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // Таблица статистики серверов
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS server_stats (
                    server_id INT NOT NULL,
                    stat_type_id INT NOT NULL,
                    value_int INT DEFAULT 0,
                    value_double DOUBLE DEFAULT 0,
                    value_string VARCHAR(255),
                    PRIMARY KEY(server_id, stat_type_id),
                    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                    FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // Таблица лидербордов
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

            // Таблица записей лидербордов
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS leaderboard_entries (
                    leaderboard_id INT NOT NULL,
                    player_id INT NOT NULL,
                    rank INT NOT NULL,
                    value_int INT DEFAULT 0,
                    value_double DOUBLE DEFAULT 0,
                    PRIMARY KEY(leaderboard_id, player_id),
                    FOREIGN KEY (leaderboard_id) REFERENCES leaderboards(id) ON DELETE CASCADE,
                    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);


            stmt.executeUpdate("INSERT IGNORE INTO stat_types (name) VALUES ('excluded')");

            // Добавляем стандартные типы статистики (безопасно)
            String[] defaultStats = {
                    "play_time", "blocks_placed", "blocks_broken",
                    "kills", "deaths", "mobs_killed", "bosses_killed",
                    "balance", "group"
            };
            for (String stat : defaultStats) {
                stmt.executeUpdate(
                        "INSERT IGNORE INTO stat_types (name) VALUES ('" + stat + "')"
                );
            }
        }
    }
}