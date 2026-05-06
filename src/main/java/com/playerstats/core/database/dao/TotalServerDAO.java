package com.playerstats.core.database.dao;

import com.playerstats.core.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAO для агрегированной статистики по всем серверам и игрокам.
 * Использует правильную схему с stat_type_id.
 */
public class TotalServerDAO {

    private final DatabaseConnection db;
    private final StatTypeDAO statTypeDAO;

    // Кэш stat_name → stat_type_id
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public TotalServerDAO(DatabaseConnection db, StatTypeDAO statTypeDAO) {
        this.db = db;
        this.statTypeDAO = statTypeDAO;
    }

    // ==================== Статистика конкретного сервера ====================

    /**
     * Получить агрегированную статистику сервера.
     *
     * @param serverId ID сервера
     * @return Map<название_статистики, значение>
     */
    public Map<String, Number> getServerStats(int serverId) throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();

        // Количество уникальных игроков
        stats.put("uniquePlayers", getUniquePlayerCount(serverId));

        // Суммарные значения по типам статистики
        String sql = """
            SELECT st.name, SUM(COALESCE(ps.value_int, 0)) AS total_int,
                   SUM(COALESCE(ps.value_double, 0)) AS total_double
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            WHERE ps.server_id = ?
            GROUP BY st.name
            """;

        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double doubleVal = rs.getDouble("total_double");
                    if (rs.wasNull() || doubleVal == 0.0) {
                        stats.put(name, rs.getInt("total_int"));
                    } else {
                        stats.put(name, doubleVal);
                    }
                }
            }
        }
        return stats;
    }

    /**
     * Количество уникальных игроков на сервере.
     */
    public int getUniquePlayerCount(int serverId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT player_id) FROM player_stats WHERE server_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    // ==================== Общая статистика по всем серверам ====================

    /**
     * Получить общую агрегированную статистику по всем серверам.
     */
    public Map<String, Number> getTotalStats() throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();

        // Общее количество уникальных игроков
        stats.put("totalUniquePlayers", getTotalUniquePlayerCount());

        // Суммарные значения по всем серверам
        String sql = """
            SELECT st.name, SUM(COALESCE(ps.value_int, 0)) AS total_int,
                   SUM(COALESCE(ps.value_double, 0)) AS total_double
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            GROUP BY st.name
            """;

        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                double doubleVal = rs.getDouble("total_double");
                if (rs.wasNull() || doubleVal == 0.0) {
                    stats.put(name, rs.getInt("total_int"));
                } else {
                    stats.put(name, doubleVal);
                }
            }
        }
        return stats;
    }

    /**
     * Общее количество уникальных игроков по всем серверам.
     */
    public int getTotalUniquePlayerCount() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT player_id) FROM player_stats";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    /**
     * Общее количество серверов.
     */
    public int getServerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM servers";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    /**
     * Общее количество игроков в БД.
     */
    public int getTotalPlayerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM players";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    // ==================== Статистика конкретного игрока ====================

    /**
     * Получить суммарную статистику игрока по всем серверам.
     *
     * @param playerId ID игрока
     * @return Map<название_статистики, значение>
     */
    public Map<String, Number> getPlayerTotalStats(int playerId) throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();

        String sql = """
            SELECT st.name, SUM(COALESCE(ps.value_int, 0)) AS total_int,
                   SUM(COALESCE(ps.value_double, 0)) AS total_double
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            WHERE ps.player_id = ?
            GROUP BY st.name
            """;

        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double doubleVal = rs.getDouble("total_double");
                    if (rs.wasNull() || doubleVal == 0.0) {
                        stats.put(name, rs.getInt("total_int"));
                    } else {
                        stats.put(name, doubleVal);
                    }
                }
            }
        }
        return stats;
    }

    /**
     * Количество серверов, на которых играл игрок.
     */
    public int getPlayerServerCount(int playerId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT server_id) FROM player_stats WHERE player_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    // ==================== Топ по конкретной статистике ====================

    /**
     * Топ игроков по конкретной статистике (глобально по всем серверам).
     *
     * @param statName название статистики
     * @param limit    количество записей
     * @return Map<имя_игрока, значение>
     */
    public Map<String, Number> getTopPlayers(String statName, int limit) throws SQLException {
        Map<String, Number> top = new LinkedHashMap<>();
        int statTypeId = getOrCacheStatTypeId(statName);

        String sql = """
            SELECT p.name, SUM(COALESCE(ps.value_double, ps.value_int, 0)) AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ?
            GROUP BY p.id, p.name
            ORDER BY total_value DESC
            LIMIT ?
            """;

        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, Math.min(limit, 100));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    double value = rs.getDouble("total_value");
                    top.put(name, value);
                }
            }
        }
        return top;
    }

    // ==================== Быстрые методы для конкретных статов ====================

    public int getTotalPlayTime() throws SQLException {
        return getSingleTotalInt("play_time");
    }

    public int getTotalMobsKilled() throws SQLException {
        return getSingleTotalInt("mobs_killed");
    }

    public int getTotalBossesKilled() throws SQLException {
        return getSingleTotalInt("bosses_killed");
    }

    public int getTotalBlocksPlaced() throws SQLException {
        return getSingleTotalInt("blocks_placed");
    }

    public int getTotalBlocksBroken() throws SQLException {
        return getSingleTotalInt("blocks_broken");
    }

    public int getTotalKills() throws SQLException {
        return getSingleTotalInt("kills");
    }

    public int getTotalDeaths() throws SQLException {
        return getSingleTotalInt("deaths");
    }

    // ==================== Приватные методы ====================

    private int getSingleTotalInt(String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT SUM(COALESCE(value_int, 0)) FROM player_stats WHERE stat_type_id = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) return cached;

        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);
        return id;
    }

    /**
     * Очистить кэш stat_type_id.
     */
    public void clearCache() {
        statTypeCache.clear();
    }
}