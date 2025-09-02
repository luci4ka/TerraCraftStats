package com.playerstats.core.config;

import com.playerstats.Main;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import net.luckperms.api.model.user.User;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class ConfigHandler {

    private static ConfigHandler instance;
    private final FileConfiguration config;

    private ConfigHandler(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static ConfigHandler getInstance(JavaPlugin plugin) {
        if (instance == null) instance = new ConfigHandler(plugin);
        return instance;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // ====== Database ======
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getMySQLHost() {
        return config.getString("database.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.name", "playerstats");
    }

    public String getMySQLUsername() {
        return config.getString("database.user", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.password", "password");
    }

    public String getSQLiteFile() {
        return config.getString("sqlite.file", "plugins/PlayerStats/playerstats.db");
    }

    // ====== Groups ======
    private Map<String, String> groupColors;

    private void loadGroupColors() {
        groupColors = new HashMap<>();
        if (config.contains("groups")) {
            for (String key : config.getConfigurationSection("groups").getKeys(false)) {
                groupColors.put(key.toLowerCase(), config.getString("groups." + key));
            }
        }
    }

    public String getGroupFormat(String group, String defaultValue) {
        if (groupColors == null) loadGroupColors();
        return groupColors.getOrDefault(group.toLowerCase(), defaultValue);
    }

    /**
     * Получаем группу игрока с LuckPerms или из базы с форматированием из конфига
     */
    public String getPlayerGroup(Main plugin, PlayerStatsDAO statsDAO, int playerId, Player player, net.luckperms.api.LuckPerms luckPermsApi) throws SQLException {
        if (luckPermsApi != null) {
            User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String group = user.getPrimaryGroup();
                return getGroupFormat(group, "§rИгрок");
            }
        }

        // Если LuckPerms нет или пользователь не найден — берем из базы
        int serverId = plugin.getServerId();
        String groupFromDB = statsDAO.getStatString(playerId, serverId, "group");
        if (groupFromDB != null && !groupFromDB.isEmpty()) {
            return getGroupFormat(groupFromDB, "§rИгрок");
        }

        return getGroupFormat("default", "§rИгрок");
    }

    /** ====== Multi-server ====== */
    public boolean isMultiServerEnabled() {
        return config.getBoolean("multi-server.enabled", false);
    }

    public String getServerName() {
        return config.getString("multi-server.server-name", "default");
    }

    public String getServerDisplayName(String key) {
        return config.getString("server-names." + key, key);
    }

    public String getStatsClickCommand(String statKey) {
        return config.getString("stats-click-commands." + statKey, "/say Not set");
    }
    public boolean isApiEnabled() {
        return config.getBoolean("api.enabled", false);
    }

    public String getApiHost() {
        return config.getString("api.host", "0.0.0.0");
    }

    public int getApiPort() {
        return config.getInt("api.port", 8080);
    }
}

