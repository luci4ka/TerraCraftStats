package com.playerstats.api.http.Handler;

import com.playerstats.Main;
import com.playerstats.core.achievements.Achievement;
import com.playerstats.core.achievements.AchievementManager;
import com.playerstats.core.database.dao.AchievementDAO;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.StatHistoryDAO;
import com.playerstats.core.database.dao.StatTypeDAO;
import com.playerstats.core.services.ServerStatsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class PlayerHandler implements HttpHandler {

    private final Main plugin;
    private final ServerStatsService service;
    private final PlayerDAO playerDAO;
    private final AchievementDAO achievementDAO;
    private final AchievementManager achievementManager;
    private final StatHistoryDAO statHistoryDAO;
    private final StatTypeDAO statTypeDAO;
    private final Gson gson = new Gson();

    public PlayerHandler(Main plugin, ServerStatsService service, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.service = service;
        this.playerDAO = playerDAO;
        this.achievementDAO = plugin.getAchievementDAO();
        this.achievementManager = plugin.getAchievementManager();
        this.statHistoryDAO = plugin.getStatHistoryDAO();
        this.statTypeDAO = plugin.getStatTypeDAO();
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            // ==================== МЕТОД ====================
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            // ==================== РАЗБОР PATH ====================
            // /api/player/{id}
            // /api/player/{id}/achievements
            // /api/player/{id}/history/{stat}?days=30

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            // parts[0] = ""
            // parts[1] = "api"
            // parts[2] = "player"
            // parts[3] = {id}
            // parts[4] = "achievements" | "history"
            // parts[5] = {stat} (для history)

            if (parts.length < 4) {
                sendError(exchange, 400, "Player identifier required");
                return;
            }

            String key = parts[3];
            if (key.isEmpty()) {
                sendError(exchange, 400, "Player identifier required");
                return;
            }

            // ==================== ПАРСИНГ ИГРОКА ====================
            Integer playerId = resolvePlayerId(key);
            if (playerId == null) {
                sendError(exchange, 404, "Player not found: " + key);
                return;
            }

            // ==================== SUB-ROUTES ====================
            if (parts.length == 4) {
                // GET /api/player/{id}
                Map<String, Object> json = service.getPlayerStatsJson(playerId);
                sendJson(exchange, 200, json);
                return;
            }

            String action = parts[4];

            if ("achievements".equals(action)) {
                // GET /api/player/{id}/achievements
                Map<String, Object> json = buildAchievements(playerId);
                sendJson(exchange, 200, json);
                return;
            }

            if ("history".equals(action)) {
                if (parts.length < 6) {
                    sendError(exchange, 400, "Stat name required: /api/player/{id}/history/{stat}");
                    return;
                }
                String stat = parts[5];
                int days = parseDays(exchange.getRequestURI().getQuery());
                Map<String, Object> json = buildHistory(playerId, stat, days);
                sendJson(exchange, 200, json);
                return;
            }

            sendError(exchange, 404, "Unknown action: " + action);

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid parameter");
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Invalid identifier");
        } catch (Exception e) {
            plugin.getLogger().severe("API error in PlayerHandler: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Internal server error");
        }
    }

    // ==================== PLAYER RESOLVE ====================

    private Integer resolvePlayerId(String key) throws SQLException {
        if (isNumeric(key)) {
            return Integer.parseInt(key);
        }
        if (isUuid(key)) {
            int id = playerDAO.getPlayerId(UUID.fromString(key));
            return id == -1 ? null : id;
        }
        // По имени
        UUID uuid = playerDAO.getUuidByName(key);
        if (uuid == null) return null;
        int id = playerDAO.getPlayerId(uuid);
        return id == -1 ? null : id;
    }

    // ==================== ACHIEVEMENTS ====================

    private Map<String, Object> buildAchievements(int playerId) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        Set<String> unlocked = achievementDAO.getUnlockedAchievements(playerId);
        Map<String, Long> unlockedWithDates = achievementDAO.getUnlockedWithDates(playerId);
        Collection<Achievement> all = achievementManager.getAll();

        result.put("unlocked_count", unlocked.size());
        result.put("total_count", all.size());

        List<Map<String, Object>> list = new ArrayList<>();
        for (Achievement ac : all) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ac.id());
            item.put("unlocked", unlocked.contains(ac.id()));

            Long date = unlockedWithDates.get(ac.id());
            item.put("unlocked_at", date != null && date > 0 ? date : null);

            list.add(item);
        }
        result.put("achievements", list);

        return result;
    }

    // ==================== HISTORY ====================

    private Map<String, Object> buildHistory(int playerId, String stat, int days)
            throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        int statTypeId = statTypeDAO.createOrGetStatType(stat);
        int serverId = plugin.getServerId();

        List<Map<String, Object>> history = statHistoryDAO
                .getHistoryWithDates(playerId, statTypeId, serverId, days);

        result.put("stat", stat);
        result.put("days", days);
        result.put("history", history);
        return result;
    }

    // ==================== PARSE HELPERS ====================

    private int parseDays(String query) {
        if (query == null || query.isEmpty()) return 30;
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && "days".equals(param.substring(0, eq))) {
                try {
                    int d = Integer.parseInt(param.substring(eq + 1));
                    return Math.max(1, Math.min(d, 365));
                } catch (NumberFormatException ignored) {
                    return 30;
                }
            }
        }
        return 30;
    }

    // ==================== SEND ====================

    private void sendJson(HttpExchange exchange, int code, Object data) {
        try {
            String json = gson.toJson(data);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type",
                    "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось отправить JSON: " + e.getMessage());
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) {
        try {
            String json = "{\"error\":\"" + escapeJson(message) + "\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type",
                    "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== UTIL ====================

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private boolean isUuid(String s) {
        if (s == null || s.length() != 36) return false;
        return s.charAt(8) == '-' && s.charAt(13) == '-'
                && s.charAt(18) == '-' && s.charAt(23) == '-';
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}