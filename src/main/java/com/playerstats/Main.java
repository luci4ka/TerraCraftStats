package com.playerstats;

import com.playerstats.api.ApiServer;
import com.playerstats.core.achievements.AchievementManager;
import com.playerstats.core.commands.CommandRegistrar;
import com.playerstats.core.commands.ExcludeCommand;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.DatabaseConnection;
import com.playerstats.core.database.MySQLConnection;
import com.playerstats.core.database.SQLiteConnection;
import com.playerstats.core.database.dao.*;
import com.playerstats.core.gui.StatsGui;
import com.playerstats.core.gui.StatsGuiListener;
import com.playerstats.core.listeners.ListenerRegistrar;
import com.playerstats.core.listeners.StatsListener;
import com.playerstats.core.multithreading.ThreadManager;
import com.playerstats.core.pvp.PvPTracker;
import com.playerstats.core.services.*;
import com.playerstats.hooks.HooksManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
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
    private HologramManager hologramManager;
    private MilestoneManager milestoneManager;
    private DiscordWebhook discordWebhook;
    private TopChangeNotifier topChangeNotifier;
    private StatHistoryDAO statHistoryDAO;
    private StatSnapshotTask statSnapshotTask;
    private AchievementDAO achievementDAO;
    private AchievementManager achievementManager;
    private PlayerGroupService groupService;
    private ExcludeCommand excludeCommand;
    private StatsListener statsListener;

    private int serverId = -1;
    private String serverName;

    // ======= Жизненный цикл =======

    @Override
    public void onEnable() {
        // 1. Конфиг
        try {
            this.config = ConfigHandler.getInstance(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось загрузить конфиг.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.languageManager = new LanguageManager(this);

        boolean useMySQL = config.getDatabaseType().equalsIgnoreCase("mysql");
        if (config.isVerboseDebug()) {
            getLogger().info("[DEBUG] Тип БД: " + (useMySQL ? "MySQL" : "SQLite"));
        }

        // 2. БД
        try {
            if (useMySQL) {
                db = new MySQLConnection(this, config.getMySQLHost(),
                        config.getMySQLPort(), config.getMySQLDatabase(),
                        config.getMySQLUsername(), config.getMySQLPassword());
            } else {
                File dbFile = new File(getDataFolder(), config.getSQLiteFile());
                db = new SQLiteConnection(this, dbFile.getAbsolutePath());
            }
            db.connect();
            db.initTables();
            getLogger().info("База данных подключена: " + (useMySQL ? "MySQL" : "SQLite"));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Не удалось подключиться к БД.", e);
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
            this.statHistoryDAO = new StatHistoryDAO(this, db, useMySQL);
            this.achievementDAO = new AchievementDAO(this, db, useMySQL);

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
            getLogger().log(Level.SEVERE, "Не удалось получить ID сервера.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. ThreadManager
        this.threadManager = new ThreadManager(this);

        // 6. TopCache
        this.topCache = new TopCache(this, leaderboardDAO, serverId, 5);

        // 6.5. TopChangeNotifier
        this.topChangeNotifier = new TopChangeNotifier(this, topCache);

        // 6.6. StatSnapshotTask
        this.statSnapshotTask = new StatSnapshotTask(this);

        // 6.7. HologramManager
        this.hologramManager = new HologramManager(this);

        // 7. Хуки
        this.hooksManager = new HooksManager(this);
        hooksManager.init();

        // 8. PlayerGroupService
        this.groupService = new PlayerGroupService(this, config, statsDAO,
                hooksManager.getLuckPermsHook());

        // 9. ExcludeCommand
        this.excludeCommand = new ExcludeCommand(this);
        excludeCommand.loadExcludedPlayers();

        // 9.5. PvPTracker
        this.pvpTracker = new PvPTracker(this);

        // 10. MilestoneManager
        this.milestoneManager = new MilestoneManager(this);

        this.achievementManager = new AchievementManager(this, achievementDAO);
        getLogger().info("AchievementManager инициализирован.");

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
                this.apiServer = new ApiServer(statsService, playerDAO,
                        config.getApiHost(), config.getApiPort());
                this.apiServer.start();
                getLogger().info("HTTP API запущен на "
                        + config.getApiHost() + ":" + config.getApiPort());
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Не удалось запустить HTTP API.", e);
                this.apiServer = null;
            }
        } else {
            this.apiServer = null;
        }

        // 13. Слушатели — вынесено
        ListenerRegistrar listenerRegistrar = new ListenerRegistrar(this);
        this.statsListener = listenerRegistrar.registerAll();

        // 14. GUI
        this.statsGui = new StatsGui(this);
        Bukkit.getPluginManager().registerEvents(new StatsGuiListener(this), this);
        getLogger().info("StatsGui инициализирован.");

        // 15. Команды — вынесено
        new CommandRegistrar(this, groupService).register();

        // 16. bStats
        try {
            new Metrics(this, 27074);
            getLogger().info("bStats подключен.");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось подключить bStats.", e);
        }

        // 17. UpdateChecker
        try {
            this.updateChecker = new UpdateChecker(this, "luci/PlayerStats");
            updateChecker.checkAsync();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось запустить UpdateChecker", e);
        }

        logPluginStartInfo(useMySQL);
    }

    @Override
    public void onDisable() {
        if (statsListener != null) {
            try {
                statsListener.saveAllOnline();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка сохранения статистики", e);
            }
        }

        if (apiServer != null) {
            try {
                apiServer.stop();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка остановки API.", e);
            }
        }

        if (groupService != null) groupService.clearCache();
        if (milestoneManager != null) milestoneManager.clearCache();
        if (hologramManager != null) hologramManager.clear();
        if (topChangeNotifier != null) topChangeNotifier.clear();
        if (pvpTracker != null) pvpTracker.clear();
        if (achievementManager != null) {
            achievementManager.clearCache();
        }

        if (threadManager != null) {
            try {
                threadManager.shutdown();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка остановки ThreadManager.", e);
            }
        }

        if (hooksManager != null) hooksManager.shutdown();

        if (db != null) {
            try {
                db.disconnect();
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Ошибка отключения от БД.", e);
            }
        }

        ConfigHandler.reset();

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§c[PlayerStats] ⛔ Плагин выключен!");
        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    // ======= Публичные методы =======

    public void updateServerInfo(int newServerId, String newServerName) {
        this.serverId = newServerId;
        this.serverName = newServerName;
        getLogger().info("Информация о сервере обновлена: " + newServerName + " (ID: " + newServerId + ")");
    }

    /**
     * Отправить сообщение о статусе обновления.
     */
    public void sendUpdateMessage(CommandSender sender) {
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
            Bukkit.getConsoleSender().sendMessage("§6Хост: §3"
                    + config.getApiHost() + " §7| §6Порт: §3" + config.getApiPort());
        }

        hooksManager.logStatus();

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    // ======= Геттеры =======

    public PlayerDAO getPlayerDAO() { return playerDAO; }
    public PlayerStatsDAO getStatsDAO() { return statsDAO; }
    public StatTypeDAO getStatTypeDAO() { return statTypeDAO; }
    public ThreadManager getThreadManager() { return threadManager; }
    public ServerDAO getServerDAO() { return serverDAO; }
    public AchievementDAO getAchievementDAO() { return achievementDAO; }
    public AchievementManager getAchievementManager() { return achievementManager; }
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
    public StatHistoryDAO getStatHistoryDAO() { return statHistoryDAO; }
    public StatSnapshotTask getStatSnapshotTask() { return statSnapshotTask; }
    public PvPTracker getPvpTracker() { return pvpTracker; }
    public TopChangeNotifier getTopChangeNotifier() { return topChangeNotifier; }
    public HologramManager getHologramManager() { return hologramManager; }
    public ExcludeCommand getExcludeCommand() { return excludeCommand; }
}