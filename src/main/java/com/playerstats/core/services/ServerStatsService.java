package com.playerstats.core.services;

import com.playerstats.core.database.dao.TotalServerDAO;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatsService {

    private final TotalServerDAO dao;
    private final Logger logger;

    public ServerStatsService(TotalServerDAO dao, Logger logger) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Конвертация Map<String, Number> → Map<String, Object>.
     * Создаёт новый LinkedHashMap, чтобы DAO не мог изменить возвращённую мапу.
     */
    private Map<String, Object> toObjectMap(Map<String, Number> source) {
        if (source == null) return new LinkedHashMap<>();
        return new LinkedHashMap<>(source);
    }

    public Map<String, Object> getServerStatsJson(int serverId) {
        try {
            return toObjectMap(dao.getServerStats(serverId));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getServerStats для serverId=" + serverId, e);
            return errorMap(e);
        }
    }

    public Map<String, Object> getTotalStatsJson() {
        try {
            return toObjectMap(dao.getTotalStats());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getTotalStats", e);
            return errorMap(e);
        }
    }

    public Map<String, Object> getPlayerStatsJson(int playerId) {
        try {
            return toObjectMap(dao.getPlayerTotalStats(playerId));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getPlayerStats для playerId=" + playerId, e);
            return errorMap(e);
        }
    }

    public Map<String, Object> getTopPlayersJson(String stat, int limit) {
        try {
            return toObjectMap(dao.getTopPlayers(stat, limit));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка getTopPlayers для stat=" + stat, e);
            return errorMap(e);
        }
    }

    /**
     * Формирует мапу с ошибкой для JSON-ответа.
     */
    private Map<String, Object> errorMap(SQLException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", e.getMessage() != null ? e.getMessage() : "Unknown database error");
        return error;
    }
}