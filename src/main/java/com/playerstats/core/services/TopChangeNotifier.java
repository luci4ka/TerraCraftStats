package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.database.dao.LeaderboardDAO.LeaderboardEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TopChangeNotifier {

    private static final int TOP_SIZE = 3;

    private final Map<String, List<UUID>> previousTop = new ConcurrentHashMap<>();

    private final Main plugin;
    private final TopCache topCache;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static final Map<String, String> CATEGORY_TO_STAT = Map.of(
            "kills", "kills",
            "mobs", "mobs_killed",
            "blocks", "blocks_placed",
            "playtime", "play_time",
            "balance", "balance"
    );

    public TopChangeNotifier(Main plugin, TopCache topCache) {
        this.plugin = plugin;
        this.topCache = topCache;
        startNotifier();
    }

    private void startNotifier() {
        if (!plugin.getConfigHandler().isTopNotificationsEnabled()) {
            plugin.getLogger().info("Top-notifications отключены в config.yml.");
            return;
        }

        long checkSeconds = plugin.getConfigHandler().getTopNotificationsCheckSeconds();
        plugin.getLogger().info("Top-notifications: проверка раз в " + checkSeconds + " сек.");

        plugin.getThreadManager().runAsyncRepeating(() -> {
            try {
                checkAll();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка проверки изменений топа", e);
            }
        }, 10, checkSeconds);
    }

    private void checkAll() {
        for (Map.Entry<String, String> entry : CATEGORY_TO_STAT.entrySet()) {
            String category = entry.getKey();
            String statName = entry.getValue();

            if (!plugin.getConfigHandler().isTopNotificationCategoryEnabled(category)) {
                continue;
            }

            checkCategory(category, statName);
        }
    }

    private void checkCategory(String category, String statName) {
        List<LeaderboardEntry> top = topCache.getTop(statName);
        if (top.isEmpty()) return;

        int size = Math.min(TOP_SIZE, top.size());
        List<LeaderboardEntry> currentTop = top.subList(0, size);

        List<UUID> currentUuids = new ArrayList<>();
        Map<UUID, String> currentNames = new HashMap<>();
        for (LeaderboardEntry e : currentTop) {
            currentUuids.add(e.uuid());
            currentNames.put(e.uuid(), e.playerName());
        }

        List<UUID> previousUuids = previousTop.get(statName);

        if (previousUuids == null) {
            previousTop.put(statName, currentUuids);
            return;
        }

        UUID currentFirst = currentUuids.isEmpty() ? null : currentUuids.get(0);
        UUID previousFirst = previousUuids.isEmpty() ? null : previousUuids.get(0);

        if (currentFirst != null && !currentFirst.equals(previousFirst)) {
            String newName = currentNames.get(currentFirst);
            String oldName = previousFirst != null
                    ? resolveName(previousFirst) : "—";
            broadcastFirstPlace(category, newName, oldName);
        }

        boolean onlyFirst = plugin.getConfigHandler().isTopNotificationsOnlyFirstPlace();

        if (!onlyFirst) {
            for (int i = 0; i < currentUuids.size(); i++) {
                UUID uuid = currentUuids.get(i);
                if (!previousUuids.contains(uuid)) {
                    broadcastTop3Enter(category, currentNames.get(uuid), i + 1);
                }
            }

            for (UUID uuid : previousUuids) {
                if (!currentUuids.contains(uuid)) {
                    broadcastTop3Leave(category, resolveName(uuid));
                }
            }
        }

        previousTop.put(statName, currentUuids);
    }

    private void broadcastFirstPlace(String category, String newName, String oldName) {
        String categoryDisplay = plugin.getConfigHandler()
                .getTopNotificationCategoryDisplay(category);

        String message = plugin.getConfigHandler()
                .getTopNotificationMessage("first-place")
                .replace("{player}", newName)
                .replace("{previous}", oldName)
                .replace("{category}", categoryDisplay);

        broadcast(message);
    }

    private void broadcastTop3Enter(String category, String playerName, int rank) {
        String categoryDisplay = plugin.getConfigHandler()
                .getTopNotificationCategoryDisplay(category);

        String message = plugin.getConfigHandler()
                .getTopNotificationMessage("top3-enter")
                .replace("{player}", playerName)
                .replace("{rank}", String.valueOf(rank))
                .replace("{category}", categoryDisplay);

        broadcast(message);
    }

    private void broadcastTop3Leave(String category, String playerName) {
        String categoryDisplay = plugin.getConfigHandler()
                .getTopNotificationCategoryDisplay(category);

        String message = plugin.getConfigHandler()
                .getTopNotificationMessage("top3-leave")
                .replace("{player}", playerName)
                .replace("{category}", categoryDisplay);

        broadcast(message);
    }

    private void broadcast(String message) {
        Component component = miniMessage.deserialize(message);

        plugin.getThreadManager().runSync(() -> {
            Bukkit.getServer().sendMessage(component);
        });

        if (plugin.getConfigHandler().isTopNotificationsSendToDiscord()) {
            if (plugin.getDiscordWebhook() != null
                    && plugin.getDiscordWebhook().isEnabled()) {
                plugin.getDiscordWebhook().send(stripMiniMessage(message));
            }
        }
    }

    private String stripMiniMessage(String input) {
        return input.replaceAll("<[^>]+>", "");
    }

    private String resolveName(UUID uuid) {
        var offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null) return offline.getName();
        return uuid.toString().substring(0, 8);
    }

    public void clear() {
        previousTop.clear();
    }
}