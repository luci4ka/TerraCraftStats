package ru.terracraft.stats.core.listeners;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.database.dao.PlayerDAO;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;
import ru.terracraft.stats.core.database.dao.ServerDAO;
import ru.terracraft.stats.core.database.dao.ServerStatsDAO;
import ru.terracraft.stats.core.multithreading.ThreadManager;
import ru.terracraft.stats.hooks.TerraCoreBridge;
import org.bukkit.Bukkit;

public final class ListenerRegistrar {

    private final PlayerStatsPlugin plugin;

    public ListenerRegistrar(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public StatsListener registerAll() {
        PlayerDAO playerDAO = plugin.getPlayerDAO();
        PlayerStatsDAO statsDAO = plugin.getStatsDAO();
        ServerStatsDAO serverStatsDAO = plugin.getServerStatsDAO();
        ServerDAO serverDAO = plugin.getServerDAO();
        ThreadManager threadManager = plugin.getThreadManager();
        TerraCoreBridge terraCore = plugin.getTerraCoreBridge();

        JoinListener joinListener = new JoinListener(plugin, playerDAO);
        Bukkit.getPluginManager().registerEvents(joinListener, plugin);

        StatsListener statsListener = new StatsListener(
                plugin, playerDAO, statsDAO, serverStatsDAO, serverDAO, threadManager,
                plugin.getHooksManager().getVaultHook(),
                terraCore);
        statsListener.setExcludeCommand(plugin.getExcludeCommand());
        statsListener.setPvpTracker(plugin.getPvpTracker());
        Bukkit.getPluginManager().registerEvents(statsListener, plugin);

        // Ванильные статы (через Statistic)
        VanillaStatsListener vanillaListener = new VanillaStatsListener(plugin);
        Bukkit.getPluginManager().registerEvents(vanillaListener, plugin);

        plugin.getLogger().info("Слушатели зарегистрированы.");
        return statsListener;
    }
}