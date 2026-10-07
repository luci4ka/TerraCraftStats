package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * Управляет голограммами топов через DecentHolograms.
 *
 * <p>⚠ Если DecentHolograms НЕ установлен — все методы становятся no-op.</p>
 */
public class HologramManager {

    private final Main plugin;
    private final Map<String, Object> holograms = new HashMap<>();  // ← Object, не Hologram

    private static final int TOP_SIZE = 10;

    /**
     * Установлен ли DecentHolograms.
     */
    private final boolean enabled;

    public HologramManager(Main plugin) {
        this.plugin = plugin;

        // ==================== ПРОВЕРКА ====================
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            plugin.getLogger().warning(
                    "DecentHolograms не установлен — голограммы отключены.");
            this.enabled = false;
            return;
        }

        this.enabled = true;
        startUpdater();
        plugin.getLogger().info("HologramManager инициализирован (DecentHolograms найден).");
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ==================== КРОН ====================

    private void startUpdater() {
        if (!plugin.getConfigHandler().isHologramsEnabled()) {
            plugin.getLogger().info("Holograms отключены в config.yml.");
            return;
        }

        plugin.getThreadManager().runAsyncRepeating(() -> {
            for (Map.Entry<String, Object> entry : holograms.entrySet()) {
                try {
                    updateHologram(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Ошибка обновления голограммы " + entry.getKey(), e);
                }
            }
        }, 10, 60);
    }

    // ==================== CRUD ====================

    /**
     * Создать голограмму для статистики.
     */
    public void createHologram(String statName, Location location) {
        if (!enabled) return;

        try {
            String holoName = "playerstats_" + statName;

            // Удаляем старую
            Object existing = getHologramByName(holoName);
            if (existing != null) {
                removeHologramByName(holoName);
            }

            // Создаём новую
            Object holo = createHologramByName(holoName, location);
            holograms.put(statName, holo);
            updateHologram(statName, holo);

            plugin.getLogger().info("Голограмма создана: " + statName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Ошибка создания голограммы " + statName, e);
        }
    }

    /**
     * Удалить голограмму.
     */
    public void removeHologram(String statName) {
        if (!enabled) return;

        Object holo = holograms.remove(statName);
        if (holo != null) {
            removeHologramByName("playerstats_" + statName);
        }
    }

    /**
     * Обновить содержимое голограммы.
     */
    private void updateHologram(String statName, Object holo) {
        if (!enabled) return;

        List<LeaderboardEntry> top;
        try {
            top = plugin.getLeaderboardDAO()
                    .getTop(statName, plugin.getServerId(), TOP_SIZE);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка загрузки топа для голограммы " + statName, e);
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("§6§l✦ " + formatStatName(statName) + " ✦");
        lines.add("");

        if (top.isEmpty()) {
            lines.add("§7Нет данных");
        } else {
            for (int i = 0; i < top.size(); i++) {
                LeaderboardEntry e = top.get(i);
                String color = switch (i) {
                    case 0 -> "§6";
                    case 1 -> "§7";
                    case 2 -> "§c";
                    default -> "§f";
                };
                lines.add(color + "#" + (i + 1) + " §f" + e.playerName()
                        + " §7— §e" + formatValue(e.value(), statName));
            }
        }

        setHologramLines(holo, lines);
    }

    // ==================== РЕФЛЕКСИЯ (DHAPI) ====================

    /**
     * Получить голограмму по имени через рефлексию.
     */
    private Object getHologramByName(String name) {
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            return dhapiClass.getMethod("getHologram", String.class).invoke(null, name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Создать голограмму через рефлексию.
     */
    private Object createHologramByName(String name, Location location) {
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            return dhapiClass.getMethod("createHologram",
                            String.class, Location.class, boolean.class)
                    .invoke(null, name, location, true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка создания голограммы", e);
            return null;
        }
    }

    /**
     * Удалить голограмму через рефлексию.
     */
    private void removeHologramByName(String name) {
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            dhapiClass.getMethod("removeHologram", String.class).invoke(null, name);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка удаления голограммы", e);
        }
    }

    /**
     * Установить строки голограммы через рефлексию.
     */
    private void setHologramLines(Object hologram, List<String> lines) {
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            Class<?> hologramClass = Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram");

            dhapiClass.getMethod("setHologramLines", hologramClass, List.class)
                    .invoke(null, hologram, lines);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка обновления строк голограммы", e);
        }
    }

    // ==================== ЗАГРУЗКА / СОХРАНЕНИЕ ====================

    /**
     * Загрузить голограммы из конфига.
     */
    public void loadFromConfig() {
        if (!enabled) return;

        var section = plugin.getConfigHandler().getConfig()
                .getConfigurationSection("holograms.positions");
        if (section == null) return;

        for (String statName : section.getKeys(false)) {
            try {
                String world = section.getString(statName + ".world");
                double x = section.getDouble(statName + ".x");
                double y = section.getDouble(statName + ".y");
                double z = section.getDouble(statName + ".z");

                var worldObj = plugin.getServer().getWorld(world);
                if (worldObj == null) {
                    plugin.getLogger().warning("Мир не найден для голограммы "
                            + statName + ": " + world);
                    continue;
                }

                Location loc = new Location(worldObj, x, y, z);
                createHologram(statName, loc);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка загрузки голограммы " + statName, e);
            }
        }
    }

    /**
     * Сохранить голограмму в конфиг.
     */
    public void saveToConfig(String statName, Location loc) {
        String path = "holograms.positions." + statName;
        var config = plugin.getConfigHandler().getConfig();
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());

        try {
            plugin.getConfigHandler().save();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка сохранения конфига", e);
        }
    }

    // ==================== УТИЛИТЫ ====================

    private String formatValue(double value, String statName) {
        if (statName.equals("play_time")) {
            int sec = (int) value;
            int h = sec / 3600;
            int m = (sec % 3600) / 60;
            return h + "ч " + m + "м";
        }
        if (statName.equals("balance")) {
            return String.format("%,.2f₽", value);
        }
        return String.format("%,d", (long) value);
    }

    private String formatStatName(String stat) {
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

    public Set<String> getHologramNames() {
        return holograms.keySet();
    }

    public void clear() {
        holograms.clear();
    }
}