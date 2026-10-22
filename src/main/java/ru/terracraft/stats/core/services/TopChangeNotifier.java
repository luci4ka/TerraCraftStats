package ru.terracraft.stats.core.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.model.TopNotificationsConfig;
import ru.terracraft.stats.core.database.dao.LeaderboardDAO.LeaderboardEntry;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TopChangeNotifier {

    private static final int TOP_SIZE = 3;
    private final Map<String, List<UUID>> previousTop = new ConcurrentHashMap<>();
    private final PlayerStatsPlugin plugin;
    private final TopCache topCache;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static final Map<String, String> CATEGORY_TO_STAT = Map.of(
            "kills", "kills", "mobs", "mobs_killed", "blocks", "blocks_placed",
            "playtime", "play_time", "balance", "balance"
    );

    public TopChangeNotifier(PlayerStatsPlugin plugin, TopCache topCache) {
        this.plugin = plugin;
        this.topCache = topCache;
        startNotifier();
    }

    private TopNotificationsConfig cfg() {
        return plugin.getConfigManager().topNotifications();
    }

    private void startNotifier() {
        var cm = plugin.getConfigManager();
        if (!cm.main().modules().topNotifications() || cm.topNotifications() == null || !cm.topNotifications().enabled()) {
            plugin.getLogger().info("Top-notifications отключены.");
            return;
        }
        long checkSeconds = cm.topNotifications().checkSeconds();
        plugin.getLogger().info("Top-notifications: проверка раз в " + checkSeconds + " сек.");
        plugin.getThreadManager().runAsyncRepeating(() -> {
            try { checkAll(); }
            catch (Exception e) { plugin.getLogger().log(Level.WARNING, "Ошибка проверки топа", e); }
        }, 10, checkSeconds);
    }

    private void checkAll() {
        TopNotificationsConfig c = cfg();
        if (c == null) return;
        for (var entry : CATEGORY_TO_STAT.entrySet()) {
            if (c.categories() == null || !c.categories().getOrDefault(entry.getKey(), true)) continue;
            checkCategory(entry.getKey(), entry.getValue());
        }
    }

    private void checkCategory(String category, String statName) {
        List<LeaderboardEntry> top = topCache.getTop(statName);
        if (top.isEmpty()) return;
        int size = Math.min(TOP_SIZE, top.size());
        List<LeaderboardEntry> currentTop = new ArrayList<>(top.subList(0, size));
        List<UUID> currentUuids = new ArrayList<>();
        Map<UUID, String> currentNames = new HashMap<>();
        for (LeaderboardEntry e : currentTop) { currentUuids.add(e.uuid()); currentNames.put(e.uuid(), e.playerName()); }

        List<UUID> previousUuids = previousTop.get(statName);
        if (previousUuids == null) { previousTop.put(statName, currentUuids); return; }

        UUID currentFirst = currentUuids.isEmpty() ? null : currentUuids.get(0);
        UUID previousFirst = previousUuids.isEmpty() ? null : previousUuids.get(0);

        if (currentFirst != null && !currentFirst.equals(previousFirst)) {
            broadcastFirstPlace(category, currentNames.get(currentFirst),
                    previousFirst != null ? resolveName(previousFirst) : "—");
        }

        TopNotificationsConfig c = cfg();
        boolean onlyFirst = c != null && c.onlyFirstPlace();
        if (!onlyFirst) {
            for (int i = 0; i < currentUuids.size(); i++) {
                UUID uuid = currentUuids.get(i);
                if (!previousUuids.contains(uuid)) broadcastTop3Enter(category, currentNames.get(uuid), i + 1);
            }
            for (UUID uuid : previousUuids) {
                if (!currentUuids.contains(uuid)) broadcastTop3Leave(category, resolveName(uuid));
            }
        }
        previousTop.put(statName, currentUuids);
    }

    private String categoryDisplay(String category) {
        TopNotificationsConfig c = cfg();
        if (c == null || c.messages() == null || c.messages().categories() == null) return category;
        return c.messages().categories().getOrDefault(category, category);
    }

    private String message(String key) {
        TopNotificationsConfig c = cfg();
        if (c == null || c.messages() == null) return "";
        return switch (key) {
            case "first-place" -> c.messages().firstPlace();
            case "top3-enter" -> c.messages().top3Enter();
            case "top3-leave" -> c.messages().top3Leave();
            default -> "";
        };
    }

    private void broadcastFirstPlace(String category, String newName, String oldName) {
        broadcast(message("first-place").replace("{player}", newName)
                .replace("{previous}", oldName).replace("{category}", categoryDisplay(category)));
    }

    private void broadcastTop3Enter(String category, String playerName, int rank) {
        broadcast(message("top3-enter").replace("{player}", playerName)
                .replace("{rank}", String.valueOf(rank)).replace("{category}", categoryDisplay(category)));
    }

    private void broadcastTop3Leave(String category, String playerName) {
        broadcast(message("top3-leave").replace("{player}", playerName)
                .replace("{category}", categoryDisplay(category)));
    }

    private void broadcast(String message) {
        if (message == null || message.isEmpty()) return;
        Component component = miniMessage.deserialize(message);
        plugin.getThreadManager().runSync(() -> Bukkit.getServer().sendMessage(component));

        TopNotificationsConfig c = cfg();
        if (c != null && c.sendToDiscord() && plugin.getDiscordWebhook() != null
                && plugin.getDiscordWebhook().isEnabled()) {
            plugin.getDiscordWebhook().send(stripMiniMessage(message));
        }
    }

    private String stripMiniMessage(String input) { return input.replaceAll("<[^>]+>", ""); }

    /** Имя через БД (быстро), а не через Mojang API (блокирует). */
    private String resolveName(UUID uuid) {
        try {
            String name = plugin.getPlayerDAO().getPlayerName(uuid);
            return name != null ? name : uuid.toString().substring(0, 8);
        } catch (SQLException e) {
            return uuid.toString().substring(0, 8);
        }
    }

    public void clear() { previousTop.clear(); }
}