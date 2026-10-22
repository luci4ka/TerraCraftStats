package ru.terracraft.stats;

import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.plugin.minesql.MineSQL;
import ru.terracraft.stats.api.PlayerStatsApi;
import ru.terracraft.stats.api.impl.PlayerStatsApiImpl;
import ru.terracraft.stats.core.commands.CommandRegistrar;
import ru.terracraft.stats.core.commands.ExcludeCommand;
import ru.terracraft.stats.core.config.ConfigManager;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.config.TrackedBlocks;
import ru.terracraft.stats.core.database.DatabaseMigrator;
import ru.terracraft.stats.core.database.dao.*;
import ru.terracraft.stats.core.gui.StatsGui;
import ru.terracraft.stats.core.gui.StatsGuiListener;
import ru.terracraft.stats.core.listeners.ListenerRegistrar;
import ru.terracraft.stats.core.listeners.StatsListener;
import ru.terracraft.stats.core.multithreading.ThreadManager;
import ru.terracraft.stats.core.pvp.PvPTracker;
import ru.terracraft.stats.core.services.*;
import ru.terracraft.stats.hooks.DiscordWebhook;
import ru.terracraft.stats.hooks.HooksManager;
import ru.terracraft.stats.hooks.TerraCoreBridge;
import net.kyori.adventure.text.Component;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class PlayerStatsPlugin extends JavaPlugin {

    // ======= Поля =======
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private SQLManager sqlManager;

    private PlayerDAO playerDAO;
    private PlayerStatsDAO statsDAO;
    private StatTypeDAO statTypeDAO;
    private ServerDAO serverDAO;
    private ServerStatsDAO serverStatsDAO;
    private LeaderboardDAO leaderboardDAO;
    private StatHistoryDAO statHistoryDAO;
    private TotalServerDAO totalServerDAO;

    private ThreadManager threadManager;
    private RollbackLockService rollbackLockService;
    private TopCache topCache;
    private TopChangeNotifier topChangeNotifier;
    private StatSnapshotTask statSnapshotTask;
    private HooksManager hooksManager;
    private TerraCoreBridge terraCoreBridge;
    private PlayerGroupService groupService;
    private ExcludeCommand excludeCommand;
    private PvPTracker pvpTracker;
    private DiscordWebhook discordWebhook;
    private StatsGui statsGui;
    private StatsListener statsListener;
    private UpdateChecker updateChecker;
    private Metrics metrics;

    private long startTime;
    private int serverId = -1;
    private String serverName;

    // ======= Жизненный цикл =======

    @Override
    public void onEnable() {
        // 1. Конфиг
        try {
            this.configManager = new ConfigManager(this);
            this.configManager.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось загрузить конфиг.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Языки
        this.languageManager = new LanguageManager(this);

        // 3. БД (MineSQL)
        try {
            String databaseId = configManager.main().database().id();
            this.sqlManager = MineSQL.getRegistry().get(databaseId);

            if (sqlManager == null) {
                getLogger().severe("Не удалось получить SQLManager для БД '" + databaseId
                        + "'. Проверьте plugins/MineSQL/config.yml");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            DatabaseMigrator migrator = new DatabaseMigrator(this, sqlManager);
            if (migrator.hasFailed()) {
                throw new SQLException("Не удалось применить миграции БД.");
            }

            initDefaultStatTypes();
            getLogger().info("БД подключена через MineSQL (ID: " + databaseId + ")");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось подключиться к БД.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. DAO
        try {
            this.statTypeDAO = new StatTypeDAO(this);
            this.playerDAO = new PlayerDAO(this);
            this.statsDAO = new PlayerStatsDAO(this, statTypeDAO);
            this.serverDAO = new ServerDAO(this);
            this.serverStatsDAO = new ServerStatsDAO(this, statTypeDAO);
            this.leaderboardDAO = new LeaderboardDAO(this, statTypeDAO);
            this.statHistoryDAO = new StatHistoryDAO(this);
            this.totalServerDAO = new TotalServerDAO(this, statTypeDAO);

            getLogger().info("DAO инициализированы.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось инициализировать DAO.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. Серверный ID
        try {
            this.serverName = configManager.main().multiServer().enabled()
                    ? configManager.main().multiServer().serverName()
                    : Bukkit.getServer().getName();
            this.serverId = serverDAO.createOrGetServer(serverName);
            getLogger().info("Сервер зарегистрирован: " + serverName + " (ID: " + serverId + ")");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Не удалось получить ID сервера.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 6. ThreadManager
        this.threadManager = new ThreadManager(this);
        this.startTime = System.currentTimeMillis();

        // 7. Сервисы
        this.rollbackLockService = new RollbackLockService(this);
        this.topCache = new TopCache(this, leaderboardDAO, serverId, 5);
        this.topChangeNotifier = new TopChangeNotifier(this, topCache);
        this.statSnapshotTask = new StatSnapshotTask(this);

        // 8. Хуки
        this.hooksManager = new HooksManager(this);
        hooksManager.init();

        // 8.5. TerraCraftCore API (AFK/god)
        this.terraCoreBridge = new TerraCoreBridge(this);

        // 9. Группы
        this.groupService = new PlayerGroupService(this, configManager, statsDAO);

        // 10. Exclude
        this.excludeCommand = new ExcludeCommand(this);
        excludeCommand.loadExcludedPlayers();

        // 11. PvP
        this.pvpTracker = new PvPTracker(this);

        // 12. Discord
        this.discordWebhook = new DiscordWebhook(this);
        if (discordWebhook.isEnabled()) {
            getLogger().info("Discord Webhook подключен.");
        }

        // 13. Слушатели
        ListenerRegistrar listenerRegistrar = new ListenerRegistrar(this);
        this.statsListener = listenerRegistrar.registerAll();

        // 14. GUI
        this.statsGui = new StatsGui(this);
        Bukkit.getPluginManager().registerEvents(new StatsGuiListener(this), this);

        // 15. Команды
        new CommandRegistrar(this, groupService).register();

        // 16. bStats
        try {
            this.metrics = new Metrics(this, 27074);
            getLogger().info("bStats подключен.");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось подключить bStats.", e);
        }

        // 17. UpdateChecker
        try {
            this.updateChecker = new UpdateChecker(this);
            updateChecker.checkAsync();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Не удалось запустить UpdateChecker", e);
        }

        // 18. Java API — регистрируем в САМОМ КОНЦЕ
        getServer().getServicesManager().register(
                PlayerStatsApi.class,
                new PlayerStatsApiImpl(this),
                this,
                ServicePriority.Normal
        );
        getLogger().info("Java API зарегистрирован через ServicesManager.");

        logPluginStartInfo();
    }

    @Override
    public void onDisable() {
        // Сначала сохраняем — потом останавливаем ThreadManager,
        // иначе flushIncrements может не успеть.
        if (statsListener != null) {
            try {
                statsListener.saveAllOnline();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка сохранения статистики", e);
            }
        }

        if (groupService != null) groupService.clearCache();
        if (topChangeNotifier != null) topChangeNotifier.clear();
        if (pvpTracker != null) pvpTracker.clear();
        if (rollbackLockService != null) rollbackLockService.clear();

        if (threadManager != null) {
            try {
                threadManager.shutdown();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка остановки ThreadManager.", e);
            }
        }

        if (hooksManager != null) hooksManager.shutdown();

        ConfigManager.reset();

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§c[PlayerStats] ⛔ Плагин выключен!");
        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    // ======= Инициализация stat_types =======

    private void initDefaultStatTypes() {
        List<String> types = new ArrayList<>(List.of(
                "excluded", "play_time", "blocks_placed", "blocks_broken",
                "kills", "deaths", "mobs_killed", "bosses_killed",
                "balance", "group", "distance_walked", "damage_dealt", "damage_taken",
                "items_crafted", "food_consumed", "arrows_shot", "fish_caught",
                "entities_tamed", "entities_bred", "teleports", "portal_uses", "logins",
                "killstreak", "best_killstreak",

                // === Новые лёгкие статы ===
                "jumps", "sneak_time", "sprint_time",
                "elytra_distance", "boat_distance", "minecart_distance",
                "damage_blocked",
                "items_enchanted", "items_broken",
                "animals_sheared", "villager_trades", "crops_harvested", "sleeps",
                "commands_used", "chat_messages",
                "totems_used", "experience_gained", "levels_gained",
                "potions_brewed", "potions_drunk",
                "signs_placed", "beds_placed",
                "tnt_ignited", "cakes_eaten",
                "snow_golems_built", "crops_planted",
                "diamonds_mined", "netherite_mined",
                "sprint_distance", "sneak_distance", "climbed_ladders",
                "bells_rung", "dolphins_fed"
        ));

        for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
            if (type.isAlive() && !type.equals(org.bukkit.entity.EntityType.PLAYER)) {
                types.add("mob_killed_" + type.name().toLowerCase());
            }
        }

        List<String> trackedBlocks = configManager.main().tracking().blocks();
        if (trackedBlocks != null && !trackedBlocks.isEmpty()) {
            TrackedBlocks tracked = new TrackedBlocks(trackedBlocks);
            types.addAll(tracked.toStatTypes());
        }

        List<Object[]> batch = new ArrayList<>();
        for (String type : types) batch.add(new Object[]{type});

        try {
            sqlManager.executeSQLBatch("INSERT IGNORE INTO stat_types (name) VALUES (?)", batch);
            getLogger().info("Предсоздано " + types.size() + " типов статистики.");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Ошибка предсоздания stat_types", e);
        }
    }

    // ======= Публичные методы =======

    public void updateServerInfo(int newServerId, String newServerName) {
        this.serverId = newServerId;
        this.serverName = newServerName;
        getLogger().info("Информация о сервере обновлена: " + newServerName + " (ID: " + newServerId + ")");
    }

    public void sendUpdateMessage(CommandSender sender) {
        if (updateChecker == null) {
            sender.sendMessage(getComponent(sender, "update.not-initialized"));
            return;
        }

        if (updateChecker.isUpdateAvailable()) {
            sender.sendMessage(getComponent(sender, "update.header"));
            sender.sendMessage(getComponent(sender, "update.available"));
            sender.sendMessage(getComponent(sender, "update.current",
                    "current", updateChecker.getCurrentVersion()));
            sender.sendMessage(getComponent(sender, "update.latest",
                    "latest", updateChecker.getLatestVersion()));
            sender.sendMessage(getComponent(sender, "update.download",
                    "url", updateChecker.getDownloadUrl()));
            sender.sendMessage(getComponent(sender, "update.header"));
        } else {
            sender.sendMessage(getComponent(sender, "update.up-to-date",
                    "version", updateChecker.getCurrentVersion()));
        }
    }

    private void logPluginStartInfo() {
        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§b[PlayerStats] Плагин успешно включен!");
        Bukkit.getConsoleSender().sendMessage("§6Версия: §a" + getPluginMeta().getVersion() + " §7by §3luci");
        Bukkit.getConsoleSender().sendMessage("§6База: §3MariaDB (MineSQL)");
        Bukkit.getConsoleSender().sendMessage("§6Сервер: §3" + serverName + " §7(ID: " + serverId + ")");
        Bukkit.getConsoleSender().sendMessage("§6TerraCore API: §3"
                + (terraCoreBridge != null && terraCoreBridge.isEnabled()
                ? "подключён" : "не подключён"));

        hooksManager.logStatus();

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
    }

    private Component getComponent(CommandSender sender, String key, Object... replacements) {
        if (sender instanceof Player player) {
            return languageManager.getComponent(player, key, replacements);
        }
        return languageManager.getComponent(key, replacements);
    }

    // ======= Геттеры =======

    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public SQLManager getSqlManager() { return sqlManager; }

    public PlayerDAO getPlayerDAO() { return playerDAO; }
    public PlayerStatsDAO getStatsDAO() { return statsDAO; }
    public StatTypeDAO getStatTypeDAO() { return statTypeDAO; }
    public ThreadManager getThreadManager() { return threadManager; }
    public ServerDAO getServerDAO() { return serverDAO; }
    public ServerStatsDAO getServerStatsDAO() { return serverStatsDAO; }
    public LeaderboardDAO getLeaderboardDAO() { return leaderboardDAO; }
    public TotalServerDAO getTotalServerDAO() { return totalServerDAO; }
    public int getServerId() { return serverId; }
    public PlayerGroupService getGroupService() { return groupService; }
    public HooksManager getHooksManager() { return hooksManager; }
    public TerraCoreBridge getTerraCoreBridge() { return terraCoreBridge; }
    public String getServerName() { return serverName; }
    public TopCache getTopCache() { return topCache; }
    public StatsGui getStatsGui() { return statsGui; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public DiscordWebhook getDiscordWebhook() { return discordWebhook; }
    public StatHistoryDAO getStatHistoryDAO() { return statHistoryDAO; }
    public StatSnapshotTask getStatSnapshotTask() { return statSnapshotTask; }
    public PvPTracker getPvpTracker() { return pvpTracker; }
    public TopChangeNotifier getTopChangeNotifier() { return topChangeNotifier; }
    public ExcludeCommand getExcludeCommand() { return excludeCommand; }
    public StatsListener getStatsListener() { return statsListener; }
    public long getStartTime() { return startTime; }
    public RollbackLockService getRollbackLockService() { return rollbackLockService; }
}