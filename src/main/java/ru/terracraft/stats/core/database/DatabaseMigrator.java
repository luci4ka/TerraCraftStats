package ru.terracraft.stats.core.database;

import cc.carm.lib.easysql.api.SQLManager;
import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DatabaseMigrator {

    public static final int CURRENT_DB_VERSION = 4;

    private final PlayerStatsPlugin plugin;
    private final SQLManager manager;
    private final List<List<MigrationStep>> migrations = new ArrayList<>();

    public DatabaseMigrator(PlayerStatsPlugin plugin, SQLManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        registerMigrations();
    }

    private void registerMigrations() {
        List<MigrationStep> v1 = new ArrayList<>();
        v1.add(sql(createPlayersTable()));
        v1.add(sql(createServersTable()));
        v1.add(sql(createStatTypesTable()));
        v1.add(sql(createPlayerStatsTable()));
        v1.add(sql(createServerStatsTable()));
        v1.add(sql(createStatHistoryTable()));
        v1.add(sql("UPDATE db_version SET version = 1 WHERE id = 1"));
        migrations.add(v1);

        List<MigrationStep> v2 = new ArrayList<>();
        v2.add(sql("UPDATE db_version SET version = 2 WHERE id = 1"));
        migrations.add(v2);

        List<MigrationStep> v3 = new ArrayList<>();
        v3.add(sql(createStatDeltasTable()));
        v3.add(sql("UPDATE db_version SET version = 3 WHERE id = 1"));
        migrations.add(v3);

        List<MigrationStep> v4 = new ArrayList<>();
        v4.add(sql(createAchievementsReceivedTable()));
        v4.add(sql("UPDATE db_version SET version = 4 WHERE id = 1"));
        migrations.add(v4);
    }

    private MigrationStep sql(String sql) {
        return (conn, stmt) -> stmt.executeUpdate(sql);
    }

    public boolean hasFailed() {
        try (Connection conn = manager.getConnection()) {
            ensureVersionTable(conn);
            int currentVersion = readCurrentVersion(conn);

            if (currentVersion == CURRENT_DB_VERSION) {
                plugin.getLogger().info("БД актуальна (v" + currentVersion + ").");
                return false;
            }
            if (currentVersion > CURRENT_DB_VERSION) {
                plugin.getLogger().severe("БД новее плагина (db: v" + currentVersion + ")!");
                return true;
            }

            plugin.getLogger().info("Миграция БД: v" + currentVersion + " → v" + CURRENT_DB_VERSION);
            for (int v = currentVersion; v < CURRENT_DB_VERSION; v++) {
                applyMigration(conn, v);
            }
            plugin.getLogger().info("Миграция завершена. Версия: v" + CURRENT_DB_VERSION);
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка миграции БД", e);
            return true;
        }
    }

    private void ensureVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS db_version (id INT PRIMARY KEY, version INT NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.executeUpdate("INSERT IGNORE INTO db_version (id, version) VALUES (1, 0)");
        }
    }

    private int readCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM db_version WHERE id = 1")) {
            return rs.next() ? rs.getInt("version") : 0;
        }
    }

    private void applyMigration(Connection conn, int fromVersion) throws SQLException {
        List<MigrationStep> steps = migrations.get(fromVersion);
        if (steps == null || steps.isEmpty()) {
            plugin.getLogger().warning("Нет миграции v" + fromVersion + " → v" + (fromVersion + 1));
            return;
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new SQLException("Не удалось начать транзакцию миграции", e);
        }

        try (Statement stmt = conn.createStatement()) {
            for (int i = 0; i < steps.size(); i++) {
                try {
                    steps.get(i).apply(conn, stmt);
                } catch (SQLException e) {
                    throw new SQLException("Шаг " + (i + 1) + " миграции v" + fromVersion
                            + " → v" + (fromVersion + 1) + " упал: " + e.getMessage(), e);
                }
            }
            conn.commit();
            plugin.getLogger().info("Миграция v" + fromVersion + " → v" + (fromVersion + 1) + " применена.");
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new SQLException("Миграция v" + fromVersion + " → v" + (fromVersion + 1)
                    + " откачена: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ignored) {}
        }
    }

    private String createPlayersTable() {
        return "CREATE TABLE IF NOT EXISTS players (id INT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) UNIQUE, name VARCHAR(16), first_join DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createServersTable() {
        return "CREATE TABLE IF NOT EXISTS servers (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) UNIQUE, created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createStatTypesTable() {
        return "CREATE TABLE IF NOT EXISTS stat_types (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) UNIQUE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createPlayerStatsTable() {
        return "CREATE TABLE IF NOT EXISTS player_stats (" +
                "player_id INT NOT NULL, " +
                "server_id INT NOT NULL, " +
                "stat_type_id INT NOT NULL, " +
                "value_int INT DEFAULT NULL, " +
                "value_double DECIMAL(20,2) DEFAULT NULL, " +
                "value_string VARCHAR(255) DEFAULT NULL, " +
                "PRIMARY KEY(player_id, server_id, stat_type_id), " +
                "FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createServerStatsTable() {
        return "CREATE TABLE IF NOT EXISTS server_stats (" +
                "server_id INT NOT NULL, " +
                "stat_type_id INT NOT NULL, " +
                "value_int INT DEFAULT NULL, " +
                "value_double DECIMAL(20,2) DEFAULT NULL, " +
                "value_string VARCHAR(255) DEFAULT NULL, " +
                "PRIMARY KEY(server_id, stat_type_id), " +
                "FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createStatHistoryTable() {
        return "CREATE TABLE IF NOT EXISTS stat_history (id INT AUTO_INCREMENT PRIMARY KEY, player_id INT NOT NULL, stat_type_id INT NOT NULL, server_id INT NOT NULL, value INT NOT NULL, date DATE NOT NULL, UNIQUE KEY unique_snapshot (player_id, stat_type_id, server_id, date), INDEX idx_player_stat (player_id, stat_type_id, server_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createStatDeltasTable() {
        return "CREATE TABLE IF NOT EXISTS stat_deltas (id INT AUTO_INCREMENT PRIMARY KEY, player_id INT NOT NULL, stat_type_id INT NOT NULL, delta INT NOT NULL, timestamp BIGINT NOT NULL, FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE, FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE, INDEX idx_delta_player_stat (player_id, stat_type_id), INDEX idx_delta_timestamp (timestamp)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createAchievementsReceivedTable() {
        return "CREATE TABLE IF NOT EXISTS achievements_received (" +
                "uuid CHAR(36) NOT NULL, " +
                "achievement_id VARCHAR(128) NOT NULL, " +
                "date BIGINT NOT NULL, " +
                "PRIMARY KEY (uuid, achievement_id), " +
                "INDEX idx_ach_date (date)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }
}