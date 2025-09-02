package com.playerstats.api.Handler;

import com.playerstats.core.services.ServerStatsService;
import com.playerstats.core.database.dao.PlayerDAO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public class PlayerHandler implements HttpHandler {

    private final ServerStatsService service;
    private final PlayerDAO playerDAO;
    private final Gson gson = new Gson();

    public PlayerHandler(ServerStatsService service, PlayerDAO playerDAO) {
        this.service = service;
        this.playerDAO = playerDAO;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath(); // /api/player/<id_or_uuid>
            String[] parts = path.split("/");
            String key = parts[parts.length - 1];

            int playerId;

            try {
                // Попробуем как ID
                playerId = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                // Если не число — считаем UUID
                UUID uuid = UUID.fromString(key);
                playerId = playerDAO.getPlayerId(uuid);
                if (playerId == -1) {
                    throw new IllegalArgumentException("Player not found with UUID: " + uuid);
                }
            }

            Map<String, Object> json = service.getPlayerStatsJson(playerId);
            sendJson(exchange, json);

        } catch (Exception e) {
            sendError(exchange, e);
        }
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
