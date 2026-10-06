package com.playerstats.core.listeners;

import com.playerstats.Main;
import com.playerstats.core.commands.ExcludeCommand;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.TrackedBlocks;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.database.dao.ServerDAO;
import com.playerstats.core.database.dao.ServerStatsDAO;
import com.playerstats.core.multithreading.ThreadManager;
import com.playerstats.core.pvp.PvPTracker;
import com.playerstats.hooks.EssentialsHook;
import com.playerstats.hooks.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

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
    private PvPTracker pvpTracker;
    private final int serverId;
    private final boolean verboseDebug;

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
        this.verboseDebug = plugin.getConfigHandler().isVerboseDebug();

        startPeriodicUpdate();
    }

    public void setExcludeCommand(ExcludeCommand excludeCommand) {
        this.excludeCommand = excludeCommand;
    }

    public void setPvpTracker(PvPTracker pvpTracker) {
        this.pvpTracker = pvpTracker;
    }

    // ==================== Периодическое обновление ====================

    private void startPeriodicUpdate() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline()) continue;
                if (!shouldCount(player)) continue;

                PlayerSnapshot snapshot = createSnapshot(player);
                if (snapshot == null || snapshot.playerId == -1) continue;

                threadManager.runAsync(() -> updatePlayerStats(snapshot));
            }
        }, 20L, 20L * 60);
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

        int playerId;
        try {
            playerId = playerDAO.getOrCreatePlayerId(uuid, name);
        } catch (SQLException e) {
            logError("Не удалось получить ID игрока: " + name, e);
            return null;
        }

        double balance = 0.0;
        if (vaultHook != null && vaultHook.isEnabled()) {
            try {
                balance = vaultHook.getBalance(player);
            } catch (Throwable e) {
                logError("Не удалось получить баланс игрока: " + name, e);
                balance = 0.0;
            }
        }

        String group = null;
        try {
            group = plugin.getGroupService().getPlayerGroup(player, playerId);
        } catch (Exception e) {
            logError("Не удалось получить группу игрока: " + name, e);
        }

        if (verboseDebug) {
            plugin.getLogger().info("[DEBUG] Снимок: " + name
                    + " (ID=" + playerId + ", balance=" + balance + ", group=" + group + ")");
        }

        return new PlayerSnapshot(uuid, name, playerId, balance, group);
    }

    private void updatePlayerStats(PlayerSnapshot snap) {
        if (snap.playerId == -1) return;

        try {
            int pid = snap.playerId;
            int sid = serverId;

            statsDAO.incrementStat(pid, sid, "play_time", 60);

            if (vaultHook != null && vaultHook.isEnabled()) {
                statsDAO.setStatDouble(pid, sid, "balance", snap.balance);
            }
            if (snap.group != null && !snap.group.isEmpty()) {
                statsDAO.setStatString(pid, sid, "group", snap.group);
            }

            serverStatsDAO.incrementStat(sid, "play_time", 60);

            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] Обновлена статистика " + snap.name
                        + ": play_time +60, balance=" + snap.balance);
            }

            if (plugin.getHooksManager() != null
                    && plugin.getHooksManager().getPlaceholderAPIHook() != null) {
                plugin.getHooksManager().getPlaceholderAPIHook().invalidate(snap.uuid);
            }

        } catch (SQLException e) {
            logError("Ошибка обновления периодической статистики для " + snap.name, e);
        }
    }

    // ==================== Проверки ====================

    private boolean shouldCount(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;

        if (essentialsHook != null && essentialsHook.isEnabled()) {
            if (essentialsHook.isAfk(player)) return false;
            if (essentialsHook.isGodMode(player)) return false;
        }

        if (excludeCommand != null && excludeCommand.isExcluded(player.getUniqueId())) return false;

        return true;
    }

    /**
     * Сохранить статистику всех онлайн-игроков (вызывается при выключении).
     */
    public void saveAllOnline() {
        plugin.getLogger().info("Сохранение статистики онлайн-игроков...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldCount(player)) continue;

            try {
                UUID uuid = player.getUniqueId();
                String name = player.getName();

                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                statsDAO.incrementStat(playerId, serverId, "play_time", 60);
                serverStatsDAO.incrementStat(serverId, "play_time", 60);

                if (vaultHook != null && vaultHook.isEnabled()) {
                    try {
                        double balance = vaultHook.getBalance(player);
                        statsDAO.setStatDouble(playerId, serverId, "balance", balance);
                    } catch (Throwable e) {
                        plugin.getLogger().warning("Vault: ошибка баланса для "
                                + player.getName() + " (" + e.getClass().getSimpleName() + ")");
                    }
                }

                try {
                    String group = plugin.getGroupService().getPlayerGroup(player, playerId);
                    if (group != null && !group.isEmpty()) {
                        statsDAO.setStatString(playerId, serverId, "group", group);
                    }
                } catch (Throwable e) {
                    plugin.getLogger().warning("Ошибка группы для "
                            + player.getName() + " (" + e.getClass().getSimpleName() + ")");
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Не удалось сохранить статистику " + player.getName(), e);
            }
        }

        plugin.getLogger().info("Статистика онлайн-игроков сохранена.");
    }

    // ==================== Обработчики событий ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();

        handleStatEvent(player, "blocks_placed");

        TrackedBlocks tracked = plugin.getConfigHandler().getTrackedBlocks();
        if (tracked != null && tracked.isTracked(type)) {
            String blockStatKey = "block_placed_" + type.name().toLowerCase();
            handleStatEvent(player, blockStatKey);

            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] BlockPlaceEvent: " + player.getName()
                        + " поставил " + type + " → blocks_placed +1, " + blockStatKey + " +1");
            }
        } else {
            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] BlockPlaceEvent: " + player.getName()
                        + " поставил " + type + " → blocks_placed +1 (не отслеживается)");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();

        handleStatEvent(player, "blocks_broken");

        TrackedBlocks tracked = plugin.getConfigHandler().getTrackedBlocks();
        if (tracked != null && tracked.isTracked(type)) {
            String blockStatKey = "block_broken_" + type.name().toLowerCase();
            handleStatEvent(player, blockStatKey);

            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] BlockBreakEvent: " + player.getName()
                        + " сломал " + type + " → blocks_broken +1, " + blockStatKey + " +1");
            }
        } else {
            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] BlockBreakEvent: " + player.getName()
                        + " сломал " + type + " → blocks_broken +1 (не отслеживается)");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        handleStatEvent(dead, "deaths");

        if (killer == null || killer == dead) return;
        if (!shouldCount(killer)) return;

        // Жертва в /exclude → не считаем убийство
        if (plugin.getConfigHandler().isPvpIgnoreExcludedVictims()
                && excludeCommand != null
                && excludeCommand.isExcluded(dead.getUniqueId())) {
            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] PvP: жертва " + dead.getName()
                        + " в /exclude → kills не засчитан");
            }
            return;
        }

        UUID killerUuid = killer.getUniqueId();
        UUID victimUuid = dead.getUniqueId();

        // ==================== АНТИ-АБУЗ ====================
        boolean countFullKill = true;
        if (pvpTracker != null && plugin.getConfigHandler().isPvpPerVictimStats()) {
            if (!pvpTracker.shouldCount(killerUuid, victimUuid)) {
                countFullKill = false;
                if (verboseDebug) {
                    plugin.getLogger().info("[DEBUG] PvP-антиабуз: " + killer.getName()
                            + " уже убивал " + dead.getName()
                            + " недавно → только pvp_kill, без kills");
                }
            }
        }

        // ==================== ОСНОВНАЯ СТАТИСТИКА ====================
        if (countFullKill) {
            handleStatEvent(killer, "kills");
        }

        // ==================== СТАТИСТИКА ПО ЖЕРТВЕ ====================
        if (plugin.getConfigHandler().isPvpPerVictimStats()) {
            handleStatEvent(killer, "pvp_kill_" + victimUuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !shouldCount(killer)) return;

        if (wasKilledByProjectile(event.getEntity())) {
            return;
        }

        handleStatEvent(killer, "mobs_killed");

        EntityType mobType = event.getEntity().getType();
        if (mobType.isAlive() && !mobType.equals(EntityType.PLAYER)) {
            String mobStatKey = "mob_killed_" + mobType.name().toLowerCase();
            handleStatEvent(killer, mobStatKey);

            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] MobKill: " + killer.getName()
                        + " убил " + mobType + " → mobs_killed +1, "
                        + mobStatKey + " +1");
            }
        }

        if (isBossMob(event.getEntity())) {
            handleStatEvent(killer, "bosses_killed");
        }
    }

    private boolean wasKilledByProjectile(Entity entity) {
        EntityDamageByEntityEvent lastDamage = null;

        if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            lastDamage = damageEvent;
        }

        if (lastDamage == null) return false;

        if (lastDamage.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player;
        }

        return false;
    }

    private void handleStatEvent(Player player, String statKey) {
        if (!shouldCount(player)) {
            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] shouldCount=false для "
                        + player.getName() + " → " + statKey);
            }
            return;
        }

        UUID uuid = player.getUniqueId();
        String name = player.getName();

        if (verboseDebug) {
            plugin.getLogger().info("[DEBUG] Событие: " + name + " → " + statKey);
        }

        threadManager.runAsync(() -> {
            try {
                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                statsDAO.incrementStat(playerId, serverId, statKey, 1);
                serverStatsDAO.incrementStat(serverId, statKey, 1);

                final int finalPlayerId = playerId;
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline() && plugin.getMilestoneManager() != null) {
                        try {
                            int newValue = statsDAO.getStat(finalPlayerId, serverId, statKey);
                            plugin.getMilestoneManager().check(player, statKey, newValue);
                        } catch (SQLException ex) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Ошибка проверки Milestone для " + name, ex);
                        }
                    }
                });

                if (plugin.getHooksManager() != null
                        && plugin.getHooksManager().getPlaceholderAPIHook() != null) {
                    plugin.getHooksManager().getPlaceholderAPIHook().invalidate(uuid);
                }
                if (plugin.getTopCache() != null) {
                    plugin.getTopCache().invalidate(statKey);
                }

            } catch (SQLException e) {
                logError("Ошибка обновления статистики '" + statKey + "' для " + name, e);
            }
        });
    }

    private boolean isBossMob(Entity entity) {
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