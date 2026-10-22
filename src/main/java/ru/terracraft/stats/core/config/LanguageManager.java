package ru.terracraft.stats.core.config;

import ru.terracraft.stats.PlayerStatsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.regex.Pattern;

public class LanguageManager {

    public static final String MISSING_KEY_MARKER = "<red>Missing key: ";

    private static final Pattern LEGACY_CODE_PATTERN =
            Pattern.compile(".*&[0-9a-fk-orA-FK-OR].*", Pattern.DOTALL);

    private final Map<String, Map<String, String>> translations = new ConcurrentHashMap<>();
    private final Set<String> availableLocales = new HashSet<>();

    private final PlayerStatsPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static final String FALLBACK_EN = "en_US";
    private static final String FALLBACK_CIS = "ru_RU";

    private static final Set<String> CIS_LANGS = Set.of(
            "ru", "uk", "be", "kk", "uz", "ky", "tg", "tk",
            "hy", "az", "ka", "mo", "ab", "os", "ce", "cv",
            "ba", "tt", "sah", "udm", "kv", "mhr", "myv"
    );

    public enum Format { AUTO, MINIMESSAGE, LEGACY, JSON }

    private String defaultLang;
    private Format format = Format.AUTO;

    private Set<String> rainbowKeys = new HashSet<>();
    private Set<String> festiveKeys = new HashSet<>();
    private boolean rainbowEnabled = false;
    private boolean festiveEnabled = false;

    public LanguageManager(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        translations.clear();
        availableLocales.clear();

        ConfigManager cm = plugin.getConfigManager();
        this.defaultLang = cm.main().language();
        this.format = parseFormat(cm.main().lang().format());

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        saveDefaultLangs();

        File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("Не найдено ни одного lang-файла!");
            return;
        }

        for (File file : files) {
            String code = file.getName().replace(".yml", "");
            String locale = normalizeLocale(code);

            translations.put(locale, loadFile(file, code));
            availableLocales.add(code);
        }

        loadRainbowSettings();

        plugin.getLogger().info("Загружено языков: " + translations.size()
                + " (" + String.join(", ", availableLocales) + ")");
        plugin.getLogger().info("Язык по умолчанию: " + defaultLang
                + ", формат сообщений: " + format
                + " (fallback: " + FALLBACK_EN + ", CIS fallback: " + FALLBACK_CIS + ")");
    }

    private Format parseFormat(String raw) {
        if (raw == null) return Format.AUTO;
        return switch (raw.toLowerCase()) {
            case "minimessage", "mm" -> Format.MINIMESSAGE;
            case "legacy", "ampersand", "&" -> Format.LEGACY;
            case "json", "gson" -> Format.JSON;
            default -> Format.AUTO;
        };
    }

    private void saveDefaultLangs() {
        String[] langs = {"ru", "en", "uk", "kk", "be", "uz", "zh", "es"};

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

    private Map<String, String> loadFile(File file, String code) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = plugin.getResource("lang/" + code + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Не удалось сохранить lang/" + code + ".yml", e);
            }
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

    public String getPlayerLocale(Player player) {
        try {
            Locale locale = player.locale();
            String lang = locale.getLanguage();
            String code = lang + "_" + locale.getCountry();

            if (translations.containsKey(code)) {
                return code;
            }

            for (String key : translations.keySet()) {
                if (key.startsWith(lang + "_")) {
                    return key;
                }
            }

            return getFallbackLocale(lang);
        } catch (Exception e) {
            return FALLBACK_EN;
        }
    }

    private String getFallbackLocale(String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return FALLBACK_EN;
        }

        String lower = langCode.toLowerCase();

        if (CIS_LANGS.contains(lower)) {
            return translations.containsKey(FALLBACK_CIS)
                    ? FALLBACK_CIS : FALLBACK_EN;
        }

        return FALLBACK_EN;
    }

    public boolean hasKey(Player player, String key) {
        if (player == null) return hasKey(key);
        return hasKey(getPlayerLocale(player), key);
    }

    public boolean hasKey(String locale, String key) {
        Map<String, String> map = translations.get(locale);
        if (map != null && map.containsKey(key)) return true;

        String lang = locale.contains("_")
                ? locale.substring(0, locale.indexOf("_")) : locale;
        String fallbackLocale = getFallbackLocale(lang);
        Map<String, String> fallback = translations.get(fallbackLocale);
        if (fallback != null && fallback.containsKey(key)) return true;

        Map<String, String> enMap = translations.get(FALLBACK_EN);
        if (enMap != null && enMap.containsKey(key)) return true;

        Map<String, String> defaultMap = translations.get(normalizeLocale(defaultLang));
        return defaultMap != null && defaultMap.containsKey(key);
    }

    public boolean hasKey(String key) {
        return hasKey(normalizeLocale(defaultLang), key);
    }

    public String getRaw(Player player, String key) {
        return getRaw(getPlayerLocale(player), key);
    }

    public String getRaw(String locale, String key) {
        Map<String, String> map = translations.get(locale);
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }

        String lang = locale.contains("_")
                ? locale.substring(0, locale.indexOf("_")) : locale;
        String fallbackLocale = getFallbackLocale(lang);
        Map<String, String> fallback = translations.get(fallbackLocale);
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }

        Map<String, String> enMap = translations.get(FALLBACK_EN);
        if (enMap != null && enMap.containsKey(key)) {
            return enMap.get(key);
        }

        Map<String, String> defaultMap = translations.get(normalizeLocale(defaultLang));
        if (defaultMap != null && defaultMap.containsKey(key)) {
            return defaultMap.get(key);
        }

        return MISSING_KEY_MARKER + key;
    }

    public String getRaw(String key) {
        return getRaw(normalizeLocale(defaultLang), key);
    }

    public String get(Player player, String key, Object... replacements) {
        return replace(getRaw(player, key), replacements);
    }

    public String get(String key, Object... replacements) {
        return replace(getRaw(key), replacements);
    }

    public Component getComponent(Player player, String key, Object... replacements) {
        String message = get(player, key, replacements);
        return toComponent(applyRainbow(key, message));
    }

    public Component getComponent(String key, Object... replacements) {
        String message = get(key, replacements);
        return toComponent(applyRainbow(key, message));
    }

    public Component getComponentFromString(String message, Object... replacements) {
        if (message == null || message.isEmpty()) return Component.empty();
        return toComponent(replace(message, replacements));
    }

    public Component toComponent(String message) {
        if (message == null || message.isEmpty()) return Component.empty();

        Format effective = format;
        if (format == Format.AUTO) {
            effective = detectFormat(message);
        }

        try {
            return switch (effective) {
                case JSON -> JSONComponentSerializer.json().deserialize(message);
                case LEGACY -> LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(message.replace('§', '&'));
                case MINIMESSAGE, AUTO -> miniMessage.deserialize(message);
            };
        } catch (Exception e) {
            return Component.text(message);
        }
    }

    private Format detectFormat(String message) {
        String trimmed = message.trim();

        if (trimmed.startsWith("{")) return Format.JSON;
        if (trimmed.indexOf('§') >= 0) return Format.LEGACY;
        if (trimmed.indexOf('&') >= 0 && LEGACY_CODE_PATTERN.matcher(trimmed).matches())
            return Format.LEGACY;
        return Format.MINIMESSAGE;
    }

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
        ConfigManager cm = plugin.getConfigManager();
        var cosmetics = cm.cosmetics();
        if (cosmetics == null) {
            rainbowEnabled = false;
            festiveEnabled = false;
            rainbowKeys = new HashSet<>();
            festiveKeys = new HashSet<>();
            return;
        }
        this.rainbowEnabled = cm.main().modules().cosmetics() && cosmetics.rainbow().enabled();
        this.festiveEnabled = cm.main().modules().cosmetics() && cosmetics.festive().enabled();
        this.rainbowKeys = new HashSet<>(cosmetics.rainbow().applyTo());
        this.festiveKeys = new HashSet<>(cosmetics.festive().applyTo());
    }

    public String getDefaultLang() { return defaultLang; }
    public Set<String> getAvailableLocales() { return availableLocales; }
    public Format getFormat() { return format; }
}