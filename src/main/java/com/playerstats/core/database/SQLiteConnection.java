package com.playerstats.core.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteConnection implements DatabaseConnection {

    private final String filePath;
    private Connection connection;

    public SQLiteConnection(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public void connect() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + filePath);
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
            // Игроки
            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS players (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT UNIQUE,
                name TEXT,
                first_join DATETIME
            );
        """);

            // Серверы
            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS servers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
        """);

            // Типы статистики
            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS stat_types (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE
            );
        """);

            // Статистика игрока (с привязкой к серверу)
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
                FOREIGN KEY(stat_type_id) REFERENCES stat_types(id)
            );
        """);

            // Статистика серверов
            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS server_stats (
                server_id INTEGER NOT NULL,
                stat_type_id INTEGER NOT NULL,
                value_int INTEGER DEFAULT 0,
                value_double REAL DEFAULT 0,
                value_string TEXT,
                PRIMARY KEY(server_id, stat_type_id),
                FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE CASCADE,
                FOREIGN KEY(stat_type_id) REFERENCES stat_types(id)
            );
        """);

            // Таблица топов
            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS leaderboards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                stat_type_id INTEGER NOT NULL,
                server_id INTEGER DEFAULT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(stat_type_id) REFERENCES stat_types(id),
                FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE CASCADE
            );
        """);

            // Записи топов
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

            // Базовые типы статистики
            stmt.executeUpdate("""
            INSERT OR IGNORE INTO stat_types (name) VALUES
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
