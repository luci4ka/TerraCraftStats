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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ConfigHandler {

    private static final int CURRENT_CONFIG_VERSION = 4;

    private static volatile ConfigHandler instance;

    private final JavaPlugin plugin;
    private volatile FileConfiguration config;
    private volatile Map<String, String> groupColors;
    private volatile TrackedBlocks trackedBlocks;

    /**
     * Загруженные модули: fileName → ModuleConfig.
     */
    private final Map<String, ModuleConfig> moduleConfigs = new ConcurrentHashMap<>();

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
        // Очищаем модули
        moduleConfigs.clear();

        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }

        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        if (loaded.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("Конфиг пуст или повреждён: " + file.getAbsolutePath());
        }

        // ==================== ВАЛИДАЦИЯ ====================
        if (loaded instanceof YamlConfiguration yaml) {
            ConfigValidator.Result result = ConfigValidator.validateConfig(yaml, plugin);

            ConfigValidator.log(result, plugin.getLogger());

            if (!result.isValid()) {
                plugin.getLogger().severe(
                        "Конфиг содержит " + result.errors().size()
                                + " ошибок! Сохраняю как config.broken.yml и загружаю дефолтный.");

                try {
                    File broken = new File(plugin.getDataFolder(),
                            "config.broken-" + System.currentTimeMillis() + ".yml");
                    Files.copy(file.toPath(), broken.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Сломанный конфиг сохранён: " + broken.getName());
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Не удалось сохранить config.broken.yml", e);
                }

                plugin.saveResource("config.yml", true);
                loaded = YamlConfiguration.loadConfiguration(file);

                if (loaded instanceof YamlConfiguration yaml2) {
                    ConfigValidator.Result r2 = ConfigValidator.validateConfig(yaml2, plugin);
                    if (!r2.isValid()) {
                        plugin.getLogger().severe(
                                "Дефолтный config.yml ТОЖЕ невалиден! "
                                        + "Проверьте ресурсы плагина.");
                    }
                }
            }
        }

        this.config = loaded;

        // Миграция версий
        int version = config.getInt("config-version", 0);
        if (version < CURRENT_CONFIG_VERSION) {
            migrate(version, file);
        }

        this.groupColors = loadGroupColors();
        // tracking.blocks — новая структура
        this.trackedBlocks = new TrackedBlocks(config.getStringList("tracking.blocks"));

        // ==================== МОДУЛИ ====================
        loadModule("achievements.yml");
        loadModule("milestones.yml");
        loadModule("top-chips.yml");
        loadModule("top-notifications.yml");
        loadModule("sparkline.yml");
        loadModule("holograms.yml");
        loadModule("backup.yml");
        loadModule("api.yml");
        loadModule("discord.yml");
        loadModule("cosmetics.yml");
        // Добавляйте сюда новые модули

        applyDebugLevel();
    }

    /**
     * Загрузить модуль (если он включён).
     */
    private void loadModule(String fileName) {
        String moduleKey = fileName.replace(".yml", "");

        if (!isModuleEnabled(moduleKey)) {
            plugin.getLogger().info("Модуль '" + moduleKey + "' выключен в config.yml.");
            return;
        }

        try {
            ModuleConfig mc = new ModuleConfig(plugin, fileName);
            moduleConfigs.put(fileName, mc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось загрузить модуль " + fileName, e);
        }
    }

    /**
     * Миграция конфига через ConfigUpdater.
     */
    private void migrate(int fromVersion, File file) {
        plugin.getLogger().info("Миграция конфига с версии " + fromVersion
                + " до " + CURRENT_CONFIG_VERSION);

        makeBackup(file, fromVersion);

        if (fromVersion < 4) {
            plugin.getLogger().warning(
                    "Секции 'api', 'discord', 'backup', 'rainbow', 'festive' "
                            + "больше не используются в config.yml.");
            plugin.getLogger().warning(
                    "Перенесите настройки в api.yml, discord.yml, backup.yml, cosmetics.yml.");
            plugin.getLogger().warning(
                    "После этого удалите старые секции из config.yml.");
        }

        List<String> ignoredSections = List.of(
                "database",
                "multi-server",
                "groups",
                "server-names",
                "stats-click-commands",
                // Устаревшие (перенесены в модули) — игнорируем при обновлении,
                // чтобы ConfigUpdater не трогал пользовательские данные
                "api",
                "discord",
                "backup",
                "rainbow",
                "festive",
                "tracked-stats",
                "tracked-blocks",
                "mob-boss-keywords"
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

    // ==================== Модули ====================

    public boolean isModuleEnabled(String module) {
        return config.getBoolean("modules." + module, true);
    }

    public ModuleConfig getModule(String fileName) {
        return moduleConfigs.get(fileName);
    }

    public YamlConfiguration getModuleConfig(String fileName) {
        ModuleConfig mc = moduleConfigs.get(fileName);
        return mc != null ? mc.get() : new YamlConfiguration();
    }

    public void saveModule(String fileName) {
        ModuleConfig mc = moduleConfigs.get(fileName);
        if (mc != null) mc.save();
    }

    // ==================== Achievements ====================

    public org.bukkit.configuration.ConfigurationSection getAchievementsSection() {
        return getModuleConfig("achievements.yml").getConfigurationSection("achievements");
    }

    // ==================== Milestones ====================

    public org.bukkit.configuration.ConfigurationSection getMilestonesSection() {
        return getModuleConfig("milestones.yml").getConfigurationSection("milestones");
    }

    public boolean isMilestonesEnabled() {
        return getModuleConfig("milestones.yml").getBoolean("enabled", true);
    }

    // ==================== Top Chips ====================

    public boolean isTopChipsEnabled() {
        return isModuleEnabled("top-chips")
                && getModuleConfig("top-chips.yml").getBoolean("enabled", true);
    }

    public long getTopChipsRefreshSeconds() {
        return getModuleConfig("top-chips.yml").getLong("refresh-seconds", 300);
    }

    public String getTopChip(String category) {
        return getModuleConfig("top-chips.yml").getString("chips." + category, "");
    }

    public List<String> getTopChipsOrder() {
        List<String> order = getModuleConfig("top-chips.yml").getStringList("order");
        if (order.isEmpty()) {
            return List.of("kills", "mobs", "blocks", "playtime", "balance");
        }
        return order;
    }

    // ==================== Top Notifications ====================

    public boolean isTopNotificationsEnabled() {
        return isModuleEnabled("top-notifications")
                && getModuleConfig("top-notifications.yml").getBoolean("enabled", true);
    }

    public long getTopNotificationsCheckSeconds() {
        return getModuleConfig("top-notifications.yml").getLong("check-seconds", 300);
    }

    public boolean isTopNotificationsOnlyFirstPlace() {
        return getModuleConfig("top-notifications.yml").getBoolean("only-first-place", false);
    }

    public boolean isTopNotificationsSendToDiscord() {
        return getModuleConfig("top-notifications.yml").getBoolean("send-to-discord", true);
    }

    public boolean isTopNotificationCategoryEnabled(String category) {
        return getModuleConfig("top-notifications.yml")
                .getBoolean("categories." + category, true);
    }

    public String getTopNotificationMessage(String key) {
        return getModuleConfig("top-notifications.yml").getString("messages." + key, "");
    }

    public String getTopNotificationCategoryDisplay(String category) {
        return getModuleConfig("top-notifications.yml")
                .getString("messages.categories." + category, category);
    }

    // ==================== Sparkline ====================

    public boolean isSparklineEnabled() {
        return isModuleEnabled("sparkline")
                && getModuleConfig("sparkline.yml").getBoolean("enabled", true);
    }

    public int getSparklineDays() {
        return getModuleConfig("sparkline.yml").getInt("days", 7);
    }

    public boolean isSparklineShowInStats() {
        return getModuleConfig("sparkline.yml").getBoolean("show-in-stats", true);
    }

    public boolean isSparklineCategoryEnabled(String category) {
        return getModuleConfig("sparkline.yml")
                .getBoolean("categories." + category, true);
    }

    // ==================== Holograms ====================

    public boolean isHologramsEnabled() {
        return isModuleEnabled("holograms")
                && getModuleConfig("holograms.yml").getBoolean("enabled", true);
    }

    public long getHologramsUpdateIntervalSeconds() {
        return getModuleConfig("holograms.yml").getLong("update-interval-seconds", 300);
    }

    public long getHologramsInitialDelaySeconds() {
        return getModuleConfig("holograms.yml").getLong("initial-delay-seconds", 60);
    }

    public int getHologramsTopSize() {
        return getModuleConfig("holograms.yml").getInt("top-size", 10);
    }

    public String getHologramsEmptyLine() {
        return getModuleConfig("holograms.yml").getString("empty-line", "§8— — — — — —");
    }

    public org.bukkit.configuration.ConfigurationSection getHologramsListSection() {
        return getModuleConfig("holograms.yml").getConfigurationSection("list");
    }

    // ==================== BACKUP (backup.yml) ====================

    public boolean isBackupEnabled() {
        return isModuleEnabled("backup")
                && getModuleConfig("backup.yml").getBoolean("enabled", true);
    }

    public long getBackupIntervalHours() {
        return getModuleConfig("backup.yml").getLong("interval-hours", 6);
    }

    public long getBackupInitialDelayMinutes() {
        return getModuleConfig("backup.yml").getLong("initial-delay-minutes", 1);
    }

    public int getBackupKeepCount() {
        return getModuleConfig("backup.yml").getInt("keep-count", 14);
    }

    public String getBackupFolder() {
        return getModuleConfig("backup.yml").getString("folder", "backups");
    }

    public String getBackupFilenameFormat() {
        return getModuleConfig("backup.yml").getString("filename-format", "yyyy-MM-dd_HH-mm-ss");
    }

    public String getBackupMysqldumpPath() {
        return getModuleConfig("backup.yml").getString("mysqldump-path", "");
    }

    public boolean isBackupLogEach() {
        return getModuleConfig("backup.yml").getBoolean("log-each-backup", true);
    }

    public boolean isBackupBeforeMigration() {
        return getModuleConfig("backup.yml").getBoolean("before-migration", true);
    }

    public boolean isBackupGzip() {
        return getModuleConfig("backup.yml").getBoolean("gzip", true);
    }

    // ==================== API (api.yml) ====================

    public boolean isApiEnabled() {
        return isModuleEnabled("api")
                && getModuleConfig("api.yml").getBoolean("enabled", false);
    }

    public String getApiHost() {
        return getModuleConfig("api.yml").getString("host", "0.0.0.0");
    }

    public int getApiPort() {
        return getModuleConfig("api.yml").getInt("port", 8080);
    }

    public boolean isApiAutoGenerateKey() {
        return getModuleConfig("api.yml").getBoolean("auth.auto-generate-key", true);
    }

    public String getApiKey() {
        return getModuleConfig("api.yml").getString("auth.api-key", "");
    }

    public void setApiKey(String key) {
        ModuleConfig mc = getModule("api.yml");
        if (mc != null) {
            mc.get().set("auth.api-key", key);
            mc.save();
        }
    }

    public boolean isApiLogKeyOnStartup() {
        return getModuleConfig("api.yml").getBoolean("auth.log-key-on-startup", true);
    }

    public List<String> getApiPublicRoutes() {
        return getModuleConfig("api.yml").getStringList("auth.public-routes");
    }

    public String getApiAuthHeader() {
        return getModuleConfig("api.yml").getString("auth.header", "X-API-Key");
    }

    public String getApiAuthBearerPrefix() {
        return getModuleConfig("api.yml").getString("auth.bearer-prefix", "Bearer ");
    }

    public boolean isApiCorsEnabled() {
        return getModuleConfig("api.yml").getBoolean("cors.enabled", false);
    }

    public String getApiCorsAllowOrigin() {
        return getModuleConfig("api.yml").getString("cors.allow-origin", "*");
    }

    public String getApiCorsAllowMethods() {
        return getModuleConfig("api.yml").getString("cors.allow-methods", "GET, OPTIONS");
    }

    public String getApiCorsAllowHeaders() {
        return getModuleConfig("api.yml").getString("cors.allow-headers",
                "Content-Type, X-API-Key, Authorization");
    }

    public boolean isApiRateLimitEnabled() {
        return getModuleConfig("api.yml").getBoolean("rate-limit.enabled", true);
    }

    public int getApiRateLimitPerMinute() {
        return getModuleConfig("api.yml").getInt("rate-limit.requests-per-minute", 60);
    }

    // ==================== Discord (discord.yml) ====================

    public boolean isDiscordEnabled() {
        return isModuleEnabled("discord")
                && getModuleConfig("discord.yml").getBoolean("enabled", false);
    }

    public String getDiscordWebhookUrl() {
        return getModuleConfig("discord.yml").getString("webhook-url", "");
    }

    public String getDiscordUsername() {
        return getModuleConfig("discord.yml").getString("username", "PlayerStats");
    }

    public String getDiscordAvatarUrl() {
        return getModuleConfig("discord.yml").getString("avatar-url", "");
    }

    public long getDiscordRateLimitSeconds() {
        return getModuleConfig("discord.yml").getLong("rate-limit-seconds", 5);
    }

    public boolean isDiscordEventEnabled(String event) {
        return getModuleConfig("discord.yml").getBoolean("events." + event, false);
    }

    public boolean isDiscordUseTimestamp() {
        return getModuleConfig("discord.yml").getBoolean("use-timestamp", true);
    }

    public String getDiscordMention() {
        return getModuleConfig("discord.yml").getString("mention", "none");
    }

    // ==================== Cosmetics (cosmetics.yml) ====================

    public boolean isRainbowEnabled() {
        return isModuleEnabled("cosmetics")
                && getModuleConfig("cosmetics.yml").getBoolean("rainbow.enabled", false);
    }

    public List<String> getRainbowKeys() {
        return getModuleConfig("cosmetics.yml").getStringList("rainbow.apply-to");
    }

    public boolean isFestiveEnabled() {
        return isModuleEnabled("cosmetics")
                && getModuleConfig("cosmetics.yml").getBoolean("festive.enabled", false);
    }

    public List<String> getFestiveKeys() {
        return getModuleConfig("cosmetics.yml").getStringList("festive.apply-to");
    }

    // ==================== ROLLBACK (config.yml) ====================

    public int getRollbackLockSeconds() {
        return config.getInt("rollback.lock-seconds", 60);
    }

    public boolean isRollbackNotifyPlayer() {
        return config.getBoolean("rollback.notify-player", true);
    }

    public boolean isRollbackLogToConsole() {
        return config.getBoolean("rollback.log-to-console", true);
    }

    public boolean isRollbackTrackDeltas() {
        return config.getBoolean("rollback.track-deltas", false);
    }

    // ==================== Tracked Stats (tracking.*) ====================

    public boolean isTracked(String stat) {
        return config.getBoolean("tracking.stats." + stat, true);
    }

    public List<String> getTrackedBlockList() {
        return config.getStringList("tracking.blocks");
    }

    // ==================== Общие геттеры ====================

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

    // ==================== Боссы (tracking.mob-boss-keywords) ====================

    public List<String> getBossKeywords() {
        List<String> keywords = config.getStringList("tracking.mob-boss-keywords");
        return keywords != null ? keywords : Collections.emptyList();
    }

    // ==================== Язык ====================

    public String getLanguage() {
        return config.getString("language", "ru");
    }

    // ==================== Сохранение ====================

    public void save() throws IOException {
        config.save(new File(plugin.getDataFolder(), "config.yml"));
    }
}