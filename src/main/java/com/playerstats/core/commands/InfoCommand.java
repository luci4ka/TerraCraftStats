package com.playerstats.core.commands;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InfoCommand {

    private static final int TOTAL_HOOKS = 6;

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;

    public InfoCommand(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
    }

    public void execute(CommandSender sender) {
        if (!(sender instanceof Player) && !sender.hasPermission("playerstats.info")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        // ==================== ЗАГОЛОВОК ====================
        sender.sendMessage(getComponent(sender, "info.header"));

        Component title = getComponent(sender, "info.title",
                "version", plugin.getPluginMeta().getVersion());

        String authors = String.join(", ", plugin.getPluginMeta().getAuthors());

        // Hover — из lang.yml
        Component hoverText = getComponent(sender, "info.title-hover",
                "authors", authors,
                "version", plugin.getPluginMeta().getVersion());

        title = title.hoverEvent(HoverEvent.showText(hoverText));
        sender.sendMessage(title);

        sender.sendMessage(getComponent(sender, "info.author", "authors", authors));

        // ==================== СЕРВЕР ====================
        sender.sendMessage(getComponent(sender, "info.section-server"));
        sender.sendMessage(getComponent(sender, "info.server",
                "server", plugin.getServerName(),
                "id", plugin.getServerId()));
        sender.sendMessage(getComponent(sender, "info.bukkit-server",
                "name", Bukkit.getServer().getName(),
                "version", Bukkit.getServer().getVersion()));

        // ==================== БАЗА ДАННЫХ ====================
        sender.sendMessage(getComponent(sender, "info.section-database"));

        String dbType = config.getDatabaseType().toUpperCase();
        boolean dbConnected = plugin.getConnection() != null
                && plugin.getConnection().isConnected();
        String dbStatus = dbConnected
                ? getRaw(sender, "status.connected")
                : getRaw(sender, "status.disconnected");

        sender.sendMessage(getComponent(sender, "info.database",
                "type", dbType,
                "status", dbStatus));

        // ==================== HTTP API ====================
        boolean apiRunning = plugin.getApiServer() != null;

        String apiStatus = apiRunning
                ? getRaw(sender, "status.enabled")
                : getRaw(sender, "status.disabled");

        Component apiMessage = getComponent(sender, "info.api", "status", apiStatus);

        if (apiRunning) {
            String host = config.getApiHost() + ":" + config.getApiPort();

            // Hover — из lang.yml
            Component hostHover = getComponent(sender, "info.api-host-hover",
                    "host", host);

            Component hostComponent = getComponent(sender, "info.api-host", "host", host)
                    .clickEvent(ClickEvent.copyToClipboard(host))
                    .hoverEvent(HoverEvent.showText(hostHover));
            apiMessage = apiMessage.append(hostComponent);
        }
        sender.sendMessage(apiMessage);

        // ==================== ИНТЕГРАЦИИ ====================
        if (plugin.getHooksManager() != null) {
            int active = countActiveHooks();

            sender.sendMessage(getComponent(sender, "info.section-hooks",
                    "active", active,
                    "total", TOTAL_HOOKS));

            logHook(sender, "LuckPerms",
                    plugin.getHooksManager().getLuckPermsHook() != null
                            && plugin.getHooksManager().getLuckPermsHook().isEnabled());
            logHook(sender, "Vault",
                    plugin.getHooksManager().getVaultHook() != null
                            && plugin.getHooksManager().getVaultHook().isEnabled());
            logHook(sender, "Essentials",
                    plugin.getHooksManager().getEssentialsHook() != null
                            && plugin.getHooksManager().getEssentialsHook().isEnabled());
            logHook(sender, "PlaceholderAPI",
                    plugin.getHooksManager().getPlaceholderAPIHook() != null
                            && plugin.getHooksManager().getPlaceholderAPIHook().isEnabled());
            logHook(sender, "DecentHolograms",
                    plugin.getHooksManager().getDecentHologramsHook() != null
                            && plugin.getHooksManager().getDecentHologramsHook().isEnabled());
            logHook(sender, "Jobs Reborn",
                    plugin.getHooksManager().getJobsHook() != null
                            && plugin.getHooksManager().getJobsHook().isEnabled());
        }

        // ==================== НАСТРОЙКИ ====================
        sender.sendMessage(getComponent(sender, "info.section-settings"));

        // Debug — из lang.yml
        int debugLevel = config.getDebugLevel();
        String debugText = switch (debugLevel) {
            case 0 -> getRaw(sender, "info.debug-off");
            case 1 -> getRaw(sender, "info.debug-basic");
            case 2 -> getRaw(sender, "info.debug-detailed");
            default -> getRaw(sender, "info.debug-unknown");
        };
        sender.sendMessage(getComponent(sender, "info.debug",
                "level", debugText + " <gray>(" + debugLevel + ")"));

        // Multi-server
        String multiStatus = config.isMultiServerEnabled()
                ? getRaw(sender, "status.enabled-f")
                : getRaw(sender, "status.disabled-f");
        sender.sendMessage(getComponent(sender, "info.multi", "status", multiStatus));

        // Top-chips
        boolean chipsEnabled = config.isTopChipsEnabled();
        sender.sendMessage(getComponent(sender, "info.top-chips",
                "status", chipsEnabled
                        ? getRaw(sender, "status.enabled-f")
                        : getRaw(sender, "status.disabled-f")));

        // Sparkline
        boolean sparklineEnabled = config.isSparklineEnabled();
        sender.sendMessage(getComponent(sender, "info.sparkline",
                "status", sparklineEnabled
                        ? getRaw(sender, "status.enabled-f")
                        : getRaw(sender, "status.disabled-f")));

        // PvP
        boolean pvpEnabled = config.isPvpPerVictimStats();
        sender.sendMessage(getComponent(sender, "info.pvp",
                "status", pvpEnabled
                        ? getRaw(sender, "status.enabled-f")
                        : getRaw(sender, "status.disabled-f")));

        sender.sendMessage(getComponent(sender, "info.header"));
    }

    // ==================== ВНУТРЕННЕЕ ====================

    private int countActiveHooks() {
        int count = 0;
        var hooks = plugin.getHooksManager();
        if (hooks.getLuckPermsHook() != null && hooks.getLuckPermsHook().isEnabled()) count++;
        if (hooks.getVaultHook() != null && hooks.getVaultHook().isEnabled()) count++;
        if (hooks.getEssentialsHook() != null && hooks.getEssentialsHook().isEnabled()) count++;
        if (hooks.getPlaceholderAPIHook() != null && hooks.getPlaceholderAPIHook().isEnabled()) count++;
        if (hooks.getDecentHologramsHook() != null && hooks.getDecentHologramsHook().isEnabled()) count++;
        if (hooks.getJobsHook() != null && hooks.getJobsHook().isEnabled()) count++;
        return count;
    }

    private void logHook(CommandSender sender, String name, boolean enabled) {
        Component line = getComponent(sender,
                enabled ? "info.hook-enabled" : "info.hook-disabled",
                "name", name);

        // Hover — из lang.yml
        Component hoverText = getComponent(sender,
                enabled ? "info.hook-enabled-hover" : "info.hook-disabled-hover",
                "name", name);

        line = line.hoverEvent(HoverEvent.showText(hoverText));

        sender.sendMessage(line);
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
}