package com.playerstats;

import com.playerstats.api.ApiServer;
import com.playerstats.core.commands.*;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.DatabaseConnection;
import com.playerstats.core.database.MySQLConnection;
import com.playerstats.core.database.SQLiteConnection;
import com.playerstats.core.database.dao.*;
import com.playerstats.core.listeners.JoinListener;
import com.playerstats.core.listeners.StatsListener;
import com.playerstats.core.multithreading.ThreadManager;
import com.playerstats.core.services.PlayerGroupService;
import com.playerstats.core.services.ServerStatsService;
import com.playerstats.hooks.HooksManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
    private ThreadManager threadManager;
    private ApiServer apiServer;
    private ConfigHandler config;
    private HooksManager hooksManager;

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

        boolean useMySQL = config.getDatabaseType().equalsIgnoreCase("mysql");

        // 2. База данных
        try {
            if (useMySQL) {
                db = new MySQLConnection(
                        config.getMySQLHost(),
                        config.getMySQLPort(),
                        config.getMySQLDatabase(),
                        config.getMySQLUsername(),
                        config.getMySQLPassword()
                );
            } else {
                db = new SQLiteConnection(config.getSQLiteFile());
            }

            db.connect();
            db.initTables();

            getLogger().info("База данных подключена: " + (useMySQL ? "MySQL" : "SQLite"));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Не удалось подключиться к базе данных или инициализировать таблицы.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Инициализация DAO
        try {
            this.statTypeDAO = new StatTypeDAO(db, useMySQL);
            this.playerDAO = new PlayerDAO(db, useMySQL);
            this.statsDAO = new PlayerStatsDAO(db, useMySQL, statTypeDAO);
            this.serverDAO = new ServerDAO(db, useMySQL);
            this.serverStatsDAO = new ServerStatsDAO(db, useMySQL, statTypeDAO);
            getLogger().info("DAO инициализированы.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось инициализировать объекты доступа к данным (DAO).", e);
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

        // 5. Thread Manager
        this.threadManager = new ThreadManager(this);
        getLogger().info("ThreadManager инициализирован.");

        // 6. Инициализация хуков (LuckPerms, Vault, Essentials, PlaceholderAPI, DecentHolograms)
        this.hooksManager = new HooksManager(this);
        hooksManager.init();

        // 7. PlayerGroupService (использует LuckPermsHook)
        this.groupService = new PlayerGroupService(
                this, config, statsDAO, hooksManager.getLuckPermsHook());
        getLogger().info("PlayerGroupService инициализирован.");

        // 8. ExcludeCommand
        this.excludeCommand = new ExcludeCommand(this);
        excludeCommand.loadExcludedPlayers();
        getLogger().info("Список исключённых игроков загружен.");

        // 9. HTTP API
        if (config.isApiEnabled()) {
            try {
                TotalServerDAO totalServerDAO = new TotalServerDAO(db, statTypeDAO);
                ServerStatsService statsService = new ServerStatsService(totalServerDAO, getLogger());
                this.apiServer = new ApiServer(statsService, playerDAO, config.getApiHost(), config.getApiPort());
                this.apiServer.start();
                getLogger().info("HTTP API запущен на " + config.getApiHost() + ":" + config.getApiPort());
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Не удалось запустить HTTP API. Плагин продолжит работу без него.", e);
                this.apiServer = null;
            }
        } else {
            this.apiServer = null;
            getLogger().info("HTTP API отключен в конфиге.");
        }

        // 10. Слушатели
        JoinListener joinListener = new JoinListener(this, playerDAO);
        Bukkit.getPluginManager().registerEvents(joinListener, this);

        this.statsListener = new StatsListener(
                this, playerDAO, statsDAO, serverStatsDAO, serverDAO, threadManager,
                hooksManager.getVaultHook(), hooksManager.getEssentialsHook());
        statsListener.setExcludeCommand(excludeCommand);
        Bukkit.getPluginManager().registerEvents(statsListener, this);
        getLogger().info("Слушатели зарегистрированы.");

        // 11. Команды
        registerCommands();

        // 12. bStats
        try {
            int pluginId = 27074;
            new Metrics(this, pluginId);
            getLogger().info("bStats подключен.");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось подключиться к bStats.", e);
        }

        // 13. Информация о запуске
        logPluginStartInfo(useMySQL);
    }

    @Override
    public void onDisable() {
        // Порядок отключения: API → Слушатели → Менеджеры → Хуки → БД
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

        if (threadManager != null) {
            try {
                threadManager.shutdown();
                getLogger().info("ThreadManager остановлен.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка при остановке менеджера потоков.", e);
            }
        }

        // Отключаем хуки
        if (hooksManager != null) {
            hooksManager.shutdown();
        }

        if (db != null) {
            try {
                db.disconnect();
                getLogger().info("База данных отключена.");
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Ошибка при отключении от базы данных.", e);
            }
        }

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§c[PlayerStats] ⛔ Плагин выключен!");
        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    // ======= Приватные методы =======

    /**
     * Регистрация всех команд плагина.
     */
    private void registerCommands() {
        if (getServer().getCommandMap() == null) {
            getLogger().warning("CommandMap недоступна, команды не будут зарегистрированы.");
            return;
        }

        // /stats
        getServer().getCommandMap().register("playerstats",
                new BukkitCommand("stats", "Просмотр своей статистики", "/stats", List.of()) {
                    @Override
                    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage("§cЭта команда может использоваться только игроком.");
                            return true;
                        }
                        new StatsCommand(Main.this, groupService).execute(player);
                        return true;
                    }
                }
        );

        // /top
        getServer().getCommandMap().register("playerstats", new TopCommand(this));

        // /share
        getServer().getCommandMap().register("playerstats",
                new BukkitCommand("share", "Поделиться статистикой в чат", "/share [игрок]", List.of()) {
                    @Override
                    public boolean execute(CommandSender sender, String label, String[] args) {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage("§cТолько игрок может использовать эту команду.");
                            return true;
                        }
                        ShareCommand shareCmd = new ShareCommand(Main.this, groupService);
                        if (args.length > 0) {
                            Player target = Bukkit.getPlayer(args[0]);
                            shareCmd.execute(player, target);
                        } else {
                            shareCmd.execute(player);
                        }
                        return true;
                    }
                }
        );

        // /exclude
        getServer().getCommandMap().register("playerstats",
                new BukkitCommand("exclude", "Исключить игрока из статистики", "/exclude <игрок> [remove|list]", List.of()) {
                    @Override
                    public boolean execute(CommandSender sender, String label, String[] args) {
                        excludeCommand.execute(sender, args);
                        return true;
                    }
                }
        );

        // /playerstats reload
        getServer().getCommandMap().register("playerstats",
                new BukkitCommand("playerstats", "Управление плагином PlayerStats", "/playerstats reload", List.of("ps")) {
                    @Override
                    public boolean execute(CommandSender sender, String label, String[] args) {
                        if (args.length == 0) {
                            sender.sendMessage("§6[PlayerStats] §eИспользуйте: /playerstats reload");
                            return true;
                        }

                        if (args[0].equalsIgnoreCase("reload")) {
                            new ReloadCommand(Main.this, groupService).execute(sender);
                            return true;
                        }

                        sender.sendMessage("§cНеизвестная подкоманда. Используйте: /playerstats reload");
                        return true;
                    }
                }
        );

        getLogger().info("Команды зарегистрированы: /stats, /top, /share, /exclude, /playerstats reload");
    }

    /**
     * Вывод информации о запуске плагина в консоль.
     */
    private void logPluginStartInfo(boolean useMySQL) {
        String apiStatus = (apiServer != null) ? "§aЗапущен ✅" : "§cОтключен ❌";

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§b[PlayerStats] 🌟 Плагин успешно включен!");
        Bukkit.getConsoleSender().sendMessage("§6Версия: §a" + getDescription().getVersion() + " §7by §3luci");
        Bukkit.getConsoleSender().sendMessage("§6База: §3" + (useMySQL ? "MySQL ✅" : "SQLite ✅"));
        Bukkit.getConsoleSender().sendMessage("§6Сервер: §3" + serverName + " §7(ID: " + serverId + ")");
        Bukkit.getConsoleSender().sendMessage("§6API: " + apiStatus);
        if (apiServer != null) {
            Bukkit.getConsoleSender().sendMessage("§6Хост: §3" + config.getApiHost() + " §7| §6Порт: §3" + config.getApiPort());
        }

        // Статус хуков
        hooksManager.logStatus();

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    // ======= Геттеры =======
    public PlayerDAO getPlayerDAO() { return playerDAO; }
    public PlayerStatsDAO getStatsDAO() { return statsDAO; }
    public StatTypeDAO getStatTypeDAO() { return statTypeDAO; }
    public ThreadManager getThreadManager() { return threadManager; }
    public ServerDAO getServerDAO() { return serverDAO; }
    public ServerStatsDAO getServerStatsDAO() { return serverStatsDAO; }
    public int getServerId() { return serverId; }
    public DatabaseConnection getConnection() { return db; }
    public ApiServer getApiServer() { return apiServer; }
    public ConfigHandler getConfigHandler() { return config; }
    public PlayerGroupService getGroupService() { return groupService; }
    public HooksManager getHooksManager() { return hooksManager; }
}