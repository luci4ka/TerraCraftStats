package com.playerstats;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.playerstats.api.ApiServer;
import com.playerstats.core.commands.*;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.DatabaseConnection;
import com.playerstats.core.database.MySQLConnection;
import com.playerstats.core.database.SQLiteConnection;
import com.playerstats.core.database.dao.*;
import com.playerstats.core.gui.StatsGui;
import com.playerstats.core.gui.StatsGuiListener;
import com.playerstats.core.listeners.JoinListener;
import com.playerstats.core.listeners.ProjectileKillListener;
import com.playerstats.core.listeners.StatsListener;
import com.playerstats.core.multithreading.ThreadManager;
import com.playerstats.core.pvp.PvPTracker;
import com.playerstats.core.services.*;
import com.playerstats.hooks.HooksManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class Main extends JavaPlugin {

    // ======= Поля =======
    private DatabaseConnection db;
    private PlayerDAO playerDAO;
    private PlayerStatsDAO statsDAO;
    private StatTypeDAO statTypeDAO;
    private ServerDAO serverDAO;
    private ServerStatsDAO serverStatsDAO;
    private LeaderboardDAO leaderboardDAO;
    private ThreadManager threadManager;
    private ApiServer apiServer;
    private TopCache topCache;
    private ConfigHandler config;
    private UpdateChecker updateChecker;
    private LanguageManager languageManager;
    private PvPTracker pvpTracker;
    private HooksManager hooksManager;
    private StatsGui statsGui;
    private MilestoneManager milestoneManager;
    private DiscordWebhook discordWebhook;

    private PlayerGroupService groupService;
    private ExcludeCommand excludeCommand;
    private StatsListener statsListener;

    private int serverId = -1;
    private String serverName;

    // ======= Жизненный цикл =======

    @Override
    public void onEnable() {
        // 1. Конфигурация
        try {
            this.config = ConfigHandler.getInstance(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось загрузить конфигурацию. Плагин отключается.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.languageManager = new LanguageManager(this);

        boolean useMySQL = config.getDatabaseType().equalsIgnoreCase("mysql");
        if (config.isVerboseDebug()) {
            getLogger().info("[DEBUG] Тип БД: " + (useMySQL ? "MySQL" : "SQLite"));
        }

        // 2. База данных
        try {
            if (useMySQL) {
                db = new MySQLConnection(
                        this,                    // ← добавлен plugin
                        config.getMySQLHost(),
                        config.getMySQLPort(),
                        config.getMySQLDatabase(),
                        config.getMySQLUsername(),
                        config.getMySQLPassword()
                );
            } else {
                File dbFile = new File(getDataFolder(), config.getSQLiteFile());
                if (config.isVerboseDebug()) {
                    getLogger().info("[DEBUG] SQLite файл: " + dbFile.getAbsolutePath());
                }
                db = new SQLiteConnection(
                        this,                    // ← добавлен plugin
                        dbFile.getAbsolutePath()
                );
            }

            db.connect();
            db.initTables();

            getLogger().info("База данных подключена: " + (useMySQL ? "MySQL" : "SQLite"));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Не удалось подключиться к базе данных или инициализировать таблицы.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. DAO
        try {
            this.statTypeDAO = new StatTypeDAO(this, db, useMySQL);
            this.playerDAO = new PlayerDAO(this, db, useMySQL);
            this.statsDAO = new PlayerStatsDAO(this, db, useMySQL, statTypeDAO);
            this.serverDAO = new ServerDAO(this, db, useMySQL);
            this.serverStatsDAO = new ServerStatsDAO(this, db, useMySQL, statTypeDAO);
            this.leaderboardDAO = new LeaderboardDAO(this, db, statTypeDAO);
            getLogger().info("DAO инициализированы.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось инициализировать DAO.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Серверный ID
        try {
            this.serverName = config.isMultiServerEnabled()
                    ? config.getServerName()
                    : Bukkit.getServer().getName();
            this.serverId = serverDAO.createOrGetServer(serverName);
            getLogger().info("Сервер зарегистрирован: " + serverName + " (ID: " + serverId + ")");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Не удалось получить или создать ID сервера.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. ThreadManager
        this.threadManager = new ThreadManager(this);
        getLogger().info("ThreadManager инициализирован.");

        // 6. TopCache
        this.topCache = new TopCache(this, leaderboardDAO, serverId, 5);
        getLogger().info("TopCache инициализирован.");

        // 7. Хуки
        this.hooksManager = new HooksManager(this);
        hooksManager.init();

        // 8. PlayerGroupService
        this.groupService = new PlayerGroupService(
                this, config, statsDAO, hooksManager.getLuckPermsHook());
        getLogger().info("PlayerGroupService инициализирован.");

        // 9. ExcludeCommand
        this.excludeCommand = new ExcludeCommand(this);
        excludeCommand.loadExcludedPlayers();
        getLogger().info("Список исключённых игроков загружен.");

        //9.5
        this.pvpTracker = new PvPTracker(this);
        getLogger().info("PvPTracker инициализирован.");

        // 10. MilestoneManager
        this.milestoneManager = new MilestoneManager(this);
        getLogger().info("MilestoneManager инициализирован.");

        // 11. DiscordWebhook
        this.discordWebhook = new DiscordWebhook(this);
        if (discordWebhook.isEnabled()) {
            getLogger().info("Discord Webhook подключен.");
        }

        // 12. HTTP API
        if (config.isApiEnabled()) {
            try {
                TotalServerDAO totalServerDAO = new TotalServerDAO(this, db, statTypeDAO);
                ServerStatsService statsService = new ServerStatsService(totalServerDAO, getLogger());
                this.apiServer = new ApiServer(statsService, playerDAO, config.getApiHost(), config.getApiPort());
                this.apiServer.start();
                getLogger().info("HTTP API запущен на " + config.getApiHost() + ":" + config.getApiPort());
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Не удалось запустить HTTP API.", e);
                this.apiServer = null;
            }
        } else {
            this.apiServer = null;
            getLogger().info("HTTP API отключен в конфиге.");
        }

        // 13. Слушатели
        JoinListener joinListener = new JoinListener(this, playerDAO);
        Bukkit.getPluginManager().registerEvents(joinListener, this);

        this.statsListener = new StatsListener(
                this, playerDAO, statsDAO, serverStatsDAO, serverDAO, threadManager,
                hooksManager.getVaultHook(), hooksManager.getEssentialsHook());
        statsListener.setExcludeCommand(excludeCommand);
        statsListener.setPvpTracker(pvpTracker);  // ← добавить
        Bukkit.getPluginManager().registerEvents(statsListener, this);

        ProjectileKillListener projectileListener = new ProjectileKillListener(
                this, playerDAO, statsDAO, serverStatsDAO, threadManager,
                hooksManager.getEssentialsHook());
        projectileListener.setExcludeCommand(excludeCommand);
        projectileListener.setPvpTracker(pvpTracker);  // ← добавить
        Bukkit.getPluginManager().registerEvents(projectileListener, this);

        // Jobs Reborn — с безопасной проверкой классов через рефлексию
        if (hooksManager.getJobsHook() != null && hooksManager.getJobsHook().isEnabled()) {
            try {
                // Проверяем наличие классов ДО регистрации слушателя
                Class.forName("com.gamingmesh.jobs.api.JobsPaymentEvent");
                Class.forName("com.gamingmesh.jobs.api.JobsLevelUpEvent");

                // Классы найдены — регистрируем слушатель
                Class<?> jobsListenerClass = Class.forName(
                        "com.playerstats.core.listeners.JobsListener");

                Object jobsListener = jobsListenerClass
                        .getConstructor(Main.class, PlayerDAO.class,
                                PlayerStatsDAO.class, ThreadManager.class)
                        .newInstance(this, playerDAO, statsDAO, threadManager);

                Bukkit.getPluginManager().registerEvents(
                        (org.bukkit.event.Listener) jobsListener, this);

                getLogger().info("JobsListener зарегистрирован.");

            } catch (ClassNotFoundException e) {
                getLogger().warning("Jobs API не найден — JobsListener не зарегистрирован.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING,
                        "Не удалось зарегистрировать JobsListener", e);
            }
        }

        getLogger().info("Слушатели зарегистрированы.");

        // 14. GUI
        this.statsGui = new StatsGui(this);
        Bukkit.getPluginManager().registerEvents(new StatsGuiListener(this), this);
        getLogger().info("StatsGui инициализирован.");

        // 15. Команды (Brigadier API)
        registerCommands();

        // 16. bStats
        try {
            int pluginId = 27074;
            new Metrics(this, pluginId);
            getLogger().info("bStats подключен.");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось подключиться к bStats.", e);
        }

        // 17. UpdateChecker
        try {
            this.updateChecker = new UpdateChecker(this, "luci/PlayerStats");
            updateChecker.checkAsync();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось запустить UpdateChecker", e);
        }

        // 18. Информация о запуске
        logPluginStartInfo(useMySQL);
    }

    @Override
    public void onDisable() {
        if (statsListener != null) {
            try {
                statsListener.saveAllOnline();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка при сохранении статистики", e);
            }
        }

        if (apiServer != null) {
            try {
                apiServer.stop();
                getLogger().info("HTTP API остановлен.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка при остановке HTTP API.", e);
            }
        }

        if (groupService != null) {
            groupService.clearCache();
        }

        if (milestoneManager != null) {
            milestoneManager.clearCache();
        }

        if (threadManager != null) {
            try {
                threadManager.shutdown();
                getLogger().info("ThreadManager остановлен.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка при остановке менеджера потоков.", e);
            }
        }

        if (hooksManager != null) {
            hooksManager.shutdown();
        }

        if (pvpTracker != null) {
            pvpTracker.clear();
        }

        if (db != null) {
            try {
                db.disconnect();
                getLogger().info("База данных отключена.");
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Ошибка при отключении от базы данных.", e);
            }
        }

        ConfigHandler.reset();

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§c[PlayerStats] ⛔ Плагин выключен!");
        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    // ======= Регистрация команд (Brigadier) =======

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // ==================== /stats ====================
            LiteralCommandNode<CommandSourceStack> statsNode = Commands.literal("stats")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (sender instanceof Player player) {
                            new StatsCommand(Main.this, groupService).execute(player);
                        } else {
                            sender.sendMessage(Component.text("Только игрок может использовать эту команду.")
                                    .color(NamedTextColor.RED));
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
                                    player.sendMessage(Component.text("Игрок '" + targetName + "' не найден.")
                                            .color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                new StatsCommand(Main.this, groupService).execute(player, target);
                                return Command.SINGLE_SUCCESS;
                            }))
                    // /stats pvp [player] [page]
                    .then(Commands.literal("pvp")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (sender instanceof Player player) {
                                    new StatsCommand(Main.this, groupService).executePvp(player, player, 0);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                        String targetName = StringArgumentType.getString(ctx, "player");
                                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                        new StatsCommand(Main.this, groupService).executePvp(player, target, 0);
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                            .executes(ctx -> {
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                                String targetName = StringArgumentType.getString(ctx, "player");
                                                int page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page");
                                                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                                new StatsCommand(Main.this, groupService).executePvp(player, target, page);
                                                return Command.SINGLE_SUCCESS;
                                            }))))
                    // /stats mob [player] [page]
                    .then(Commands.literal("mob")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (sender instanceof Player player) {
                                    new StatsCommand(Main.this, groupService).executeMob(player, player, 0);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                        String targetName = StringArgumentType.getString(ctx, "player");
                                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                        new StatsCommand(Main.this, groupService).executeMob(player, target, 0);
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                            .executes(ctx -> {
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                                String targetName = StringArgumentType.getString(ctx, "player");
                                                int page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page");
                                                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                                new StatsCommand(Main.this, groupService).executeMob(player, target, page);
                                                return Command.SINGLE_SUCCESS;
                                            }))))
                    // /stats block [player] [page]
                    .then(Commands.literal("block")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (sender instanceof Player player) {
                                    new StatsCommand(Main.this, groupService).executeBlock(player, player, 0);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                        String targetName = StringArgumentType.getString(ctx, "player");
                                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                        new StatsCommand(Main.this, groupService).executeBlock(player, target, 0);
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                            .executes(ctx -> {
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
                                                String targetName = StringArgumentType.getString(ctx, "player");
                                                int page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page");
                                                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                                new StatsCommand(Main.this, groupService).executeBlock(player, target, page);
                                                return Command.SINGLE_SUCCESS;
                                            }))))
                    // /stats gui
                    .then(Commands.literal("gui")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (sender instanceof Player player) {
                                    statsGui.open(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build();

            commands.register(statsNode, "Просмотр статистики",
                    List.of("playerstats.stats", "playerstats.stats.others"));

            // ==================== /top ====================
            LiteralCommandNode<CommandSourceStack> topNode = Commands.literal("top")
                    // /top
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (sender instanceof Player player) {
                            new TopCommand(Main.this).execute(player);
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
                                            player.sendMessage(Component.text("Игрок '" + targetName + "' не найден.")
                                                    .color(NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        new TopCommand(Main.this).executePvp(
                                                player, target.getUniqueId(), targetName);
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    // /top <stat>
                    .then(Commands.argument("stat", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                // Стандартные статистики
                                builder.suggest("mobs_killed");
                                builder.suggest("bosses_killed");
                                builder.suggest("kills");
                                builder.suggest("deaths");
                                builder.suggest("play_time");
                                builder.suggest("blocks_placed");
                                builder.suggest("blocks_broken");
                                builder.suggest("balance");

                                // Типы мобов
                                for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
                                    if (type.isAlive() && !type.equals(org.bukkit.entity.EntityType.PLAYER)) {
                                        builder.suggest("mob_killed_" + type.name().toLowerCase());
                                    }
                                }

                                // Отслеживаемые блоки
                                var tracked = ConfigHandler.getInstance(Main.this).getTrackedBlocks();
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
                                new TopCommand(Main.this).execute(player, statName);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build();

            commands.register(topNode, "Показать топ игроков", List.of("playerstats.top"));

            // ==================== /share ====================
            LiteralCommandNode<CommandSourceStack> shareNode = Commands.literal("share")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (sender instanceof Player player) {
                            new ShareCommand(Main.this, groupService).execute(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

                                String targetName = StringArgumentType.getString(ctx, "player");
                                Player target = Bukkit.getPlayer(targetName);
                                new ShareCommand(Main.this, groupService).execute(player, target);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build();

            commands.register(shareNode, "Поделиться статистикой", List.of("playerstats.share"));

            // ==================== /compare ====================
            LiteralCommandNode<CommandSourceStack> compareNode = Commands.literal("compare")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

                                String targetName = StringArgumentType.getString(ctx, "player");
                                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                                    player.sendMessage(languageManager.getComponent(
                                            "player-not-found", "player", targetName));
                                    return Command.SINGLE_SUCCESS;
                                }
                                new CompareCommand(Main.this, groupService).execute(player, target);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build();

            commands.register(compareNode, "Сравнить статистику", List.of("playerstats.compare"));

            // ==================== /serverstats ====================
            LiteralCommandNode<CommandSourceStack> serverStatsNode = Commands.literal("serverstats")
                    .executes(ctx -> {
                        new ServerStatsCommand(Main.this).execute(ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    })
                    .build();

            commands.register(serverStatsNode, "Статистика сервера", List.of("playerstats.serverstats"));

            // ==================== /exclude ====================
            LiteralCommandNode<CommandSourceStack> excludeNode = Commands.literal("exclude")
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                String[] args = StringArgumentType.getString(ctx, "args").split(" ");
                                excludeCommand.execute(sender, args);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build();

            commands.register(excludeNode, "Управление исключениями", List.of("playerstats.exclude"));

            // ==================== /playerstats ====================
            LiteralCommandNode<CommandSourceStack> playerstatsNode = Commands.literal("playerstats")
                    .then(Commands.literal("reload")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                new ReloadCommand(Main.this, groupService).execute(sender);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .then(Commands.literal("info")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                new InfoCommand(Main.this).execute(sender);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .then(Commands.literal("help")
                            .executes(ctx -> {
                                new HelpCommand(Main.this).execute(ctx.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            }))
                    .then(Commands.literal("update")
                            .requires(source -> source.getSender().hasPermission("playerstats.update"))
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                sendUpdateMessage(sender);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build();

            commands.register(playerstatsNode, "Управление плагином PlayerStats",
                    List.of("playerstats.reload", "playerstats.info", "playerstats.update"));
        });

        getLogger().info("Команды зарегистрированы через Brigadier API.");
    }

    /**
     * Отправить сообщение о статусе обновления.
     */
    private void sendUpdateMessage(CommandSender sender) {
        if (updateChecker == null) {
            sender.sendMessage(Component.text("UpdateChecker не инициализирован.")
                    .color(NamedTextColor.RED));
            return;
        }

        if (updateChecker.isUpdateAvailable()) {
            sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .color(NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("📢 Доступно обновление!")
                    .color(NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  Текущая: v" + updateChecker.getCurrentVersion())
                    .color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Новая: v" + updateChecker.getLatestVersion())
                    .color(NamedTextColor.GREEN));
            sender.sendMessage(Component.text("  Скачать: " + updateChecker.getDownloadUrl())
                    .color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .color(NamedTextColor.DARK_GRAY));
        } else {
            sender.sendMessage(Component.text("✅ Плагин актуален (v"
                            + updateChecker.getCurrentVersion() + ")")
                    .color(NamedTextColor.GREEN));
        }
    }

    private void logPluginStartInfo(boolean useMySQL) {
        String apiStatus = (apiServer != null) ? "§aЗапущен ✅" : "§cОтключен ❌";

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§b[PlayerStats] 🌟 Плагин успешно включен!");
        Bukkit.getConsoleSender().sendMessage("§6Версия: §a" + getPluginMeta().getVersion() + " §7by §3luci");
        Bukkit.getConsoleSender().sendMessage("§6База: §3" + (useMySQL ? "MySQL ✅" : "SQLite ✅"));
        Bukkit.getConsoleSender().sendMessage("§6Сервер: §3" + serverName + " §7(ID: " + serverId + ")");
        Bukkit.getConsoleSender().sendMessage("§6API: " + apiStatus);
        if (apiServer != null) {
            Bukkit.getConsoleSender().sendMessage("§6Хост: §3" + config.getApiHost() + " §7| §6Порт: §3" + config.getApiPort());
        }

        hooksManager.logStatus();

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    public void updateServerInfo(int newServerId, String newServerName) {
        this.serverId = newServerId;
        this.serverName = newServerName;
        getLogger().info("Информация о сервере обновлена: " + newServerName + " (ID: " + newServerId + ")");
    }

    // ======= Геттеры =======
    public PlayerDAO getPlayerDAO() { return playerDAO; }
    public PlayerStatsDAO getStatsDAO() { return statsDAO; }
    public StatTypeDAO getStatTypeDAO() { return statTypeDAO; }
    public ThreadManager getThreadManager() { return threadManager; }
    public ServerDAO getServerDAO() { return serverDAO; }
    public ServerStatsDAO getServerStatsDAO() { return serverStatsDAO; }
    public LeaderboardDAO getLeaderboardDAO() { return leaderboardDAO; }
    public int getServerId() { return serverId; }
    public DatabaseConnection getConnection() { return db; }
    public ApiServer getApiServer() { return apiServer; }
    public ConfigHandler getConfigHandler() { return config; }
    public PlayerGroupService getGroupService() { return groupService; }
    public HooksManager getHooksManager() { return hooksManager; }
    public String getServerName() { return serverName; }
    public TopCache getTopCache() { return topCache; }
    public StatsGui getStatsGui() { return statsGui; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public MilestoneManager getMilestoneManager() { return milestoneManager; }
    public DiscordWebhook getDiscordWebhook() { return discordWebhook; }
    public PvPTracker getPvpTracker() { return pvpTracker; }
}