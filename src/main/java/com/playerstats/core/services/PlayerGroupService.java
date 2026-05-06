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

    /**
     * @param plugin       главный класс плагина
     * @param config       обработчик конфига
     * @param statsDAO     DAO для работы со статистикой (где хранится группа)
     * @param luckPermsHook хук LuckPerms (может быть выключен)
     */
    public PlayerGroupService(Main plugin, ConfigHandler config, PlayerStatsDAO statsDAO, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.config = config;
        this.statsDAO = statsDAO;
        this.luckPermsHook = luckPermsHook;

        // Регистрируем слушатели событий
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Если LuckPerms есть, слушаем его событие обновления данных пользователя
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            luckPermsHook.subscribeToEvents((LuckPermsHook.LuckPermsEventListener) this::onUserDataRecalculateHandler);
        }
    }

    // ==================== Публичные методы ====================

    /**
     * Получить отформатированную группу игрока.
     * Порядок определения:
     * 1. Кэш
     * 2. LuckPerms (если доступен)
     * 3. База данных (статистика "group")
     * 4. Значение по умолчанию из конфига
     *
     * @param player   объект игрока
     * @param playerId ID игрока в базе данных
     * @return форматированная строка группы (с цветами)
     */
    public String getPlayerGroup(Player player, int playerId) {
        UUID uuid = player.getUniqueId();

        // 1. Проверяем кэш
        String cached = groupCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // 2. Пытаемся получить из LuckPerms
        String group = resolveFromLuckPerms(player);

        // 3. Если LuckPerms не дал результата — идём в базу
        if (group == null) {
            group = resolveFromDatabase(playerId);
        }

        // 4. Если ничего не нашли — берём default из конфига
        if (group == null) {
            group = config.getGroupFormat("default", "§rИгрок");
        }

        // Сохраняем в кэш
        groupCache.put(uuid, group);
        return group;
    }

    /**
     * Получить группу игрока по UUID (без Player объекта).
     */
    public String getPlayerGroup(UUID uuid) {
        String cached = groupCache.get(uuid);
        if (cached != null) return cached;

        // Пробуем LuckPerms
        if (luckPermsHook != null && luckPermsHook.isEnabled()) {
            String group = luckPermsHook.getPlayerGroup(uuid);
            if (group != null) {
                String formatted = config.getGroupFormat(group, "§r" + group);
                groupCache.put(uuid, formatted);
                return formatted;
            }
        }

        return config.getGroupFormat("default", "§rИгрок");
    }

    /**
     * Принудительно обновить кэш для игрока (например, при смене группы админом).
     */
    public void invalidateCache(UUID uuid) {
        groupCache.remove(uuid);
    }

    /**
     * Принудительно обновить кэш для игрока и сразу получить актуальную группу.
     */
    public String refreshGroup(Player player, int playerId) {
        invalidateCache(player.getUniqueId());
        return getPlayerGroup(player, playerId);
    }

    // ==================== Приватные методы разрешения ====================

    /**
     * Попытаться получить группу через LuckPerms.
     */
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

    /**
     * Попытаться получить группу из базы данных.
     */
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
     * Кэшируем группу при входе игрока.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Запускаем асинхронно, чтобы не задерживать вход
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
     * Очищаем кэш при выходе игрока (экономия памяти).
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        groupCache.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Обработчик события LuckPerms об обновлении данных пользователя.
     */
    private void onUserDataRecalculateHandler(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            invalidateCache(uuid);
            plugin.getLogger().info("Кэш группы сброшен для " + player.getName() + " (обновление данных LuckPerms)");
        }
    }

    // ==================== Служебные методы ====================

    /**
     * Полностью очистить кэш (например, при перезагрузке плагина).
     */
    public void clearCache() {
        groupCache.clear();
    }

    /**
     * Получить размер кэша (для дебага/мониторинга).
     */
    public int getCacheSize() {
        return groupCache.size();
    }
}