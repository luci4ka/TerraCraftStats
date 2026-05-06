package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardDAO {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 10;

    private final DatabaseConnection db;
    private final StatTypeDAO statTypeDAO;

    // Кэш stat_type_id → name, чтобы не ходить в БД каждый раз
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public LeaderboardDAO(DatabaseConnection db, StatTypeDAO statTypeDAO) {
        this.db = db;
        this.statTypeDAO = statTypeDAO;
    }

    /**
     * Запись в топе с полной информацией об игроке.
     */
    public record LeaderboardEntry(UUID uuid, String playerName, double value) {
        @Override
        public String toString() {
            return playerName + " — " + String.format("%.2f", value);
        }
    }

    // ==================== Топ по конкретному серверу ====================

    /**
     * Топ игроков по статистике на конкретном сервере.
     *
     * @param statName название статистики (play_time, mobs_killed и т.д.)
     * @param serverId ID сервера
     * @param limit    количество записей (1-100)
     * @return список отсортированный по убыванию значения
     */
    public List<LeaderboardEntry> getTop(String statName, int serverId, int limit) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        limit = clampLimit(limit);

        String query = """
            SELECT p.uuid, p.name, ps.value_double, ps.value_int
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ? AND ps.server_id = ?
            ORDER BY ps.value_double DESC, ps.value_int DESC
            LIMIT ?
            """;

        return executeTopQuery(query, statTypeId, serverId, limit);
    }

    /**
     * Топ игроков по статистике на конкретном сервере (лимит по умолчанию = 10).
     */
    public List<LeaderboardEntry> getTop(String statName, int serverId) throws SQLException {
        return getTop(statName, serverId, DEFAULT_LIMIT);
    }

    // ==================== Топ по всем серверам (суммарно) ====================

    /**
     * Топ игроков по статистике суммарно по всем серверам.
     * Группирует записи игрока со всех серверов и суммирует значения.
     *
     * @param statName название статистики
     * @param limit    количество записей
     */
    public List<LeaderboardEntry> getTopGlobal(String statName, int limit) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        limit = clampLimit(limit);

        String query = """
            SELECT p.uuid, p.name, 
                   SUM(COALESCE(ps.value_double, 0)) + SUM(COALESCE(ps.value_int, 0)) AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ?
            GROUP BY p.id, p.uuid, p.name
            ORDER BY total_value DESC
            LIMIT ?
            """;

        List<LeaderboardEntry> result = new ArrayList<>();
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    double value = rs.getDouble("total_value");
                    result.add(new LeaderboardEntry(uuid, name, value));
                }
            }
        }
        return result;
    }

    // ==================== Топ игрока относительно других ====================

    /**
     * Получить позицию игрока в топе по статистике на конкретном сервере.
     *
     * @param statName название статистики
     * @param serverId ID сервера
     * @param playerId ID игрока
     * @return позиция (1-indexed) или -1, если игрок не в топе
     */
    public int getPlayerRank(String statName, int serverId, int playerId) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        String query = """
            SELECT COUNT(*) + 1 AS rank
            FROM player_stats
            WHERE stat_type_id = ? AND server_id = ?
              AND (COALESCE(value_double, 0) + COALESCE(value_int, 0)) >
                  (SELECT COALESCE(value_double, 0) + COALESCE(value_int, 0)
                   FROM player_stats
                   WHERE stat_type_id = ? AND server_id = ? AND player_id = ?)
            """;

        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setInt(4, serverId);
            stmt.setInt(5, playerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("rank");
                }
            }
        }
        return -1;
    }

    // ==================== Удобные методы ====================

    public List<LeaderboardEntry> getTopPlayTime(int serverId, int limit) throws SQLException {
        return getTop("play_time", serverId, limit);
    }

    public List<LeaderboardEntry> getTopMobsKilled(int serverId, int limit) throws SQLException {
        return getTop("mobs_killed", serverId, limit);
    }

    public List<LeaderboardEntry> getTopBlocksPlaced(int serverId, int limit) throws SQLException {
        return getTop("blocks_placed", serverId, limit);
    }

    public List<LeaderboardEntry> getTopBalance(int serverId, int limit) throws SQLException {
        return getTop("balance", serverId, limit);
    }

    public List<LeaderboardEntry> getTopKills(int serverId, int limit) throws SQLException {
        return getTop("kills", serverId, limit);
    }

    public List<LeaderboardEntry> getTopDeaths(int serverId, int limit) throws SQLException {
        return getTop("deaths", serverId, limit);
    }

    public List<LeaderboardEntry> getTopBossesKilled(int serverId, int limit) throws SQLException {
        return getTop("bosses_killed", serverId, limit);
    }

    // ==================== Приватные методы ====================

    private List<LeaderboardEntry> executeTopQuery(String query, int statTypeId, int serverId, int limit)
            throws SQLException {
        List<LeaderboardEntry> result = new ArrayList<>();
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    double doubleVal = rs.getDouble("value_double");
                    if (rs.wasNull()) {
                        doubleVal = rs.getInt("value_int");
                    }
                    result.add(new LeaderboardEntry(uuid, name, doubleVal));
                }
            }
        }
        return result;
    }

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        return statTypeCache.computeIfAbsent(statName, name -> {
            try {
                return statTypeDAO.createOrGetStatType(name);
            } catch (SQLException e) {
                throw new RuntimeException("Не удалось получить stat_type_id для " + name, e);
            }
        });
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        if (limit > MAX_LIMIT) return MAX_LIMIT;
        return limit;
    }

    /**
     * Очистить кэш stat_type_id (при перезагрузке конфига, например).
     */
    public void clearCache() {
        statTypeCache.clear();
    }
}