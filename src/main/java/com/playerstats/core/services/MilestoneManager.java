package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MilestoneManager {

    private final Main plugin;
    private final ConfigHandler config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * Кэш уже выданных вех: "uuid:statName:value" → true.
     */
    private final Set<String> awarded = ConcurrentHashMap.newKeySet();

    public MilestoneManager(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
    }

    /**
     * Проверить, не достиг ли игрок новой вехи.
     * Вызывать после incrementStat.
     */
    public void check(Player player, String statName, int newValue) {
        if (!config.getConfig().getBoolean("milestones.enabled", false)) return;

        ConfigurationSection statSection = config.getConfig()
                .getConfigurationSection("milestones." + statName);
        if (statSection == null) return;

        for (String key : statSection.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            // Игрок достиг вехи?
            if (newValue >= threshold) {
                String cacheKey = player.getUniqueId() + ":" + statName + ":" + threshold;

                // Уже выдавали?
                if (awarded.contains(cacheKey)) continue;

                // Помечаем как выданную
                awarded.add(cacheKey);

                // Выдаём награду
                award(player, statName, threshold, statSection.getConfigurationSection(key));
            }
        }
    }

    private void award(Player player, String statName, int threshold,
                       ConfigurationSection section) {
        if (section == null) return;

        // Сообщение
        String message = section.getString("message");
        if (message != null) {
            Component msg = miniMessage.deserialize(
                    message.replace("%player%", player.getName())
                            .replace("%value%", String.valueOf(threshold)));
            player.sendMessage(msg);
        }

        // Команды
        List<String> commands = section.getStringList("commands");
        for (String cmd : commands) {
            String finalCmd = cmd.replace("%player%", player.getName())
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
     * Очистить кэш (при перезагрузке).
     */
    public void clearCache() {
        awarded.clear();
    }
}