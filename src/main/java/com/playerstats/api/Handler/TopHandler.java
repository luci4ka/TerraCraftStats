package com.playerstats.api.Handler;

import com.playerstats.core.services.ServerStatsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TopHandler implements HttpHandler {

    private final ServerStatsService service;
    private final Gson gson = new Gson();
    private static final int MAX_LIMIT = 50;

    public TopHandler(ServerStatsService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String query = exchange.getRequestURI().getQuery(); // ?stat=blocks_placed&limit=5
            Map<String, String> params = parseQuery(query);

            String stat = params.getOrDefault("stat", "play_time");
            int limit = Integer.parseInt(params.getOrDefault("limit", "5"));
            if (limit > MAX_LIMIT) limit = MAX_LIMIT;

            Map<String, Object> json = service.getTopPlayersJson(stat, limit);
            sendJson(exchange, json);

        } catch (Exception e) {
            sendError(exchange, e);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new java.util.HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) map.put(pair[0], pair[1]);
        }
        return map;
    }

    private void sendJson(HttpExchange exchange, Map<String, Object> json) throws Exception {
        byte[] response = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private void sendError(HttpExchange exchange, Exception e) {
        e.printStackTrace();
        try {
            String error = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, error.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(error.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch (Exception ignored) {}
    }
}
