package com.playerstats.core.listeners;

import com.playerstats.Main;
import com.playerstats.core.commands.ExcludeCommand;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.database.dao.ServerStatsDAO;
import com.playerstats.core.multithreading.ThreadManager;
import com.playerstats.core.pvp.PvPTracker;
import com.playerstats.hooks.EssentialsHook;
import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Отслеживает убийства снарядами (стрелы, зелья, трезубцы).
 */
public class ProjectileKillListener implements Listener {

    private final Main plugin;
    private final PlayerDAO playerDAO;
    private final PlayerStatsDAO statsDAO;
    private final ServerStatsDAO serverStatsDAO;
    private final ThreadManager threadManager;
    private final EssentialsHook essentialsHook;
    private final int serverId;
    private PvPTracker pvpTracker;
    private final boolean verboseDebug;

    private ExcludeCommand excludeCommand;

    public ProjectileKillListener(Main plugin,
                                  PlayerDAO playerDAO,
                                  PlayerStatsDAO statsDAO,
                                  ServerStatsDAO serverStatsDAO,
                                  ThreadManager threadManager,
                                  EssentialsHook essentialsHook) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.statsDAO = statsDAO;
        this.serverStatsDAO = serverStatsDAO;
        this.threadManager = threadManager;
        this.essentialsHook = essentialsHook;
        this.serverId = plugin.getServerId();
        this.verboseDebug = plugin.getConfigHandler().isVerboseDebug();
    }

    public void setExcludeCommand(ExcludeCommand excludeCommand) {
        this.excludeCommand = excludeCommand;
    }

    public void setPvpTracker(PvPTracker pvpTracker) {
        this.pvpTracker = pvpTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(event.getDamager() instanceof Projectile projectile)) return;

        ProjectileSource shooter = projectile.getShooter();
        if (!(shooter instanceof Player killer)) return;

        if (victim instanceof Player victimPlayer
                && victimPlayer.getUniqueId().equals(killer.getUniqueId())) {
            return;
        }

        if (!shouldCount(killer)) return;

        double finalDamage = event.getFinalDamage();
        double victimHealth = victim.getHealth();

        if (finalDamage >= victimHealth) {
            if (verboseDebug) {
                plugin.getLogger().info("[DEBUG] ProjectileKill: " + killer.getName()
                        + " → " + victim.getType() + " (damage=" + finalDamage
                        + ", health=" + victimHealth + ")");
            }

            if (victim instanceof Player victimPlayer) {
                // Проверка жертвы на /exclude
                if (plugin.getConfigHandler().isPvpIgnoreExcludedVictims()
                        && excludeCommand != null
                        && excludeCommand.isExcluded(victimPlayer.getUniqueId())) {
                    return;
                }

                UUID killerUuid = killer.getUniqueId();
                UUID victimUuid = victimPlayer.getUniqueId();

                boolean countFullKill = true;
                if (pvpTracker != null && plugin.getConfigHandler().isPvpPerVictimStats()) {
                    if (!pvpTracker.shouldCount(killerUuid, victimUuid)) {
                        countFullKill = false;
                        if (verboseDebug) {
                            plugin.getLogger().info("[DEBUG] PvP-антиабуз (projectile): "
                                    + killer.getName() + " уже убивал " + victimPlayer.getName()
                                    + " недавно → только pvp_kill");
                        }
                    }
                }

                if (countFullKill) {
                    handleProjectileKill(killer, victim, "kills");
                }

                if (plugin.getConfigHandler().isPvpPerVictimStats()) {
                    handleProjectileKill(killer, victim, "pvp_kill_" + victimUuid);
                }
            } else {
                handleProjectileKill(killer, victim, "mobs_killed");
            }
        }
    }

    private void handleProjectileKill(Player killer, Entity victim, String statKey) {
        UUID uuid = killer.getUniqueId();
        String name = killer.getName();

        threadManager.runAsync(() -> {
            try {
                int playerId = playerDAO.getOrCreatePlayerId(uuid, name);

                statsDAO.incrementStat(playerId, serverId, statKey, 1);
                serverStatsDAO.incrementStat(serverId, statKey, 1);

                // Если это моб — пишем и по конкретному типу
                if (!(victim instanceof Player) && victim.getType().isAlive()) {
                    String mobStatKey = "mob_killed_" + victim.getType().name().toLowerCase();
                    statsDAO.incrementStat(playerId, serverId, mobStatKey, 1);
                    serverStatsDAO.incrementStat(serverId, mobStatKey, 1);

                    if (verboseDebug) {
                        plugin.getLogger().info("[DEBUG] ProjectileKill: " + name
                                + " убил " + victim.getType() + " → " + mobStatKey + " +1");
                    }
                }

                // Milestones для стрелковых убийств
                final int finalPlayerId = playerId;
                plugin.getThreadManager().runSync(() -> {
                    if (killer.isOnline() && plugin.getMilestoneManager() != null) {
                        try {
                            int newValue = statsDAO.getStat(finalPlayerId, serverId, statKey);
                            plugin.getMilestoneManager().check(killer, statKey, newValue);
                        } catch (SQLException ex) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Ошибка Milestone для " + name, ex);
                        }
                    }
                });

                if (isBossMob(victim)) {
                    statsDAO.incrementStat(playerId, serverId, "bosses_killed", 1);
                    serverStatsDAO.incrementStat(serverId, "bosses_killed", 1);

                    plugin.getThreadManager().runSync(() -> {
                        if (killer.isOnline() && plugin.getMilestoneManager() != null) {
                            try {
                                int bossValue = statsDAO.getStat(
                                        finalPlayerId, serverId, "bosses_killed");
                                plugin.getMilestoneManager().check(
                                        killer, "bosses_killed", bossValue);
                            } catch (SQLException ex) {
                                plugin.getLogger().log(Level.WARNING,
                                        "Ошибка Milestone (босс) для " + name, ex);
                            }
                        }
                    });
                }

                if (plugin.getHooksManager() != null
                        && plugin.getHooksManager().getPlaceholderAPIHook() != null) {
                    plugin.getHooksManager().getPlaceholderAPIHook().invalidate(uuid);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Ошибка записи убийства снарядом для " + name, e);
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
}