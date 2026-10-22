package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardDAO {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 10;

    /**
     * Приоритет значения:
     * <ol>
     *   <li>{@code value_double} — баланс и другие double-статы</li>
     *   <li>{@code value_int} — обычные int-статы</li>
     *   <li>{@code 0}</li>
     * </ol>
     *
     * <p>Схема {@code player_stats} использует {@code DEFAULT NULL},
     * поэтому {@code IS NOT NULL} корректно различает "записано" и "не записано".</p>
     */
    private static final String EFFECTIVE_VALUE =
            "CASE " +
                    "  WHEN ps.value_double IS NOT NULL THEN ps.value_double " +
                    "  WHEN ps.value_int IS NOT NULL THEN ps.value_int " +
                    "  ELSE 0 " +
                    "END";

    private final PlayerStatsPlugin plugin;
    private final StatTypeDAO statTypeDAO;
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public LeaderboardDAO(PlayerStatsPlugin plugin, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.statTypeDAO = statTypeDAO;
    }

    public record LeaderboardEntry(int rank, UUID uuid, String playerName, double value) {}

    public List<LeaderboardEntry> getTop(String statName, int serverId, int limit) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        limit = clampLimit(limit);

        String query = """
            SELECT p.uuid, p.name,
                   %s AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ? AND ps.server_id = ?
            ORDER BY total_value DESC
            LIMIT ?
            """.formatted(EFFECTIVE_VALUE);

        return executeTopQuery(query, statTypeId, serverId, limit);
    }

    public List<LeaderboardEntry> getTop(String statName, int serverId) throws SQLException {
        return getTop(statName, serverId, DEFAULT_LIMIT);
    }

    /**
     * Глобальный топ (сумма по всем серверам).
     *
     * <p><b>Внимание:</b> для int-стат суммирование `value_int` и `value_double`
     * может смешивать типы. Обычно для одной статы используется один тип —
     * но если в БД остались "переходные" записи, `COALESCE` выберет double.</p>
     */
    public List<LeaderboardEntry> getTopGlobal(String statName, int limit) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        limit = clampLimit(limit);

        String query = """
            SELECT p.uuid, p.name,
                   COALESCE(SUM(%s), 0) AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ?
            GROUP BY p.id, p.uuid, p.name
            ORDER BY total_value DESC
            LIMIT ?
            """.formatted(EFFECTIVE_VALUE);

        List<LeaderboardEntry> result = new ArrayList<>();
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    result.add(new LeaderboardEntry(rank++,
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getDouble("total_value")));
                }
            }
        }
        return result;
    }

    /**
     * Возвращает ранг игрока (1-based) или {@code -1}, если у игрока нет записи.
     *
     * <p><b>Tie-ranks:</b> при равных значениях ранг считается «плотным»
     * (как в спорте с делением мест): два игрока с одинаковым значением
     * получат одинаковый ранг, следующий — ранг + 2 (не + 1).</p>
     */
    public int getPlayerRank(String statName, int serverId, int playerId) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        String checkQuery = """
            SELECT %s AS player_value
            FROM player_stats ps
            WHERE ps.stat_type_id = ? AND ps.server_id = ? AND ps.player_id = ?
            """.formatted(EFFECTIVE_VALUE);

        double playerValue = 0;
        boolean hasRecord = false;

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkQuery)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    playerValue = rs.getDouble("player_value");
                    hasRecord = true;
                }
            }
        }

        if (!hasRecord) return -1;

        String rankQuery = """
            SELECT COUNT(*) + 1 AS rank
            FROM player_stats ps
            WHERE ps.stat_type_id = ? AND ps.server_id = ? AND %s > ?
            """.formatted(EFFECTIVE_VALUE);

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(rankQuery)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setDouble(3, playerValue);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("rank");
            }
        }
        return -1;
    }

    private List<LeaderboardEntry> executeTopQuery(String query, int statTypeId, int serverId, int limit)
            throws SQLException {
        List<LeaderboardEntry> result = new ArrayList<>();
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    result.add(new LeaderboardEntry(rank++,
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getDouble("total_value")));
                }
            }
        }
        return result;
    }

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) return cached;
        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);
        return id;
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        if (limit > MAX_LIMIT) return MAX_LIMIT;
        return limit;
    }

    public void clearCache() {
        statTypeCache.clear();
    }
}