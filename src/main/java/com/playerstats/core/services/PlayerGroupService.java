package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.hooks.LuckPermsHook;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Сервис для определения группы игрока с кэшированием.
 * Использует цепочку: LuckPerms → База данных → Конфиг (default).
 */
public final class PlayerGroupService implements Listener {

    private final Main plugin;
    private final ConfigHandler config;
    private final PlayerStatsDAO statsDAO;
    private final LuckPermsHook luckPermsHook;

    // Потокобезопасный кэш: UUID игрока → форматированное название группы
    private final Map<UUID, String> groupCache = new ConcurrentHashMap<>();

    public PlayerGroupService(Main plugin, ConfigHandler config,
                              PlayerStatsDAO statsDAO, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.config = config;
        this.statsDAO = statsDAO;
        this.luckPermsHook = luckPermsHook;

        // Регистрируем слушатели событий
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Если LuckPerms есть, слушаем его событие обновления данных пользователя
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            luckPermsHook.subscribeToEvents(this::onUserDataRecalculateHandler);
        }
    }

    // ==================== Публичные методы ====================

    /**
     * Получить отформатированную группу игрока.
     * Порядок: кэш → LuckPerms → БД → конфиг (default).
     */
    public String getPlayerGroup(Player player, int playerId) {
        UUID uuid = player.getUniqueId();

        // 1. Кэш
        String cached = groupCache.get(uuid);
        if (cached != null) return cached;

        // 2. LuckPerms
        String group = resolveFromLuckPerms(player);

        // 3. БД
        if (group == null) {
            group = resolveFromDatabase(playerId);
        }

        // 4. Конфиг
        if (group == null) {
            group = config.getGroupFormat("default", "§rИгрок");
        }

        groupCache.put(uuid, group);
        return group;
    }

    /**
     * Получить группу игрока по UUID.
     * Для офлайн-игроков БД недоступна (нет playerId), поэтому цепочка: кэш → LuckPerms → конфиг.
     */
    public String getPlayerGroup(UUID uuid) {
        String cached = groupCache.get(uuid);
        if (cached != null) return cached;

        // Пробуем LuckPerms
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            String group = luckPermsHook.getPlayerGroup(uuid);
            if (group != null && !group.isEmpty()) {
                String formatted = config.getGroupFormat(group, "§r" + group);
                groupCache.put(uuid, formatted);
                return formatted;
            }
        }

        return config.getGroupFormat("default", "§rИгрок");
    }

    /**
     * Принудительно обновить кэш для игрока.
     */
    public void invalidateCache(UUID uuid) {
        groupCache.remove(uuid);
    }

    /**
     * Обновить кэш и сразу получить актуальную группу.
     */
    public String refreshGroup(Player player, int playerId) {
        invalidateCache(player.getUniqueId());
        return getPlayerGroup(player, playerId);
    }

    // ==================== Приватные методы разрешения ====================

    private String resolveFromLuckPerms(Player player) {
        if (luckPermsHook == null || !luckPermsHook.isEnabled()) return null;

        try {
            String primaryGroup = luckPermsHook.getPlayerGroup(player);
            if (primaryGroup != null && !primaryGroup.isEmpty()) {
                return config.getGroupFormat(primaryGroup, "§r" + primaryGroup);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка при получении группы из LuckPerms для игрока " + player.getName(), e);
        }
        return null;
    }

    private String resolveFromDatabase(int playerId) {
        try {
            int serverId = plugin.getServerId();
            String groupFromDB = statsDAO.getStatString(playerId, serverId, "group");
            if (groupFromDB != null && !groupFromDB.isEmpty()) {
                return config.getGroupFormat(groupFromDB, "§r" + groupFromDB);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка при получении группы из БД для playerId=" + playerId, e);
        }
        return null;
    }

    // ==================== Слушатели событий ====================

    /**
     * Кэшируем группу при входе игрока (асинхронно, чтобы не задерживать вход).
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int playerId = plugin.getPlayerDAO().getOrCreatePlayerId(
                        player.getUniqueId(), player.getName());
                getPlayerGroup(player, playerId); // заполнит кэш
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Не удалось закэшировать группу при входе: " + player.getName(), e);
            }
        });
    }

    /**
     * Очищаем кэш при выходе игрока.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        groupCache.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Обработчик события LuckPerms об обновлении данных пользователя.
     * ВАЖНО: событие может срабатывать в async-потоке.
     * Для работы с Bukkit API переключаемся на главный поток.
     */
    private void onUserDataRecalculateHandler(UserDataRecalculateEvent event) {
        if (event.getUser() == null) return;

        UUID uuid = event.getUser().getUniqueId();

        // Переключаемся на главный поток для безопасной работы с Bukkit API
        plugin.getThreadManager().runSync(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                invalidateCache(uuid);
                plugin.getLogger().info("Кэш группы сброшен для "
                        + player.getName() + " (обновление данных LuckPerms)");
            } else {
                // Игрок офлайн — просто сбрасываем кэш
                invalidateCache(uuid);
            }
        });
    }

    // ==================== Служебные методы ====================

    public void clearCache() {
        groupCache.clear();
    }

    public int getCacheSize() {
        return groupCache.size();
    }
}