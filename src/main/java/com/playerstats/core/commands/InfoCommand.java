package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InfoCommand {

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;

    public InfoCommand(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
    }

    /**
     * Выполнить команду /playerstats info.
     */
    public void execute(CommandSender sender) {
        if (!(sender instanceof Player) && !sender.hasPermission("playerstats.info")) {
            sender.sendMessage(lang.getComponent("no-permission"));
            return;
        }

        // Заголовок
        sender.sendMessage(lang.getComponent("info.header"));
        sender.sendMessage(lang.getComponent("info.title",
                "version", plugin.getPluginMeta().getVersion()));

        // Авторы
        String authors = String.join(", ", plugin.getPluginMeta().getAuthors());
        sender.sendMessage(lang.getComponent("info.author", "authors", authors));

        // Сервер
        sender.sendMessage(lang.getComponent("info.server",
                "server", plugin.getServerName(),
                "id", plugin.getServerId()));

        // База данных
        String dbType = config.getDatabaseType().toUpperCase();
        boolean dbConnected = plugin.getConnection() != null
                && plugin.getConnection().isConnected();
        String dbStatus = lang.getRaw(dbConnected
                ? "status.connected"
                : "status.disconnected");
        sender.sendMessage(lang.getComponent("info.database",
                "type", dbType,
                "status", dbStatus));

        // HTTP API
        boolean apiEnabled = config.isApiEnabled();
        String apiStatus = lang.getRaw(apiEnabled
                ? "status.enabled"
                : "status.disabled");
        Component apiMessage = lang.getComponent("info.api", "status", apiStatus);

        // Если API запущен — добавляем хост
        if (plugin.getApiServer() != null) {
            apiMessage = apiMessage.append(
                    lang.getComponent("info.api-host",
                            "host", config.getApiHost() + ":" + config.getApiPort()));
        }
        sender.sendMessage(apiMessage);

        // Мультисерверность
        String multiStatus = lang.getRaw(config.isMultiServerEnabled()
                ? "status.enabled-f"
                : "status.disabled-f");
        sender.sendMessage(lang.getComponent("info.multi", "status", multiStatus));

        // Debug level
        int debugLevel = config.getDebugLevel();
        String debugText = switch (debugLevel) {
            case 0 -> "Выкл";
            case 1 -> "Базовый";
            case 2 -> "Детальный";
            default -> "Неизвестно";
        };
        sender.sendMessage(lang.getComponent("info.debug",
                "level", debugText + " (" + debugLevel + ")"));

        // Интеграции
        sender.sendMessage(lang.getComponent("info.integrations"));
        if (plugin.getHooksManager() != null) {
            logHook(sender, "LuckPerms", plugin.getHooksManager().getLuckPermsHook() != null
                    && plugin.getHooksManager().getLuckPermsHook().isEnabled());
            logHook(sender, "Vault", plugin.getHooksManager().getVaultHook() != null
                    && plugin.getHooksManager().getVaultHook().isEnabled());
            logHook(sender, "Essentials", plugin.getHooksManager().getEssentialsHook() != null
                    && plugin.getHooksManager().getEssentialsHook().isEnabled());
            logHook(sender, "PlaceholderAPI", plugin.getHooksManager().getPlaceholderAPIHook() != null
                    && plugin.getHooksManager().getPlaceholderAPIHook().isEnabled());
            logHook(sender, "DecentHolograms", plugin.getHooksManager().getDecentHologramsHook() != null
                    && plugin.getHooksManager().getDecentHologramsHook().isEnabled());
            logHook(sender, "Jobs Reborn", plugin.getHooksManager().getJobsHook() != null
                    && plugin.getHooksManager().getJobsHook().isEnabled());
        }

        // Сервер Bukkit
        sender.sendMessage(lang.getComponent("info.bukkit-server",
                "name", Bukkit.getServer().getName(),
                "version", Bukkit.getServer().getVersion()));

        sender.sendMessage(lang.getComponent("info.header"));
    }

    private void logHook(CommandSender sender, String name, boolean enabled) {
        sender.sendMessage(lang.getComponent(
                enabled ? "info.hook-enabled" : "info.hook-disabled",
                "name", name));
    }
}