package com.playerstats.api.http.Handler;

import com.playerstats.Main;
import com.playerstats.core.services.ServerStatsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TotalServerHandler implements HttpHandler {

    private final Main plugin;
    private final ServerStatsService service;
    private final Gson gson = new Gson();

    public TotalServerHandler(Main plugin, ServerStatsService service) {
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

            // ==================== ОТВЕТ ====================
            Map<String, Object> json = service.getTotalStatsJson();
            if (json == null || json.isEmpty()) {
                sendError(exchange, 404, "No total stats available");
                return;
            }

            sendJson(exchange, 200, json);

        } catch (Exception e) {
            plugin.getLogger().severe("API error in TotalServerHandler: " + e.getMessage());
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