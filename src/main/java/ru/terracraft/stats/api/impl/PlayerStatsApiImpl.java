package ru.terracraft.stats.api.impl;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.api.PlayerStatsApi;
import ru.terracraft.stats.api.model.LeaderboardEntry;
import ru.terracraft.stats.api.model.PlayerStatsSnapshot;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO.StatValue;
import ru.terracraft.stats.core.services.TopCache;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerStatsApiImpl implements PlayerStatsApi {

    /**
     * Известные double-статы. Используется, чтобы не терять нулевые значения
     *
     * <p>Если добавляешь новую double-стату — не забудь добавить сюда,
     * иначе она попадёт в {@link PlayerStatsSnapshot#intStats()} с усечением.</p>
     */
    private static final Set<String> DOUBLE_STATS = Set.of("balance");

    private final PlayerStatsPlugin plugin;

    public PlayerStatsApiImpl(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== STATS ====================

    @Override
    public int getStat(Player player, String stat) {
        if (player == null) return 0;
        return getStat(player.getUniqueId(), stat);
    }

    @Override
    public int getStat(UUID uuid, String stat) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return 0;
            return plugin.getStatsDAO().getStat(playerId, plugin.getServerId(), stat);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "API getStat error (" + uuid + ", '" + stat + "')", e);
            return 0;
        }
    }

    @Override
    public double getStatDouble(UUID uuid, String stat) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return 0.0;
            return plugin.getStatsDAO().getStatDouble(playerId, plugin.getServerId(), stat);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "API getStatDouble error (" + uuid + ", '" + stat + "')", e);
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
            plugin.getLogger().log(Level.WARNING,
                    "API getStatString error (" + uuid + ", '" + stat + "')", e);
            return null;
        }
    }

    @Override
    public PlayerStatsSnapshot getSnapshot(UUID uuid) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return null;

            Map<String, StatValue> all = plugin.getStatsDAO()
                    .getAllStatsDetailed(playerId, plugin.getServerId());

            Map<String, Integer> intStats = new LinkedHashMap<>();
            Map<String, Double> doubleStats = new LinkedHashMap<>();
            Map<String, String> stringStats = new LinkedHashMap<>();

            for (Map.Entry<String, StatValue> e : all.entrySet()) {
                String key = e.getKey();
                StatValue sv = e.getValue();

                // Тип определяем по ИМЕНИ статы, а не по значению:
                // 0 — валидное значение (kills=0, deaths=0), терять нельзя.
                if (sv.valueString() != null) {
                    stringStats.put(key, sv.valueString());
                } else if (DOUBLE_STATS.contains(key)) {
                    doubleStats.put(key, sv.valueDouble());
                } else {
                    intStats.put(key, sv.valueInt());
                }
            }

            String name;
            try {
                name = plugin.getPlayerDAO().getPlayerName(playerId);
            } catch (SQLException ignored) {
                name = null;
            }

            return new PlayerStatsSnapshot(
                    uuid,
                    name,
                    Map.copyOf(intStats),
                    Map.copyOf(doubleStats),
                    Map.copyOf(stringStats)
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "API getSnapshot error: " + uuid, e);
            return null;
        }
    }

    // ==================== TOP ====================

    @Override
    public List<LeaderboardEntry> getTop(String stat, int limit) {
        if (limit <= 0) return List.of();

        // 1) Сначала — кэш (in-memory, никогда не блокирует).
        TopCache topCache = plugin.getTopCache();
        if (topCache != null && topCache.isCached(stat)) {
            var cached = topCache.getTop(stat);
            if (cached != null && !cached.isEmpty()) {
                int n = Math.min(limit, cached.size());
                List<LeaderboardEntry> result = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    var e = cached.get(i);
                    result.add(new LeaderboardEntry(e.rank(), e.uuid(),
                            e.playerName(), e.value()));
                }
                return List.copyOf(result);
            }
        }

        // 2) Fallback — прямой запрос к БД (для не-кэшируемых стат).
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
            return List.copyOf(result);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "API getTop error ('" + stat + "', limit=" + limit + ")", e);
            return List.of();
        }
    }

    @Override
    public int getRank(UUID uuid, String stat) {
        try {
            int playerId = getPlayerId(uuid);
            if (playerId == -1) return 0;
            int rank = plugin.getLeaderboardDAO()
                    .getPlayerRank(stat, plugin.getServerId(), playerId);
            return Math.max(rank, 0);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "API getRank error (" + uuid + ", '" + stat + "')", e);
            return 0;
        }
    }

    // ==================== PLAYERS ====================

    @Override
    public UUID getUuidByName(String name) {
        return plugin.getPlayerDAO().getUuidByName(name);
    }

    @Override
    public String getNameByUuid(UUID uuid) {
        try {
            return plugin.getPlayerDAO().getPlayerName(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "API getNameByUuid error: " + uuid, e);
            return null;
        }
    }

    @Override
    public int getPlayerId(UUID uuid) {
        try {
            return plugin.getPlayerDAO().getPlayerId(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "API getPlayerId error: " + uuid, e);
            return -1;
        }
    }

    // ==================== UTIL ====================

    @Override
    public boolean isExcluded(UUID uuid) {
        var cmd = plugin.getExcludeCommand();
        return cmd != null && cmd.isExcluded(uuid);
    }

    @Override
    public int getServerId() {
        return plugin.getServerId();
    }

    @Override
    public String getServerName() {
        return plugin.getServerName();
    }
}