package com.playerstats.core.config;

import com.playerstats.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import com.tchristofferson.configupdater.ConfigUpdater;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class LanguageManager {

    private final Main plugin;
    private final Map<String, String> messages = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private String currentLang;

    // Кэш ключей для rainbow/festive (чтобы не читать конфиг каждый раз)
    private Set<String> rainbowKeys = new HashSet<>();
    private Set<String> festiveKeys = new HashSet<>();
    private boolean rainbowEnabled = false;
    private boolean festiveEnabled = false;

    public LanguageManager(Main plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Загрузить язык из конфига.
     */
    public void load() {
        messages.clear();

        String lang = plugin.getConfigHandler().getLanguage();
        this.currentLang = lang;

        File langFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");

        // Если файла нет — создаём из ресурсов
        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            saveDefaultLang(lang);
        }

        // ==================== ОБНОВЛЕНИЕ ФАЙЛА ====================
        // ConfigUpdater добавляет недостающие ключи из jar-ресурса,
        // сохраняет комментарии и НЕ трогает существующие значения.
        try {
            ConfigUpdater.update(plugin, "lang/" + lang + ".yml", langFile,
                    java.util.Collections.emptyList());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось обновить lang-файл: " + lang, e);
        }
        // ============================================================

        YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);

        // Дополняем недостающие ключи из дефолтного (на случай, если ConfigUpdater не сработал)
        InputStream defaultStream = plugin.getResource("lang/" + lang + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
            config.options().copyDefaults(true);
        }

        // Загружаем все ключи в память
        for (String key : config.getKeys(true)) {
            Object value = config.get(key);
            if (value instanceof String str) {
                messages.put(key, str);
            }
        }

        // Загружаем настройки rainbow/festive
        loadRainbowSettings();

        plugin.getLogger().info("Загружен язык: " + lang + " (" + messages.size() + " строк)");
        if (rainbowEnabled) {
            plugin.getLogger().info("Rainbow mode: ВКЛ (" + rainbowKeys.size() + " ключей)");
        }
        if (festiveEnabled) {
            plugin.getLogger().info("Festive mode: ВКЛ (" + festiveKeys.size() + " ключей)");
        }
    }

    /**
     * Загрузить настройки rainbow/festive из конфига.
     */
    private void loadRainbowSettings() {
        ConfigHandler config = plugin.getConfigHandler();

        this.rainbowEnabled = config.isRainbowEnabled();
        this.festiveEnabled = config.isFestiveEnabled();

        this.rainbowKeys = new HashSet<>(config.getRainbowKeys());
        this.festiveKeys = new HashSet<>(config.getFestiveKeys());
    }

    /**
     * Сохранить дефолтный lang-файл из JAR.
     */
    private void saveDefaultLang(String lang) {
        try {
            if (plugin.getResource("lang/" + lang + ".yml") != null) {
                plugin.saveResource("lang/" + lang + ".yml", false);
            } else {
                // Fallback: используем en.yml, но НЕ переименовываем
                plugin.saveResource("lang/en.yml", false);
                File enFile = new File(plugin.getDataFolder(), "lang/en.yml");
                File targetFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
                java.nio.file.Files.copy(enFile.toPath(), targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить lang-файл: " + lang, e);
        }
    }

    /**
     * Получить сырую строку по ключу.
     */
    public String getRaw(String key) {
        return messages.getOrDefault(key, "<red>Missing key: " + key);
    }

    /**
     * Получить строку с заменой плейсхолдеров.
     * Плейсхолдеры: {player}, {value} и т.д.
     */
    public String get(String key, Object... replacements) {
        String message = getRaw(key);

        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = "{" + replacements[i] + "}";
            String value = String.valueOf(replacements[i + 1]);
            message = message.replace(placeholder, value);
        }

        return message;
    }

    /**
     * Получить Component (MiniMessage) с применением rainbow/festive.
     */
    public Component getComponent(String key, Object... replacements) {
        String message = get(key, replacements);

        // Применяем rainbow/festive
        if (rainbowEnabled && rainbowKeys.contains(key)) {
            message = "<rainbow>" + message + "</rainbow>";
        } else if (festiveEnabled && festiveKeys.contains(key)) {
            message = "<gradient:red:green>" + message + "</gradient>";
        }

        return miniMessage.deserialize(message);
    }

    public String getCurrentLang() {
        return currentLang;
    }

    /**
     * Проверить, включён ли rainbow для ключа.
     */
    public boolean isRainbow(String key) {
        return rainbowEnabled && rainbowKeys.contains(key);
    }

    /**
     * Проверить, включён ли festive для ключа.
     */
    public boolean isFestive(String key) {
        return festiveEnabled && festiveKeys.contains(key);
    }
}