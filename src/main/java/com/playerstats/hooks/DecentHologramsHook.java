package com.playerstats.hooks;

import com.playerstats.Main;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;
import java.util.logging.Level;

/**
 * Тонкая обёртка над DecentHolograms API.
 * Вся логика (топ, форматирование, авто-обновление) — в {@link com.playerstats.core.services.HologramManager}.
 */
public class DecentHologramsHook implements Hook {

    private final Main plugin;
    private boolean enabled = false;

    public DecentHologramsHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            plugin.getLogger().info("DecentHolograms не обнаружен — интеграция отключена.");
            return;
        }
        this.enabled = true;
        plugin.getLogger().info("DecentHolograms успешно подключен.");
    }

    @Override
    public void shutdown() {
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getPluginName() {
        return "DecentHolograms";
    }

    // ==================== API-ОБЁРТКИ ====================

    /** Есть ли голограмма с таким именем. */
    public boolean exists(String name) {
        if (!enabled) return false;
        return DHAPI.getHologram(name) != null;
    }

    /** Создать голограмму. */
    public void create(String name, Location location, List<String> lines) {
        if (!enabled) return;
        try {
            DHAPI.createHologram(name, location, lines);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DH: не удалось создать " + name, e);
        }
    }

    /** Установить строки существующей голограммы (без пересоздания). */
    public void setLines(String name, List<String> lines) {
        if (!enabled) return;
        Hologram holo = DHAPI.getHologram(name);
        if (holo == null) return;
        try {
            DHAPI.setHologramLines(holo, lines);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DH: не удалось обновить " + name, e);
        }
    }

    /** Удалить голограмму. */
    public void delete(String name) {
        if (!enabled) return;
        Hologram holo = DHAPI.getHologram(name);
        if (holo == null) return;
        try {
            DHAPI.removeHologram(name);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DH: не удалось удалить " + name, e);
        }
    }


}