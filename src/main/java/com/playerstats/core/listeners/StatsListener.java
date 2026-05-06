package com.playerstats.core.listeners;

import com.playerstats.Main;
import com.playerstats.core.commands.ExcludeCommand;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.database.dao.ServerDAO;
import com.playerstats.core.database.dao.ServerStatsDAO;
import com.playerstats.core.multithreading.ThreadManager;
import com.playerstats.hooks.EssentialsHook;
import com.playerstats.hooks.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class StatsListener implements Listener {

    private final Main plugin;
    private final PlayerDAO playerDAO;
    private final PlayerStatsDAO statsDAO;
    private final ServerStatsDAO serverStatsDAO;
    private final ThreadManager threadManager;
    private final VaultHook vaultHook;
    private final EssentialsHook essentialsHook;
    private final int serverId;

    private ExcludeCommand excludeCommand;

    public StatsListener(Main plugin,
                         PlayerDAO playerDAO,
                         PlayerStatsDAO statsDAO,
                         ServerStatsDAO serverStatsDAO,
                         ServerDAO serverDAO,
                         ThreadManager threadManager,
                         VaultHook vaultHook,
                         EssentialsHook essentialsHook) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.statsDAO = statsDAO;
        this.serverStatsDAO = serverStatsDAO;
        this.threadManager = threadManager;
        this.vaultHook = vaultHook;
        this.essentialsHook = essentialsHook;
        this.serverId = plugin.getServerId();

        // Запускаем периодическое обновление
        startPeriodicUpdate();
    }

    /**
     * Установить ExcludeCommand для проверки исключённых игроков.
     */
    public void setExcludeCommand(ExcludeCommand excludeCommand) {
        this.excludeCommand = excludeCommand;
    }

    // ==================== Периодическое обновление ====================

    private void startPeriodicUpdate() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!shouldCount(player)) continue;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    PlayerSnapshot snapshot = createSnapshot(player);
                    threadManager.runAsync(() -> updatePlayerStats(snapshot));
                });
            }
        }, 20L, 20L * 60); // Каждые 60 секунд
    }

    // ==================== Снимок данных игрока ====================

    private static class PlayerSnapshot {
        final UUID uuid;
        final String name;
        final int playerId;
        final double balance;
        final String group;

        PlayerSnapshot(UUID uuid, String name, int playerId, double balance, String group) {
            this.uuid = uuid;
            this.name = name;
            this.playerId = playerId;
            this.balance = balance;
            this.group = group;
        }
    }

    private PlayerSnapshot createSnapshot(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Получаем ID игрока
        int playerId = -1;
        try {
            playerId = playerDAO.getOrCreatePlayerId(uuid, name);
        } catch (SQLException e) {
            logError("Не удалось получить ID игрока: " + name, e);
        }

        // Баланс через VaultHook
        double balance = 0.0;
        if (vaultHook != null && vaultHook.isEnabled()) {
            try {
                balance = vaultHook.getBalance(player);
            } catch (Exception e) {
                logError("Не удалось получить баланс игрока: " + name, e);
            }
        }

        // Группа через LuckPermsHook (через Main)
        String group = null;
        try {
            group = plugin.getGroupService().getPlayerGroup(player, playerId);
        } catch (Exception e) {
            logError("Не удалось получить группу игрока: " + name, e);
        }

        return new PlayerSnapshot(uuid, name, playerId, balance, group);
    }

    private void updatePlayerStats(PlayerSnapshot snap) {
        if (snap.playerId == -1) return;

        try {
            int pid = snap.playerId;
            int sid = serverId;

            // play_time +60 секунд
            int playTime = statsDAO.getStat(pid, sid, "play_time");
            statsDAO.setStat(pid, sid, "play_time", playTime + 60);

            // Серверное время
            int serverPlayTime = serverStatsDAO.getStat(sid, "play_time");
            serverStatsDAO.setStat(sid, "play_time", serverPlayTime + 60);

            // Баланс (если Vault включен)
            if (vaultHook != null && vaultHook.isEnabled()) {
                statsDAO.setStatDouble(pid, sid, "balance", snap.balance);
            }

            // Группа
            if (snap.group != null && !snap.group.isEmpty()) {
                statsDAO.setStatString(pid, sid, "group", snap.group);
            }

        } catch (SQLException e) {
            logError("Ошибка обновления периодической статистики для " + snap.name, e);
        }
    }

    // ==================== Проверки ====================

    private boolean shouldCount(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;

        // Проверка AFK через EssentialsHook
        if (essentialsHook != null && essentialsHook.isEnabled()) {
            if (essentialsHook.isAfk(player)) return false;
        }

        // Проверка исключённых
        if (excludeCommand != null && excludeCommand.isExcluded(player.getUniqueId())) return false;

        // Проверка GodMode через EssentialsHook
        if (essentialsHook != null && essentialsHook.isEnabled()) {
            if (essentialsHook.isGodMode(player)) return false;
        }

        return true;
    }

    // ==================== Обработчики событий ====================

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        handleStatEvent(event.getPlayer(), "blocks_placed");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        handleStatEvent(event.getPlayer(), "blocks_broken");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        handleStatEvent(dead, "deaths");

        if (killer != null && killer != dead && shouldCount(killer)) {
            handleStatEvent(killer, "kills");
        }
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !shouldCount(killer)) return;

        handleStatEvent(killer, "mobs_killed");

        if (isBossMob(event.getEntity())) {
            handleStatEvent(killer, "bosses_killed");
        }
    }

    private void handleStatEvent(Player player, String statKey) {
        if (!shouldCount(player)) return;

        UUID uuid = player.getUniqueId();
        String name = player.getName();

        threadManager.runAsync(() -> {
            try {
                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                // Обновляем статистику игрока
                int current = statsDAO.getStat(playerId, serverId, statKey);
                statsDAO.setStat(playerId, serverId, statKey, current + 1);

                // Обновляем серверную статистику
                int serverCurrent = serverStatsDAO.getStat(serverId, statKey);
                serverStatsDAO.setStat(serverId, statKey, serverCurrent + 1);

            } catch (SQLException e) {
                logError("Ошибка обновления статистики '" + statKey + "' для " + name, e);
            }
        });
    }

    private boolean isBossMob(org.bukkit.entity.Entity entity) {
        String name = entity.getCustomName();
        if (name == null) return false;

        List<String> keywords = ConfigHandler.getInstance(plugin).getBossKeywords();
        for (String keyword : keywords) {
            if (name.toLowerCase().contains(keyword.toLowerCase())) return true;
        }
        return false;
    }

    private void logError(String message, Throwable e) {
        plugin.getLogger().log(Level.SEVERE, message, e);
    }
}