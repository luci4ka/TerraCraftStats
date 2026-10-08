package com.playerstats.core.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Один шаг миграции.
 *
 * <p>Реализации могут быть идемпотентными (проверять существование
 * колонок/таблиц) или простыми SQL-командами.</p>
 *
 * <p>Использование:</p>
 * <pre>{@code
 * // Простой SQL
 * MigrationStep s1 = (conn, stmt, isMySQL) -> stmt.executeUpdate("CREATE TABLE ...");
 *
 * // Условный ALTER
 * MigrationStep s2 = (conn, stmt, isMySQL) ->
 *         MigrationUtils.addColumnIfMissing(conn, stmt, "players", "last_seen", "DATETIME");
 * }</pre>
 */
@FunctionalInterface
public interface MigrationStep {

    /**
     * Применить шаг.
     *
     * @param conn    соединение
     * @param stmt    statement для выполнения SQL
     * @param isMySQL true — MySQL; false — SQLite
     */
    void apply(Connection conn, Statement stmt, boolean isMySQL) throws SQLException;
}