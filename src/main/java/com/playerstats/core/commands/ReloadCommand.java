package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.services.PlayerGroupService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        long startTime = System.currentTimeMillis();

        sender.sendMessage(getComponent(sender, "reload.header"));
        sender.sendMessage(getComponent(sender, "reload.starting"));

        plugin.getThreadManager().runAsync(() -> {
            try {
                // ==================== ЭТАПЫ ====================
                int clearedCaches = 0;

                // 1. Конфиг
                ConfigHandler.reload(plugin);
                ConfigHandler config = ConfigHandler.getInstance(plugin);
                sendSync(sender, getComponent(sender, "reload.step-config"));

                // 2. DAO-кэши
                clearedCaches = clearAllCaches();
                sendSync(sender, getComponent(sender, "reload.step-caches",
                        "count", clearedCaches));

                // 3. Группы
                if (groupService != null) {
                    groupService.clearCache();
                    sendSync(sender, getComponent(sender, "reload.step-groups"));
                }

                // 4. Топы
                if (plugin.getTopCache() != null) {
                    plugin.getTopCache().invalidateAll();
                    sendSync(sender, getComponent(sender, "reload.step-tops"));
                }

                // 5. Сервер
                String newServerName = config.isMultiServerEnabled()
                        ? config.getServerName()
                        : Bukkit.getServer().getName();

                int newServerId = plugin.getServerDAO().createOrGetServer(newServerName);
                plugin.updateServerInfo(newServerId, newServerName);
                sendSync(sender, getComponent(sender, "reload.step-server"));

                // ==================== ИНФО ====================
                long elapsed = System.currentTimeMillis() - startTime;

                final int finalClearedCaches = clearedCaches;
                final long finalElapsed = elapsed;

                plugin.getThreadManager().runSync(() -> {
                    sendReloadInfo(sender, config, newServerName,
                            newServerId, finalClearedCaches, finalElapsed);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка при перезагрузке плагина: " + e.getMessage());
                e.printStackTrace();

                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "reload.error")));
            }
        });
    }

    // ==================== INFO ====================

    private void sendReloadInfo(CommandSender sender, ConfigHandler config,
                                String serverName, int serverId,
                                int clearedCaches, long elapsedMs) {
        Component separator = getComponent(sender, "reload.header");

        // ==================== КОНФИГ ====================
        sender.sendMessage(Component.empty());
        sender.sendMessage(getComponent(sender, "reload.section-config"));
        sender.sendMessage(getComponent(sender, "reload.info-db",
                "type", config.getDatabaseType().toUpperCase()));
        sender.sendMessage(getComponent(sender, "reload.info-caches",
                "count", clearedCaches));

        // ==================== СЕРВЕР ====================
        sender.sendMessage(Component.empty());
        sender.sendMessage(getComponent(sender, "reload.section-server"));
        sender.sendMessage(getComponent(sender, "reload.info-server",
                "server", serverName,
                "id", serverId));

        String multiStatus = config.isMultiServerEnabled()
                ? getRaw(sender, "status.enabled-f")
                : getRaw(sender, "status.disabled-f");
        sender.sendMessage(getComponent(sender, "reload.info-multi",
                "status", multiStatus));

        // ==================== API ====================
        sender.sendMessage(Component.empty());
        sender.sendMessage(getComponent(sender, "reload.section-api"));

        boolean apiRunning = plugin.getApiServer() != null;

        String apiStatus = apiRunning
                ? getRaw(sender, "status.enabled")
                : getRaw(sender, "status.disabled");

        Component apiLine = getComponent(sender, "reload.info-api",
                "status", apiStatus);

        if (apiRunning) {
            String host = config.getApiHost() + ":" + config.getApiPort();

            Component hoverText = getComponentFromString(sender,
                    "<gray>Хост: <white>" + host + "\n" +
                            "<dark_gray>Нажмите, чтобы скопировать");

            Component hostComponent = getComponent(sender, "reload.info-api-host",
                    "host", host)
                    .clickEvent(ClickEvent.copyToClipboard(host))
                    .hoverEvent(HoverEvent.showText(hoverText));
            apiLine = apiLine.append(hostComponent);
        }
        sender.sendMessage(apiLine);

        // ==================== ИТОГ ====================
        sender.sendMessage(separator);
        sender.sendMessage(getComponent(sender, "reload.success",
                "time", elapsedMs));
        sender.sendMessage(separator);
    }

    // ==================== CACHES ====================

    /**
     * Очистить все кэши в DAO.
     *
     * @return количество очищенных кэшей
     */
    private int clearAllCaches() {
        int count = 0;
        try {
            if (plugin.getStatTypeDAO() != null) {
                plugin.getStatTypeDAO().clearCache();
                count++;
            }
            if (plugin.getServerDAO() != null) {
                plugin.getServerDAO().clearCache();
                count++;
            }
            if (plugin.getStatsDAO() != null) {
                plugin.getStatsDAO().clearCache();
                count++;
            }
            if (plugin.getServerStatsDAO() != null) {
                plugin.getServerStatsDAO().clearCache();
                count++;
            }
            if (plugin.getLeaderboardDAO() != null) {
                plugin.getLeaderboardDAO().clearCache();
                count++;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при очистке кэшей DAO: " + e.getMessage());
        }
        return count;
    }

    // ==================== UTIL ====================

    private void sendSync(CommandSender sender, Component message) {
        plugin.getThreadManager().runSync(() -> sender.sendMessage(message));
    }

    // ==================== LANG HELPERS ====================

    private Component getComponent(CommandSender sender, String key, Object... replacements) {
        if (sender instanceof Player player) {
            return lang.getComponent(player, key, replacements);
        }
        return lang.getComponent(key, replacements);
    }

    private String getRaw(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            return lang.getRaw(player, key);
        }
        return lang.getRaw(key);
    }

    private Component getComponentFromString(CommandSender sender, String message, Object... replacements) {
        return lang.getComponentFromString(message, replacements);
    }
}