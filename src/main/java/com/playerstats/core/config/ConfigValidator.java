package com.playerstats.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Проверка конфигов при загрузке.
 *
 * <p>Возвращает список ошибок. Если список пуст — конфиг валиден.
 * Накапливаем ВСЕ ошибки, а не падаем на первой.</p>
 */
public final class ConfigValidator {

    /** Результат валидации. */
    public record Result(List<String> errors, List<String> warnings) {
        public boolean isValid() { return errors.isEmpty(); }
    }

    private ConfigValidator() {}

    // ==================== config.yml ====================

    public static Result validateConfig(YamlConfiguration cfg, JavaPlugin plugin) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ===== config-version =====
        int version = cfg.getInt("config-version", 0);
        if (version <= 0) {
            errors.add("config-version отсутствует или <= 0 (найдено: " + version + ")");
        }

        // ===== debug-level =====
        int debug = cfg.getInt("debug-level", 1);
        if (debug < 0 || debug > 2) {
            errors.add("debug-level должен быть 0, 1 или 2 (найдено: " + debug + ")");
        }

        // ===== language =====
        String lang = cfg.getString("language", "ru");
        if (lang == null || lang.isBlank()) {
            errors.add("language пустой");
        } else {
            File langFile = new File(plugin.getDataFolder(),
                    "lang" + File.separator + lang + ".yml");
            if (!langFile.exists()) {
                warnings.add("Язык '" + lang + "' не найден (lang/" + lang + ".yml). "
                        + "Будет использован 'ru'.");
                cfg.set("language", "ru");
            }
        }

        // ===== database.type =====
        String dbType = cfg.getString("database.type", "sqlite");
        if (dbType == null || (!dbType.equalsIgnoreCase("sqlite")
                && !dbType.equalsIgnoreCase("mysql"))) {
            errors.add("database.type должен быть 'sqlite' или 'mysql' (найдено: "
                    + dbType + ")");
        }

        // ===== database.port =====
        int port = cfg.getInt("database.port", 3306);
        if (port < 1 || port > 65535) {
            errors.add("database.port должен быть 1..65535 (найдено: " + port + ")");
        }

        // ===== database.name / user =====
        if ("mysql".equalsIgnoreCase(dbType)) {
            String name = cfg.getString("database.name", "");
            if (name.isBlank()) {
                errors.add("database.name пустой (обязателен для MySQL)");
            }
            String user = cfg.getString("database.user", "");
            if (user.isBlank()) {
                errors.add("database.user пустой (обязателен для MySQL)");
            }
        }

        // ===== sqlite.file =====
        String sqliteFile = cfg.getString("sqlite.file", "playerstats.db");
        if (sqliteFile == null || sqliteFile.isBlank()) {
            errors.add("sqlite.file пустой");
        } else if (!sqliteFile.endsWith(".db")) {
            warnings.add("sqlite.file не заканчивается на '.db' (найдено: "
                    + sqliteFile + "). Возможны проблемы.");
        }

        // ===== api =====
        boolean apiEnabled = cfg.getBoolean("api.enabled", false);
        int apiPort = cfg.getInt("api.port", 8080);
        if (apiPort < 1 || apiPort > 65535) {
            errors.add("api.port должен быть 1..65535 (найдено: " + apiPort + ")");
        }
        if (apiEnabled) {
            String host = cfg.getString("api.host", "");
            if (host.isBlank()) {
                warnings.add("api.host пустой — будет использован '0.0.0.0'");
            }
        }

        // ===== multi-server =====
        if (!cfg.isBoolean("multi-server.enabled")) {
            errors.add("multi-server.enabled должен быть boolean");
        }
        if (cfg.getBoolean("multi-server.enabled", false)) {
            String sname = cfg.getString("multi-server.server-name", "");
            if (sname.isBlank()) {
                errors.add("multi-server.enabled=true, но server-name пустой");
            }
        }

        // ===== backup =====
        if (cfg.isConfigurationSection("backup")) {
            long interval = cfg.getLong("backup.interval-hours", 6);
            if (interval <= 0) {
                errors.add("backup.interval-hours должен быть > 0 (найдено: "
                        + interval + ")");
            }
            int keep = cfg.getInt("backup.keep-count", 14);
            if (keep < 1) {
                errors.add("backup.keep-count должен быть >= 1 (найдено: " + keep + ")");
            }
        }

        // ===== rollback =====
        if (cfg.isConfigurationSection("rollback")) {
            int lock = cfg.getInt("rollback.lock-seconds", 60);
            if (lock < 0) {
                errors.add("rollback.lock-seconds не может быть отрицательным ("
                        + lock + ")");
            }
        }

        // ===== groups =====
        if (!cfg.isConfigurationSection("groups")) {
            warnings.add("Секция 'groups' отсутствует — будут использоваться дефолтные цвета");
        }

        // ===== modules =====
        if (!cfg.isConfigurationSection("modules")) {
            errors.add("Секция 'modules' отсутствует");
        } else {
            for (String key : cfg.getConfigurationSection("modules").getKeys(false)) {
                if (!cfg.isBoolean("modules." + key)) {
                    errors.add("modules." + key + " должен быть boolean");
                }
            }
        }

        // ===== stats-click-commands =====
        if (!cfg.isConfigurationSection("stats-click-commands")) {
            warnings.add("Секция 'stats-click-commands' отсутствует — "
                    + "команды по клику не будут работать");
        }

        // ===== tracked-blocks =====
        if (!cfg.isList("tracked-blocks")) {
            errors.add("tracked-blocks должен быть списком");
        } else {
            List<String> blocks = cfg.getStringList("tracked-blocks");
            for (String b : blocks) {
                if (!b.matches("[a-z_]+")) {
                    warnings.add("tracked-blocks: '" + b
                            + "' не похож на Material (ожидается lowercase_snake_case)");
                }
            }
        }

        // ===== mob-boss-keywords =====
        if (!cfg.isList("mob-boss-keywords")) {
            warnings.add("mob-boss-keywords должен быть списком");
        }

        return new Result(errors, warnings);
    }

    // ==================== lang.yml ====================

    public static Result validateLang(YamlConfiguration lang, JavaPlugin plugin) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ===== Обязательные ключи =====
        String[] required = {
                "prefix",
                "no-permission",
                "player-not-found",
                "error",
                "commands.descriptions.stats",
                "commands.descriptions.top",
                "stats.title-self",
                "stats.title-other",
                "top.header",
                "top.footer",
                "achievements.title",
                "milestones"
        };

        for (String key : required) {
            if (!lang.contains(key)) {
                warnings.add("lang.yml: отсутствует ключ '" + key + "'");
            }
        }

        // ===== Проверка, что все list.* достижений в lang =====
        YamlConfiguration achievements = null;
        File achFile = new File(plugin.getDataFolder(), "achievements.yml");
        if (achFile.exists()) {
            achievements = YamlConfiguration.loadConfiguration(achFile);
        }

        if (achievements != null && achievements.isConfigurationSection("achievements")) {
            var sec = achievements.getConfigurationSection("achievements");
            for (String id : sec.getKeys(false)) {
                if (!sec.isConfigurationSection(id)) continue;
                String langKey = "achievements.list." + id + ".name";
                if (!lang.contains(langKey)) {
                    warnings.add("lang.yml: нет перевода для достижения '" + id + "'");
                }
            }
        }

        return new Result(errors, warnings);
    }

    // ==================== Логирование ====================

    public static void log(Result result, Logger logger) {
        for (String err : result.errors()) {
            logger.severe("[CONFIG] ✗ " + err);
        }
        for (String warn : result.warnings()) {
            logger.warning("[CONFIG] ⚠ " + warn);
        }
    }
}