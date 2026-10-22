package ru.terracraft.stats.core.commands;

import dev.jorel.commandapi.CommandAPICommand;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.services.PlayerGroupService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

/**
 * /playerstats reload|snapshot|info|help|update
 */
public class PlayerStatsCommand {

    private final PlayerStatsPlugin plugin;
    private final PlayerGroupService groupService;

    public PlayerStatsCommand(PlayerStatsPlugin plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.groupService = groupService;
    }

    public void register() {
        new CommandAPICommand("playerstats")
                .withSubcommand(new CommandAPICommand("reload")
                        .withPermission("playerstats.reload")
                        .executes((sender, args) -> {
                            new ReloadCommand(plugin, groupService).execute(sender);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("snapshot")
                        .withPermission("playerstats.reload")
                        .executes((sender, args) -> {
                            sender.sendMessage(getComponent(sender, "snapshot.creating"));
                            plugin.getThreadManager().runAsync(() -> {
                                try {
                                    plugin.getStatHistoryDAO().snapshotAllPlayers();
                                    plugin.getThreadManager().runSync(() ->
                                            sender.sendMessage(getComponent(sender, "snapshot.success")));
                                } catch (SQLException e) {
                                    plugin.getThreadManager().runSync(() ->
                                            sender.sendMessage(getComponent(sender,
                                                    "snapshot.error", "error", e.getMessage())));
                                }
                            });
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("info")
                        .withPermission("playerstats.info")
                        .executes((sender, args) -> {
                            new InfoCommand(plugin).execute(sender);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("help")
                        .executes((sender, args) -> {
                            new HelpCommand(plugin).execute(sender);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("update")
                        .withPermission("playerstats.update")
                        .executes((sender, args) -> {
                            plugin.sendUpdateMessage(sender);
                            return 0;
                        }))
                .register();
    }

    private Component getComponent(CommandSender sender, String key, Object... repl) {
        if (sender instanceof Player player) {
            return plugin.getLanguageManager().getComponent(player, key, repl);
        }
        return plugin.getLanguageManager().getComponent(key, repl);
    }
}