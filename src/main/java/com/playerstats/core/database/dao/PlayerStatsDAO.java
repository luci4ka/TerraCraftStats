package com.playerstats.core.database.dao;

import com.playerstats.Main;
import com.playerstats.core.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStatsDAO {

    private final Main plugin;
    private final DatabaseConnection db;
    private final boolean isMySQL;
    private final StatTypeDAO statTypeDAO;

    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public PlayerStatsDAO(Main plugin, DatabaseConnection db, boolean isMySQL, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.db = db;
        this.isMySQL = isMySQL;
        this.statTypeDAO = statTypeDAO;
    }

    // ==================== Int ====================

    public void setStat(int playerId, int serverId, int statTypeId, int value) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: setStat(playerId=" + playerId
                    + ", serverId=" + serverId + ", statTypeId=" + statTypeId
                    + ", value=" + value + ")");
        }

        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_int = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_int = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setInt(4, value);
            stmt.setInt(5, value);
            stmt.executeUpdate();
        }
    }

    public void setStat(int playerId, int serverId, String statName, int value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        setStat(playerId, serverId, statTypeId, value);
    }

    /**
     * Получить int-статистику.
     * Читает ТОЛЬКО value_int — не трогает value_double.
     */
    public int getStat(int playerId, int serverId, int statTypeId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getStat(playerId=" + playerId
                    + ", serverId=" + serverId + ", statTypeId=" + statTypeId + ")");
        }

        String query = "SELECT value_int FROM player_stats " +
                "WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int result = rs.getInt("value_int");
                    if (isVerboseDebug()) {
                        plugin.getLogger().info("[DEBUG] SQL: getStat → " + result);
                    }
                    return result;
                }
            }
        }
        return 0;
    }

    public int getStat(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        return getStat(playerId, serverId, statTypeId);
    }

    // ==================== Rollback ====================

    /**
     * Обнулить все int-статы игрока на сервере.
     * НЕ трогает value_double (balance) и value_string (group).
     *
     * @return количество обновлённых строк
     */
    public int resetAllStats(int playerId, int serverId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: resetAllStats(playerId=" + playerId
                    + ", serverId=" + serverId + ")");
        }

        String sql = "UPDATE player_stats SET value_int = 0 "
                + "WHERE player_id = ? AND server_id = ? AND value_int IS NOT NULL";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Записать дельту в журнал stat_deltas.
     * Используется, если {@code rollback.track-deltas: true}.
     */
    public void recordDelta(int playerId, String statName, int delta) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        long now = System.currentTimeMillis();

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: recordDelta(playerId=" + playerId
                    + ", statName=" + statName + ", delta=" + delta + ")");
        }

        String sql = "INSERT INTO stat_deltas "
                + "(player_id, stat_type_id, delta, timestamp) VALUES (?, ?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, delta);
            stmt.setLong(4, now);
            stmt.executeUpdate();
        }
    }

    /**
     * Сумма всех дельт с определённого времени.
     * Для {@code /stats rollback <player> --from <date> <stat>}.
     *
     * @return сумма дельт (0, если нет записей)
     */
    public int sumDeltasSince(int playerId, String statName, long sinceMillis)
            throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        String sql = "SELECT COALESCE(SUM(delta), 0) FROM stat_deltas "
                + "WHERE player_id = ? AND stat_type_id = ? AND timestamp >= ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setLong(3, sinceMillis);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Удалить дельты старше N миллисекунд (cleanup).
     */
    public int cleanupDeltasOlderThan(long millis) throws SQLException {
        long threshold = System.currentTimeMillis() - millis;

        String sql = "DELETE FROM stat_deltas WHERE timestamp < ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, threshold);
            return stmt.executeUpdate();
        }
    }

    // ==================== Double ====================

    public void setStatDouble(int playerId, int serverId, String statName, double value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: setStatDouble(playerId=" + playerId
                    + ", statName=" + statName + ", value=" + value + ")");
        }

        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_double = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_double = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setDouble(4, value);
            stmt.setDouble(5, value);
            stmt.executeUpdate();
        }
    }

    /**
     * Получить double-статистику.
     * Читает value_double; если NULL — value_int.
     * Если value_double = 0.0 (но не NULL) — вернёт 0.0.
     * Это правильно для balance.
     */
    public double getStatDouble(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getStatDouble(playerId=" + playerId
                    + ", statName=" + statName + ")");
        }

        String query = "SELECT " +
                "CASE WHEN value_double IS NOT NULL THEN value_double " +
                "     WHEN value_int IS NOT NULL THEN CAST(value_int AS DOUBLE) " +
                "     ELSE 0 END AS value " +
                "FROM player_stats " +
                "WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("value");
            }
        }
        return 0.0;
    }

    // ==================== String ====================

    public void setStatString(int playerId, int serverId, String statName, String value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: setStatString(playerId=" + playerId
                    + ", statName=" + statName + ", value=" + value + ")");
        }

        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_string) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_string = ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_string) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) DO UPDATE SET value_string = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setString(4, value);
            stmt.setString(5, value);
            stmt.executeUpdate();
        }
    }

    public String getStatString(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "SELECT value_string FROM player_stats " +
                "WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("value_string");
            }
        }
        return null;
    }

    // ==================== Сводные данные ====================

    public Map<String, Integer> getAllStatsForPlayer(int playerId, int serverId) throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getAllStatsForPlayer(playerId=" + playerId
                    + ", serverId=" + serverId + ")");
        }

        Map<String, Integer> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name,
                   CASE
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       WHEN ps.value_double IS NOT NULL THEN CAST(ps.value_double AS INT)
                       ELSE 0
                   END AS value
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ? AND ps.server_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getInt("value"));
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getAllStatsForPlayer → " + stats.size() + " записей");
        }

        return stats;
    }

    public Map<String, Integer> getTotalStatsForPlayer(int playerId) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name,
                   SUM(CASE
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       WHEN ps.value_double IS NOT NULL THEN ps.value_double
                       ELSE 0
                   END) AS total
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ?
            GROUP BY s.name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getInt("total"));
                }
            }
        }
        return stats;
    }

    public Map<String, Double> getTotalStatsDoubleForPlayer(int playerId) throws SQLException {
        Map<String, Double> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name,
                   SUM(CASE
                       WHEN ps.value_double IS NOT NULL THEN ps.value_double
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       ELSE 0
                   END) AS total
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ?
            GROUP BY s.name
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getDouble("total"));
                }
            }
        }
        return stats;
    }

    public Map<String, StatValue> getAllStatsDetailed(int playerId, int serverId) throws SQLException {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name, ps.value_int, ps.value_double, ps.value_string
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ? AND ps.server_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");

                    int intVal = rs.getInt("value_int");
                    boolean intNull = rs.wasNull();

                    double doubleVal = rs.getDouble("value_double");
                    boolean doubleNull = rs.wasNull();

                    String strVal = rs.getString("value_string");

                    stats.put(name, new StatValue(
                            intNull ? 0 : intVal,
                            doubleNull ? 0.0 : doubleVal,
                            strVal
                    ));
                }
            }
        }
        return stats;
    }

    public record StatValue(int valueInt, double valueDouble, String valueString) {
        public Number getNumberValue() {
            if (valueDouble != 0.0) return valueDouble;
            return valueInt;
        }
    }

    // ==================== Кэширование ====================

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) {
            if (isVerboseDebug()) {
                plugin.getLogger().info("[DEBUG] Кэш stat_type: " + statName
                        + " → " + cached);
            }
            return cached;
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Создание stat_type: " + statName);
        }

        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Создан stat_type: " + statName
                    + " → " + id);
        }

        return id;
    }

    public void clearCache() {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Очистка кэша stat_type ("
                    + statTypeCache.size() + " записей)");
        }
        statTypeCache.clear();
    }

    // ==================== ДЕПРЕКЕЙТЕД ====================

    @Deprecated
    public List<Map.Entry<String, Number>> getTopPlayers(String statName, int limit) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = """
            SELECT p.name,
                   CASE
                       WHEN ps.value_int IS NOT NULL THEN ps.value_int
                       WHEN ps.value_double IS NOT NULL THEN ps.value_double
                       ELSE 0
                   END AS value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ?
            ORDER BY value DESC
            LIMIT ?
            """;

        List<Map.Entry<String, Number>> topList = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String playerName = rs.getString("name");
                    double value = rs.getDouble("value");
                    topList.add(new AbstractMap.SimpleEntry<>(playerName, value));
                }
            }
        }
        return topList;
    }

    public void incrementStat(int playerId, int serverId, String statName, int increment) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: incrementStat(playerId=" + playerId
                    + ", statName=" + statName + ", increment=" + increment
                    + ", statTypeId=" + statTypeId + ")");
        }

        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_int = value_int + ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) " +
                "DO UPDATE SET value_int = value_int + ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setInt(4, increment);
            stmt.setInt(5, increment);
            stmt.executeUpdate();
        }
    }

    public void incrementStatDouble(int playerId, int serverId, String statName, double increment) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: incrementStatDouble(playerId=" + playerId
                    + ", statName=" + statName + ", increment=" + increment + ")");
        }

        String query = isMySQL
                ? "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_double = value_double + ?"
                : "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_double) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT(player_id, server_id, stat_type_id) " +
                "DO UPDATE SET value_double = value_double + ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setDouble(4, increment);
            stmt.setDouble(5, increment);
            stmt.executeUpdate();
        }
    }

    public Map<String, Integer> getStatsByPrefix(int playerId, int serverId, String prefix)
            throws SQLException {
        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getStatsByPrefix(playerId=" + playerId
                    + ", prefix=" + prefix + ")");
        }

        Map<String, Integer> stats = new LinkedHashMap<>();
        String query = """
        SELECT st.name, ps.value_int
        FROM player_stats ps
        JOIN stat_types st ON ps.stat_type_id = st.id
        WHERE ps.player_id = ? AND ps.server_id = ?
        AND st.name LIKE ?
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setString(3, prefix + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getInt("value_int"));
                }
            }
        }

        if (isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] SQL: getStatsByPrefix(" + prefix
                    + ") → " + stats.size() + " записей");
        }

        return stats;
    }

    private boolean isVerboseDebug() {
        try {
            return plugin.getConfigHandler().isVerboseDebug();
        } catch (Exception e) {
            return false;
        }
    }
}