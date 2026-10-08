package com.playerstats.api.http.Handler;

import com.playerstats.Main;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HealthHandler implements HttpHandler {

    private final Main plugin;

    public HealthHandler(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        boolean dbOk = isDatabaseConnected();
        boolean apiOk = true;  // если этот код выполняется — API работает

        String status = (dbOk && apiOk) ? "ok" : "degraded";
        int httpCode = (dbOk && apiOk) ? 200 : 503;

        long uptimeMs = System.currentTimeMillis() - plugin.getStartTime();

        String response = """
                {
                  "status": "%s",
                  "database": %s,
                  "api": %s,
                  "uptime_ms": %d
                }
                """.formatted(
                status,
                dbOk,
                apiOk,
                uptimeMs
        );

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(httpCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean isDatabaseConnected() {
        try {
            return plugin.getConnection() != null
                    && plugin.getConnection().isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}