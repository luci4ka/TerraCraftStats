package ru.terracraft.stats.core.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;

import java.util.ArrayList;
import java.util.List;

public class AchievementsCommand {

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;

    public AchievementsCommand(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void register() {
        new CommandAPICommand("aach")
                .withAliases("achievements", "ach")
                .withPermission("playerstats.achievements")
                .executes((sender, args) -> {
                    showHelp(sender);
                    return 0;
                })
                .withSubcommand(new CommandAPICommand("list")
                        .executesPlayer((player, args) -> {
                            showList(player);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("gui")
                        .executesPlayer((player, args) -> {
                            plugin.getAchievementsGui().open(player);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("stats")
                        .executesPlayer((player, args) -> {
                            showStats(player);
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("reload")
                        .withPermission("playerstats.achievements.reload")
                        .executes((sender, args) -> {
                            plugin.getConfigManager().reloadAchievements();
                            sender.sendMessage(lang.getComponent(sender instanceof Player p ? p : null,
                                    "achievements.reload-success"));
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("reset")
                        .withPermission("playerstats.achievements.reset")
                        .withArguments(new StringArgument("player")
                                .replaceSuggestions(ArgumentSuggestions.stringCollection(info ->
                                        onlinePlayerNames())))
                        .executes((sender, args) -> {
                            String name = (String) args.get("player");
                            Player target = Bukkit.getPlayerExact(name);
                            if (target == null) {
                                sender.sendMessage(lang.getComponent(sender instanceof Player p ? p : null,
                                        "commands.player-not-found", "player", name));
                                return 0;
                            }
                            plugin.getAchievementsService().reset(target.getUniqueId());
                            sender.sendMessage(lang.getComponent(sender instanceof Player p ? p : null,
                                    "achievements.reset-success", "player", target.getName()));
                            return 0;
                        }))
                .withSubcommand(new CommandAPICommand("give")
                        .withPermission("playerstats.achievements.give")
                        .withArguments(new StringArgument("player")
                                .replaceSuggestions(ArgumentSuggestions.stringCollection(info ->
                                        onlinePlayerNames())))
                        .withArguments(new StringArgument("achievement")
                                .replaceSuggestions(ArgumentSuggestions.stringCollection(info ->
                                        achievementIds())))
                        .executes((sender, args) -> {
                            String name = (String) args.get("player");
                            String achievementId = (String) args.get("achievement");

                            Player target = Bukkit.getPlayerExact(name);
                            if (target == null) {
                                sender.sendMessage(lang.getComponent(sender instanceof Player p ? p : null,
                                        "commands.player-not-found", "player", name));
                                return 0;
                            }

                            boolean success = plugin.getAchievementsService()
                                    .grantAdmin(target.getUniqueId(), achievementId);

                            if (success) {
                                sender.sendMessage(lang.getComponent(sender instanceof Player p ? p : null,
                                        "achievements.give-success",
                                        "player", target.getName(), "id", achievementId));
                                sender.sendMessage(lang.getComponent(sender instanceof Player p ? p : null,
                                        "achievements.give-no-reward"));
                            } else {
                                sender.sendMessage(lang.getComponent(sender instanceof Player p ? p : null,
                                        "achievements.give-fail", "id", achievementId));
                            }
                            return 0;
                        }))
                .register();
    }

    private void showHelp(CommandSender sender) {
        Player p = sender instanceof Player pl ? pl : null;
        sender.sendMessage(lang.getComponent(p, "achievements.help-header"));
        sender.sendMessage(lang.getComponent(p, "achievements.help-list"));
        sender.sendMessage(lang.getComponent(p, "achievements.help-stats"));
        if (sender.hasPermission("playerstats.achievements.reload")) {
            sender.sendMessage(lang.getComponent(p, "achievements.help-reload"));
        }
        if (sender.hasPermission("playerstats.achievements.give")) {
            sender.sendMessage(lang.getComponent(p, "achievements.help-give"));
        }
        if (sender.hasPermission("playerstats.achievements.reset")) {
            sender.sendMessage(lang.getComponent(p, "achievements.help-reset"));
        }
    }

    private void showList(Player player) {
        var service = plugin.getAchievementsService();
        player.sendMessage(lang.getComponent(player, "achievements.list-header"));

        int received = service.countReceived(player.getUniqueId());
        int total = service.totalCount();
        player.sendMessage(lang.getComponent(player, "achievements.list-progress",
                "received", received, "total", total));

        for (var entry : service.allCategories().entrySet()) {
            player.sendMessage(lang.getComponent(player, "achievements.list-category",
                    "category", entry.getKey(), "count", entry.getValue().size()));
        }
    }

    private void showStats(Player player) {
        var service = plugin.getAchievementsService();
        int received = service.countReceived(player.getUniqueId());
        int total = service.totalCount();
        int percent = total == 0 ? 0 : received * 100 / total;

        player.sendMessage(lang.getComponent(player, "achievements.stats-header"));
        player.sendMessage(lang.getComponent(player, "achievements.stats-line",
                "received", received, "total", total, "percent", percent));
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private List<String> achievementIds() {
        List<String> ids = new ArrayList<>();
        var cfg = plugin.getConfigManager().achievements();
        if (cfg != null && cfg.enabled()) {
            for (var entry : cfg.achievements().entrySet()) {
                for (var e : entry.getValue()) {
                    ids.add(entry.getKey() + ":" + e.threshold());
                }
            }
        }
        return ids;
    }
}