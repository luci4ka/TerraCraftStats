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
 * <p><b>MiniMessage-безопасно:</b> все значения возвращаются в MiniMessage
 * (не legacy {@code §}). Если в конфиге указана legacy-строка — она
 * конвертируется.</p>
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

        group = sanitize(group);
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
                group = sanitize(group);
                groupCache.put(uuid, group);
                return group;
            }
        }
        return sanitize(getDefaultGroupFormat());
    }

    public void invalidateCache(UUID uuid) { groupCache.remove(uuid); }

    public String refreshGroup(Player player, int playerId) {
        invalidateCache(player.getUniqueId());
        return getPlayerGroup(player, playerId);
    }

    private String getDefaultGroupFormat() {
        Map<String, String> groups = config.main().groups();
        return groups.getOrDefault("default", "<gray>Игрок");
    }

    /** Конвертирует legacy {@code §}-коды в MiniMessage-эквивалент. */
    private String sanitize(String raw) {
        if (raw == null) return "";
        if (raw.indexOf('§') < 0) return raw;
        // legacy → MiniMessage (простая замена кодов на теги)
        return raw
                .replace("§0", "<black>").replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>").replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>").replace("§5", "<dark_purple>")
                .replace("§6", "<gold>").replace("§7", "<gray>")
                .replace("§8", "<dark_gray>").replace("§9", "<blue>")
                .replace("§a", "<green>").replace("§b", "<aqua>")
                .replace("§c", "<red>").replace("§d", "<light_purple>")
                .replace("§e", "<yellow>").replace("§f", "<white>")
                .replace("§r", "<reset>")
                .replace("§l", "<bold>").replace("§o", "<italic>")
                .replace("§n", "<underlined>").replace("§m", "<strikethrough>")
                .replace("§k", "<obfuscated>");
    }

    // ==================== Разрешение ====================

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
                    .getOrDefault(group.toLowerCase(), "<gray>" + group);
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
                        .getOrDefault(groupFromDB.toLowerCase(), "<gray>" + groupFromDB);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка БД при получении группы playerId=" + playerId, e);
        }
        return null;
    }

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

    public void clearCache() { groupCache.clear(); }
    public int getCacheSize() { return groupCache.size(); }
}