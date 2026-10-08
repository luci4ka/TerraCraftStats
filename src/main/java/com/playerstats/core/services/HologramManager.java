package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.ModuleConfig;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import com.playerstats.core.utils.FormatUtil;
import com.playerstats.hooks.DecentHologramsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Логика голограмм: топ-N, форматирование, авто-обновление.
 *
 * <p>Все настройки — в {@code holograms.yml} (модуль).</p>
 * <p>Тексты — в MiniMessage ({@code <gold>}, {@code <gradient:...>}).</p>
 * <p>API-вызовы делегируются в {@link DecentHologramsHook}.
 * Если DH не установлен — все методы no-op.</p>
 */
public class HologramManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final Main plugin;

    /** name (из holograms.yml list.*) → statName (kills, play_time, ...). */
    private final Map<String, String> holograms = new HashMap<>();
    /** name → заголовок (MiniMessage). */
    private final Map<String, String> titles = new HashMap<>();

    /** Размер топа и пустая строка — из конфига. */
    private int topSize = 10;
    private String emptyLine = "<dark_gray>— — — — — —";

    private long updateIntervalTicks = 20L * 300; // 5 мин
    private long initialDelayTicks = 20L * 60;    // 1 мин

    private BukkitTask updateTask;

    public HologramManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        ConfigHandler cfg = plugin.getConfigHandler();
        if (cfg == null || !cfg.isHologramsEnabled()) return false;

        return plugin.getHooksManager() != null
                && plugin.getHooksManager().getDecentHologramsHook() != null
                && plugin.getHooksManager().getDecentHologramsHook().isEnabled();
    }

    private DecentHologramsHook dh() {
        return plugin.getHooksManager().getDecentHologramsHook();
    }

    // ==================== ЗАГРУЗКА ИЗ holograms.yml ====================

    /**
     * Загрузить список голограмм из {@code holograms.yml} (секция {@code list.*}).
     *
     * <p>Каждая запись:
     * <pre>
     * list:
     *   top-kills:
     *     enabled: true
     *     stat: kills
     *     title: "&lt;gold&gt;&lt;bold&gt;★ ТОП УБИЙСТВ ★"
     *     location: { world: world, x: 100.5, y: 65.0, z: 200.5 }
     * </pre></p>
     */
    public void loadFromConfig() {
        if (!isEnabled()) return;

        ConfigHandler cfg = plugin.getConfigHandler();

        this.updateIntervalTicks = 20L * cfg.getHologramsUpdateIntervalSeconds();
        this.initialDelayTicks  = 20L * cfg.getHologramsInitialDelaySeconds();
        this.topSize             = Math.max(1, cfg.getHologramsTopSize());
        this.emptyLine           = cfg.getHologramsEmptyLine();

        ConfigurationSection list = cfg.getHologramsListSection();
        if (list == null) {
            plugin.getLogger().warning("holograms.yml: секция 'list' пуста — голограммы не созданы.");
            return;
        }

        for (String name : list.getKeys(false)) {
            ConfigurationSection h = list.getConfigurationSection(name);
            if (h == null) continue;

            if (!h.getBoolean("enabled", true)) continue;

            String statName = h.getString("stat");
            if (statName == null || statName.isEmpty()) {
                plugin.getLogger().warning("Голограмма '" + name + "': нет stat — пропуск.");
                continue;
            }

            String title = h.getString("title", defaultTitle(statName));

            Location loc = readLocation(h.getConfigurationSection("location"));
            if (loc == null) {
                plugin.getLogger().warning("Голограмма '" + name + "': плохая location — пропуск.");
                continue;
            }

            createOrUpdate(name, statName, loc, title);
        }

        plugin.getLogger().info("Голограммы: загружено " + holograms.size() + " шт.");
    }

    private Location readLocation(ConfigurationSection s) {
        if (s == null) return null;
        String worldName = s.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Мир не найден: " + worldName);
            return null;
        }
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
    }

    // ==================== CRUD ====================

    /**
     * Создать или обновить голограмму (без сохранения в конфиг).
     */
    public void createOrUpdate(String name, String statName, Location location, String title) {
        if (!isEnabled()) return;

        String holoName = "playerstats_" + name;
        holograms.put(name, statName);
        titles.put(name, title);

        if (!dh().exists(holoName)) {
            List<String> placeholder = new ArrayList<>(topSize + 1);
            placeholder.add(toLegacy(title));
            for (int i = 0; i < topSize; i++) placeholder.add(toLegacy(emptyLine));
            dh().create(holoName, location, placeholder);
        }

        refresh(name);
    }

    /**
     * Удалить голограмму (из DH + памяти, БЕЗ конфига).
     */
    public void removeHologram(String name) {
        if (!isEnabled()) return;
        holograms.remove(name);
        titles.remove(name);
        dh().delete("playerstats_" + name);
    }

    // ==================== УПРАВЛЕНИЕ ИЗ КОМАНД ====================

    /**
     * Создать голограмму для статистики + сохранить в {@code holograms.yml}.
     *
     * <p>Имя голограммы: {@code top-<statName>}.</p>
     *
     * @param statName статистика (например, {@code kills})
     * @param location где поставить
     * @return имя созданной голограммы или {@code null}, если disabled
     */
    public String createHologram(String statName, Location location) {
        if (!isEnabled()) return null;

        String name = "top-" + statName;
        String title = defaultTitle(statName);

        // 1. Создаём/обновляем в DH + памяти
        createOrUpdate(name, statName, location, title);

        // 2. Сохраняем в holograms.yml
        saveToConfig(name, statName, location, title);

        return name;
    }

    /**
     * Удалить голограмму (по имени) — из DH, памяти и {@code holograms.yml}.
     */
    public void removeHologramAndSave(String name) {
        if (!isEnabled()) return;

        removeHologram(name);
        removeFromConfig(name);
    }

    /**
     * Legacy: удалить по statName (ищет стандартное имя {@code top-<stat>}).
     */
    public void removeHologramByStat(String statName) {
        removeHologramAndSave("top-" + statName);
    }

    // ==================== СОХРАНЕНИЕ В holograms.yml ====================

    private void saveToConfig(String name, String statName, Location location, String title) {
        try {
            ModuleConfig moduleCfg = plugin.getConfigHandler().getModule("holograms.yml");
            if (moduleCfg == null) {
                plugin.getLogger().warning(
                        "Не удалось сохранить голограмму: модуль 'holograms' не загружен.");
                return;
            }

            YamlConfiguration cfg = moduleCfg.get();
            String path = "list." + name;

            cfg.set(path + ".enabled", true);
            cfg.set(path + ".stat", statName);
            cfg.set(path + ".title", title);
            cfg.set(path + ".location.world", location.getWorld().getName());
            cfg.set(path + ".location.x", location.getX());
            cfg.set(path + ".location.y", location.getY());
            cfg.set(path + ".location.z", location.getZ());

            moduleCfg.save();

            plugin.getLogger().info("Голограмма сохранена в holograms.yml: " + name);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка сохранения голограммы в holograms.yml: " + name, e);
        }
    }

    private void removeFromConfig(String name) {
        try {
            ModuleConfig moduleCfg = plugin.getConfigHandler().getModule("holograms.yml");
            if (moduleCfg == null) return;

            YamlConfiguration cfg = moduleCfg.get();
            String path = "list." + name;

            if (cfg.contains(path)) {
                cfg.set(path, null);
                moduleCfg.save();
                plugin.getLogger().info("Голограмма удалена из holograms.yml: " + name);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка удаления голограммы из holograms.yml: " + name, e);
        }
    }

    // ==================== АВТО-ОБНОВЛЕНИЕ ====================

    public void startAutoUpdate() {
        if (!isEnabled()) return;
        if (updateTask != null) return;

        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::refreshAll,
                initialDelayTicks,
                updateIntervalTicks
        );
        plugin.getLogger().info("Голограммы: авто-обновление каждые "
                + (updateIntervalTicks / 20) + " сек.");
    }

    private void refreshAll() {
        if (holograms.isEmpty()) return;
        for (String name : new ArrayList<>(holograms.keySet())) {
            try {
                refresh(name);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка обновления голограммы '" + name + "'", e);
            }
        }
    }

    /**
     * Перестроить строки одной голограммы.
     */
    public void refresh(String name) {
        if (!isEnabled()) return;

        String statName = holograms.get(name);
        if (statName == null) return;

        String holoName = "playerstats_" + name;
        String title = titles.getOrDefault(name, defaultTitle(statName));

        List<LeaderboardEntry> top;
        try {
            top = plugin.getLeaderboardDAO()
                    .getTop(statName, plugin.getServerId(), topSize);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка загрузки топа для '" + name + "' (" + statName + ")", e);
            return;
        }

        List<String> lines = new ArrayList<>(topSize + 2);
        lines.add(toLegacy(title));
        lines.add("");

        for (int rank = 1; rank <= topSize; rank++) {
            if (top == null || rank > top.size()) {
                lines.add(toLegacy(emptyLine));
                continue;
            }
            LeaderboardEntry e = top.get(rank - 1);

            String color = switch (rank) {
                case 1 -> "<gold>";
                case 2 -> "<gray>";
                case 3 -> "<red>";
                default -> "<white>";
            };

            String line = color + "#" + rank + " <white>" + e.playerName()
                    + " <dark_gray>— <yellow>" + formatValue(e.value(), statName);

            lines.add(toLegacy(line));
        }

        dh().setLines(holoName, lines);
    }

    // ==================== УТИЛИТЫ ====================

    /**
     * MiniMessage → legacy {@code §}-строка (для DecentHolograms).
     */
    private String toLegacy(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return "";
        try {
            Component comp = MM.deserialize(miniMessage);
            return LEGACY.serialize(comp);
        } catch (Exception e) {
            // fallback — если сломанный MiniMessage, вернуть как есть
            return miniMessage;
        }
    }

    /** Имена всех активных голограмм (ID из конфига). */
    public Set<String> getHologramNames() {
        return holograms.keySet();
    }

    /**
     * Полная очистка: отменяет таск и удаляет все голограммы.
     */
    public void clear() {
        if (updateTask != null) {
            try {
                updateTask.cancel();
            } catch (Exception ignored) {}
            updateTask = null;
        }

        if (isEnabled()) {
            for (String name : new ArrayList<>(holograms.keySet())) {
                dh().delete("playerstats_" + name);
            }
        }
        holograms.clear();
        titles.clear();
    }

    private String formatValue(double value, String statName) {
        return switch (statName) {
            case "play_time" -> FormatUtil.formatTimeShort((int) value);
            case "balance" -> FormatUtil.formatBalancePlain(value);
            default -> FormatUtil.formatNumber((long) value);
        };
    }

    /**
     * Заголовок по умолчанию (MiniMessage).
     */
    private String defaultTitle(String statName) {
        return "<gold><bold>✦ " + formatStatName(statName) + " ✦";
    }

    /**
     * Название статы — из lang.yml ({@code holograms.stats.<stat>}) с fallback.
     */
    private String formatStatName(String stat) {
        // 1. Попытка из lang.yml
        String key = "holograms.stats." + stat;
        String fromLang = plugin.getLanguageManager().getRaw(key);

        if (fromLang != null && !fromLang.startsWith("<red>Missing key")) {
            return fromLang;
        }

        // 2. Fallback — хардкод
        return switch (stat) {
            case "play_time" -> "Время игры";
            case "kills" -> "Убийства";
            case "mobs_killed" -> "Мобы";
            case "bosses_killed" -> "Боссы";
            case "blocks_placed" -> "Поставлено блоков";
            case "blocks_broken" -> "Сломано блоков";
            case "balance" -> "Баланс";
            case "deaths" -> "Смерти";
            case "killstreak" -> "Серия убийств";
            default -> stat;
        };
    }
}