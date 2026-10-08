package com.playerstats.core.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Утилиты для идемпотентных миграций.
 */
public final class MigrationUtils {

    private MigrationUtils() {}

    // ==================== CHECK ====================

    /**
     * Проверить, существует ли колонка.
     *
     * <p>Работает и для MySQL, и для SQLite — через JDBC Metadata.</p>
     */
    public static boolean columnExists(Connection conn, String table, String column)
            throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(
                null, null, table, column)) {
            return rs.next();
        }
    }

    /**
     * Проверить, существует ли таблица.
     */
    public static boolean tableExists(Connection conn, String table)
            throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(
                null, null, table, null)) {
            return rs.next();
        }
    }

    /**
     * Проверить, существует ли индекс на таблице.
     */
    public static boolean indexExists(Connection conn, String table, String index)
            throws SQLException {
        try (ResultSet rs = conn.getMetaData().getIndexInfo(
                null, null, table, false, false)) {
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                if (idxName != null && idxName.equalsIgnoreCase(index)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== IDEMPOTENT DDL ====================

    /**
     * Добавить колонку, если её нет.
     */
    public static void addColumnIfMissing(Connection conn, Statement stmt,
                                          String table, String column,
                                          String definition) throws SQLException {
        if (!columnExists(conn, table, column)) {
            stmt.executeUpdate("ALTER TABLE " + table
                    + " ADD COLUMN " + column + " " + definition);
        }
    }

    /**
     * Удалить колонку, если она есть.
     *
     * <p>⚠ SQLite поддерживает {@code DROP COLUMN} только с 3.35+.
     * Для старых — используйте пересоздание таблицы.</p>
     */
    public static void dropColumnIfExists(Connection conn, Statement stmt,
                                          String table, String column)
            throws SQLException {
        if (columnExists(conn, table, column)) {
            stmt.executeUpdate("ALTER TABLE " + table
                    + " DROP COLUMN " + column);
        }
    }

    /**
     * Создать индекс, если его нет.
     */
    public static void createIndexIfMissing(Connection conn, Statement stmt,
                                            String table, String index,
                                            String columns) throws SQLException {
        if (!indexExists(conn, table, index)) {
            stmt.executeUpdate("CREATE INDEX " + index
                    + " ON " + table + "(" + columns + ")");
        }
    }
}