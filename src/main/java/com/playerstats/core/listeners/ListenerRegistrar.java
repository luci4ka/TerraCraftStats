package com.playerstats.core.listeners;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.database.dao.ServerDAO;
import com.playerstats.core.database.dao.ServerStatsDAO;
import com.playerstats.core.gui.AchievementsGuiListener;
import com.playerstats.core.multithreading.ThreadManager; // ← см. ниже
import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * Регистрирует всех слушателей плагина.
 */
public final class ListenerRegistrar {

    private final Main plugin;

    public ListenerRegistrar(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Зарегистрировать всех слушателей.
     *
     * @return экземпляр StatsListener (нужен для onDisable)
     */
    public StatsListener registerAll() {
        PlayerDAO playerDAO = plugin.getPlayerDAO();
        PlayerStatsDAO statsDAO = plugin.getStatsDAO();
        ServerStatsDAO serverStatsDAO = plugin.getServerStatsDAO();
        ServerDAO serverDAO = plugin.getServerDAO();
        ThreadManager threadManager = plugin.getThreadManager();

        // 1. JoinListener
        JoinListener joinListener = new JoinListener(plugin, playerDAO);
        Bukkit.getPluginManager().registerEvents(joinListener, plugin);

        // 2. StatsListener
        StatsListener statsListener = new StatsListener(
                plugin, playerDAO, statsDAO, serverStatsDAO, serverDAO, threadManager,
                plugin.getHooksManager().getVaultHook(),
                plugin.getHooksManager().getEssentialsHook());
        statsListener.setExcludeCommand(plugin.getExcludeCommand());
        statsListener.setPvpTracker(plugin.getPvpTracker());
        Bukkit.getPluginManager().registerEvents(statsListener, plugin);

        // 3. ProjectileKillListener
        ProjectileKillListener projectileListener = new ProjectileKillListener(
                plugin, playerDAO, statsDAO, serverStatsDAO, threadManager,
                plugin.getHooksManager().getEssentialsHook());
        projectileListener.setExcludeCommand(plugin.getExcludeCommand());
        projectileListener.setPvpTracker(plugin.getPvpTracker());
        Bukkit.getPluginManager().registerEvents(projectileListener, plugin);

        // 4. JobsListener (через рефлексию)
        registerJobsListener(playerDAO, statsDAO, threadManager);

        Bukkit.getPluginManager().registerEvents(new AchievementsGuiListener(plugin), plugin);

        plugin.getLogger().info("Слушатели зарегистрированы.");

        return statsListener;
    }

    private void registerJobsListener(PlayerDAO playerDAO,
                                      PlayerStatsDAO statsDAO,
                                      ThreadManager threadManager) {
        var jobsHook = plugin.getHooksManager().getJobsHook();
        if (jobsHook == null || !jobsHook.isEnabled()) return;

        try {
            Class.forName("com.gamingmesh.jobs.api.JobsPaymentEvent");
            Class.forName("com.gamingmesh.jobs.api.JobsLevelUpEvent");

            Class<?> jobsListenerClass = Class.forName(
                    "com.playerstats.core.listeners.JobsListener");

            Object jobsListener = jobsListenerClass
                    .getConstructor(Main.class, PlayerDAO.class,
                            PlayerStatsDAO.class, ThreadManager.class)
                    .newInstance(plugin, playerDAO, statsDAO, threadManager);

            Bukkit.getPluginManager().registerEvents(
                    (org.bukkit.event.Listener) jobsListener, plugin);

            plugin.getLogger().info("JobsListener зарегистрирован.");

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Jobs API не найден — JobsListener не зарегистрирован.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось зарегистрировать JobsListener", e);
        }
    }
}