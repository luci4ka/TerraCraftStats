package com.playerstats.hooks;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.playerstats.Main;
import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EssentialsHook implements Hook, Listener {

    private final Main plugin;
    private Essentials essentials;
    private boolean enabled = false;

    // AFK-игроки
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    public EssentialsHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            plugin.getLogger().info("Essentials не обнаружен — интеграция отключена.");
            return;
        }

        try {
            this.essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
            this.enabled = true;

            // Регистрируем слушатели
            Bukkit.getPluginManager().registerEvents(this, plugin);

            plugin.getLogger().info("Essentials успешно подключен.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подключиться к Essentials.", e);
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        HandlerList.unregisterAll(this);
        this.essentials = null;
        this.enabled = false;
        this.afkPlayers.clear();
    }

    @Override
    public boolean isEnabled() {
        return enabled && essentials != null;
    }

    @Override
    public String getPluginName() {
        return "Essentials";
    }

    /**
     * Получить API Essentials.
     */
    public Essentials getEssentials() {
        return essentials;
    }

    /**
     * Проверить, AFK ли игрок.
     */
    public boolean isAfk(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    /**
     * Проверить, AFK ли игрок (по UUID).
     */
    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    /**
     * Проверить, включен ли GodMode у игрока.
     */
    public boolean isGodMode(Player player) {
        if (!isEnabled() || player == null) return false;

        try {
            User user = essentials.getUser(player);
            return user != null && user.isGodModeEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    public void onAfkChange(AfkStatusChangeEvent event) {
        // Игнорируем событие при выходе игрока
        if (event.getCause() == AfkStatusChangeEvent.Cause.QUIT
                || event.getCause() == AfkStatusChangeEvent.Cause.UNKNOWN) {
            return;
        }

        UUID uuid = event.getAffected().getBase().getUniqueId();
        if (event.getValue()) {
            afkPlayers.add(uuid);
        } else {
            afkPlayers.remove(uuid);
        }
    }

    /**
     * Очистка AFK-статуса при выходе игрока.
     * Предотвращает "застревание" игрока в AFK после реконнекта.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        afkPlayers.remove(event.getPlayer().getUniqueId());
    }
}