package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStatsDAO {

    private final PlayerStatsPlugin plugin;
    private final StatTypeDAO statTypeDAO;
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public PlayerStatsDAO(PlayerStatsPlugin plugin, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.statTypeDAO = statTypeDAO;
    }

    // ==================== Int ====================

    /**
     * Записывает int-значение. Обнуляет {@code value_double},
     * чтобы не осталось старое значение с прошлой записи.
     */
    public void setStat(int playerId, int serverId, int statTypeId, int value) throws SQLException {
        String query = "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE value_int = ?, value_double = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
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
        setStat(playerId, serverId, getOrCacheStatTypeId(statName), value);
    }

    /**
     * Читает только {@code value_int}. NULL → 0.
     */
    public int getStat(int playerId, int serverId, int statTypeId) throws SQLException {
        String query = "SELECT value_int FROM player_stats WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt("value_int");
                    return rs.wasNull() ? 0 : v;
                }
            }
        }
        return 0;
    }

    public int getStat(int playerId, int serverId, String statName) throws SQLException {
        return getStat(playerId, serverId, getOrCacheStatTypeId(statName));
    }

    // ==================== Rollback ====================

    /**
     * Сбрасывает все статы игрока: обнуляет и {@code value_int}, и {@code value_double}.
     */
    public int resetAllStats(int playerId, int serverId) throws SQLException {
        String sql = "UPDATE player_stats SET value_int = NULL, value_double = NULL " +
                "WHERE player_id = ? AND server_id = ? " +
                "AND (value_int IS NOT NULL OR value_double IS NOT NULL)";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            return stmt.executeUpdate();
        }
    }

    public void recordDelta(int playerId, String statName, int delta) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "INSERT INTO stat_deltas (player_id, stat_type_id, delta, timestamp) VALUES (?, ?, ?, ?)";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, delta);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public int sumDeltasSince(int playerId, String statName, long sinceMillis) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT COALESCE(SUM(delta), 0) FROM stat_deltas " +
                "WHERE player_id = ? AND stat_type_id = ? AND timestamp >= ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
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

    public int cleanupDeltasOlderThan(long millis) throws SQLException {
        String sql = "DELETE FROM stat_deltas WHERE timestamp < ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis() - millis);
            return stmt.executeUpdate();
        }
    }

    // ==================== Double ====================

    /**
     * Записывает double-значение. Обнуляет {@code value_int}.
     */
    public void setStatDouble(int playerId, int serverId, String statName, double value) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, NULL, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = ?, value_int = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setDouble(4, value);
            stmt.setDouble(5, value);
            stmt.executeUpdate();
        }
    }

    public double getStatDouble(int playerId, int serverId, String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "SELECT CASE WHEN value_double IS NOT NULL THEN value_double " +
                "WHEN value_int IS NOT NULL THEN CAST(value_int AS DOUBLE) ELSE 0 END AS value " +
                "FROM player_stats WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
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
        String query = "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_string) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value_string = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
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
        String query = "SELECT value_string FROM player_stats WHERE player_id = ? AND server_id = ? AND stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
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

    /**
     * Все статы игрока (int-проекция).
     *
     * <p><b>Внимание:</b> {@code value_double} приводится к {@code SIGNED}
     * (усечение вниз). Для {@code balance} это теряет копейки — осознанно,
     * т.к. метод используется для int-сравнений (achievements/thresholds).</p>
     */
    public Map<String, Integer> getAllStatsForPlayer(int playerId, int serverId) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name,
                   COALESCE(ps.value_int, CAST(ps.value_double AS SIGNED), 0) AS value
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ? AND ps.server_id = ?
            """;
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) stats.put(rs.getString("name"), rs.getInt("value"));
            }
        }
        return stats;
    }

    public Map<String, Integer> getTotalStatsForPlayer(int playerId) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name,
                   SUM(COALESCE(ps.value_int, CAST(ps.value_double AS SIGNED), 0)) AS total
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ?
            GROUP BY s.name
            """;
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) stats.put(rs.getString("name"), rs.getInt("total"));
            }
        }
        return stats;
    }

    public Map<String, Double> getTotalStatsDoubleForPlayer(int playerId) throws SQLException {
        Map<String, Double> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name,
                   SUM(COALESCE(ps.value_double, CAST(ps.value_int AS DOUBLE), 0)) AS total
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ?
            GROUP BY s.name
            """;
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) stats.put(rs.getString("name"), rs.getDouble("total"));
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

    public Map<String, StatValue> getAllStatsDetailed(int playerId, int serverId) throws SQLException {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        String query = """
            SELECT s.name, ps.value_int, ps.value_double, ps.value_string
            FROM player_stats ps
            JOIN stat_types s ON ps.stat_type_id = s.id
            WHERE ps.player_id = ? AND ps.server_id = ?
            """;
        try (Connection conn = plugin.getSqlManager().getConnection();
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
                    stats.put(name, new StatValue(intNull ? 0 : intVal,
                            doubleNull ? 0.0 : doubleVal, strVal));
                }
            }
        }
        return stats;
    }

    // ==================== Increment ====================

    /**
     * Атомарный инкремент int-значения. Устанавливает {@code value_double = NULL}.
     */
    public void incrementStat(int playerId, int serverId, String statName, int increment) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String query = "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE value_int = COALESCE(value_int, 0) + ?, value_double = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
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
        String query = "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, NULL, ?) " +
                "ON DUPLICATE KEY UPDATE value_double = COALESCE(value_double, 0) + ?, value_int = NULL";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setDouble(4, increment);
            stmt.setDouble(5, increment);
            stmt.executeUpdate();
        }
    }

    /**
     * Статы по префиксу.
     *
     * <p><b>LIKE-экранирование:</b> префикс может содержать {@code _} (например,
     * {@code mob_killed_}, {@code block_placed_}) — в LIKE это wildcard «любой
     * один символ». Экранируем {@code _}, {@code %} и {@code \}, и используем
     * {@code ESCAPE '\\'}.</p>
     */
    public Map<String, Integer> getStatsByPrefix(int playerId, int serverId, String prefix) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        // MariaDB: ESCAPE должен быть ОДНИМ символом. '\\' ломает парсер.
        // Используем '!' как escape-символ.
        String escaped = prefix
                .replace("!", "!!")
                .replace("_", "!_")
                .replace("%", "!%");

        String query = """
        SELECT st.name, COALESCE(ps.value_int, 0) AS value_int
        FROM player_stats ps
        JOIN stat_types st ON ps.stat_type_id = st.id
        WHERE ps.player_id = ? AND ps.server_id = ? AND st.name LIKE ? ESCAPE '!'
        """;
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setString(3, escaped + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) stats.put(rs.getString("name"), rs.getInt("value_int"));
            }
        }
        return stats;
    }

    /**
     * Батч-инкремент нескольких int-стат одним prepared statement.
     *
     * <p><b>Резолв statTypeId — до открытия PreparedStatement:</b> если
     * {@link #getOrCacheStatTypeId} кинет {@code SQLException} в середине
     * батча, {@code addBatch()} уже накопил часть параметров — но без
     * {@code executeBatch()} они не уйдут в БД, а statement закроется.
     * Всё равно резолвим заранее, чтобы не держать лишний коннект.</p>
     */
    public void incrementStatsBatch(int playerId, int serverId,
                                    Map<String, Integer> increments) throws SQLException {
        if (increments == null || increments.isEmpty()) return;

        // Резолвим все statTypeId заранее (вне try-with-resources для connection)
        Map<Integer, Integer> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : increments.entrySet()) {
            int inc = e.getValue();
            if (inc == 0) continue;
            resolved.put(getOrCacheStatTypeId(e.getKey()), inc);
        }
        if (resolved.isEmpty()) return;

        String query = "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE value_int = COALESCE(value_int, 0) + ?, value_double = NULL";

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            for (Map.Entry<Integer, Integer> e : resolved.entrySet()) {
                int statTypeId = e.getKey();
                int inc = e.getValue();
                stmt.setInt(1, playerId);
                stmt.setInt(2, serverId);
                stmt.setInt(3, statTypeId);
                stmt.setInt(4, inc);
                stmt.setInt(5, inc);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Атомарно обновляет value_int, только если текущее значение равно expectedOld.
     *
     * <p>Если строки нет — создаёт её при {@code expectedOld == 0},
     * иначе считает конфликтом (caller повторит).</p>
     */
    public boolean compareAndSetStat(int playerId, int serverId, int statTypeId,
                                     int expectedOld, int newValue) throws SQLException {
        String update = "UPDATE player_stats SET value_int = ?, value_double = NULL " +
                "WHERE player_id = ? AND server_id = ? AND stat_type_id = ? " +
                "AND (value_int = ? OR (value_int IS NULL AND ? = 0))";

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(update)) {
            stmt.setInt(1, newValue);
            stmt.setInt(2, playerId);
            stmt.setInt(3, serverId);
            stmt.setInt(4, statTypeId);
            stmt.setInt(5, expectedOld);
            stmt.setInt(6, expectedOld);
            if (stmt.executeUpdate() > 0) return true;
        }

        if (expectedOld != 0) return false;

        String insert = "INSERT INTO player_stats (player_id, server_id, stat_type_id, value_int, value_double) " +
                "VALUES (?, ?, ?, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE value_int = value_int";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, statTypeId);
            stmt.setInt(4, newValue);
            return stmt.executeUpdate() > 0;
        }
    }

    // ==================== Кэш ====================

    private int getOrCacheStatTypeId(String statName) throws SQLException {
        Integer cached = statTypeCache.get(statName);
        if (cached != null) return cached;
        int id = statTypeDAO.createOrGetStatType(statName);
        statTypeCache.put(statName, id);
        return id;
    }

    public void clearCache() {
        statTypeCache.clear();
    }
}