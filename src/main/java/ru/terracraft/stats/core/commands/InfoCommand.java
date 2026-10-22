package ru.terracraft.stats.core.commands;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.config.model.MainConfig;
import ru.terracraft.stats.hooks.Hook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InfoCommand {

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;

    public InfoCommand(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    private MainConfig mainCfg() { return plugin.getConfigManager().main(); }

    public void execute(CommandSender sender) {
        // Консоль — всегда, игрок — только с playerstats.info
        if (sender instanceof Player && !sender.hasPermission("playerstats.info")) {
            sender.sendMessage(getComponent(sender, "no-permission"));
            return;
        }

        // ==================== ЗАГОЛОВОК ====================
        sender.sendMessage(getComponent(sender, "info.header"));

        Component title = getComponent(sender, "info.title",
                "version", plugin.getPluginMeta().getVersion());

        String authors = String.join(", ", plugin.getPluginMeta().getAuthors());

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

        // Тип БД — MariaDB (через MineSQL), другого не поддерживаем.
        String dbType = "MARIADB";
        boolean dbConnected = plugin.getSqlManager() != null;
        String dbStatus = dbConnected
                ? getRaw(sender, "status.connected")
                : getRaw(sender, "status.disconnected");

        sender.sendMessage(getComponent(sender, "info.database",
                "type", dbType,
                "status", dbStatus));

        // ==================== ИНТЕГРАЦИИ ====================
        if (plugin.getHooksManager() != null) {
            var hooksManager = plugin.getHooksManager();
            int active = countActiveHooks();

            sender.sendMessage(getComponent(sender, "info.section-hooks",
                    "active", active,
                    "total", hooksManager.getHookCount()));

            for (Hook hook : hooksManager.getAll()) {
                logHook(sender, hook.getPluginName(), hook.isEnabled());
            }
        }

        // ==================== НАСТРОЙКИ ====================
        sender.sendMessage(getComponent(sender, "info.section-settings"));

        int debugLevel = mainCfg().debugLevel();
        String debugText = switch (debugLevel) {
            case 0 -> getRaw(sender, "info.debug-off");
            case 1 -> getRaw(sender, "info.debug-basic");
            case 2 -> getRaw(sender, "info.debug-detailed");
            default -> getRaw(sender, "info.debug-unknown");
        };
        sender.sendMessage(getComponent(sender, "info.debug",
                "level", debugText + " <gray>(" + debugLevel + ")"));

        String multiStatus = mainCfg().multiServer().enabled()
                ? getRaw(sender, "status.enabled-f")
                : getRaw(sender, "status.disabled-f");
        sender.sendMessage(getComponent(sender, "info.multi", "status", multiStatus));

        boolean chipsEnabled = mainCfg().modules().topChips()
                && plugin.getConfigManager().topChips() != null;
        sender.sendMessage(getComponent(sender, "info.top-chips",
                "status", chipsEnabled
                        ? getRaw(sender, "status.enabled-f")
                        : getRaw(sender, "status.disabled-f")));

        boolean sparklineEnabled = mainCfg().modules().sparkline()
                && plugin.getConfigManager().sparkline() != null;
        sender.sendMessage(getComponent(sender, "info.sparkline",
                "status", sparklineEnabled
                        ? getRaw(sender, "status.enabled-f")
                        : getRaw(sender, "status.disabled-f")));

        boolean pvpEnabled = mainCfg().pvp().perVictimStats();
        sender.sendMessage(getComponent(sender, "info.pvp",
                "status", pvpEnabled
                        ? getRaw(sender, "status.enabled-f")
                        : getRaw(sender, "status.disabled-f")));

        sender.sendMessage(getComponent(sender, "info.header"));
    }

    // ==================== ВНУТРЕННЕЕ ====================

    private int countActiveHooks() {
        var hooks = plugin.getHooksManager();
        int count = 0;
        for (Hook hook : hooks.getAll()) {
            if (hook.isEnabled()) count++;
        }
        return count;
    }

    private void logHook(CommandSender sender, String name, boolean enabled) {
        Component line = getComponent(sender,
                enabled ? "info.hook-enabled" : "info.hook-disabled",
                "name", name);

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