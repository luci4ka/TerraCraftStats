package com.playerstats.core.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnection {
    void connect() throws SQLException;
    void disconnect() throws SQLException;
    Connection getConnection() throws SQLException;
    void initTables() throws SQLException;
    boolean isConnected();

    /**
     * Проверить соединение и выбросить исключение, если оно недоступно.
     * Удобно для использования в DAO.
     */
    default void requireConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("База данных недоступна.");
        }
    }
}