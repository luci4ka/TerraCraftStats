package com.playerstats.api.java;

import com.playerstats.api.java.event.PlayerAchievementUnlockEvent;
import com.playerstats.api.java.event.PlayerMilestoneEvent;
import com.playerstats.api.java.event.PlayerStatChangeEvent;
import com.playerstats.api.java.model.AchievementInfo;
import com.playerstats.api.java.model.LeaderboardEntry;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Публичный Java API плагина PlayerStats.
 *
 * <p>Другие плагины могут получать экземпляр через Bukkit ServicesManager:</p>
 *
 * <pre>{@code
 * RegisteredServiceProvider<PlayerStatsApi> provider =
 *         Bukkit.getServicesManager().getRegistration(PlayerStatsApi.class);
 * if (provider != null) {
 *     PlayerStatsApi api = provider.getProvider();
 *     int kills = api.getStat(player, "kills");
 * }
 * }</pre>
 */
public interface PlayerStatsApi {

    // ==================== STATS ====================

    /**
     * Получить значение статы игрока (int).
     */
    int getStat(Player player, String stat);

    int getStat(UUID uuid, String stat);

    /**
     * Получить значение статы как long.
     */
    long getStatLong(UUID uuid, String stat);

    /**
     * Получить значение статы как double (баланс).
     */
    double getStatDouble(UUID uuid, String stat);

    /**
     * Получить строковое значение статы (группа).
     */
    String getStatString(UUID uuid, String stat);

    // ==================== TOP ====================

    /**
     * Получить топ игроков по стате.
     */
    List<LeaderboardEntry> getTop(String stat, int limit);

    /**
     * Получить место игрока в топе (0 если не в топе).
     */
    int getRank(UUID uuid, String stat);

    // ==================== ACHIEVEMENTS ====================

    boolean isAchievementUnlocked(UUID uuid, String achievementId);

    Set<String> getUnlockedAchievements(UUID uuid);

    Collection<AchievementInfo> getAllAchievements();

    // ==================== PLAYERS ====================

    UUID getUuidByName(String name);

    String getNameByUuid(UUID uuid);

    int getPlayerId(UUID uuid);

    // ==================== UTIL ====================

    boolean isExcluded(UUID uuid);

    long getServerId();

    String getServerName();
}