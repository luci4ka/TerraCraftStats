package com.playerstats.hooks;

import com.playerstats.Main;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.logging.Level;

public class PlaceholderAPIHook implements Hook {

    private final Main plugin;
    private PlayerStatsExpansion expansion;
    private boolean enabled = false;

    public PlaceholderAPIHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.getLogger().info("PlaceholderAPI не обнаружен — интеграция отключена.");
            return;
        }

        try {
            this.expansion = new PlayerStatsExpansion(plugin);
            this.expansion.register();
            this.enabled = true;
            plugin.getLogger().info("PlaceholderAPI плейсхолдеры зарегистрированы (префикс %ps_...%).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось зарегистрировать PlaceholderAPI.", e);
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        if (expansion != null) {
            expansion.unregister();
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled && expansion != null;
    }

    @Override
    public String getPluginName() {
        return "PlaceholderAPI";
    }

    public void invalidate(UUID uuid) {
        if (expansion != null) {
            expansion.invalidate(uuid);
        }
    }
}