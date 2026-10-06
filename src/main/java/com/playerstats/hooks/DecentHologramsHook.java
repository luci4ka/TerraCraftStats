package com.playerstats.hooks;

import com.playerstats.Main;
import com.playerstats.core.database.dao.LeaderboardDAO;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DecentHologramsHook implements Hook {

    private final Main plugin;
    private boolean enabled = false;

    public DecentHologramsHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            plugin.getLogger().info("DecentHolograms не обнаружен — интеграция отключена.");
            return;
        }

        try {
            this.enabled = true;
            plugin.getLogger().info("DecentHolograms успешно подключен.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подключиться к DecentHolograms.", e);
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getPluginName() {
        return "DecentHolograms";
    }

    /**
     * Создать голограмму с топ-5 игроков по статистике.
     * Если голограмма уже существует — обновляет её строки.
     */
    public void createTopHologram(String hologramName, Location location, String statName, String title) {
        if (!isEnabled()) return;

        // Проверяем, существует ли голограмма
        Hologram existing = DHAPI.getHologram(hologramName);
        if (existing != null) {
            // Обновляем существующую — безопасно и без мерцания
            updateHologramLines(existing, statName, title);
            return;
        }

        // Создаём новую
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.add("§7Загрузка...");

        Hologram hologram = DHAPI.createHologram(hologramName, location, lines);

        // Обновляем содержимое асинхронно
        updateHologramLines(hologram, statName, title);
    }

    /**
     * Обновить строки существующей голограммы через DHAPI.
     * Это правильный способ — без удаления и создания заново.
     */
    private void updateHologramLines(Hologram hologram, String statName, String title) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                LeaderboardDAO leaderboardDAO = new LeaderboardDAO(
                        plugin,                    // ← ДОБАВЛЕН plugin
                        plugin.getConnection(),
                        plugin.getStatTypeDAO()
                );

                List<LeaderboardEntry> top = leaderboardDAO.getTop(
                        statName, plugin.getServerId(), 5);

                List<String> lines = new ArrayList<>();
                lines.add(title);

                int rank = 1;
                for (LeaderboardEntry entry : top) {
                    String color = switch (rank) {
                        case 1 -> "§6";
                        case 2 -> "§7";
                        case 3 -> "§c";
                        default -> "§f";
                    };
                    lines.add(color + "#" + rank + " " + entry.playerName() + " §8- §f" +
                            formatValue(entry.value(), statName));
                    rank++;
                }

                if (top.isEmpty()) {
                    lines.add("§7Нет данных");
                }

                // Обновляем строки в главном потоке
                plugin.getThreadManager().runSync(() -> {
                    // Проверяем, что голограмма ещё существует
                    Hologram current = DHAPI.getHologram(hologram.getName());
                    if (current == null) return;

                    // Правильный способ обновления — setHologramLines
                    DHAPI.setHologramLines(current, lines);
                });

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка обновления голограммы " + hologram.getName(), e);
            }
        });
    }

    /**
     * Форматирование названия статистики для заголовка голограммы.
     */
    private String formatStatName(String statName) {
        return switch (statName) {
            case "play_time" -> "по времени";
            case "mobs_killed" -> "по мобам";
            case "bosses_killed" -> "по боссам";
            case "blocks_placed" -> "по блокам";
            case "blocks_broken" -> "по сломанным блокам";
            case "kills" -> "по убийствам";
            case "deaths" -> "по смертям";
            case "balance" -> "по балансу";
            default -> statName;
        };
    }

    private String formatValue(double value, String statName) {
        return switch (statName) {
            case "play_time" -> {
                int hours = (int) value / 3600;
                int minutes = ((int) value % 3600) / 60;
                yield hours + "ч " + minutes + "м";
            }
            case "balance" -> String.format("%.0f₽", value);
            default -> String.valueOf((int) value);
        };
    }
}