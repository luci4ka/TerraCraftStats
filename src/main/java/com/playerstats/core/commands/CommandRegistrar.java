package com.playerstats.core.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.gui.AchievementsGui;
import com.playerstats.core.services.PlayerGroupService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * Регистрирует все Brigadier-команды плагина.
 */
public final class CommandRegistrar {

    /**
     * Имена stat-ов для /top <stat> — не переводятся.
     */
    private static final List<String> SUGGESTED_STATS = List.of(
            "mobs_killed", "bosses_killed", "kills", "deaths",
            "play_time", "blocks_placed", "blocks_broken", "balance",
            "killstreak", "best_killstreak", "distance_walked",
            "damage_dealt", "damage_taken", "items_crafted",
            "food_consumed", "arrows_shot", "fish_caught",
            "entities_tamed", "entities_bred", "teleports",
            "portal_uses", "logins"
    );

    /**
     * Имена stat-ов для /playerstats hologram create — не переводятся.
     */
    private static final List<String> SUGGESTED_HOLOGRAM_STATS = List.of(
            "kills", "mobs_killed", "blocks_placed",
            "play_time", "balance", "killstreak"
    );

    private final Main plugin;
    private final PlayerGroupService groupService;

    public CommandRegistrar(Main plugin, PlayerGroupService groupService) {
        this.plugin = plugin;
        this.groupService = groupService;
    }

    /**
     * Зарегистрировать все команды через LifecycleEvents.
     */
    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            registerStats(commands);
            registerTop(commands);
            registerShare(commands);
            registerCompare(commands);
            registerServerStats(commands);
            registerExclude(commands);
            registerPlayerStats(commands);

            plugin.getLogger().info("Команды зарегистрированы через Brigadier API.");
        });
    }

    // ==================== /stats ====================

    private void registerStats(Commands commands) {
        LiteralCommandNode<CommandSourceStack> statsNode = Commands.literal("stats")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (sender instanceof Player player) {
                        new StatsCommand(plugin, groupService).execute(player);
                    } else {
                        sender.sendMessage(getComponent(sender, "commands.only-player"));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                // /stats <player>
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

                            String targetName = StringArgumentType.getString(ctx, "player");
                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                                player.sendMessage(getComponent(player,
                                        "commands.player-not-found",
                                        "player", targetName));
                                return Command.SINGLE_SUCCESS;
                            }
                            new StatsCommand(plugin, groupService).execute(player, target);
                            return Command.SINGLE_SUCCESS;
                        }))
                // /stats pvp [player] [page]
                .then(Commands.literal("pvp")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (sender instanceof Player player) {
                                new StatsCommand(plugin, groupService).executePvp(player, player, 0);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                    new StatsCommand(plugin, groupService).executePvp(player, target, 0);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            int page = IntegerArgumentType.getInteger(ctx, "page");
                                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                            new StatsCommand(plugin, groupService).executePvp(player, target, page);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // /stats mob [player] [page]
                .then(Commands.literal("mob")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (sender instanceof Player player) {
                                new StatsCommand(plugin, groupService).executeMob(player, player, 0);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                    new StatsCommand(plugin, groupService).executeMob(player, target, 0);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            int page = IntegerArgumentType.getInteger(ctx, "page");
                                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                            new StatsCommand(plugin, groupService).executeMob(player, target, page);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // /stats block [player] [page]
                .then(Commands.literal("block")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (sender instanceof Player player) {
                                new StatsCommand(plugin, groupService).executeBlock(player, player, 0);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                    new StatsCommand(plugin, groupService).executeBlock(player, target, 0);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                            String targetName = StringArgumentType.getString(ctx, "player");
                                            int page = IntegerArgumentType.getInteger(ctx, "page");
                                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                            new StatsCommand(plugin, groupService).executeBlock(player, target, page);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // /stats achievements
                .then(Commands.literal("achievements")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (sender instanceof Player player) {
                                new AchievementsGui(plugin).open(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                // /stats gui
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (sender instanceof Player player) {
                                plugin.getStatsGui().open(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("rollback")
                        .requires(src -> src.getSender().hasPermission("playerstats.rollback"))
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String[] args = StringArgumentType.getString(ctx, "args")
                                            .split("\\s+");
                                    new RollbackCommand(plugin).execute(sender, args);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();

        commands.register(statsNode,
                getDescription("commands.descriptions.stats"),
                List.of("playerstats.stats", "playerstats.stats.others"));
    }

    // ==================== /top ====================

    private void registerTop(Commands commands) {
        LiteralCommandNode<CommandSourceStack> topNode = Commands.literal("top")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (sender instanceof Player player) {
                        new TopCommand(plugin).execute(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                // /top pvp <ник>
                .then(Commands.literal("pvp")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                    if (target == null || target.getUniqueId() == null
                                            || (!target.hasPlayedBefore() && !target.isOnline())) {
                                        player.sendMessage(getComponent(player,
                                                "commands.player-not-found",
                                                "player", targetName));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    new TopCommand(plugin).executePvp(
                                            player, target.getUniqueId(), targetName);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // /top <stat>
                .then(Commands.argument("stat", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            // Имена stat-ов — не переводятся
                            SUGGESTED_STATS.forEach(builder::suggest);

                            for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
                                if (type.isAlive() && !type.equals(org.bukkit.entity.EntityType.PLAYER)) {
                                    builder.suggest("mob_killed_" + type.name().toLowerCase());
                                }
                            }

                            var tracked = ConfigHandler.getInstance(plugin).getTrackedBlocks();
                            if (tracked != null) {
                                for (String block : tracked.getBlocks()) {
                                    builder.suggest("block_placed_" + block);
                                    builder.suggest("block_broken_" + block);
                                }
                            }

                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

                            String statName = StringArgumentType.getString(ctx, "stat");
                            new TopCommand(plugin).execute(player, statName);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        commands.register(topNode,
                getDescription("commands.descriptions.top"),
                List.of("playerstats.top"));
    }

    // ==================== /share ====================

    private void registerShare(Commands commands) {
        LiteralCommandNode<CommandSourceStack> shareNode = Commands.literal("share")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (sender instanceof Player player) {
                        new ShareCommand(plugin, groupService).execute(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

                            String targetName = StringArgumentType.getString(ctx, "player");
                            Player target = Bukkit.getPlayer(targetName);
                            new ShareCommand(plugin, groupService).execute(player, target);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        commands.register(shareNode,
                getDescription("commands.descriptions.share"),
                List.of("playerstats.share"));
    }

    // ==================== /compare ====================

    private void registerCompare(Commands commands) {
        LiteralCommandNode<CommandSourceStack> compareNode = Commands.literal("compare")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

                            String targetName = StringArgumentType.getString(ctx, "player");
                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                                player.sendMessage(getComponent(player,
                                        "commands.player-not-found",
                                        "player", targetName));
                                return Command.SINGLE_SUCCESS;
                            }
                            new CompareCommand(plugin, groupService).execute(player, target);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        commands.register(compareNode,
                getDescription("commands.descriptions.compare"),
                List.of("playerstats.compare"));
    }

    // ==================== /serverstats ====================

    private void registerServerStats(Commands commands) {
        LiteralCommandNode<CommandSourceStack> serverStatsNode = Commands.literal("serverstats")
                .executes(ctx -> {
                    new ServerStatsCommand(plugin).execute(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build();

        commands.register(serverStatsNode,
                getDescription("commands.descriptions.serverstats"),
                List.of("playerstats.serverstats"));
    }

    // ==================== /exclude ====================

    private void registerExclude(Commands commands) {
        LiteralCommandNode<CommandSourceStack> excludeNode = Commands.literal("exclude")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String[] args = StringArgumentType.getString(ctx, "args").split(" ");
                            plugin.getExcludeCommand().execute(sender, args);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        commands.register(excludeNode,
                getDescription("commands.descriptions.exclude"),
                List.of("playerstats.exclude"));
    }

    // ==================== /playerstats ====================

    private void registerPlayerStats(Commands commands) {
        LiteralCommandNode<CommandSourceStack> playerstatsNode = Commands.literal("playerstats")
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            new ReloadCommand(plugin, groupService).execute(sender);
                            return Command.SINGLE_SUCCESS;
                        }))
                // /playerstats snapshot
                .then(Commands.literal("snapshot")
                        .requires(source -> source.getSender().hasPermission("playerstats.reload"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            sender.sendMessage(getComponent(sender, "snapshot.creating"));

                            plugin.getThreadManager().runAsync(() -> {
                                try {
                                    plugin.getStatHistoryDAO().snapshotAllPlayers();
                                    plugin.getThreadManager().runSync(() ->
                                            sender.sendMessage(getComponent(sender,
                                                    "snapshot.success")));
                                } catch (SQLException e) {
                                    plugin.getThreadManager().runSync(() ->
                                            sender.sendMessage(getComponent(sender,
                                                    "snapshot.error",
                                                    "error", e.getMessage())));
                                }
                            });
                            return Command.SINGLE_SUCCESS;
                        }))
                // /playerstats hologram ...
                .then(Commands.literal("hologram")
                        .requires(source -> source.getSender().hasPermission("playerstats.reload"))
                        .then(Commands.literal("create")
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            // Имена stat-ов — не переводятся
                                            SUGGESTED_HOLOGRAM_STATS.forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) {
                                                sender.sendMessage(getComponent(sender,
                                                        "commands.only-player-short"));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String stat = StringArgumentType.getString(ctx, "stat");
                                            Location loc = player.getLocation();

                                            plugin.getHologramManager().createHologram(stat, loc);

                                            player.sendMessage(getComponent(player,
                                                    "holograms.created",
                                                    "stat", stat));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            String stat = StringArgumentType.getString(ctx, "stat");
                                            plugin.getHologramManager().removeHologram(stat);
                                            sender.sendMessage(getComponent(sender,
                                                    "holograms.removed",
                                                    "stat", stat));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    var names = plugin.getHologramManager().getHologramNames();
                                    if (names.isEmpty()) {
                                        sender.sendMessage(getComponent(sender,
                                                "holograms.list-empty"));
                                    } else {
                                        sender.sendMessage(getComponent(sender,
                                                "holograms.list-header",
                                                "list", String.join(", ", names)));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            new InfoCommand(plugin).execute(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            new HelpCommand(plugin).execute(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("apikey")
                        .requires(source -> source.getSender().hasPermission("playerstats.apikey"))
                        .executes(ctx -> {
                            new ApiKeyCommand(plugin).execute(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("regenerate")
                                .executes(ctx -> {
                                    new ApiKeyCommand(plugin).regenerate(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("show")
                                .executes(ctx -> {
                                    new ApiKeyCommand(plugin).execute(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("update")
                        .requires(source -> source.getSender().hasPermission("playerstats.update"))
                        .executes(ctx -> {
                            plugin.sendUpdateMessage(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
// /playerstats backup [now|list|restore <file>]
                .then(Commands.literal("backup")
                        .requires(source -> source.getSender().hasPermission("playerstats.backup"))
                        .executes(ctx -> {
                            // /playerstats backup → now
                            new BackupCommand(plugin).execute(ctx.getSource().getSender(), new String[0]);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("now")
                                .executes(ctx -> {
                                    new BackupCommand(plugin).execute(
                                            ctx.getSource().getSender(), new String[]{"now"});
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    new BackupCommand(plugin).execute(
                                            ctx.getSource().getSender(), new String[]{"list"});
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Path p : plugin.getDatabaseBackupTask().listBackups()) {
                                                builder.suggest(p.getFileName().toString());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String file = StringArgumentType.getString(ctx, "file");
                                            new BackupCommand(plugin).execute(
                                                    ctx.getSource().getSender(),
                                                    new String[]{"restore", file});
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();

        commands.register(playerstatsNode,
                getDescription("commands.descriptions.playerstats"),
                List.of("playerstats.reload", "playerstats.info", "playerstats.update"));
    }

    // ==================== LANG HELPERS ====================

    /**
     * Получить Component для игрока (на его языке) или консоли (defaultLang).
     */
    private Component getComponent(CommandSender sender, String key, Object... replacements) {
        if (sender instanceof Player player) {
            return plugin.getLanguageManager().getComponent(player, key, replacements);
        }
        return plugin.getLanguageManager().getComponent(key, replacements);
    }

    /**
     * Получить описание команды из lang.yml (plain text, без MiniMessage).
     * Brigadier description — обычная строка, без цветов.
     */
    private String getDescription(String key) {
        String message = plugin.getLanguageManager().getRaw(key);
        // Убираем MiniMessage-теги
        return message.replaceAll("<[^>]+>", "");
    }
}