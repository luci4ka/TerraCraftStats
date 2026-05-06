package com.playerstats.hooks;

import com.playerstats.Main;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

public class LuckPermsHook implements Hook {

    private final Main plugin;
    private LuckPerms luckPermsApi;
    private boolean enabled = false;

    public LuckPermsHook(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            plugin.getLogger().info("LuckPerms не обнаружен — интеграция отключена.");
            return;
        }

        try {
            this.luckPermsApi = LuckPermsProvider.get();
            this.enabled = true;
            plugin.getLogger().info("LuckPerms успешно подключен.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подключиться к LuckPerms.", e);
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        this.luckPermsApi = null;
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled && luckPermsApi != null;
    }

    @Override
    public String getPluginName() {
        return "LuckPerms";
    }

    /**
     * Получить API LuckPerms.
     */
    public LuckPerms getApi() {
        return luckPermsApi;
    }

    /**
     * Получить группу игрока.
     */
    public String getPlayerGroup(Player player) {
        if (!isEnabled()) return null;

        try {
            User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getPrimaryGroup();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения группы игрока " + player.getName(), e);
        }
        return null;
    }

    /**
     * Получить группу игрока по UUID.
     */
    public String getPlayerGroup(UUID uuid) {
        if (!isEnabled()) return null;

        try {
            User user = luckPermsApi.getUserManager().getUser(uuid);
            if (user != null) {
                return user.getPrimaryGroup();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения группы игрока " + uuid, e);
        }
        return null;
    }

    /**
     * Подписаться на событие обновления данных пользователя.
     */
    public void subscribeToEvents(Object listener) {
        if (!isEnabled()) return;
        luckPermsApi.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                (UserDataRecalculateEvent event) -> {
                    if (listener instanceof LuckPermsEventListener le) {
                        le.onUserDataRecalculate(event);
                    }
                });
    }

    /**
     * Интерфейс для слушателей событий LuckPerms.
     */
    public interface LuckPermsEventListener {
        void onUserDataRecalculate(UserDataRecalculateEvent event);
    }
}