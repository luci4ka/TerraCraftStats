package ru.terracraft.stats.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;
import ru.terracraft.stats.core.services.PlayerGroupService;
import ru.terracraft.stats.core.services.TopCache;
import ru.terracraft.stats.core.utils.FormatUtil;
import ru.terracraft.stats.core.utils.TimeUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Плейсхолдеры PlayerStats для PlaceholderAPI.
 *
 * <p><b>Никаких SQL-запросов в onRequest:</b> все данные читаются из
 * in-memory кэша. Кэш заполняется в фоне при первом обращении к игроку
 * и инвалидируется из {@code StatsListener} после каждого изменения статы
 * поэтому внутри onRequest только map.get().</p>
 *
 * <p>Префикс: <code>%ps_...%</code></p>
 */
public class PlayerStatsExpansion extends PlaceholderExpansion {

    private static final String IDENTIFIER = "ps";
    private static final String AUTHOR = "TerraCraft";
    private static final String VERSION = "1.1.0";

    /** TTL кэша статов на игрока — 30 секунд. */
    private static final long PLAYER_CACHE_TTL_MS = 30_000L;

    /** Доп. защита: не спамить фоновыми fill'ами чаще раза в 1 сек. */
    private static final long FILL_THROTTLE_MS = 1_000L;

    private final PlayerStatsPlugin plugin;

    /** uuid → snapshot статов. Заполняется в async. */
    private final Map<UUID, PlayerSnapshot> playerCache = new ConcurrentHashMap<>();
    /** uuid → timestamp последнего fill (мс). */
    private final Map<UUID, Long> fillStamp = new ConcurrentHashMap<>();
    /** uuid → «fill уже в полёте». */
    private final Map<UUID, Boolean> fillInFlight = new ConcurrentHashMap<>();

    public PlayerStatsExpansion(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== PlaceholderExpansion ====================

    @Override public @NotNull String getIdentifier() { return IDENTIFIER; }
    @Override public @NotNull String getAuthor() { return AUTHOR; }
    @Override public @NotNull String getVersion() { return VERSION; }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    // ==================== onRequest ====================

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        // Глобальные плейсхолдеры (без игрока)
        switch (key) {
            case "server_name" -> { return plugin.getServerName(); }
            case "server_id"   -> { return String.valueOf(plugin.getServerId()); }
        }

        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();
        PlayerSnapshot snap = getOrScheduleSnapshot(uuid);

        // Если снапшота ещё нет — отдаём пустую строку, fill уже в полёте.
        if (snap == null) {
            return "";
        }

        return render(player, snap, key);
    }

    // ==================== Рендер ====================

    private String render(OfflinePlayer player, PlayerSnapshot snap, String key) {
        // ---- Топы: top_<stat>_<n>_name / top_<stat>_<n>_value ----
        if (key.startsWith("top_")) {
            return resolveTopPlaceholder(key);
        }

        // ---- Позиция игрока в топе: rank_<stat> ----
        if (key.startsWith("rank_")) {
            String stat = key.substring("rank_".length());
            return resolveRank(snap.uuid(), stat);
        }

        // ---- Спец. плейсхолдеры ----
        switch (key) {
            case "group"             -> { return snap.group() != null ? snap.group() : ""; }
            case "balance"           -> { return resolveBalanceFormatted(player, snap); }
            case "balance_raw"       -> { return String.format(Locale.ROOT, "%.2f", snap.balance()); }
            case "play_time"         -> { return resolvePlayTimeFormatted(player, snap, false); }
            case "play_time_seconds" -> { return String.valueOf(snap.stat("play_time")); }
            case "total_play_time"   -> { return resolvePlayTimeFormatted(player, snap, true); }
            case "kd"                -> { return resolveKD(player, snap); }
            case "unique_players"    -> { return resolveUniquePlayers(); }
        }

        // ---- Обычные статы ----
        return String.valueOf(snap.stat(key));
    }

    private String resolvePlayTimeFormatted(OfflinePlayer player, PlayerSnapshot snap, boolean total) {
        int seconds = total ? snap.totalPlayTime() : snap.stat("play_time");
        LanguageManager lang = plugin.getLanguageManager();
        if (player instanceof Player online) {
            return TimeUtil.formatTime(lang, online, seconds);
        }
        return TimeUtil.formatTime(lang, seconds);
    }

    private String resolveKD(OfflinePlayer player, PlayerSnapshot snap) {
        LanguageManager lang = plugin.getLanguageManager();
        if (player instanceof Player online) {
            return FormatUtil.formatKD(lang, online, snap.stat("kills"), snap.stat("deaths"));
        }
        return FormatUtil.formatKD(lang, snap.stat("kills"), snap.stat("deaths"));
    }

    private String resolveBalanceFormatted(OfflinePlayer player, PlayerSnapshot snap) {
        LanguageManager lang = plugin.getLanguageManager();
        if (player instanceof Player online) {
            return FormatUtil.formatBalance(plugin, lang, online, snap.balance());
        }
        return FormatUtil.formatBalance(lang, snap.balance());
    }

    // ==================== Топы ====================

    private String resolveTopPlaceholder(String key) {
        // key: "top_<stat>_<n>_name"
        String rest = key.substring("top_".length());

        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore <= 0) return "";
        String field = rest.substring(lastUnderscore + 1);
        if (!field.equals("name") && !field.equals("value")) return "";

        String left = rest.substring(0, lastUnderscore);
        int prevUnderscore = left.lastIndexOf('_');
        if (prevUnderscore <= 0) return "";

        String stat = left.substring(0, prevUnderscore);
        int rank;
        try {
            rank = Integer.parseInt(left.substring(prevUnderscore + 1));
        } catch (NumberFormatException e) {
            return "";
        }
        if (rank < 1) return "";

        TopCache topCache = plugin.getTopCache();
        if (topCache == null) return "";

        var entries = topCache.getTop(stat); // никогда не блокирует
        if (entries == null || entries.size() < rank) return "";
        var entry = entries.get(rank - 1);

        return field.equals("name")
                ? entry.playerName()
                : String.format(Locale.ROOT, "%,.0f", entry.value());
    }

    private String resolveRank(UUID uuid, String stat) {
        TopCache topCache = plugin.getTopCache();
        if (topCache == null) return "";

        var entries = topCache.getTop(stat); // никогда не блокирует
        if (entries == null) return "";

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).uuid().equals(uuid)) {
                return String.valueOf(i + 1);
            }
        }
        return "";
    }

    // ==================== Snapshot ====================

    /**
     * Снимок статов игрока. Заполняется один раз в async,
     * читается сколько угодно раз из onRequest.
     */
    private record PlayerSnapshot(
            UUID uuid,
            Map<String, Integer> stats,
            int totalPlayTime,
            double balance,
            String group
    ) {
        int stat(String name) {
            return stats.getOrDefault(name, 0);
        }
    }

    private PlayerSnapshot getOrScheduleSnapshot(UUID uuid) {
        PlayerSnapshot snap = playerCache.get(uuid);
        Long ts = fillStamp.get(uuid);

        boolean stale = snap == null || ts == null
                || (System.currentTimeMillis() - ts) > PLAYER_CACHE_TTL_MS;

        if (stale) {
            scheduleFill(uuid);
        }
        return snap;
    }

    private void scheduleFill(UUID uuid) {
        Long ts = fillStamp.get(uuid);
        if (ts != null && (System.currentTimeMillis() - ts) < FILL_THROTTLE_MS) {
            return;
        }
        if (fillInFlight.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return;
        }
        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerSnapshot snap = loadSnapshot(uuid);
                if (snap != null) {
                    playerCache.put(uuid, snap);
                    fillStamp.put(uuid, System.currentTimeMillis());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[PAPI] Ошибка fill для " + uuid + ": " + e.getMessage());
            } finally {
                fillInFlight.remove(uuid);
            }
        });
    }

    private PlayerSnapshot loadSnapshot(UUID uuid) throws SQLException {
        int playerId = plugin.getPlayerDAO().getPlayerId(uuid);
        if (playerId == -1) return null;

        PlayerStatsDAO statsDAO = plugin.getStatsDAO();
        int serverId = plugin.getServerId();

        // Один запрос за всеми статами сервера — вместо N одиночных getStat().
        Map<String, PlayerStatsDAO.StatValue> all =
                statsDAO.getAllStatsDetailed(playerId, serverId);

        Map<String, Integer> intStats = new HashMap<>();
        double balance = 0.0;
        for (Map.Entry<String, PlayerStatsDAO.StatValue> e : all.entrySet()) {
            PlayerStatsDAO.StatValue v = e.getValue();
            if ("balance".equals(e.getKey())) {
                balance = v.valueDouble();
            } else {
                intStats.put(e.getKey(), v.valueInt());
            }
        }

        // Суммарный play_time по всем серверам
        Map<String, Integer> total =
                statsDAO.getTotalStatsForPlayer(playerId);
        int totalPlayTime = total.getOrDefault("play_time", 0);

        // Группа — из кэша сервиса; если нет — пустая
        String group = "";
        try {
            PlayerGroupService svc = plugin.getGroupService();
            if (svc != null) {
                Player online = plugin.getServer().getPlayer(uuid);
                if (online != null) {
                    group = svc.getPlayerGroup(online, playerId);
                } else {
                    group = svc.getPlayerGroup(uuid);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[PAPI] Группа: " + e.getMessage());
        }

        return new PlayerSnapshot(uuid, intStats, totalPlayTime, balance,
                group == null ? "" : group);
    }

    // ==================== Остальное ====================

    private String resolveUniquePlayers() {
        // Кэшируется TotalServerDAO? Нет. Но PAPI-вызов «unique_players» редкий.
        // Оставим как есть — единственное место, где возможен SQL.
        // Если вызывается часто — закэшировать в TopCache.
        try {
            Map<String, Number> stats = plugin.getTotalServerDAO()
                    .getServerStats(plugin.getServerId());
            Number n = stats.get("uniquePlayers");
            return n == null ? "0" : String.valueOf(n.longValue());
        } catch (SQLException e) {
            plugin.getLogger().warning("[PAPI] Ошибка unique_players: " + e.getMessage());
            return "0";
        }
    }

    /** Сброс кэша игрока. Вызывать после изменения стат. */
    public void invalidate(UUID uuid) {
        playerCache.remove(uuid);
        fillStamp.remove(uuid);
    }

    public void invalidateAll() {
        playerCache.clear();
        fillStamp.clear();
    }
}