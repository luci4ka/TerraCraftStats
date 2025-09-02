package com.playerstats.core.services;

import com.playerstats.core.database.dao.TotalServerDAO;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ServerStatsService {
    private final TotalServerDAO dao;

    public ServerStatsService(TotalServerDAO dao) {
        this.dao = dao;
    }

    public Map<String, Object> getServerStatsJson(int serverId) {
        Map<String, Object> json = new HashMap<>();
        try {
            json.putAll(dao.getServerStats(serverId));
        } catch (SQLException e) {
            json.put("error", e.getMessage());
        }
        return json;
    }

    public Map<String, Object> getTotalStatsJson() {
        Map<String, Object> json = new HashMap<>();
        try {
            json.putAll(dao.getTotalStats());
        } catch (SQLException e) {
            json.put("error", e.getMessage());
        }
        return json;
    }

    public Map<String, Object> getPlayerStatsJson(int playerId) {
        Map<String, Object> json = new HashMap<>();
        try {
            json.putAll(dao.getPlayerTotalStats(playerId));
        } catch (SQLException e) {
            json.put("error", e.getMessage());
        }
        return json;
    }

    public Map<String, Object> getTopPlayersJson(String stat, int limit) {
        Map<String, Object> json = new HashMap<>();
        try {
            json.putAll(dao.getTopPlayers(stat, limit));
        } catch (SQLException e) {
            json.put("error", e.getMessage());
        }
        return json;
    }
}
