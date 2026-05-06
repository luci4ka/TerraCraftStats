package com.playerstats.core.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

public class SQLiteConnection implements DatabaseConnection {

    private final String filePath;
    private Connection connection;
    private final ReentrantLock lock = new ReentrantLock();

    public SQLiteConnection(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public void connect() throws SQLException {
        lock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + filePath);

                // Критически важно для SQLite!
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");      // Включаем WAL-режим (многопоточность)
                    stmt.execute("PRAGMA foreign_keys = ON");     // Включаем внешние ключи!
                    stmt.execute("PRAGMA busy_timeout = 5000");   // Таймаут при блокировке (5 сек)
                    stmt.execute("PRAGMA synchronous = NORMAL");  // Баланс скорости и надёжности
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disconnect() throws SQLException {
        lock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        lock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
            return connection;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isConnected() {
        lock.lock();
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            // Если не можем проверить состояние — считаем что отключено
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void initTables() throws SQLException {
        lock.lock();
        try {
            // Убедимся, что подключение есть
            if (connection == null || connection.isClosed()) {
                connect();
            }

            try (Statement stmt = connection.createStatement()) {
                // Включаем внешние ключи (на случай, если connect() не был вызван)
                stmt.execute("PRAGMA foreign_keys = ON");

                // Игроки
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS players (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT UNIQUE,
                        name TEXT,
                        first_join DATETIME DEFAULT CURRENT_TIMESTAMP
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

                // Статистика игрока
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
                        FOREIGN KEY(stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                    );
                """);

                // Лидерборды
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

                // Записи лидербордов
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


                stmt.executeUpdate("INSERT OR IGNORE INTO stat_types (name) VALUES ('excluded')");

                // Базовые типы статистики
                String[] defaultStats = {
                        "play_time", "blocks_placed", "blocks_broken",
                        "kills", "deaths", "mobs_killed", "bosses_killed",
                        "balance", "group"
                };
                for (String stat : defaultStats) {
                    stmt.executeUpdate(
                            "INSERT OR IGNORE INTO stat_types (name) VALUES ('" + stat + "')"
                    );
                }
            }
        } finally {
            lock.unlock();
        }
    }
}