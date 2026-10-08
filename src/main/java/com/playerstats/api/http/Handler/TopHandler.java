package com.playerstats.api.http.Handler;

import com.playerstats.Main;
import com.playerstats.core.services.ServerStatsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TopHandler implements HttpHandler {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 50;

    /**
     * Известные базовые stat-ы (для валидации).
     * Динамические (mob_killed_*, block_placed_*, block_broken_*, pvp_kill_*) — префиксы.
     */
    private static final Set<String> BASE_STATS = Set.of(
            "kills", "deaths", "mobs_killed", "bosses_killed",
            "play_time", "blocks_placed", "blocks_broken", "balance",
            "killstreak", "best_killstreak", "distance_walked",
            "damage_dealt", "damage_taken", "items_crafted",
            "food_consumed", "arrows_shot", "fish_caught",
            "entities_tamed", "entities_bred", "teleports",
            "portal_uses", "logins"
    );

    private static final String[] DYNAMIC_PREFIXES = {
            "mob_killed_", "block_placed_", "block_broken_", "pvp_kill_"
    };

    private final Main plugin;
    private final ServerStatsService service;
    private final Gson gson = new Gson();

    public TopHandler(Main plugin, ServerStatsService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            // ==================== МЕТОД ====================
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            // ==================== QUERY ====================
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

            String stat = params.getOrDefault("stat", "play_time");
            if (!isValidStat(stat)) {
                sendError(exchange, 400, "Unknown stat: " + stat);
                return;
            }

            int limit = parseLimit(params.get("limit"));

            // ==================== ОТВЕТ ====================
            Map<String, Object> json = service.getTopPlayersJson(stat, limit);
            if (json == null || json.isEmpty()) {
                sendError(exchange, 404, "No data for stat: " + stat);
                return;
            }

            sendJson(exchange, 200, json);

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid parameter");
        } catch (Exception e) {
            plugin.getLogger().severe("API error in TopHandler: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Internal server error");
        }
    }

    // ==================== PARSING ====================

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;

        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0) {
                String key = urlDecode(param.substring(0, eq));
                String value = urlDecode(param.substring(eq + 1));
                map.put(key, value);
            }
        }
        return map;
    }

    private String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private int parseLimit(String raw) {
        if (raw == null || raw.isEmpty()) return DEFAULT_LIMIT;
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) return 1;
            if (value > MAX_LIMIT) return MAX_LIMIT;
            return value;
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }

    private boolean isValidStat(String stat) {
        if (stat == null || stat.isEmpty()) return false;
        if (BASE_STATS.contains(stat)) return true;
        for (String prefix : DYNAMIC_PREFIXES) {
            if (stat.startsWith(prefix) && stat.length() > prefix.length()) {
                return true;
            }
        }
        return false;
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