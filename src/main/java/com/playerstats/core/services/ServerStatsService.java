package com.playerstats.core.services;

import com.playerstats.core.database.dao.TotalServerDAO;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatsService {

    private final TotalServerDAO dao;
    private final Logger logger;

    public ServerStatsService(TotalServerDAO dao, Logger logger) {
        this.dao = dao;
        this.logger = logger;
    }

    /**
     * Безопасное получение статистики с конвертацией Number → Object.
     */
    private Map<String, Object> safeGet(Map<String, Number> source) {
        return new LinkedHashMap<>(source);
    }

    public Map<String, Object> getServerStatsJson(int serverId) {
        try {
            return safeGet(dao.getServerStats(serverId));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getServerStats для serverId=" + serverId, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    public Map<String, Object> getTotalStatsJson() {
        try {
            return safeGet(dao.getTotalStats());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getTotalStats", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    public Map<String, Object> getPlayerStatsJson(int playerId) {
        try {
            return safeGet(dao.getPlayerTotalStats(playerId));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getPlayerStats для playerId=" + playerId, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    public Map<String, Object> getTopPlayersJson(String stat, int limit) {
        try {
            return safeGet(dao.getTopPlayers(stat, limit));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getTopPlayers для stat=" + stat, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }
}