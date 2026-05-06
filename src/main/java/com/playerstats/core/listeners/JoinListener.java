package com.playerstats.core.listeners;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerDAO;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class JoinListener implements Listener {

    private final Main plugin;
    private final PlayerDAO playerDAO;

    /**
     * @param plugin    главный класс плагина (для логгера и планировщика)
     * @param playerDAO DAO для работы с таблицей игроков
     */
    public JoinListener(Main plugin, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        // Асинхронно, чтобы не блокировать главный поток
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // createOrUpdate, чтобы обновлять имя при смене ника
                playerDAO.getOrCreatePlayerId(uuid, name);

                // Опционально: логируем успех (только для дебага)
                // plugin.getLogger().fine("Игрок " + name + " сохранён/обновлён в БД.");

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Не удалось сохранить игрока в БД: " + name + " (" + uuid + ")", e);

                // Опционально: возвращаемся в главный поток, чтобы кикнуть игрока
                // если целостность данных критична
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (event.getPlayer().isOnline()) {
                        event.getPlayer().kickPlayer(
                                "§cВнутренняя ошибка сервера.\nПожалуйста, попробуйте зайти позже."
                        );
                    }
                });
            }
        });
    }
}