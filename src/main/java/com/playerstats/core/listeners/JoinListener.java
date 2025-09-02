package com.playerstats.core.listeners;

import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.UUID;

public class JoinListener implements Listener {

    private final PlayerDAO playerDAO;
    private final PlayerStatsDAO statsDAO;

    public JoinListener(PlayerDAO playerDAO, PlayerStatsDAO statsDAO) {
        this.playerDAO = playerDAO;
        this.statsDAO = statsDAO;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        try {
            playerDAO.createPlayerIfNotExists(uuid, name);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
