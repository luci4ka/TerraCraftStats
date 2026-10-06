package com.playerstats.hooks;

import com.playerstats.Main;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
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
     * Получить группу игрока через CachedData.
     * Безопасно для главного потока — не блокирует.
     */
    public String getPlayerGroup(Player player) {
        if (!isEnabled() || player == null) return null;

        try {
            // CachedData — самый быстрый и безопасный способ [citation:2][citation:12]
            var metaData = luckPermsApi.getPlayerAdapter(Player.class)
                    .getMetaData(player);
            return metaData.getPrimaryGroup();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка получения группы игрока " + player.getName(), e);
            return null;
        }
    }

    /**
     * Получить группу игрока по UUID.
     * Для офлайн-игроков может вернуть null, если данные не загружены.
     */
    public String getPlayerGroup(UUID uuid) {
        if (!isEnabled() || uuid == null) return null;

        try {
            User user = luckPermsApi.getUserManager().getUser(uuid);
            if (user == null) {
                // Данные ещё не загружены — для офлайн-игрока это нормально [citation:1]
                return null;
            }

            // Используем CachedData для безопасного получения группы [citation:2]
            var metaData = user.getCachedData().getMetaData(QueryOptions.nonContextual());
            return metaData.getPrimaryGroup();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка получения группы игрока " + uuid, e);
            return null;
        }
    }

    /**
     * Подписаться на событие обновления данных пользователя.
     * Событие может срабатывать асинхронно — слушатель должен сам переключаться на главный поток.
     */
    public void subscribeToEvents(LuckPermsEventListener listener) {
        if (!isEnabled() || listener == null) return;

        luckPermsApi.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                event -> {
                    try {
                        // Проверяем, что пользователь загружен
                        if (event.getUser() == null) return;

                        // Делегируем обработку слушателю
                        listener.onUserDataRecalculate(event);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Ошибка обработки UserDataRecalculateEvent", e);
                    }
                });
    }

    /**
     * Интерфейс для слушателей событий LuckPerms.
     * ВАЖНО: Событие может срабатывать в асинхронном потоке.
     * Если нужно работать с Bukkit API — переключайтесь на главный поток.
     */
    public interface LuckPermsEventListener {
        void onUserDataRecalculate(UserDataRecalculateEvent event);
    }
}