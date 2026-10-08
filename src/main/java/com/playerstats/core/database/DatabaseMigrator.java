package com.playerstats.core.database;

import com.playerstats.Main;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Управляет миграциями схемы БД.
 *
 * <p>Каждая миграция — переход от версии {@code N} к {@code N+1}.
 * Миграции применяются ПОСЛЕДОВАТЕЛЬНО, каждая в отдельной транзакции.</p>
 *
 * <p><b>Как добавить новую миграцию:</b></p>
 * <ol>
 *   <li>Увеличить {@link #CURRENT_DB_VERSION} на 1.</li>
 *   <li>Добавить в {@link #registerMigrations()} новый {@code List<MigrationStep>}.</li>
 *   <li>Для {@code ALTER TABLE ADD COLUMN} использовать
 *       {@link MigrationUtils#addColumnIfMissing}.</li>
 *   <li>Последний шаг должен обновить {@code db_version}.</li>
 * </ol>
 */
public class DatabaseMigrator {

    /**
     * Текущая ожидаемая версия схемы БД.
     *
     * <p>Если эта константа больше, чем версия в таблице {@code db_version} —
     * будут применены миграции. Если меньше — плагин откажется работать.</p>
     */
    public static final int CURRENT_DB_VERSION = 3;

    private final Main plugin;
    private final boolean isMySQL;
    private final DatabaseBackupTask backupTask;

    /** Список миграций: индекс = версия, значение = список шагов. */
    private final List<List<MigrationStep>> migrations = new ArrayList<>();

    public DatabaseMigrator(Main plugin, boolean isMySQL, DatabaseBackupTask backupTask) {
        this.plugin = plugin;
        this.isMySQL = isMySQL;
        this.backupTask = backupTask;
        registerMigrations();
    }

    // ==================== REGISTRY ====================

    private void registerMigrations() {
        // ===== v0 → v1 (базовая схема) =====
        List<MigrationStep> v1 = new ArrayList<>();

        // ===== Таблицы =====
        v1.add(sql(createPlayersTable()));
        v1.add(sql(createServersTable()));
        v1.add(sql(createStatTypesTable()));
        v1.add(sql(createPlayerStatsTable()));
        v1.add(sql(createServerStatsTable()));
        v1.add(sql(createLeaderboardsTable()));
        v1.add(sql(createLeaderboardEntriesTable()));
        v1.add(sql(createStatHistoryTable()));
        v1.add(sql(createPlayerAchievementsTable()));
        v1.add(sql(createPlayerMilestonesTable()));
        v1.add(sql(createPlayerAchievementsResetTable()));

        // ===== Индексы — ТОЛЬКО для SQLite =====
        // В MySQL индексы уже объявлены внутри CREATE TABLE через INDEX idx_name (col)
        if (!isMySQL) {
            v1.add(sql("CREATE INDEX IF NOT EXISTS idx_stat_history_player "
                    + "ON stat_history(player_id, stat_type_id, server_id)"));
            v1.add(sql("CREATE INDEX IF NOT EXISTS idx_achievements_player "
                    + "ON player_achievements(player_id)"));
            v1.add(sql("CREATE INDEX IF NOT EXISTS idx_milestones_player "
                    + "ON player_milestones(player_id)"));
            v1.add(sql("CREATE INDEX IF NOT EXISTS idx_milestones_key "
                    + "ON player_milestones(milestone_key)"));
            v1.add(sql("CREATE INDEX IF NOT EXISTS idx_ach_reset_player "
                    + "ON player_achievements_reset(player_id)"));
        }

        // ===== Обновление версии =====
        v1.add(sql("UPDATE db_version SET version = 1 WHERE id = 1"));

        migrations.add(v1);

        // ===== v1 → v2: player_achievements_reset (для существующих БД) =====
        // Если БД уже на v1 без этой таблицы — добавим.
        // CREATE TABLE IF NOT EXISTS — идемпотентно, безопасно.
        List<MigrationStep> v2 = new ArrayList<>();

        v2.add(sql(createPlayerAchievementsResetTable()));

        if (!isMySQL) {
            v2.add(sql("CREATE INDEX IF NOT EXISTS idx_ach_reset_player "
                    + "ON player_achievements_reset(player_id)"));
        }

        v2.add(sql("UPDATE db_version SET version = 2 WHERE id = 1"));
        migrations.add(v2);

        // ===== v2 → v3: stat_deltas (для точного отката) =====
        List<MigrationStep> v3 = new ArrayList<>();

        v3.add(sql(createStatDeltasTable()));

        if (!isMySQL) {
            v3.add(sql("CREATE INDEX IF NOT EXISTS idx_delta_player_stat "
                    + "ON stat_deltas(player_id, stat_type_id)"));
            v3.add(sql("CREATE INDEX IF NOT EXISTS idx_delta_timestamp "
                    + "ON stat_deltas(timestamp)"));
        }

        v3.add(sql("UPDATE db_version SET version = 3 WHERE id = 1"));
        migrations.add(v3);
    }

    /**
     * Обернуть SQL-строку в {@link MigrationStep}.
     */
    private MigrationStep sql(String sql) {
        return (conn, stmt, mysql) -> stmt.executeUpdate(sql);
    }

    // ==================== TABLE DEFINITIONS ====================

    private String createPlayersTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS players (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      uuid VARCHAR(36) UNIQUE,
                      name VARCHAR(16),
                      first_join DATETIME DEFAULT CURRENT_TIMESTAMP
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS players (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      uuid TEXT UNIQUE,
                      name TEXT,
                      first_join DATETIME DEFAULT CURRENT_TIMESTAMP
                  )
                  """;
    }

    private String createStatDeltasTable() {
        return isMySQL
                ? """
              CREATE TABLE IF NOT EXISTS stat_deltas (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  player_id INT NOT NULL,
                  stat_type_id INT NOT NULL,
                  delta INT NOT NULL,
                  timestamp BIGINT NOT NULL,
                  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                  FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE,
                  INDEX idx_delta_player_stat (player_id, stat_type_id),
                  INDEX idx_delta_timestamp (timestamp)
              ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
              """
                : """
              CREATE TABLE IF NOT EXISTS stat_deltas (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  player_id INTEGER NOT NULL,
                  stat_type_id INTEGER NOT NULL,
                  delta INTEGER NOT NULL,
                  timestamp INTEGER NOT NULL,
                  FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE,
                  FOREIGN KEY(stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
              )
              """;
    }

    private String createServersTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS servers (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(50) UNIQUE,
                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS servers (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT UNIQUE,
                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                  )
                  """;
    }

    private String createStatTypesTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS stat_types (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(50) UNIQUE
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS stat_types (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT UNIQUE
                  )
                  """;
    }

    private String createPlayerStatsTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS player_stats (
                      player_id INT NOT NULL,
                      server_id INT NOT NULL,
                      stat_type_id INT NOT NULL,
                      value_int INT DEFAULT 0,
                      value_double DECIMAL(20, 2) DEFAULT 0,
                      value_string VARCHAR(255),
                      PRIMARY KEY(player_id, server_id, stat_type_id),
                      FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                      FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                      FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
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
                  )
                  """;
    }

    private String createServerStatsTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS server_stats (
                      server_id INT NOT NULL,
                      stat_type_id INT NOT NULL,
                      value_int INT DEFAULT 0,
                      value_double DECIMAL(20, 2) DEFAULT 0,
                      value_string VARCHAR(255),
                      PRIMARY KEY(server_id, stat_type_id),
                      FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
                      FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS server_stats (
                      server_id INTEGER NOT NULL,
                      stat_type_id INTEGER NOT NULL,
                      value_int INTEGER DEFAULT 0,
                      value_double REAL DEFAULT 0,
                      value_string TEXT,
                      PRIMARY KEY(server_id, stat_type_id),
                      FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE CASCADE,
                      FOREIGN KEY(stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE
                  )
                  """;
    }

    private String createLeaderboardsTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS leaderboards (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      stat_type_id INT NOT NULL,
                      server_id INT DEFAULT NULL,
                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                      FOREIGN KEY (stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE,
                      FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE SET NULL
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS leaderboards (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      stat_type_id INTEGER NOT NULL,
                      server_id INTEGER DEFAULT NULL,
                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                      FOREIGN KEY(stat_type_id) REFERENCES stat_types(id) ON DELETE CASCADE,
                      FOREIGN KEY(server_id) REFERENCES servers(id) ON DELETE SET NULL
                  )
                  """;
    }

    private String createLeaderboardEntriesTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS leaderboard_entries (
                      leaderboard_id INT NOT NULL,
                      player_id INT NOT NULL,
                      rank INT NOT NULL,
                      value_int INT DEFAULT 0,
                      value_double DECIMAL(20, 2) DEFAULT 0,
                      PRIMARY KEY(leaderboard_id, player_id),
                      FOREIGN KEY (leaderboard_id) REFERENCES leaderboards(id) ON DELETE CASCADE,
                      FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS leaderboard_entries (
                      leaderboard_id INTEGER NOT NULL,
                      player_id INTEGER NOT NULL,
                      rank INTEGER NOT NULL,
                      value_int INTEGER DEFAULT 0,
                      value_double REAL DEFAULT 0,
                      PRIMARY KEY(leaderboard_id, player_id),
                      FOREIGN KEY(leaderboard_id) REFERENCES leaderboards(id) ON DELETE CASCADE,
                      FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE
                  )
                  """;
    }

    private String createStatHistoryTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS stat_history (
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      player_id INT NOT NULL,
                      stat_type_id INT NOT NULL,
                      server_id INT NOT NULL,
                      value INT NOT NULL,
                      date DATE NOT NULL,
                      UNIQUE KEY unique_snapshot (player_id, stat_type_id, server_id, date),
                      INDEX idx_player_stat (player_id, stat_type_id, server_id)
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS stat_history (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      player_id INTEGER NOT NULL,
                      stat_type_id INTEGER NOT NULL,
                      server_id INTEGER NOT NULL,
                      value INTEGER NOT NULL,
                      date DATE NOT NULL,
                      UNIQUE(player_id, stat_type_id, server_id, date)
                  )
                  """;
    }

    private String createPlayerAchievementsTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS player_achievements (
                      player_id INT NOT NULL,
                      achievement_id VARCHAR(64) NOT NULL,
                      unlocked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY(player_id, achievement_id),
                      FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                      INDEX idx_ach_player (player_id),
                      INDEX idx_ach_id (achievement_id)
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS player_achievements (
                      player_id INTEGER NOT NULL,
                      achievement_id TEXT NOT NULL,
                      unlocked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY(player_id, achievement_id),
                      FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE
                  )
                  """;
    }

    private String createPlayerMilestonesTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS player_milestones (
                      player_id INT NOT NULL,
                      milestone_key VARCHAR(128) NOT NULL,
                      awarded_at BIGINT NOT NULL,
                      PRIMARY KEY(player_id, milestone_key),
                      FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                      INDEX idx_ms_player (player_id),
                      INDEX idx_ms_key (milestone_key)
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS player_milestones (
                      player_id INTEGER NOT NULL,
                      milestone_key TEXT NOT NULL,
                      awarded_at INTEGER NOT NULL,
                      PRIMARY KEY(player_id, milestone_key),
                      FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE
                  )
                  """;
    }

    /**
     * Таблица для временных достижений (reset: daily/weekly/monthly).
     *
     * <p>Хранит timestamp последнего сброса. Если {@code now - reset_at >= interval},
     * достижение сбрасывается и игрок может получить его снова.</p>
     */
    private String createPlayerAchievementsResetTable() {
        return isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS player_achievements_reset (
                      player_id INT NOT NULL,
                      achievement_id VARCHAR(64) NOT NULL,
                      reset_at BIGINT NOT NULL,
                      PRIMARY KEY(player_id, achievement_id),
                      FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                      INDEX idx_reset_player (player_id)
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS player_achievements_reset (
                      player_id INTEGER NOT NULL,
                      achievement_id TEXT NOT NULL,
                      reset_at INTEGER NOT NULL,
                      PRIMARY KEY(player_id, achievement_id),
                      FOREIGN KEY(player_id) REFERENCES players(id) ON DELETE CASCADE
                  )
                  """;
    }

    // ==================== APPLY ====================

    /**
     * Применить миграции.
     *
     * @return true — если всё ок; false — если упало
     */
    private boolean migrate(Connection conn) {
        try {
            ensureVersionTable(conn);

            int currentVersion = readCurrentVersion(conn);

            if (currentVersion == CURRENT_DB_VERSION) {
                plugin.getLogger().info("БД актуальна (v" + currentVersion + ").");
                return true;
            }

            if (currentVersion > CURRENT_DB_VERSION) {
                plugin.getLogger().severe(
                        "БД новее плагина (db: v" + currentVersion
                                + ", плагин: v" + CURRENT_DB_VERSION + ")! "
                                + "Обновите плагин.");
                return false;
            }

            plugin.getLogger().info("Миграция БД: v" + currentVersion
                    + " → v" + CURRENT_DB_VERSION);

            // ===== АВТОБЭКАП ПЕРЕД МИГРАЦИЕЙ =====
            if (backupTask != null
                    && plugin.getConfigHandler().isBackupBeforeMigration()) {
                plugin.getLogger().info("Создание бэкапа БД перед миграцией...");
                boolean ok = backupTask.backupSync();

                if (!ok) {
                    plugin.getLogger().severe(
                            "Не удалось создать бэкап перед миграцией! "
                                    + "Отменяю миграцию (риск потери данных).");
                    return false;
                }
                plugin.getLogger().info("Бэкап создан. Начинаю миграцию.");
            }

            for (int v = currentVersion; v < CURRENT_DB_VERSION; v++) {
                applyMigration(conn, v);
            }

            plugin.getLogger().info("Миграция БД завершена. Версия: v"
                    + CURRENT_DB_VERSION);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка миграции БД", e);
            return false;
        }
    }

    /**
     * Проверить, УПАЛА ли миграция.
     *
     * <p>Обёртка над {@link #migrate(Connection)} для избежания
     * предупреждения {@code BooleanMethodIsAlwaysInverted}.</p>
     *
     * @return true — если миграция НЕ удалась
     */
    public boolean hasFailed(Connection conn) {
        return !migrate(conn);
    }

    /**
     * Создать таблицу {@code db_version}, если её нет, и записать 0.
     */
    private void ensureVersionTable(Connection conn) throws SQLException {
        String sql = isMySQL
                ? """
                  CREATE TABLE IF NOT EXISTS db_version (
                      id INT PRIMARY KEY,
                      version INT NOT NULL,
                      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  """
                : """
                  CREATE TABLE IF NOT EXISTS db_version (
                      id INTEGER PRIMARY KEY CHECK (id = 1),
                      version INTEGER NOT NULL,
                      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                  )
                  """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);

            // Если строки нет — создаём с версией 0
            String insert = isMySQL
                    ? "INSERT IGNORE INTO db_version (id, version) VALUES (1, 0)"
                    : "INSERT OR IGNORE INTO db_version (id, version) VALUES (1, 0)";
            stmt.executeUpdate(insert);
        }
    }

    /**
     * Прочитать текущую версию из таблицы.
     */
    private int readCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT version FROM db_version WHERE id = 1")) {
            if (rs.next()) {
                return rs.getInt("version");
            }
        }
        return 0;
    }

    /**
     * Применить одну миграцию (в транзакции).
     *
     * <p>Для DML (INSERT/UPDATE) — полный rollback при ошибке.
     * Для DDL в MySQL — неявный COMMIT, rollback невозможен.</p>
     */
    private void applyMigration(Connection conn, int fromVersion) throws SQLException {
        List<MigrationStep> steps = migrations.get(fromVersion);
        if (steps == null || steps.isEmpty()) {
            plugin.getLogger().warning("Нет миграции с v" + fromVersion
                    + " → v" + (fromVersion + 1));
            return;
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
        } catch (SQLException ignored) {
            // некоторые драйверы могут не поддерживать
        }

        try (Statement stmt = conn.createStatement()) {
            for (int i = 0; i < steps.size(); i++) {
                MigrationStep step = steps.get(i);
                try {
                    step.apply(conn, stmt, isMySQL);
                } catch (SQLException e) {
                    throw new SQLException("Шаг " + (i + 1)
                            + " миграции v" + fromVersion
                            + " → v" + (fromVersion + 1) + " упал: "
                            + e.getMessage(), e);
                }
            }

            conn.commit();
            plugin.getLogger().info("Миграция v" + fromVersion
                    + " → v" + (fromVersion + 1) + " применена.");

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // для DDL в MySQL rollback не сработает
            }

            throw new SQLException("Миграция v" + fromVersion
                    + " → v" + (fromVersion + 1)
                    + " откачена: " + e.getMessage(), e);
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }
}