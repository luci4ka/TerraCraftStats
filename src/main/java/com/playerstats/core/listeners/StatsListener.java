package com.playerstats.core.listeners;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.database.dao.ServerDAO;
import com.playerstats.core.database.dao.ServerStatsDAO;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.ess3.api.events.AfkStatusChangeEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.UserManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.playerstats.core.multithreading.ThreadManager;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsListener implements Listener {

    private final PlayerDAO playerDAO;
    private final PlayerStatsDAO statsDAO;
    private final Main plugin;
    private final ServerStatsDAO serverStatsDAO;
    private final ServerDAO serverDAO;
    private final ThreadManager threadManager;

    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Essentials essentials; // может быть null
    private final Economy economy;       // может быть null
    private final LuckPerms luckPerms;   // может быть null

    private final int serverId; // ID текущего сервера из БД

    public StatsListener(Main plugin,
                         PlayerDAO playerDAO,
                         PlayerStatsDAO statsDAO,
                         ServerStatsDAO serverStatsDAO,
                         ServerDAO serverDAO,
                         ThreadManager threadManager) {
        this.plugin = plugin; // сохраним ссылку на плагин
        this.playerDAO = playerDAO;
        this.statsDAO = statsDAO;
        this.serverStatsDAO = serverStatsDAO;
        this.serverDAO = serverDAO;
        this.threadManager = threadManager;

        // Получаем serverId через конфиг
        int id = -1;
        try {
            String serverName = ConfigHandler.getInstance(plugin).getServerName();
            id = serverDAO.createOrGetServer(serverName);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        this.serverId = id;

        // Essentials
        Essentials ess = null;
        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        }
        this.essentials = ess;

        // Vault Economy
        RegisteredServiceProvider<Economy> econProvider = Bukkit.getServicesManager().getRegistration(Economy.class);
        this.economy = (econProvider != null) ? econProvider.getProvider() : null;

        // LuckPerms
        LuckPerms lp = null;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            lp = Bukkit.getServicesManager().load(LuckPerms.class);
        }
        this.luckPerms = lp;

        // Запускаем автообновление
        startAsyncAutoUpdate();
    }

    /** Асинхронный повторяющийся таймер через ThreadManager */
    private void startAsyncAutoUpdate() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!shouldCount(player)) continue;

                    UUID uuid = player.getUniqueId();

                    boolean godMode = false;
                    if (essentials != null) {
                        User user = essentials.getUser(player);
                        if (user != null) godMode = user.isGodModeEnabled();
                    }

                    if (!godMode) {
                        threadManager.runAsync(() -> updatePlayerStats(player, uuid));
                    }
                }

                // Запускаем задачу снова через 60 секунд
                threadManager.runAsyncDelayed(this, 1200L * 50);
            }
        };

        threadManager.runAsync(task);
    }

    /** Основная логика обновления статистики игрока */
    private void updatePlayerStats(Player player, UUID uuid) {
        try {
            playerDAO.createPlayerIfNotExists(uuid, player.getName());
            int playerId = playerDAO.getPlayerId(uuid);

            // play_time
            int playTime = statsDAO.getStat(playerId, serverId, "play_time");
            statsDAO.setStat(playerId, serverId, "play_time", playTime + 60);

            // серверная статистика: общее время игры
            int serverPlayTime = serverStatsDAO.getStat(serverId, "play_time");
            serverStatsDAO.setStat(serverId, "play_time", serverPlayTime + 60);

            // баланс через Vault
            if (economy != null) {
                double bal = economy.getBalance(player);
                statsDAO.setStatDouble(playerId, serverId, "balance", bal);
            }

            // группа через LuckPerms
            if (luckPerms != null) {
                UserManager userManager = luckPerms.getUserManager();
                var lpUser = userManager.getUser(uuid);
                if (lpUser != null) {
                    String group = lpUser.getPrimaryGroup();
                    statsDAO.setStatString(playerId, serverId, "group", group);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Проверка, учитывать ли статистику игрока */
    private boolean shouldCount(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        if (afkPlayers.contains(player.getUniqueId())) return false;

        if (essentials != null) {
            User user = essentials.getUser(player);
            if (user != null && user.isGodModeEnabled()) return false;
        }

        return true;
    }

    /** Отслеживание AFK через EssentialsX */
    @EventHandler
    public void onAfkChange(AfkStatusChangeEvent event) {
        UUID uuid = event.getAffected().getBase().getUniqueId();
        if (event.getValue()) {
            afkPlayers.add(uuid);
        } else {
            afkPlayers.remove(uuid);
        }
    }

    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        UUID uuid = player.getUniqueId();
        threadManager.runAsync(() -> {
            updateStat(uuid, "blocks_placed", 1);
            updateServerStat("blocks_placed", 1);
        });
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        UUID uuid = player.getUniqueId();
        threadManager.runAsync(() -> {
            updateStat(uuid, "blocks_broken", 1);
            updateServerStat("blocks_broken", 1);
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        UUID deadUUID = dead.getUniqueId();
        threadManager.runAsync(() -> {
            updateStat(deadUUID, "deaths", 1);
            updateServerStat("deaths", 1);
        });

        if (killer != null && killer != dead && shouldCount(killer)) {
            UUID killerUUID = killer.getUniqueId();
            threadManager.runAsync(() -> {
                updateStat(killerUUID, "kills", 1);
                updateServerStat("kills", 1);
            });
        }
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;

        Player player = event.getEntity().getKiller();
        if (!shouldCount(player)) return;

        UUID uuid = player.getUniqueId();
        boolean isBoss = event.getEntity().getCustomName() != null &&
                event.getEntity().getCustomName().contains("Босс");

        threadManager.runAsync(() -> {
            updateStat(uuid, "mobs_killed", 1);
            updateServerStat("mobs_killed", 1);
            if (isBoss) {
                updateStat(uuid, "bosses_killed", 1);
                updateServerStat("bosses_killed", 1);
            }
        });
    }

    /** Универсальная функция обновления одной статистики игрока */
    private void updateStat(UUID uuid, String stat, int increment) {
        try {
            playerDAO.createPlayerIfNotExists(uuid,
                    Bukkit.getPlayer(uuid) != null ? Bukkit.getPlayer(uuid).getName() : "Unknown");

            int playerId = playerDAO.getPlayerId(uuid);
            int current = statsDAO.getStat(playerId, serverId, stat);
            statsDAO.setStat(playerId, serverId, stat, current + increment);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Универсальная функция обновления серверной статистики */
    private void updateServerStat(String stat, int increment) {
        try {
            int current = serverStatsDAO.getStat(serverId, stat);
            serverStatsDAO.setStat(serverId, stat, current + increment);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
