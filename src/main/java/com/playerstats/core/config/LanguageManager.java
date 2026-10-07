package com.playerstats.core.config;

import com.playerstats.Main;
import com.tchristofferson.configupdater.ConfigUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Мультиязычный менеджер.
 *
 * <p>Определяет язык клиента игрока и выдаёт сообщения из соответствующего
 * {@code lang/<locale>.yml}.</p>
 *
 * <p><b>Логика выбора языка:</b></p>
 * <ol>
 *   <li>Язык клиента есть в {@code lang/} → используем его.</li>
 *   <li>Язык клиента — из СНГ, но файла нет → {@code ru} (русский).</li>
 *   <li>Язык клиента — не из СНГ, и файла нет → {@code en} (английский).</li>
 *   <li>Fallback → {@code en}.</li>
 * </ol>
 */
public class LanguageManager {

    /** locale ("ru_RU") → (key → message) */
    private final Map<String, Map<String, String>> translations = new ConcurrentHashMap<>();

    /** Доступные коды языков ("ru", "en", "uk", ...) */
    private final Set<String> availableLocales = new HashSet<>();

    private final Main plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** Fallback, если язык не из СНГ и нет файла. */
    private static final String FALLBACK_EN = "en_US";

    /** Fallback, если язык из СНГ и нет файла. */
    private static final String FALLBACK_CIS = "ru_RU";

    /** Языки СНГ (коды языков без региона). */
    private static final Set<String> CIS_LANGS = Set.of(
            "ru", "uk", "be", "kk", "uz", "ky", "tg", "tk",
            "hy", "az", "ka", "mo", "ab", "os", "ce", "cv",
            "ba", "tt", "sah", "udm", "kv", "mhr", "myv"
    );

    /** Основной язык из config.yml (для консоли). */
    private String defaultLang;

    // Кэш rainbow/festive
    private Set<String> rainbowKeys = new HashSet<>();
    private Set<String> festiveKeys = new HashSet<>();
    private boolean rainbowEnabled = false;
    private boolean festiveEnabled = false;

    public LanguageManager(Main plugin) {
        this.plugin = plugin;
        load();
    }

    // ==================== ЗАГРУЗКА ====================

    public void load() {
        translations.clear();
        availableLocales.clear();

        this.defaultLang = plugin.getConfigHandler().getLanguage();

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        // 1. Извлекаем все ресурсы из jar
        saveDefaultLangs();

        // 2. Загружаем все .yml
        File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("Не найдено ни одного lang-файла!");
            return;
        }

        for (File file : files) {
            String code = file.getName().replace(".yml", "");
            String locale = normalizeLocale(code);

            // ConfigUpdater — добавляет новые ключи
            try {
                ConfigUpdater.update(plugin, "lang/" + code + ".yml", file,
                        Collections.emptyList());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Не удалось обновить lang/" + code + ".yml", e);
            }

            translations.put(locale, loadFile(file, code));
            availableLocales.add(code);
        }

        loadRainbowSettings();

        plugin.getLogger().info("Загружено языков: " + translations.size()
                + " (" + String.join(", ", availableLocales) + ")");
        plugin.getLogger().info("Язык по умолчанию: " + defaultLang
                + " (fallback: " + FALLBACK_EN + ", CIS fallback: " + FALLBACK_CIS + ")");
    }

    /**
     * Список языков, которые нужно извлечь из jar.
     */
    private void saveDefaultLangs() {
        String[] langs = {
                "ru", "en",            // основные
                "uk", "kk", "be", "uz", // СНГ
                "zh", "es"             // дополнительные
        };

        for (String lang : langs) {
            File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
            if (!file.exists()) {
                try {
                    plugin.saveResource("lang/" + lang + ".yml", false);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE,
                            "Ресурс lang/" + lang + ".yml не найден", e);
                }
            }
        }
    }

    /**
     * Загрузить lang-файл.
     */
    private Map<String, String> loadFile(File file, String code) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = plugin.getResource("lang/" + code + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
        }

        Map<String, String> map = new HashMap<>();
        for (String key : config.getKeys(true)) {
            Object val = config.get(key);
            if (val instanceof String s) {
                map.put(key, s);
            }
        }
        return map;
    }

    /**
     * Нормализация: "ru" → "ru_RU", "en" → "en_US", "zh" → "zh_CN", ...
     */
    private String normalizeLocale(String code) {
        if (code.contains("_")) return code;

        return switch (code.toLowerCase()) {
            case "ru" -> "ru_RU";
            case "en" -> "en_US";
            case "uk" -> "uk_UA";
            case "be" -> "be_BY";
            case "kk" -> "kk_KZ";
            case "uz" -> "uz_UZ";
            case "ky" -> "ky_KG";
            case "tg" -> "tg_TJ";
            case "tk" -> "tk_TM";
            case "hy" -> "hy_AM";
            case "az" -> "az_AZ";
            case "ka" -> "ka_GE";
            case "mo" -> "mo_MD";
            case "zh" -> "zh_CN";
            case "es" -> "es_ES";
            case "de" -> "de_DE";
            case "fr" -> "fr_FR";
            case "it" -> "it_IT";
            case "pt" -> "pt_PT";
            case "pl" -> "pl_PL";
            case "tr" -> "tr_TR";
            case "ja" -> "ja_JP";
            case "ko" -> "ko_KR";
            default -> code.toLowerCase() + "_" + code.toUpperCase();
        };
    }

    // ==================== ОПРЕДЕЛЕНИЕ ЯЗЫКА ====================

    /**
     * Получить locale игрока.
     *
     * <p>Логика:</p>
     * <ol>
     *   <li>Точное совпадение ("ru_RU").</li>
     *   <li>По языку ("ru" → "ru_*").</li>
     *   <li>Fallback: если язык из СНГ → ru_RU, иначе → en_US.</li>
     * </ol>
     */
    public String getPlayerLocale(Player player) {
        try {
            Locale locale = player.locale();
            String lang = locale.getLanguage();
            String code = lang + "_" + locale.getCountry();

            // 1. Точное совпадение
            if (translations.containsKey(code)) {
                return code;
            }

            // 2. Совпадение по языку
            for (String key : translations.keySet()) {
                if (key.startsWith(lang + "_")) {
                    return key;
                }
            }

            // 3. Fallback
            return getFallbackLocale(lang);
        } catch (Exception e) {
            return FALLBACK_EN;
        }
    }

    /**
     * Определить fallback-locale по языку.
     *
     * <p>Если язык из СНГ → ru_RU, иначе → en_US.</p>
     */
    private String getFallbackLocale(String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return FALLBACK_EN;
        }

        String lower = langCode.toLowerCase();

        // СНГ-язык?
        if (CIS_LANGS.contains(lower)) {
            return translations.containsKey(FALLBACK_CIS)
                    ? FALLBACK_CIS : FALLBACK_EN;
        }

        return FALLBACK_EN;
    }

    // ==================== ГЕТТЕРЫ ====================

    /**
     * Получить сырую строку для игрока (на его языке).
     */
    public String getRaw(Player player, String key) {
        return getRaw(getPlayerLocale(player), key);
    }

    /**
     * Получить сырую строку для locale.
     *
     * <p>Порядок fallback:</p>
     * <ol>
     *   <li>Указанный locale.</li>
     *   <li>Fallback по языку (СНГ → ru, иначе → en).</li>
     *   <li>en_US.</li>
     *   <li>defaultLang из config.yml.</li>
     * </ol>
     */
    public String getRaw(String locale, String key) {
        // 1. Точный язык
        Map<String, String> map = translations.get(locale);
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }

        // 2. Fallback по языку
        String lang = locale.contains("_")
                ? locale.substring(0, locale.indexOf("_")) : locale;
        String fallbackLocale = getFallbackLocale(lang);
        Map<String, String> fallback = translations.get(fallbackLocale);
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }

        // 3. en_US
        Map<String, String> enMap = translations.get(FALLBACK_EN);
        if (enMap != null && enMap.containsKey(key)) {
            return enMap.get(key);
        }

        // 4. defaultLang
        Map<String, String> defaultMap = translations.get(normalizeLocale(defaultLang));
        if (defaultMap != null && defaultMap.containsKey(key)) {
            return defaultMap.get(key);
        }

        return "<red>Missing key: " + key;
    }

    /**
     * Сырая строка для КОНСОЛИ (defaultLang).
     */
    public String getRaw(String key) {
        return getRaw(normalizeLocale(defaultLang), key);
    }

    /**
     * Строка с плейсхолдерами для игрока.
     */
    public String get(Player player, String key, Object... replacements) {
        return replace(getRaw(player, key), replacements);
    }

    /**
     * Строка с плейсхолдерами для консоли.
     */
    public String get(String key, Object... replacements) {
        return replace(getRaw(key), replacements);
    }

    /**
     * Component для игрока.
     */
    public Component getComponent(Player player, String key, Object... replacements) {
        String message = get(player, key, replacements);
        return miniMessage.deserialize(applyRainbow(key, message));
    }

    /**
     * Component для консоли.
     */
    public Component getComponent(String key, Object... replacements) {
        String message = get(key, replacements);
        return miniMessage.deserialize(applyRainbow(key, message));
    }

    /**
     * Component из готовой строки.
     */
    public Component getComponentFromString(String message, Object... replacements) {
        if (message == null || message.isEmpty()) return Component.empty();
        return miniMessage.deserialize(replace(message, replacements));
    }

    // ==================== УТИЛИТЫ ====================

    private String replace(String message, Object... replacements) {
        if (message == null) return "";

        for (int i = 0; i < replacements.length - 1; i += 2) {
            String name = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);
            message = message
                    .replace("{" + name + "}", value)
                    .replace("%" + name + "%", value);
        }
        return message;
    }

    private String applyRainbow(String key, String message) {
        if (rainbowEnabled && rainbowKeys.contains(key)) {
            return "<rainbow>" + message + "</rainbow>";
        }
        if (festiveEnabled && festiveKeys.contains(key)) {
            return "<gradient:red:green>" + message + "</gradient>";
        }
        return message;
    }

    private void loadRainbowSettings() {
        ConfigHandler config = plugin.getConfigHandler();
        this.rainbowEnabled = config.isRainbowEnabled();
        this.festiveEnabled = config.isFestiveEnabled();
        this.rainbowKeys = new HashSet<>(config.getRainbowKeys());
        this.festiveKeys = new HashSet<>(config.getFestiveKeys());
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public Set<String> getAvailableLocales() {
        return availableLocales;
    }
}