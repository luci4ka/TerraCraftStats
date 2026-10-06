package com.playerstats.core.listeners;

import com.gamingmesh.jobs.api.JobsLevelUpEvent;
import com.gamingmesh.jobs.api.JobsPaymentEvent;
import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.multithreading.ThreadManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Слушатель событий Jobs Reborn.
 */
public class JobsListener implements Listener {

    private final Main plugin;
    private final PlayerDAO playerDAO;
    private final PlayerStatsDAO statsDAO;
    private final ThreadManager threadManager;
    private final int serverId;
    private final boolean verboseDebug;

    public JobsListener(Main plugin,
                        PlayerDAO playerDAO,
                        PlayerStatsDAO statsDAO,
                        ThreadManager threadManager) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.statsDAO = statsDAO;
        this.threadManager = threadManager;
        this.serverId = plugin.getServerId();
        this.verboseDebug = plugin.getConfigHandler().isVerboseDebug();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPayment(JobsPaymentEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String name = player.getName();
        double amount = event.get(com.gamingmesh.jobs.container.CurrencyType.MONEY);

        if (amount <= 0) return;

        if (verboseDebug) {
            plugin.getLogger().info("[DEBUG] Jobs payment: " + name + " получил " + amount);
        }

        threadManager.runAsync(() -> {
            try {
                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                // Атомарный инкремент вместо SELECT + SET
                statsDAO.incrementStatDouble(playerId, serverId, "jobs_earned", amount);
                statsDAO.incrementStat(playerId, serverId, "jobs_payments", 1);

                if (plugin.getHooksManager() != null
                        && plugin.getHooksManager().getPlaceholderAPIHook() != null) {
                    plugin.getHooksManager().getPlaceholderAPIHook().invalidate(uuid);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Ошибка записи заработка Jobs для " + name, e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelUp(JobsLevelUpEvent event) {
        if (event.getPlayer() == null) return;

        Player player = event.getPlayer().getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        String name = player.getName();

        if (event.getJob() == null) return;
        String jobName = event.getJob().getName();
        int newLevel = event.getLevel();

        if (verboseDebug) {
            plugin.getLogger().info("[DEBUG] Jobs level-up: " + name
                    + " → " + jobName + " (level " + newLevel + ")");
        }

        threadManager.runAsync(() -> {
            try {
                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                statsDAO.setStat(playerId, serverId,
                        "jobs_level_" + jobName.toLowerCase(), newLevel);

                if (plugin.getHooksManager() != null
                        && plugin.getHooksManager().getPlaceholderAPIHook() != null) {
                    plugin.getHooksManager().getPlaceholderAPIHook().invalidate(uuid);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Ошибка записи уровня Jobs для " + name, e);
            }
        });
    }
}