package ru.terracraft.stats.core.services;

import ru.terracraft.stats.PlayerStatsPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RollbackLockService {

    private final PlayerStatsPlugin plugin;
    private final Map<UUID, Map<String, Long>> locks = new ConcurrentHashMap<>();

    public RollbackLockService(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    private long getLockSeconds() {
        return plugin.getConfigManager().main().rollback().lockSeconds();
    }

    public void lock(UUID uuid, String statName) {
        long seconds = getLockSeconds();
        if (seconds <= 0) return;
        long until = System.currentTimeMillis() + seconds * 1000L;
        locks.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(statName, until);
    }

    public void lockAll(UUID uuid) {
        long seconds = getLockSeconds();
        if (seconds <= 0) return;
        long until = System.currentTimeMillis() + seconds * 1000L;
        locks.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put("*", until);
    }

    public boolean isLocked(UUID uuid, String statName) {
        Map<String, Long> map = locks.get(uuid);
        if (map == null) return false;
        long now = System.currentTimeMillis();
        Long until = map.get(statName);
        if (until != null) {
            if (until > now) return true;
            map.remove(statName);
        }
        Long wildcard = map.get("*");
        if (wildcard != null) {
            if (wildcard > now) return true;
            map.remove("*");
        }
        if (map.isEmpty()) locks.remove(uuid);
        return false;
    }

    public void clear() { locks.clear(); }
    public void clear(UUID uuid) { locks.remove(uuid); }
}