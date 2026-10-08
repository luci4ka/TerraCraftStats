package com.playerstats.api.java.impl;

import com.playerstats.Main;
import com.playerstats.api.java.PlayerStatsApi;
import com.playerstats.api.java.model.AchievementInfo;
import com.playerstats.api.java.model.LeaderboardEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class PlayerStatsApiImpl implements PlayerStatsApi {

    private final Main plugin;

    public PlayerStatsApiImpl(Main plugin) {
        this.plugin = plugin;
    }

    // ==================== STATS ====================

    @Override
    public int getStat(Player player, String stat) {
        return getStat(player.getUniqueId(), stat);
    }

    @Override
    public int getStat(UUID uuid, String stat) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return 0;
            return plugin.getStatsDAO().getStat(playerId, plugin.getServerId(), stat);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "API getStat error", e);
            return 0;
        }
    }

    @Override
    public long getStatLong(UUID uuid, String stat) {
        return getStat(uuid, stat);
    }

    @Override
    public double getStatDouble(UUID uuid, String stat) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return 0.0;
            return plugin.getStatsDAO().getStatDouble(playerId, plugin.getServerId(), stat);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "API getStatDouble error", e);
            return 0.0;
        }
    }

    @Override
    public String getStatString(UUID uuid, String stat) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return null;
            return plugin.getStatsDAO().getStatString(playerId, plugin.getServerId(), stat);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "API getStatString error", e);
            return null;
        }
    }

    // ==================== TOP ====================

    @Override
    public List<LeaderboardEntry> getTop(String stat, int limit) {
        try {
            var core = plugin.getLeaderboardDAO()
                    .getTop(stat, plugin.getServerId(), limit);

            List<LeaderboardEntry> result = new ArrayList<>(core.size());
            for (var e : core) {
                result.add(new LeaderboardEntry(
                        e.rank(),
                        e.uuid(),
                        e.playerName(),
                        e.value()
                ));
            }
            return result;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "API getTop error", e);
            return List.of();
        }
    }

    @Override
    public int getRank(UUID uuid, String stat) {
        List<LeaderboardEntry> top = getTop(stat, 100);   // ← уже List<api.LeaderboardEntry>
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).uuid().equals(uuid)) {          // ← тут вызывается uuid() на api.LeaderboardEntry
                return i + 1;
            }
        }
        return 0;
    }

    // ==================== ACHIEVEMENTS ====================

    @Override
    public boolean isAchievementUnlocked(UUID uuid, String achievementId) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return false;
            return plugin.getAchievementDAO().isUnlocked(playerId, achievementId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "API isAchievementUnlocked error", e);
            return false;
        }
    }

    @Override
    public Set<String> getUnlockedAchievements(UUID uuid) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return Set.of();
            return plugin.getAchievementDAO().getUnlockedAchievements(playerId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "API getUnlockedAchievements error", e);
            return Set.of();
        }
    }

    @Override
    public Collection<AchievementInfo> getAllAchievements() {
        return plugin.getAchievementManager().getAll().stream()
                .map(ac -> new AchievementInfo(
                        ac.id(), ac.name(), ac.description(),
                        ac.category(), ac.stat(), ac.threshold()))
                .toList();
    }

    // ==================== PLAYERS ====================

    @Override
    public UUID getUuidByName(String name) {
        return plugin.getPlayerDAO().getUuidByName(name);
    }

    @Override
    public String getNameByUuid(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName();
    }

    @Override
    public int getPlayerId(UUID uuid) {
        try {
            return plugin.getPlayerDAO().getPlayerId(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "PlayerStatsApi.getPlayerId error: " + uuid, e);
            return -1;
        }
    }

    // ==================== UTIL ====================

    @Override
    public boolean isExcluded(UUID uuid) {
        return plugin.getExcludeCommand().isExcluded(uuid);
    }

    @Override
    public long getServerId() {
        return plugin.getServerId();
    }

    @Override
    public String getServerName() {
        return plugin.getServerName();
    }
}