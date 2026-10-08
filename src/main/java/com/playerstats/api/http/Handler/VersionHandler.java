package com.playerstats.api.http.Handler;

import com.playerstats.Main;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class VersionHandler implements HttpHandler {

    /**
     * Версия формата API — увеличивается, если меняется структура ответа.
     */
    private static final String API_VERSION = "1.0.0";

    private final Main plugin;

    public VersionHandler(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // ==================== ДАННЫЕ ====================
        String pluginVersion = plugin.getPluginMeta().getVersion();
        String mcVersion = Bukkit.getBukkitVersion();
        String serverName = plugin.getServerName();
        int serverId = plugin.getServerId();
        long uptimeMs = System.currentTimeMillis() - plugin.getStartTime();

        // ==================== JSON ====================
        String response = """
                {
                  "api_version": "%s",
                  "plugin_version": "%s",
                  "mc_version": "%s",
                  "server_name": "%s",
                  "server_id": %d,
                  "uptime_ms": %d
                }
                """.formatted(
                escapeJson(API_VERSION),
                escapeJson(pluginVersion),
                escapeJson(mcVersion),
                escapeJson(serverName),
                serverId,
                uptimeMs
        );

        // ==================== ОТПРАВКА ====================
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Экранирование строк для JSON (кавычки, бэкслеши, спецсимволы).
     */
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