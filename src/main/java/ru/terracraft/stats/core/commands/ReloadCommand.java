package ru.terracraft.stats.core.commands;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.ConfigManager;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.config.model.MainConfig;
import ru.terracraft.stats.core.services.PlayerGroupService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand {

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;
    private final PlayerGroupService groupService;

    public ReloadCommand(PlayerStatsPlugin plugin, PlayerGroupService groupService) {
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
                int clearedCaches = 0;

                ConfigManager config = plugin.getConfigManager();
                config.reload();
                sendSync(sender, getComponent(sender, "reload.step-config"));

                plugin.getLanguageManager().load();
                sendSync(sender, getComponent(sender, "reload.step-lang"));

                if (plugin.getStatsListener() != null) {
                    plugin.getStatsListener().reloadTrackedBlocks();
                    sendSync(sender, getComponent(sender, "reload.step-tracked-blocks"));
                }

                clearedCaches = clearAllCaches();
                sendSync(sender, getComponent(sender, "reload.step-caches",
                        "count", clearedCaches));

                if (groupService != null) {
                    groupService.clearCache();
                    sendSync(sender, getComponent(sender, "reload.step-groups"));
                }

                if (plugin.getTopCache() != null) {
                    plugin.getTopCache().reloadConfig();
                    sendSync(sender, getComponent(sender, "reload.step-tops"));
                }

                MainConfig mainCfg = config.main();
                String newServerName = mainCfg.multiServer().enabled()
                        ? mainCfg.multiServer().serverName()
                        : Bukkit.getServer().getName();

                int oldServerId = plugin.getServerId();
                int newServerId = plugin.getServerDAO().createOrGetServer(newServerName);
                boolean serverIdChanged = newServerId != oldServerId;

                plugin.updateServerInfo(newServerId, newServerName);
                sendSync(sender, getComponent(sender, "reload.step-server"));

                if (serverIdChanged) {
                    plugin.getLogger().warning(
                            "serverName изменён: '" + plugin.getServerName()
                                    + "' (ID " + oldServerId + " → " + newServerId + "). "
                                    + "StatsListener.serverId/TopCache.serverId — final и "
                                    + "НЕ переключатся без рестарта. "
                                    + "Рекомендуется перезапустить сервер.");
                    sendSync(sender, getComponent(sender, "reload.warn-server-changed",
                            "old", oldServerId, "new", newServerId));
                }

                long elapsed = System.currentTimeMillis() - startTime;

                final int finalClearedCaches = clearedCaches;
                final long finalElapsed = elapsed;

                plugin.getThreadManager().runSync(() ->
                        sendReloadInfo(sender, mainCfg, newServerName,
                                newServerId, finalClearedCaches, finalElapsed));

            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка при перезагрузке плагина: "
                        + e.getMessage());
                e.printStackTrace();

                plugin.getThreadManager().runSync(() ->
                        sender.sendMessage(getComponent(sender, "reload.error")));
            }
        });
    }

    private void sendReloadInfo(CommandSender sender, MainConfig mainCfg,
                                String serverName, int serverId,
                                int clearedCaches, long elapsedMs) {
        Component separator = getComponent(sender, "reload.header");

        sender.sendMessage(Component.empty());
        sender.sendMessage(getComponent(sender, "reload.section-config"));
        sender.sendMessage(getComponent(sender, "reload.info-db",
                "type", "MARIADB"));
        sender.sendMessage(getComponent(sender, "reload.info-caches",
                "count", clearedCaches));

        sender.sendMessage(Component.empty());
        sender.sendMessage(getComponent(sender, "reload.section-server"));
        sender.sendMessage(getComponent(sender, "reload.info-server",
                "server", serverName,
                "id", serverId));

        String multiStatus = mainCfg.multiServer().enabled()
                ? getRaw(sender, "status.enabled-f")
                : getRaw(sender, "status.disabled-f");
        sender.sendMessage(getComponent(sender, "reload.info-multi",
                "status", multiStatus));

        sender.sendMessage(separator);
        sender.sendMessage(getComponent(sender, "reload.success",
                "time", elapsedMs));
        sender.sendMessage(separator);
    }

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
            plugin.getLogger().warning("Ошибка при очистке кэшей DAO: "
                    + e.getMessage());
        }
        return count;
    }

    private void sendSync(CommandSender sender, Component message) {
        plugin.getThreadManager().runSync(() -> sender.sendMessage(message));
    }

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
}