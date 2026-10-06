package com.playerstats.core.listeners;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerDAO;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class JoinListener implements Listener {

    private final Main plugin;
    private final PlayerDAO playerDAO;
    private final boolean verboseDebug;

    private final ConcurrentHashMap<UUID, AtomicBoolean> pendingTasks = new ConcurrentHashMap<>();

    public JoinListener(Main plugin, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.verboseDebug = plugin.getConfigHandler().isVerboseDebug();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        AtomicBoolean flag = pendingTasks.computeIfAbsent(uuid, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] Задача сохранения для " + name
                        + " уже выполняется, пропуск.");
            }
            return;
        }

        if (verboseDebug) {
            plugin.getLogger().info("[DEBUG] Сохранение игрока при входе: " + name);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Без synchronized — HikariCP сам управляет соединениями
                playerDAO.getOrCreatePlayerId(uuid, name);

                if (verboseDebug) {
                    plugin.getLogger().info("[DEBUG] Игрок сохранён: " + name);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Не удалось сохранить игрока в БД: " + name + " (" + uuid + ")", e);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (event.getPlayer().isOnline()) {
                        event.getPlayer().kickPlayer(
                                "§cВнутренняя ошибка сервера.\nПожалуйста, попробуйте зайти позже."
                        );
                    }
                });
            } finally {
                flag.set(false);
            }
        });
    }

    /**
     * Очищаем карту и кэш плейсхолдеров при выходе игрока.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingTasks.remove(uuid);

        // Инвалидируем кэш плейсхолдеров при выходе
        if (plugin.getHooksManager() != null
                && plugin.getHooksManager().getPlaceholderAPIHook() != null) {
            plugin.getHooksManager().getPlaceholderAPIHook().invalidate(uuid);
        }

        if (verboseDebug) {
            plugin.getLogger().info("[DEBUG] pendingTasks и кэш плейсхолдеров очищены для "
                    + event.getPlayer().getName());
        }
    }
}