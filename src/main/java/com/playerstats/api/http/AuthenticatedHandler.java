package com.playerstats.api.http;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Обертка над handler'ом: проверяет API-ключ (кроме публичных роутов)
 * и добавляет CORS-заголовки (если включены в config.yml).
 */
class AuthenticatedHandler implements HttpHandler {

    private final Main plugin;
    private final ConfigHandler config;
    private final HttpHandler delegate;
    private final ApiKeyManager keyManager;
    private final Set<String> publicRoutes;

    public AuthenticatedHandler(Main plugin,
                                HttpHandler delegate,
                                ApiKeyManager keyManager,
                                Set<String> publicRoutes) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.delegate = delegate;
        this.keyManager = keyManager;
        this.publicRoutes = publicRoutes;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // ==================== CORS preflight ====================
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            applyCors(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // ==================== CORS для всех ответов ====================
        applyCors(exchange);

        // ==================== Публичные роуты ====================
        if (isPublic(path)) {
            delegate.handle(exchange);
            return;
        }

        // ==================== Проверка ключа ====================
        if (!keyManager.isAuthorized(exchange)) {
            plugin.getLogger().warning("Unauthorized API request: "
                    + exchange.getRequestMethod() + " " + path
                    + " from " + exchange.getRemoteAddress());
            sendUnauthorized(exchange);
            return;
        }

        delegate.handle(exchange);
    }

    /**
     * Публичный ли роут?
     */
    private boolean isPublic(String path) {
        for (String route : publicRoutes) {
            if (route.endsWith("/*")) {
                String prefix = route.substring(0, route.length() - 2);
                if (path.startsWith(prefix)) return true;
            } else if (route.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Добавить CORS-заголовки, если включены в config.yml.
     */
    private void applyCors(HttpExchange exchange) {
        if (!config.isApiCorsEnabled()) return;

        exchange.getResponseHeaders().set("Access-Control-Allow-Origin",
                config.getApiCorsAllowOrigin());
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods",
                config.getApiCorsAllowMethods());
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                config.getApiCorsAllowHeaders());
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "3600");
    }

    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        String json = "{\"error\":\"Unauthorized — invalid or missing API key\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type",
                "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("WWW-Authenticate", "ApiKey");
        exchange.sendResponseHeaders(401, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}