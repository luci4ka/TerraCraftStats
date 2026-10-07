package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import net.kyori.adventure.text.Component;
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
    private final LanguageManager lang;

    private final Set<String> awarded = ConcurrentHashMap.newKeySet();

    public MilestoneManager(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.lang = plugin.getLanguageManager();
    }

    public void check(Player player, String statName, int newValue) {
        if (!config.isModuleEnabled("milestones")) return;

        ConfigurationSection milestonesSection = plugin.getConfigHandler().getMilestonesSection();
        if (milestonesSection == null) return;

        if (!milestonesSection.getBoolean("enabled", true)) return;

        ConfigurationSection statSection = milestonesSection.getConfigurationSection(statName);
        if (statSection == null) return;

        for (String key : statSection.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            if (newValue >= threshold) {
                String cacheKey = player.getUniqueId() + ":" + statName + ":" + threshold;

                if (awarded.contains(cacheKey)) continue;
                awarded.add(cacheKey);

                award(player, statName, threshold, statSection.getConfigurationSection(key));
            }
        }
    }

    private void award(Player player, String statName, int threshold,
                       ConfigurationSection section) {
        if (section == null) return;

        // ==================== MESSAGE (из lang.yml) ====================
        String message = getMilestoneMessage(statName, threshold);

        if (message == null) {
            // Fallback: из milestones.yml
            message = section.getString("message");
        }

        if (message != null) {
            Component msg = lang.getComponentFromString(message,
                    "player", player.getName(),
                    "value", threshold);
            player.sendMessage(msg);
        }

        // ==================== COMMANDS ====================
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
     * Получить сообщение из lang.yml.
     */
    private String getMilestoneMessage(String statName, int threshold) {
        String key = "milestones." + statName + ".\"" + threshold + "\".message";
        String message = lang.getRaw(key);

        if (message.startsWith("<red>Missing key")) {
            return null;
        }
        return message;
    }

    public void clearCache() {
        awarded.clear();
    }
}