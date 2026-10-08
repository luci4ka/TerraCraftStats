package com.playerstats.api.http;

import com.playerstats.Main;
import com.playerstats.api.http.Handler.HealthHandler;
import com.playerstats.api.http.Handler.PlayerHandler;
import com.playerstats.api.http.Handler.ServerHandler;
import com.playerstats.api.http.Handler.TopHandler;
import com.playerstats.api.http.Handler.TotalServerHandler;
import com.playerstats.api.http.Handler.VersionHandler;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.services.ServerStatsService;
import com.playerstats.core.database.dao.PlayerDAO;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class PlayerStatsHttpApi {

    private final Main plugin;
    private final ConfigHandler config;
    private final ServerStatsService statsService;
    private final PlayerDAO playerDAO;
    private final ApiKeyManager keyManager;
    private final String host;
    private final int port;
    private HttpServer server;

    public PlayerStatsHttpApi(Main plugin, ServerStatsService statsService,
                              PlayerDAO playerDAO, String host, int port) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.statsService = statsService;
        this.playerDAO = playerDAO;
        this.keyManager = new ApiKeyManager(plugin);
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // ==================== PUBLIC ROUTES ====================
        Set<String> publicRoutes = new HashSet<>(config.getApiPublicRoutes());

        // ==================== HANDLERS ====================
        var healthHandler = new HealthHandler(plugin);
        var versionHandler = new VersionHandler(plugin);
        var totalServerHandler = new TotalServerHandler(plugin, statsService);
        var serverHandler = new ServerHandler(plugin, statsService);
        var playerHandler = new PlayerHandler(plugin, statsService, playerDAO);
        var topHandler = new TopHandler(plugin, statsService);

        // ==================== REGISTER ====================
        // Порядок важен: длинные пути — первыми!

        // Public (без аутентификации, но с CORS — если настроен)
        server.createContext("/api/health",
                new AuthenticatedHandler(plugin, healthHandler, keyManager, Set.of("/api/health")));
        server.createContext("/api/version",
                new AuthenticatedHandler(plugin, versionHandler, keyManager, Set.of("/api/version")));

        // Authenticated
        server.createContext("/api/server/total",
                new AuthenticatedHandler(plugin, totalServerHandler, keyManager, publicRoutes));
        server.createContext("/api/server",
                new AuthenticatedHandler(plugin, serverHandler, keyManager, publicRoutes));
        server.createContext("/api/player",
                new AuthenticatedHandler(plugin, playerHandler, keyManager, publicRoutes));
        server.createContext("/api/top",
                new AuthenticatedHandler(plugin, topHandler, keyManager, publicRoutes));

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        plugin.getLogger().info("API запущен на " + host + ":" + port
                + " (auth: " + (keyManager.hasKey() ? "enabled" : "disabled")
                + ", CORS: " + (config.isApiCorsEnabled() ? "on" : "off") + ")");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public ApiKeyManager getKeyManager() {
        return keyManager;
    }
}