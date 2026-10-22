package ru.terracraft.stats.core.commands;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.services.PlayerGroupService;

/**
 * Регистрация всех команд PlayerStats через CommandAPI.
 *
 * <p><b>Изоляция падений:</b> каждая команда регистрируется через
 * {@link #safe(String, Runnable)} — если одна упадёт, остальные зарегистрируются.</p>
 */
public class CommandRegistrar {

    private final PlayerStatsPlugin plugin;
    private final PlayerGroupService groupService;

    public CommandRegistrar(PlayerStatsPlugin plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.groupService = groupService;
    }

    public void register() {
        int ok = 0;
        if (safe("stats",       () -> new StatsCommand(plugin, groupService).register())) ok++;
        if (safe("top",         () -> new TopCommand(plugin).register())) ok++;
        if (safe("share",       () -> new ShareCommand(plugin, groupService).register())) ok++;
        if (safe("compare",     () -> new CompareCommand(plugin, groupService).register())) ok++;
        if (safe("serverstats", () -> new ServerStatsCommand(plugin).register())) ok++;
        if (safe("exclude",     () -> plugin.getExcludeCommand().register())) ok++;
        if (safe("playerstats", () -> new PlayerStatsCommand(plugin, groupService).register())) ok++;

        plugin.getLogger().info("Команды зарегистрированы через CommandAPI: " + ok + "/7.");
    }

    // ============ Изоляция ============

    /**
     * Регистрирует команду в try/catch — одна упавшая не ломает остальные.
     *
     * @return {@code true}, если регистрация успешна
     */
    private boolean safe(String name, Runnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable e) {
            plugin.getLogger().severe("Failed to register command '" + name
                    + "': " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}