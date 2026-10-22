package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TotalServerDAO {

    /**
     * Сумма с приоритетом {@code value_double} над {@code value_int}.
     *
     * <p>Схема с {@code DEFAULT NULL}: {@code IS NOT NULL} осмыслен.</p>
     */
    private static final String SUM_EFFECTIVE =
            "SUM(CASE " +
                    "  WHEN ps.value_double IS NOT NULL THEN ps.value_double " +
                    "  WHEN ps.value_int IS NOT NULL THEN ps.value_int " +
                    "  ELSE 0 END)";

    private final PlayerStatsPlugin plugin;
    private final StatTypeDAO statTypeDAO;
    private final Map<String, Integer> statTypeCache = new ConcurrentHashMap<>();

    public TotalServerDAO(PlayerStatsPlugin plugin, StatTypeDAO statTypeDAO) {
        this.plugin = plugin;
        this.statTypeDAO = statTypeDAO;
    }

    public Map<String, Number> getServerStats(int serverId) throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();
        stats.put("uniquePlayers", getUniquePlayerCount(serverId));

        String sql = """
            SELECT st.name, %s AS total_value
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            WHERE ps.server_id = ?
            GROUP BY st.name
            """.formatted(SUM_EFFECTIVE);

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getDouble("total_value"));
                }
            }
        }
        return stats;
    }

    public int getUniquePlayerCount(int serverId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT player_id) FROM player_stats WHERE server_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public Map<String, Number> getTotalStats() throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();
        stats.put("totalUniquePlayers", getTotalUniquePlayerCount());

        String sql = """
            SELECT st.name, %s AS total_value
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            GROUP BY st.name
            """.formatted(SUM_EFFECTIVE);

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                stats.put(rs.getString("name"), rs.getDouble("total_value"));
            }
        }
        return stats;
    }

    public int getTotalUniquePlayerCount() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT player_id) FROM player_stats";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int getServerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM servers";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int getTotalPlayerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM players";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public Map<String, Number> getPlayerTotalStats(int playerId) throws SQLException {
        Map<String, Number> stats = new LinkedHashMap<>();
        String sql = """
            SELECT st.name, %s AS total_value
            FROM player_stats ps
            JOIN stat_types st ON ps.stat_type_id = st.id
            WHERE ps.player_id = ?
            GROUP BY st.name
            """.formatted(SUM_EFFECTIVE);

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("name"), rs.getDouble("total_value"));
                }
            }
        }
        return stats;
    }

    public int getPlayerServerCount(int playerId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT server_id) FROM player_stats WHERE player_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public Map<String, Number> getTopPlayers(String statName, int limit) throws SQLException {
        Map<String, Number> top = new LinkedHashMap<>();
        int statTypeId = getOrCacheStatTypeId(statName);

        String sql = """
            SELECT p.name, %s AS total_value
            FROM player_stats ps
            JOIN players p ON ps.player_id = p.id
            WHERE ps.stat_type_id = ?
            GROUP BY p.id, p.name
            ORDER BY total_value DESC
            LIMIT ?
            """.formatted(SUM_EFFECTIVE);

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            stmt.setInt(2, Math.min(limit, 100));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    top.put(rs.getString("name"), rs.getDouble("total_value"));
                }
            }
        }
        return top;
    }

    public int getTotalPlayTime() throws SQLException { return getSingleTotalInt("play_time"); }
    public int getTotalMobsKilled() throws SQLException { return getSingleTotalInt("mobs_killed"); }
    public int getTotalBossesKilled() throws SQLException { return getSingleTotalInt("bosses_killed"); }
    public int getTotalBlocksPlaced() throws SQLException { return getSingleTotalInt("blocks_placed"); }
    public int getTotalBlocksBroken() throws SQLException { return getSingleTotalInt("blocks_broken"); }
    public int getTotalKills() throws SQLException { return getSingleTotalInt("kills"); }
    public int getTotalDeaths() throws SQLException { return getSingleTotalInt("deaths"); }

    /**
     * Сумма double-значений (для balance). Приоритет — {@code value_double},
     * fallback — {@code CAST(value_int AS DOUBLE)}.
     */
    public double getSingleTotalDouble(String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT SUM(CASE WHEN value_double IS NOT NULL THEN value_double " +
                "WHEN value_int IS NOT NULL THEN CAST(value_int AS DOUBLE) ELSE 0 END) " +
                "FROM player_stats WHERE stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statTypeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0.0;
    }

    /**
     * Сумма int-значений.
     *
     * <p><b>Приоритет:</b> {@code value_double} первый (как в {@link #SUM_EFFECTIVE}
     * и {@code LeaderboardDAO.EFFECTIVE_VALUE}). Для int-стат это эквивалентно
     * (double обычно нет), но унификация убирает расхождение с остальными
     * методами суммирования.</p>
     */
    private int getSingleTotalInt(String statName) throws SQLException {
        int statTypeId = getOrCacheStatTypeId(statName);
        String sql = "SELECT SUM(CASE WHEN value_double IS NOT NULL THEN CAST(value_double AS SIGNED) " +
                "WHEN value_int IS NOT NULL THEN value_int ELSE 0 END) " +
                "FROM player_stats WHERE stat_type_id = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public void clearCache() {
        statTypeCache.clear();
    }
}