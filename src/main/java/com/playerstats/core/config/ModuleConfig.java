package com.playerstats.core.config;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.logging.Level;

/**
 * Обёртка над отдельным YAML-файлом модуля.
 *
 * <p>Автоматически:</p>
 * <ul>
 *   <li>Создаёт файл из jar-ресурса (если нет).</li>
 *   <li>Дополняет недостающие ключи из jar (через ConfigUpdater).</li>
 *   <li>Сохраняет комментарии.</li>
 * </ul>
 */
public class ModuleConfig {

    private final JavaPlugin plugin;
    private final String fileName;
    private YamlConfiguration config;
    private File file;

    public ModuleConfig(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        load();
    }

    /**
     * Загрузить (или перезагрузить) файл.
     */
    public void load() {
        this.file = new File(plugin.getDataFolder(), fileName);

        // 1. Создаём из ресурсов, если нет
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                plugin.saveResource(fileName, false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Не удалось создать " + fileName, e);
            }
        }

        // 2. Загружаем
        this.config = YamlConfiguration.loadConfiguration(file);

        // 3. Дополняем недостающие ключи из jar
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
        }

        // 4. ConfigUpdater (добавляет новые ключи, сохраняет комментарии)
        try {
            ConfigUpdater.update(plugin, fileName, file, Collections.emptyList());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось обновить " + fileName, e);
        }

        // 5. Сохраняем (чтобы новые ключи появились в файле)
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось сохранить " + fileName, e);
        }
    }

    /**
     * Получить YAML-конфиг.
     */
    public YamlConfiguration get() {
        return config;
    }

    /**
     * Сохранить изменения.
     */
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка сохранения " + fileName, e);
        }
    }

    /**
     * Получить имя файла.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Получить файл.
     */
    public File getFile() {
        return file;
    }
}