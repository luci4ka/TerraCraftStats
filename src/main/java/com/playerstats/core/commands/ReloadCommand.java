package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.services.PlayerGroupService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class ReloadCommand {

    private final Main plugin;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public ReloadCommand(Main plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.groupService = groupService;
    }

    public void execute(CommandSender sender) {
        if (!sender.hasPermission("playerstats.reload")) {
            sender.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        sender.sendMessage(lang.getComponent("reload.starting"));

        plugin.getThreadManager().runAsync(() -> {
            try {
                ConfigHandler.reload(plugin);
                ConfigHandler config = ConfigHandler.getInstance(plugin);

                clearAllCaches();

                if (groupService != null) {
                    groupService.clearCache();
                }

                // Сбрасываем кэш топов
                if (plugin.getTopCache() != null) {
                    plugin.getTopCache().invalidateAll();
                }

                String newServerName = config.isMultiServerEnabled()
                        ? config.getServerName()
                        : Bukkit.getServer().getName();

                int newServerId = plugin.getServerDAO().createOrGetServer(newServerName);
                plugin.updateServerInfo(newServerId, newServerName);

                plugin.getThreadManager().runSync(() -> {
                    sender.sendMessage(lang.getComponent("reload.success"));

                    // База данных
                    sender.sendMessage(lang.getComponent("reload.info-db",
                            "type", config.getDatabaseType().toUpperCase()));

                    // Сервер
                    sender.sendMessage(lang.getComponent("reload.info-server",
                            "server", newServerName,
                            "id", newServerId));

                    // API
                    String apiStatus = lang.getRaw(config.isApiEnabled()
                            ? "status.enabled"
                            : "status.disabled");
                    sender.sendMessage(lang.getComponent("reload.info-api",
                            "status", apiStatus));

                    // API хост (если включен)
                    if (config.isApiEnabled()) {
                        sender.sendMessage(lang.getComponent("reload.info-api-host",
                                "host", config.getApiHost() + ":" + config.getApiPort()));
                    }

                    // Мультисерверность
                    String multiStatus = lang.getRaw(config.isMultiServerEnabled()
                            ? "status.enabled-f"
                            : "status.disabled-f");
                    sender.sendMessage(lang.getComponent("reload.info-multi",
                            "status", multiStatus));
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка при перезагрузке плагина: " + e.getMessage());
                e.printStackTrace();

                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(lang.getComponent("reload.error")));
            }
        });
    }

    /**
     * Очистить все кэши в DAO.
     */
    private void clearAllCaches() {
        try {
            if (plugin.getStatTypeDAO() != null) {
                plugin.getStatTypeDAO().clearCache();
            }
            if (plugin.getServerDAO() != null) {
                plugin.getServerDAO().clearCache();
            }
            if (plugin.getStatsDAO() != null) {
                plugin.getStatsDAO().clearCache();
            }
            if (plugin.getServerStatsDAO() != null) {
                plugin.getServerStatsDAO().clearCache();
            }
            if (plugin.getLeaderboardDAO() != null) {
                plugin.getLeaderboardDAO().clearCache();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при очистке кэшей DAO: " + e.getMessage());
        }
    }
}