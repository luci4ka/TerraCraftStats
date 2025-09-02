package com.playerstats.api.Handler;

import com.playerstats.core.services.ServerStatsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ServerHandler implements HttpHandler {

    private final ServerStatsService service;
    private final Gson gson = new Gson();

    public ServerHandler(ServerStatsService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath(); // /api/server/<id>
            String[] parts = path.split("/");
            int serverId = Integer.parseInt(parts[parts.length - 1]);

            Map<String, Object> json = service.getServerStatsJson(serverId);
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
