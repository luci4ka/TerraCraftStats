package com.playerstats.core.database;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Автоматические бэкапы БД.
 *
 * <p>SQLite — {@code VACUUM INTO} (консистентный бэкап, включая WAL).
 * MySQL — {@code mysqldump}. Оба — опционально gzip.</p>
 */
public class DatabaseBackupTask {

    private final Main plugin;
    private final ConfigHandler cfg;
    private final boolean isMySQL;

    private BukkitTask task;

    public DatabaseBackupTask(Main plugin, boolean isMySQL) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigHandler();
        this.isMySQL = isMySQL;
    }

    // ==================== ЗАПУСК / СТОП ====================

    public void start() {
        if (!cfg.isBackupEnabled()) {
            plugin.getLogger().info("Автобэкапы БД отключены в config.yml.");
            return;
        }

        if (task != null) return;

        long initialDelay = 20L * 60 * cfg.getBackupInitialDelayMinutes();
        long period = 20L * 3600 * cfg.getBackupIntervalHours();

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> backupAsync(null),
                initialDelay,
                period
        );

        plugin.getLogger().info("Автобэкапы БД включены: каждые "
                + cfg.getBackupIntervalHours() + " ч, "
                + "хранить " + cfg.getBackupKeepCount() + " шт."
                + (cfg.isBackupGzip() ? " (gzip)" : ""));
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
            task = null;
        }
    }

    // ==================== ОСНОВНАЯ ЛОГИКА ====================

    /**
     * Сделать один бэкап (async).
     *
     * @param callback вызывается в main-потоке с {@code true/false}
     */
    public void backupAsync(Consumer<Boolean> callback) {
        plugin.getThreadManager().runAsync(() -> {
            boolean ok;
            try {
                if (isMySQL) {
                    ok = backupMySQL();
                } else {
                    ok = backupSQLite();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка бэкапа", e);
                ok = false;
            }

            final boolean result = ok;
            if (callback != null) {
                plugin.getThreadManager().runSync(() -> callback.accept(result));
            }
        });
    }

    // ==================== SQLITE ====================

    /**
     * Бэкап SQLite через {@code VACUUM INTO} — консистентная копия,
     * включая незакоммиченные данные из WAL.
     *
     * <p>Fallback: {@code PRAGMA wal_checkpoint(TRUNCATE)} + {@code Files.copy}.</p>
     */
    private boolean backupSQLite() {
        Path backupDir = getBackupDir();
        Path tempDb = null;

        try {
            Files.createDirectories(backupDir);

            String timestamp = new SimpleDateFormat(cfg.getBackupFilenameFormat())
                    .format(new Date());
            boolean gzip = cfg.isBackupGzip();
            String ext = gzip ? ".db.gz" : ".db";
            Path backupFile = backupDir.resolve("playerstats_" + timestamp + ext);
            tempDb = backupDir.resolve("_temp_" + timestamp + ".db");

            boolean vacuumOk = false;
            try {
                try (Connection conn = plugin.getConnection().getConnection();
                     Statement stmt = conn.createStatement()) {
                    String path = tempDb.toAbsolutePath().toString().replace("'", "''");
                    stmt.execute("VACUUM INTO '" + path + "'");
                }
                vacuumOk = true;
            } catch (SQLException e) {
                plugin.getLogger().warning("VACUUM INTO не сработал ("
                        + e.getMessage() + "), использую fallback (wal_checkpoint + copy).");
            }

            if (vacuumOk) {
                if (gzip) {
                    try (InputStream in = Files.newInputStream(tempDb);
                         OutputStream out = new GZIPOutputStream(
                                 Files.newOutputStream(backupFile))) {
                        in.transferTo(out);
                    }
                    Files.deleteIfExists(tempDb);
                } else {
                    Files.move(tempDb, backupFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.deleteIfExists(tempDb);

                File dbFile = new File(plugin.getDataFolder(), cfg.getSQLiteFile());
                if (!dbFile.exists()) {
                    plugin.getLogger().warning("Бэкап: файл БД не найден: " + dbFile);
                    return false;
                }

                try (Connection conn = plugin.getConnection().getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                } catch (SQLException ignored) {}

                if (gzip) {
                    try (InputStream in = Files.newInputStream(dbFile.toPath());
                         OutputStream out = new GZIPOutputStream(
                                 Files.newOutputStream(backupFile))) {
                        in.transferTo(out);
                    }
                } else {
                    Files.copy(dbFile.toPath(), backupFile,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }

            if (cfg.isBackupLogEach()) {
                long size = Files.size(backupFile);
                plugin.getLogger().info("Бэкап БД создан: " + backupFile.getFileName()
                        + " (" + humanSize(size) + ")"
                        + (vacuumOk ? "" : " [fallback]"));
            }

            cleanOldBackups();
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка бэкапа SQLite", e);
            if (tempDb != null) {
                try { Files.deleteIfExists(tempDb); } catch (IOException ignored) {}
            }
            return false;
        }
    }

    // ==================== MYSQL ====================

    private boolean backupMySQL() {
        try {
            Path backupDir = getBackupDir();
            Files.createDirectories(backupDir);

            String timestamp = new SimpleDateFormat(cfg.getBackupFilenameFormat())
                    .format(new Date());
            boolean gzip = cfg.isBackupGzip();
            String ext = gzip ? ".sql.gz" : ".sql";
            Path backupFile = backupDir.resolve("playerstats_" + timestamp + ext);

            String mysqldump = cfg.getBackupMysqldumpPath();
            if (mysqldump == null || mysqldump.isBlank()) {
                mysqldump = "mysqldump";
            }

            ProcessBuilder pb = new ProcessBuilder(
                    mysqldump,
                    "-h", cfg.getMySQLHost(),
                    "-P", String.valueOf(cfg.getMySQLPort()),
                    "-u", cfg.getMySQLUsername(),
                    "--password=" + cfg.getMySQLPassword(),
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    cfg.getMySQLDatabase()
            );

            pb.redirectErrorStream(false);

            Process process = pb.start();

            StringBuilder err = new StringBuilder();
            Thread errReader = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        err.append(line).append('\n');
                    }
                } catch (IOException ignored) {}
            });
            errReader.setDaemon(true);
            errReader.start();

            if (gzip) {
                try (InputStream in = process.getInputStream();
                     OutputStream out = new GZIPOutputStream(
                             Files.newOutputStream(backupFile))) {
                    in.transferTo(out);
                }
            } else {
                try (InputStream in = process.getInputStream();
                     OutputStream out = Files.newOutputStream(backupFile)) {
                    in.transferTo(out);
                }
            }

            int exit = process.waitFor();
            errReader.join(2000);

            if (exit != 0) {
                plugin.getLogger().warning("mysqldump завершился с кодом "
                        + exit + ": " + err.toString().trim());
                Files.deleteIfExists(backupFile);
                return false;
            }

            if (cfg.isBackupLogEach()) {
                long size = Files.size(backupFile);
                plugin.getLogger().info("Бэкап MySQL создан: " + backupFile.getFileName()
                        + " (" + humanSize(size) + ")");
            }

            cleanOldBackups();
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка бэкапа MySQL", e);
            return false;
        }
    }

    // ==================== ОЧИСТКА ====================

    private void cleanOldBackups() {
        try {
            Path backupDir = getBackupDir();
            if (!Files.exists(backupDir)) return;

            int keep = cfg.getBackupKeepCount();
            if (keep <= 0) return;

            try (Stream<Path> stream = Files.list(backupDir)) {
                Path[] files = stream
                        .filter(Files::isRegularFile)
                        .filter(this::isBackupFile)
                        .sorted(Comparator.comparingLong((Path p) -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }).reversed())
                        .toArray(Path[]::new);

                if (files.length <= keep) return;

                for (int i = keep; i < files.length; i++) {
                    try {
                        Files.deleteIfExists(files[i]);
                        if (cfg.isBackupLogEach()) {
                            plugin.getLogger().info("Старый бэкап удалён: "
                                    + files[i].getFileName());
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("Не удалось удалить бэкап "
                                + files[i].getFileName() + ": " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка очистки бэкапов", e);
        }
    }

    private boolean isBackupFile(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        if (n.startsWith("_temp_")) return false;
        if (n.endsWith(".before-restore")) return false;
        return n.endsWith(".db") || n.endsWith(".db.gz")
                || n.endsWith(".sql") || n.endsWith(".sql.gz");
    }

    // ==================== ПУБЛИЧНОЕ API ДЛЯ КОМАНД ====================

    public List<Path> listBackups() {
        try {
            Path backupDir = getBackupDir();
            if (!Files.exists(backupDir)) return List.of();

            try (Stream<Path> stream = Files.list(backupDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(this::isBackupFile)
                        .sorted(Comparator.comparingLong((Path p) -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }).reversed())
                        .toList();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка чтения списка бэкапов", e);
            return List.of();
        }
    }

    public Path findBackup(String filename) {
        for (Path p : listBackups()) {
            if (p.getFileName().toString().equals(filename)) return p;
        }
        return null;
    }

    public boolean restore(String backupFile) {
        if (isMySQL) {
            plugin.getLogger().warning(
                    "Restore для MySQL не поддерживается автоматически. "
                            + "Используйте: mysql -u ... -p ... db < backup.sql");
            return false;
        }

        Path backup = findBackup(backupFile);
        if (backup == null) {
            plugin.getLogger().warning("Restore: бэкап не найден: " + backupFile);
            return false;
        }

        try {
            File dbFile = new File(plugin.getDataFolder(), cfg.getSQLiteFile());

            if (dbFile.exists()) {
                File safety = new File(dbFile.getParentFile(),
                        dbFile.getName() + ".before-restore");
                Files.copy(dbFile.toPath(), safety.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            String name = backup.getFileName().toString().toLowerCase();
            if (name.endsWith(".gz")) {
                try (InputStream in = new java.util.zip.GZIPInputStream(
                        Files.newInputStream(backup));
                     OutputStream out = Files.newOutputStream(dbFile.toPath())) {
                    in.transferTo(out);
                }
            } else {
                Files.copy(backup, dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            plugin.getLogger().info("БД восстановлена из: " + backupFile);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка restore", e);
            return false;
        }
    }

    // ==================== УТИЛИТЫ ====================

    private Path getBackupDir() {
        return new File(plugin.getDataFolder(), cfg.getBackupFolder()).toPath();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    public boolean backupSync() {
        try {
            if (isMySQL) {
                return backupMySQL();
            } else {
                return backupSQLite();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка синхронного бэкапа", e);
            return false;
        }
    }
}