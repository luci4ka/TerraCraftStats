package ru.terracraft.stats.core.services;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.ConfigManager;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Сервис для определения группы игрока с кэшированием.
 *
 * <p>Порядок: кэш → PlaceholderAPI ({@code %luckperms_primary_group_name%}) → БД → дефолт из конфига.</p>
 *
 * <p><b>Внимание:</b> через PAPI группа обновляется только при перезаходе игрока
 * (или ручном {@link #invalidateCache(UUID)}). Если нужен live-апдейт — вернуть
 * подписку на LuckPerms API.</p>
 */
public final class PlayerGroupService implements Listener {

    private final PlayerStatsPlugin plugin;
    private final ConfigManager config;
    private final PlayerStatsDAO statsDAO;

    private final Map<UUID, String> groupCache = new ConcurrentHashMap<>();

    public PlayerGroupService(PlayerStatsPlugin plugin, ConfigManager config,
                              PlayerStatsDAO statsDAO) {
        this.plugin = plugin;
        this.config = config;
        this.statsDAO = statsDAO;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ==================== Публичные методы ====================

    public String getPlayerGroup(Player player, int playerId) {
        UUID uuid = player.getUniqueId();
        String cached = groupCache.get(uuid);
        if (cached != null) return cached;

        String group = resolveFromPlaceholder(player);
        if (group == null) group = resolveFromDatabase(playerId);
        if (group == null) group = getDefaultGroupFormat();

        groupCache.put(uuid, group);
        return group;
    }

    public String getPlayerGroup(UUID uuid) {
        String cached = groupCache.get(uuid);
        if (cached != null) return cached;

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            String group = resolveFromPlaceholder(online);
            if (group != null) {
                groupCache.put(uuid, group);
                return group;
            }
        }
        return getDefaultGroupFormat();
    }

    public void invalidateCache(UUID uuid) { groupCache.remove(uuid); }

    public String refreshGroup(Player player, int playerId) {
        invalidateCache(player.getUniqueId());
        return getPlayerGroup(player, playerId);
    }

    private String getDefaultGroupFormat() {
        Map<String, String> groups = config.main().groups();
        return groups.getOrDefault("default", "§rИгрок");
    }

    // ==================== Разрешение ====================

    /**
     * Группа через PlaceholderAPI ({@code %luckperms_primary_group_name%}).
     * Возвращает {@code null}, если PAPI или expansion LuckPerms не установлены.
     */
    private String resolveFromPlaceholder(Player player) {
        if (plugin.getHooksManager() == null
                || plugin.getHooksManager().getPlaceholderAPIHook() == null
                || !plugin.getHooksManager().getPlaceholderAPIHook().isEnabled()) {
            return null;
        }
        try {
            String group = PlaceholderAPI.setPlaceholders(player,
                    "%luckperms_primary_group_name%");
            if (group == null || group.isEmpty() || group.contains("%")) {
                return null;
            }
            return config.main().groups()
                    .getOrDefault(group.toLowerCase(), "§r" + group);
        } catch (Throwable t) {
            return null;
        }
    }

    private String resolveFromDatabase(int playerId) {
        try {
            int serverId = plugin.getServerId();
            String groupFromDB = statsDAO.getStatString(playerId, serverId, "group");
            if (groupFromDB != null && !groupFromDB.isEmpty()) {
                return config.main().groups()
                        .getOrDefault(groupFromDB.toLowerCase(), "§r" + groupFromDB);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка БД при получении группы playerId=" + playerId, e);
        }
        return null;
    }

    // ==================== Слушатели ====================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getThreadManager().runAsync(() -> {
            try {
                int playerId = plugin.getPlayerDAO()
                        .getOrCreatePlayerId(player.getUniqueId(), player.getName());
                getPlayerGroup(player, playerId);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Не удалось закэшировать группу: " + player.getName(), e);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        groupCache.remove(event.getPlayer().getUniqueId());
    }

    // ==================== Служебные ====================

    public void clearCache() { groupCache.clear(); }
    public int getCacheSize() { return groupCache.size(); }
}