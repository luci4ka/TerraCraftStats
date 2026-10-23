package ru.terracraft.stats.core.database.dao;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class StatHistoryDAO {

    private static final List<String> DEFAULT_SNAPSHOT_STATS = List.of(
            "play_time", "kills", "deaths", "mobs_killed",
            "blocks_placed", "blocks_broken", "bosses_killed"
    );

    private final PlayerStatsPlugin plugin;

    public StatHistoryDAO(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveSnapshot(int playerId, int statTypeId, int serverId, int value) throws SQLException {
        LocalDate today = LocalDate.now();
        String query = "INSERT INTO stat_history (player_id, stat_type_id, server_id, value, date) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = ?";
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, serverId);
            stmt.setInt(4, value);
            stmt.setDate(5, java.sql.Date.valueOf(today));
            stmt.setInt(6, value);
            stmt.executeUpdate();
        }
    }

    public List<Integer> getHistory(int playerId, int statTypeId, int serverId, int days) throws SQLException {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        String sql = """
                SELECT value FROM stat_history
                WHERE player_id = ? AND stat_type_id = ? AND server_id = ?
                  AND date >= ?
                ORDER BY date ASC
                """;
        List<Integer> history = new ArrayList<>();
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, serverId);
            stmt.setDate(4, java.sql.Date.valueOf(from));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) history.add(rs.getInt("value"));
            }
        }
        return history;
    }

    public List<Map<String, Object>> getHistoryWithDates(int playerId, int statTypeId,
                                                         int serverId, int days) throws SQLException {
        LocalDate from = LocalDate.now().minusDays(days - 1);
        String sql = """
            SELECT value, date FROM stat_history
            WHERE player_id = ? AND stat_type_id = ? AND server_id = ?
              AND date >= ?
            ORDER BY date ASC
            """;
        List<Map<String, Object>> history = new ArrayList<>();
        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, statTypeId);
            stmt.setInt(3, serverId);
            stmt.setDate(4, java.sql.Date.valueOf(from));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", rs.getDate("date").toString());
                    point.put("value", rs.getInt("value"));
                    history.add(point);
                }
            }
        }
        return history;
    }

    /**
     * Снапшот статистики всех игроков за сегодня одним {@code INSERT ... SELECT}.
     *
     * <p><b>Почему без промежуточного списка:</b> на больших серверах
     * {@code SELECT} может вернуть десятки тысяч строк — держать их в
     * {@code List<Snapshot>} в памяти опасно (OOM). Здесь всё делает БД:
     * {@code SELECT} сразу вставляет в {@code stat_history} с
     * {@code ON DUPLICATE KEY UPDATE}.</p>
     *
     * <p><b>Balance:</b> не снапшотится — он в {@code value_double}, а
     * {@code stat_history.value} — {@code INT}. В {@link #mapCategoryToStat}
     * категория {@code balance} исключена.</p>
     */
    public void snapshotAllPlayers() throws SQLException {
        List<String> statNames = resolveSnapshotStats();
        if (statNames.isEmpty()) {
            plugin.getLogger().info("Снапшот: список стат пуст — пропускаем.");
            return;
        }

        String in = String.join(",", Collections.nCopies(statNames.size(), "?"));
        String insertSelect = "INSERT INTO stat_history (player_id, stat_type_id, server_id, value, date) " +
                "SELECT ps.player_id, ps.stat_type_id, ps.server_id, ps.value_int, CURDATE() " +
                "FROM player_stats ps " +
                "WHERE ps.value_int IS NOT NULL " +
                "AND ps.stat_type_id IN (SELECT id FROM stat_types WHERE name IN (" + in + ")) " +
                "ON DUPLICATE KEY UPDATE value = VALUES(value)";

        try (Connection conn = plugin.getSqlManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSelect)) {
            int idx = 1;
            for (String name : statNames) stmt.setString(idx++, name);
            int inserted = stmt.executeUpdate();
            plugin.getLogger().info("Снапшот сохранён: " + inserted + " записей за " + LocalDate.now()
                    + " (" + statNames.size() + " стат: " + String.join(", ", statNames) + ")");
        }
    }

    /**
     * Возвращает список стат для снапшота: из конфига sparkline (только {@code true}),
     * либо дефолтный набор, если конфиг недоступен.
     */
    private List<String> resolveSnapshotStats() {
        try {
            var cm = plugin.getConfigManager();
            if (cm == null || cm.sparkline() == null) return DEFAULT_SNAPSHOT_STATS;
            Map<String, Boolean> cats = cm.sparkline().categories();
            if (cats == null || cats.isEmpty()) return DEFAULT_SNAPSHOT_STATS;

            List<String> result = new ArrayList<>();
            for (Map.Entry<String, Boolean> e : cats.entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) {
                    String statName = mapCategoryToStat(e.getKey());
                    if (statName != null) result.add(statName);
                }
            }
            return result.isEmpty() ? DEFAULT_SNAPSHOT_STATS : result;
        } catch (Exception e) {
            return DEFAULT_SNAPSHOT_STATS;
        }
    }

    /**
     * Категория sparkline → statName.
     *
     * <p><b>Balance исключён:</b> он хранится в {@code value_double}, а
     * {@code stat_history.value} — {@code INT}. Снапшот его просто не увидит
     * (всегда {@code NULL value_int}). Если нужен sparkline для balance —
     * отдельная доработка со сменой типа колонки.</p>
     */
    private String mapCategoryToStat(String category) {
        return switch (category.toLowerCase()) {
            case "kills" -> "kills";
            case "deaths" -> "deaths";
            case "elite" -> "elite_mobs_killed";
            case "blocks" -> "blocks_placed";
            case "playtime" -> "play_time";
            // "balance" -> не снапшотится (value_double, а колонка INT)
            default -> null;
        };
    }
}