package com.playerstats.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigHandler {

    private static volatile ConfigHandler instance;
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private Map<String, String> groupColors;

    // ==================== Конструктор ====================

    private ConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // ==================== Синглтон ====================

    /**
     * Получить экземпляр ConfigHandler.
     * Потокобезопасен.
     */
    public static synchronized ConfigHandler getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new ConfigHandler(plugin);
        }
        return instance;
    }

    /**
     * Перезагрузить конфиг из файла.
     */
    public static synchronized void reload(JavaPlugin plugin) {
        if (instance == null) {
            instance = new ConfigHandler(plugin);
        } else {
            instance.loadConfig();
        }
    }

    /**
     * Загрузить/перезагрузить конфигурацию из файла.
     */
    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.groupColors = loadGroupColors();
    }

    // ==================== Геттеры конфига ====================

    /**
     * Получить сырой FileConfiguration (для нестандартных полей).
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Версия конфига (для будущих миграций).
     */
    public int getConfigVersion() {
        return config.getInt("config-version", 1);
    }

    /**
     * Уровень отладки.
     * 0 = off, 1 = basic, 2 = verbose
     */
    public int getDebugLevel() {
        return config.getInt("debug-level", 1);
    }

    /**
     * Включена ли отправка статистики на другие серверы
     * (пока не используется, задел на будущее).
     */
    public boolean isStatSharingEnabled() {
        return config.getBoolean("enable-stat-sharing", false);
    }

    /**
     * Разрешать ли только один просмотр статистики за раз для игрока.
     * (пока не используется, задел на будущее).
     */
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
        return config.getString("sqlite.file", "plugins/PlayerStats/playerstats.db");
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
        // Возвращаем неизменяемую копию — защита от случайных изменений
        return Collections.unmodifiableMap(colors);
    }

    /**
     * Получить форматирование для группы из конфига.
     * Пример: getGroupFormat("vip", "§rИгрок") → "§6VIP"
     *
     * @param group        название группы (без учёта регистра)
     * @param defaultValue значение по умолчанию, если группа не найдена
     * @return форматированная строка с цветовыми кодами
     */
    public String getGroupFormat(String group, String defaultValue) {
        if (group == null) return defaultValue;
        return groupColors.getOrDefault(group.toLowerCase(), defaultValue);
    }

    // ==================== Мультисерверность ====================

    public boolean isMultiServerEnabled() {
        return config.getBoolean("multi-server.enabled", false);
    }

    public String getServerName() {
        return config.getString("multi-server.server-name", "default");
    }

    /**
     * Получить отображаемое имя сервера.
     * Пример: getServerDisplayName("survival") → "§aВыживание"
     *
     * @param key внутреннее имя сервера
     * @return отображаемое имя или key, если не найдено
     */
    public String getServerDisplayName(String key) {
        return config.getString("server-names." + key, key);
    }

    // ==================== Кликабельные команды ====================

    /**
     * Получить команду для клика по статистике.
     * Пример: getStatsClickCommand("blocks") → "/top blocks"
     *
     * @param statKey ключ статистики (blocks, pve, time, pvp, balance, group, server)
     * @return команда или "/say Not set", если не найдена
     */
    public String getStatsClickCommand(String statKey) {
        return config.getString("stats-click-commands." + statKey, "/say Not set");
    }

    // ==================== Боссы ====================

    /**
     * Ключевые слова для определения босс-мобов.
     * Моб считается боссом, если его кастомное имя содержит одно из этих слов.
     */
    public List<String> getBossKeywords() {
        return config.getStringList("mob-boss-keywords");
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
}