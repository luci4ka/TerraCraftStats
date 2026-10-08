package com.playerstats.api.http.Handler;

import com.playerstats.Main;
import com.playerstats.core.services.ServerStatsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ServerHandler implements HttpHandler {

    private final Main plugin;
    private final ServerStatsService service;
    private final Gson gson = new Gson();

    public ServerHandler(Main plugin, ServerStatsService service) {
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

            // ==================== PATH ====================
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            String last = parts.length > 0 ? parts[parts.length - 1] : "";

            // ==================== ОПРЕДЕЛЕНИЕ serverId ====================
            int serverId;
            if ("server".equals(last) || last.isEmpty()) {
                // /api/server → дефолтный serverId
                serverId = plugin.getServerId();
            } else if (isNumeric(last)) {
                serverId = Integer.parseInt(last);
            } else {
                sendError(exchange, 400, "Invalid server id: " + last);
                return;
            }

            // ==================== ОТВЕТ ====================
            Map<String, Object> json = service.getServerStatsJson(serverId);
            if (json == null || json.isEmpty()) {
                sendError(exchange, 404, "Server not found: " + serverId);
                return;
            }

            sendJson(exchange, 200, json);

        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid server id");
        } catch (Exception e) {
            plugin.getLogger().severe("API error in ServerHandler: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Internal server error");
        }
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