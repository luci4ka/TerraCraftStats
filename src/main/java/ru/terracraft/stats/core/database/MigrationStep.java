package ru.terracraft.stats.core.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@FunctionalInterface
public interface MigrationStep {
    void apply(Connection conn, Statement stmt) throws SQLException;
}