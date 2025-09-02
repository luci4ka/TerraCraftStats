package com.playerstats.core.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLConnection implements DatabaseConnection {

    private final String host, database, username, password;
    private final int port;
    private Connection connection;

    public MySQLConnection(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public void connect() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/" + database +
                            "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    username, password);
        }
    }

    @Override
    public void disconnect() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.close();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) connect();
        return connection;
    }

    @Override
    public void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Таблица игроков
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS players (
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            uuid VARCHAR(36) UNIQUE,
                            name VARCHAR(16),
                            first_join DATETIME
                        ) ENGINE=InnoDB;
                    """);

            // Таблица серверов
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS servers (
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(50) UNIQUE,
                            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB;
                    """);

            // Таблица типов статистики
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS stat_types (
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(50) UNIQUE
                        ) ENGINE=InnoDB;
                    """);

            // Таблица статистики игрока (привязка к серверу)
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_stats (
                            player_id INT NOT NULL,
                            server_id INT NOT NULL,
                            stat_type_id INT NOT NULL,
                            value_int INT DEFAULT 0,
                            value_double DOUBLE(16,2) DEFAULT 0,
                            value_string VARCHAR(255),
                            PRIMARY KEY(player_id, server_id, stat_type_id),
                            FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                            FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                            FOREIGN KEY (stat_type_id) REFERENCES stat_types(id)
                        ) ENGINE=InnoDB;
                    """);

            // Таблица статистики серверов
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS server_stats (
                            server_id INT NOT NULL,
                            stat_type_id INT NOT NULL,
                            value_int INT DEFAULT 0,
                            value_double DOUBLE(16,2) DEFAULT 0,
                            value_string VARCHAR(255),
                            PRIMARY KEY(server_id, stat_type_id),
                            FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                            FOREIGN KEY (stat_type_id) REFERENCES stat_types(id)
                        ) ENGINE=InnoDB;
                    """);

            // Таблица топов
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS leaderboards (
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            stat_type_id INT NOT NULL,
                            server_id INT DEFAULT NULL,
                            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (stat_type_id) REFERENCES stat_types(id),
                            FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
                        ) ENGINE=InnoDB;
                    """);

            // Таблица записей в топах
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS leaderboard_entries (
                            leaderboard_id INT NOT NULL,
                            player_id INT NOT NULL,
                            rank INT NOT NULL,
                            value_int INT DEFAULT 0,
                            value_double DOUBLE(16,2) DEFAULT 0,
                            PRIMARY KEY(leaderboard_id, player_id),
                            FOREIGN KEY (leaderboard_id) REFERENCES leaderboards(id) ON DELETE CASCADE,
                            FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
                        ) ENGINE=InnoDB;
                    """);

            // Добавляем стандартные типы статистики
            stmt.executeUpdate("""
                        INSERT IGNORE INTO stat_types (name) VALUES
                        ('play_time'),
                        ('blocks_placed'),
                        ('blocks_broken'),
                        ('kills'),
                        ('deaths'),
                        ('mobs_killed'),
                        ('bosses_killed'),
                        ('balance'),
                        ('group');
                    """);
        }
    }
}