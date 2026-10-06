package com.playerstats.core.config;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ConfigHandler {

    private static final int CURRENT_CONFIG_VERSION = 3;

    private static volatile ConfigHandler instance;

    private final JavaPlugin plugin;
    private volatile FileConfiguration config;
    private volatile Map<String, String> groupColors;
    private volatile TrackedBlocks trackedBlocks;

    // ==================== Конструктор ====================

    private ConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // ==================== Синглтон ====================

    public static synchronized ConfigHandler getInstance(JavaPlugin plugin) {
        if (instance == null || !instance.plugin.equals(plugin)) {
            instance = new ConfigHandler(plugin);
        }
        return instance;
    }

    public static synchronized void reload(JavaPlugin plugin) {
        if (instance == null || !instance.plugin.equals(plugin)) {
            instance = new ConfigHandler(plugin);
        } else {
            instance.loadConfig();
        }
    }

    public static synchronized void reset() {
        instance = null;
    }

    // ==================== Загрузка / Миграция ====================

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }

        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        if (loaded.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("Конфиг пуст или повреждён: " + file.getAbsolutePath());
        }

        this.config = loaded;

        // Миграция версий
        int version = config.getInt("config-version", 0);
        if (version < CURRENT_CONFIG_VERSION) {
            migrate(version, file);
        }

        this.groupColors = loadGroupColors();
        this.trackedBlocks = new TrackedBlocks(config.getStringList("tracked-blocks"));

        applyDebugLevel();
    }

    /**
     * Миграция конфига через ConfigUpdater.
     * Сохраняет комментарии и значения пользователя, добавляет новые ключи.
     * <p>
     * ВАЖНО: в ignoredSections можно передавать ТОЛЬКО секции (database, api и т.д.),
     * но НЕ одиночные ключи (например, "language" — это ключ, а не секция).
     */
    private void migrate(int fromVersion, File file) {
        plugin.getLogger().info("Миграция конфига с версии " + fromVersion
                + " до " + CURRENT_CONFIG_VERSION);

        makeBackup(file, fromVersion);

        // Список ТОЛЬКО ConfigurationSection:
        List<String> ignoredSections = List.of(
                "database",
                "api",
                "multi-server",
                "groups",
                "server-names",
                "stats-click-commands"
        );

        try {
            ConfigUpdater.update(plugin, "config.yml", file, ignoredSections);

            config.set("config-version", CURRENT_CONFIG_VERSION);
            config.save(file);

            plugin.getLogger().info("Конфиг успешно мигрирован до версии "
                    + CURRENT_CONFIG_VERSION);

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось мигрировать конфиг через ConfigUpdater", e);
        }
    }

    /**
     * Создать бэкап старого конфига.
     */
    private void makeBackup(File file, int oldVersion) {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            String timestamp = String.valueOf(System.currentTimeMillis());
            File backupFile = new File(backupDir,
                    "config-v" + oldVersion + "-" + timestamp + ".yml");

            Files.copy(file.toPath(), backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("Создан бэкап конфига: " + backupFile.getName());

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось создать бэкап конфига", e);
        }
    }

    private void applyDebugLevel() {
        int level = getDebugLevel();
        if (level <= 0) {
            plugin.getLogger().setLevel(Level.WARNING);
        } else if (level == 1) {
            plugin.getLogger().setLevel(Level.INFO);
        } else {
            plugin.getLogger().setLevel(Level.ALL);
        }
    }

    // ==================== Геттеры конфига ====================

    public FileConfiguration getConfig() {
        return config;
    }

    public int getConfigVersion() {
        return config.getInt("config-version", CURRENT_CONFIG_VERSION);
    }

    public int getDebugLevel() {
        return config.getInt("debug-level", 1);
    }

    public boolean isDebugEnabled() {
        return getDebugLevel() > 0;
    }

    public boolean isVerboseDebug() {
        return getDebugLevel() >= 2;
    }

    public boolean isRainbowEnabled() {
        return config.getBoolean("rainbow.enabled", false);
    }

    public java.util.List<String> getRainbowKeys() {
        return config.getStringList("rainbow.apply-to");
    }

    public boolean isFestiveEnabled() {
        return config.getBoolean("festive.enabled", false);
    }

    public boolean isPvpPerVictimStats() {
        return config.getBoolean("pvp.per-victim-stats", true);
    }

    public long getPvpAntifarmCooldownSeconds() {
        return config.getLong("pvp.antifarm-cooldown-seconds", 300);
    }

    public boolean isPvpIgnoreExcludedVictims() {
        return config.getBoolean("pvp.ignore-excluded-victims", true);
    }

    public int getPvpDailyLimitPerVictim() {
        return config.getInt("pvp.daily-limit-per-victim", 0);
    }

    public java.util.List<String> getFestiveKeys() {
        return config.getStringList("festive.apply-to");
    }

    public boolean isStatSharingEnabled() {
        return config.getBoolean("enable-stat-sharing", false);
    }

    public boolean isOnlyOneLookupAtATime() {
        return config.getBoolean("only-allow-one-lookup-at-a-time-per-player", true);
    }

    // ==================== База данных ====================

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getMySQLHost() {
        return config.getString("database.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.name", "playerstats");
    }

    public String getMySQLUsername() {
        return config.getString("database.user", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.password", "password");
    }

    public String getSQLiteFile() {
        return config.getString("sqlite.file", "playerstats.db");
    }

    // ==================== Группы ====================

    private Map<String, String> loadGroupColors() {
        Map<String, String> colors = new HashMap<>();
        if (config.isConfigurationSection("groups")) {
            for (String key : config.getConfigurationSection("groups").getKeys(false)) {
                String value = config.getString("groups." + key);
                if (value != null) {
                    colors.put(key.toLowerCase(), value);
                }
            }
        }
        return Collections.unmodifiableMap(colors);
    }

    public String getGroupFormat(String group, String defaultValue) {
        if (group == null) return defaultValue;
        return groupColors.getOrDefault(group.toLowerCase(), defaultValue);
    }

    public TrackedBlocks getTrackedBlocks() {
        return trackedBlocks;
    }

    // ==================== Мультисерверность ====================

    public boolean isMultiServerEnabled() {
        return config.getBoolean("multi-server.enabled", false);
    }

    public String getServerName() {
        return config.getString("multi-server.server-name", "default");
    }

    public String getServerDisplayName(String key) {
        return config.getString("server-names." + key, key);
    }

    // ==================== Кликабельные команды ====================

    public String getStatsClickCommand(String statKey) {
        return config.getString("stats-click-commands." + statKey, null);
    }

    // ==================== Боссы ====================

    public java.util.List<String> getBossKeywords() {
        java.util.List<String> keywords = config.getStringList("mob-boss-keywords");
        return keywords != null ? keywords : Collections.emptyList();
    }

    // ==================== HTTP API ====================

    public boolean isApiEnabled() {
        return config.getBoolean("api.enabled", false);
    }

    public String getApiHost() {
        return config.getString("api.host", "0.0.0.0");
    }

    public int getApiPort() {
        return config.getInt("api.port", 8080);
    }

    // ==================== Язык ====================

    public String getLanguage() {
        return config.getString("language", "ru");
    }
}