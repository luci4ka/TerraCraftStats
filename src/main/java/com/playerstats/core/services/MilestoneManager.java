package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.api.java.event.PlayerMilestoneEvent;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerMilestoneDAO;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MilestoneManager {

    private final Main plugin;
    private final ConfigHandler config;
    private final LanguageManager lang;
    private final PlayerMilestoneDAO milestoneDAO;

    /**
     * Локальный кэш: UUID:stat:threshold → уже выдан.
     * Заполняется при загрузке, синхронизируется с БД.
     */
    private final Set<String> awardedCache = ConcurrentHashMap.newKeySet();

    public MilestoneManager(Main plugin, PlayerMilestoneDAO milestoneDAO) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
        this.milestoneDAO = milestoneDAO;
    }

    // ==================== CHECK ====================

    public void check(Player player, String statName, int newValue) {
        if (!config.isModuleEnabled("milestones")) return;

        ConfigurationSection milestonesSection = config.getMilestonesSection();
        if (milestonesSection == null) return;

        if (!milestonesSection.getBoolean("enabled", true)) return;

        ConfigurationSection statSection = milestonesSection
                .getConfigurationSection(statName);
        if (statSection == null) return;

        int playerId = 0;
        try {
            playerId = plugin.getPlayerDAO().getPlayerId(player.getUniqueId());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (playerId == -1) return;

        for (String key : statSection.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            if (newValue < threshold) continue;

            String milestoneKey = statName + ":" + threshold;
            String cacheKey = player.getUniqueId() + ":" + milestoneKey;

            // Быстрая проверка кэша
            if (awardedCache.contains(cacheKey)) continue;

            // Проверка БД (async)
            checkAndAward(player, playerId, statName, threshold,
                    statSection.getConfigurationSection(key), cacheKey, milestoneKey);
        }
    }

    private void checkAndAward(Player player, int playerId,
                               String statName, int threshold,
                               ConfigurationSection section,
                               String cacheKey, String milestoneKey) {

        plugin.getThreadManager().runAsync(() -> {
            try {
                // Проверка в БД
                if (milestoneDAO.isAwarded(playerId, milestoneKey)) {
                    awardedCache.add(cacheKey);
                    return;
                }

                // Выдача (sync, т.к. работаем с Bukkit API)
                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    award(player, statName, threshold, section);

                    // Запись в БД (async)
                    plugin.getThreadManager().runAsync(() -> {
                        try {
                            milestoneDAO.markAwarded(playerId, milestoneKey);
                            awardedCache.add(cacheKey);
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Не удалось записать milestone в БД", e);
                        }
                    });
                });

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка проверки milestone в БД", e);
            }
        });
    }

    // ==================== AWARD ====================

    private void award(Player player, String statName, int threshold,
                       ConfigurationSection section) {
        if (section == null) return;

        // ===== MESSAGE =====
        String message = getMilestoneMessage(statName, threshold);

        if (message == null) {
            message = section.getString("message");
        }

        if (message != null) {
            Component msg = lang.getComponentFromString(message,
                    "player", player.getName(),
                    "value", threshold);
            player.sendMessage(msg);
        }


        PlayerMilestoneEvent event = new PlayerMilestoneEvent(
                player.getUniqueId(), statName, threshold);
        Bukkit.getPluginManager().callEvent(event);

        // ===== COMMANDS =====
        List<String> commands = section.getStringList("commands");
        for (String cmd : commands) {
            String finalCmd = cmd
                    .replace("%player%", player.getName())
                    .replace("%value%", String.valueOf(threshold));

            plugin.getThreadManager().runSync(() ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
        }

        if (config.isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Milestone: " + player.getName()
                    + " → " + statName + " = " + threshold);
        }
    }

    /**
     * Получить сообщение из lang.yml (без кавычек в ключе!).
     */
    private String getMilestoneMessage(String statName, int threshold) {
        String key = "milestones." + statName + "." + threshold + ".message";
        String message = lang.getRaw(key);

        if (message.startsWith("<red>Missing key")) {
            return null;
        }
        return message;
    }

    // ==================== CACHE ====================

    /**
     * Загрузить флаги из БД для игрока (при входе).
     */
    public void loadForPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        awardedCache.removeIf(key -> key.startsWith(uuid + ":"));

        int playerId;
        try {
            playerId = plugin.getPlayerDAO().getPlayerId(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось получить playerId для " + uuid, e);
            return;
        }
        if (playerId == -1) return;

        plugin.getThreadManager().runAsync(() -> {
            try {
                Set<String> keys = milestoneDAO.getAwardedKeys(playerId);
                for (String key : keys) {
                    awardedCache.add(uuid + ":" + key);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка загрузки milestones из БД", e);
            }
        });
    }

    public void clearCache() {
        awardedCache.clear();
    }
}