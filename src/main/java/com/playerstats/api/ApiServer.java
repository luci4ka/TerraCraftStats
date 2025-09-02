package com.playerstats.api;

import com.playerstats.api.Handler.PlayerHandler;
import com.playerstats.api.Handler.ServerHandler;
import com.playerstats.api.Handler.TopHandler;
import com.playerstats.api.Handler.TotalServerHandler;
import com.playerstats.core.services.ServerStatsService;
import com.playerstats.core.database.dao.PlayerDAO;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class ApiServer {

    private final ServerStatsService statsService;
    private final PlayerDAO playerDAO;
    private final String host;
    private final int port;
    private HttpServer server;

    public ApiServer(ServerStatsService statsService, PlayerDAO playerDAO, String host, int port) {
        this.statsService = statsService;
        this.playerDAO = playerDAO;
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // Контексты API
        server.createContext("/api/server", new ServerHandler(statsService));
        server.createContext("/api/server/total", new TotalServerHandler(statsService));
        server.createContext("/api/player", new PlayerHandler(statsService, playerDAO)); // <- теперь передаем PlayerDAO
        server.createContext("/api/top", new TopHandler(statsService));

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
