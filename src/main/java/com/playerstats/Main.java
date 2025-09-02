package com.playerstats;

import com.playerstats.api.ApiServer;
import com.playerstats.core.commands.StatsCommand;
import com.playerstats.core.commands.TopCommand;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.DatabaseConnection;
import com.playerstats.core.database.MySQLConnection;
import com.playerstats.core.database.SQLiteConnection;
import com.playerstats.core.database.dao.*;
import com.playerstats.core.listeners.JoinListener;
import com.playerstats.core.listeners.StatsListener;
import com.playerstats.core.multithreading.ThreadManager;
import com.playerstats.core.services.ServerStatsService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;

public final class Main extends JavaPlugin {

    // ======= Поля =======
    private DatabaseConnection db;
    private PlayerDAO playerDAO;
    private PlayerStatsDAO statsDAO;
    private StatTypeDAO statTypeDAO;
    private ServerDAO serverDAO;
    private ServerStatsDAO serverStatsDAO;
    private ThreadManager threadManager;
    private ApiServer apiServer; // добавляем поле

    private int serverId = -1; // ID текущего сервера

    // ======= Жизненный цикл =======
    @Override
    public void onEnable() {
        try {
            // ---------- Конфиг ----------
            ConfigHandler config = ConfigHandler.getInstance(this);
            boolean useMySQL = config.getDatabaseType().equalsIgnoreCase("mysql");

            // ---------- База ----------
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

            // ---------- DAO ----------
            statTypeDAO = new StatTypeDAO(db, useMySQL);
            playerDAO = new PlayerDAO(db, useMySQL);
            statsDAO = new PlayerStatsDAO(db, useMySQL, statTypeDAO);
            serverDAO = new ServerDAO(db);
            serverStatsDAO = new ServerStatsDAO(db, useMySQL, statTypeDAO);

            // ---------- Сервер ----------
            String serverName = config.isMultiServerEnabled()
                    ? config.getServerName()
                    : Bukkit.getServer().getName();
            serverId = serverDAO.createOrGetServer(serverName);

            // ---------- Thread Manager ----------
            threadManager = new ThreadManager(this);

            // ---------- Http API ----------
            String host = config.getApiHost();
            int port = config.getApiPort();
            boolean apiRunning = false;

            if (config.isApiEnabled()) {
                try {
                    TotalServerDAO dao = new TotalServerDAO(db.getConnection());
                    ServerStatsService statsService = new ServerStatsService(dao);
                    apiServer = new ApiServer(statsService, playerDAO, host, port);
                    apiServer.start();
                    apiRunning = true;
                } catch (Exception e) {
                    apiRunning = false;
                }
            }

            // ---------- Листенеры ----------
            Bukkit.getPluginManager().registerEvents(new JoinListener(playerDAO, statsDAO), this);
            Bukkit.getPluginManager().registerEvents(
                    new StatsListener(this, playerDAO, statsDAO, serverStatsDAO, serverDAO, threadManager),
                    this
            );

            // ---------- Команды ----------
            getServer().getCommandMap().register("playerstats",
                    new BukkitCommand("stats", "Просмотр своей статистики", null, List.of()) {
                        @Override
                        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage("§cЭта команда может использоваться только игроком.");
                                return true;
                            }
                            new StatsCommand(Main.this).execute(player);
                            return true;
                        }
                    }
            );

            getServer().getCommandMap().register("playerstats", new BukkitCommand("top", "Показ топ игроков", null, List.of()) {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§cКоманду может использовать только игрок.");
                        return true;
                    }
                    new TopCommand(Main.this).onCommand(player, this, commandLabel, args);
                    return true;
                }
            });

            // ---------- bStats ----------
            int pluginId = 27074; //
            new Metrics(this, pluginId);

            // ---------- Красивый вывод ----------
            Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
            Bukkit.getConsoleSender().sendMessage("§b[PlayerStats] 🌟 Плагин успешно включен!");
            Bukkit.getConsoleSender().sendMessage("§6Версия: §a" + getDescription().getVersion() + " §7by §3luci");
            Bukkit.getConsoleSender().sendMessage("§6База: §3" + (useMySQL ? "MySQL ✅" : "SQLite ✅"));
            Bukkit.getConsoleSender().sendMessage("§6Сервер: §3" + serverName + " §7(ID: " + serverId + ")");
            Bukkit.getConsoleSender().sendMessage("§6API: " + (apiRunning ? "§aЗапущен ✅" : "§cОтключен ❌"));
            if (apiRunning) {
                Bukkit.getConsoleSender().sendMessage("§6Хост: §3" + host + " §7| §6Порт: §3" + port);
            }
            Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");

        } catch (SQLException e) {
            Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
            Bukkit.getConsoleSender().sendMessage("§c[PlayerStats] ❌ Ошибка при инициализации базы данных!");
            Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (apiServer != null) {
            apiServer.stop();
        }
        try {
            if (db != null) db.disconnect();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Bukkit.getConsoleSender().sendMessage("§8════════════════════════════════════════════");
        Bukkit.getConsoleSender().sendMessage("§c[PlayerStats] ⛔ Плагин выключен!");
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
    public DatabaseConnection getConnection() { return db;
    }
}
