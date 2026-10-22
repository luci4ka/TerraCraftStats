package ru.terracraft.stats.core.listeners;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.database.dao.PlayerDAO;
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

    private final PlayerStatsPlugin plugin;
    private final PlayerDAO playerDAO;
    private final boolean verboseDebug;

    private final ConcurrentHashMap<UUID, AtomicBoolean> pendingTasks = new ConcurrentHashMap<>();

    public JoinListener(PlayerStatsPlugin plugin, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.verboseDebug = plugin.getConfigManager().main().debugLevel() >= 2;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        AtomicBoolean flag = pendingTasks.computeIfAbsent(uuid, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) return;

        plugin.getThreadManager().runAsync(() -> {
            try {
                playerDAO.getOrCreatePlayerId(uuid, name);
                if (verboseDebug) {
                    plugin.getLogger().info("[DEBUG] Join: playerId resolved for " + name);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Не удалось сохранить игрока в БД: " + name + " (" + uuid + ")", e);
                plugin.getThreadManager().runSync(() -> {
                    if (event.getPlayer().isOnline()) {
                        event.getPlayer().kickPlayer(
                                "§cВнутренняя ошибка сервера.\nПожалуйста, попробуйте зайти позже.");
                    }
                });
            } finally {
                flag.set(false);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingTasks.remove(uuid);

        if (plugin.getHooksManager() != null
                && plugin.getHooksManager().getPlaceholderAPIHook() != null) {
            plugin.getHooksManager().getPlaceholderAPIHook().invalidate(uuid);
        }
    }
}