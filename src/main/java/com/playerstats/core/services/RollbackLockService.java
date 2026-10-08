package com.playerstats.core.services;

import com.playerstats.Main;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Блокировка инкремента статы после rollback.
 *
 * <p>Пока игрок в «локе» — все инкременты по этой стате игнорируются.</p>
 */
public class RollbackLockService {

    private final Main plugin;

    /** UUID → (statName → until timestamp). */
    private final Map<UUID, Map<String, Long>> locks = new ConcurrentHashMap<>();

    public RollbackLockService(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Заблокировать инкремент для игрока на N секунд.
     */
    public void lock(UUID uuid, String statName) {
        long seconds = plugin.getConfigHandler().getRollbackLockSeconds();
        if (seconds <= 0) return;

        long until = System.currentTimeMillis() + seconds * 1000L;
        locks.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(statName, until);
    }

    /** Заблокировать все статы (для --all). */
    public void lockAll(UUID uuid) {
        long seconds = plugin.getConfigHandler().getRollbackLockSeconds();
        if (seconds <= 0) return;

        long until = System.currentTimeMillis() + seconds * 1000L;
        Map<String, Long> map = locks.computeIfAbsent(uuid,
                k -> new ConcurrentHashMap<>());
        map.put("*", until);
    }

    /**
     * Заблокирован ли инкремент.
     */
    public boolean isLocked(UUID uuid, String statName) {
        Map<String, Long> map = locks.get(uuid);
        if (map == null) return false;

        long now = System.currentTimeMillis();

        // Сначала — конкретная стата
        Long until = map.get(statName);
        if (until != null) {
            if (until > now) return true;
            map.remove(statName);
        }

        // Потом — wildcard (--all)
        Long wildcard = map.get("*");
        if (wildcard != null) {
            if (wildcard > now) return true;
            map.remove("*");
        }

        if (map.isEmpty()) locks.remove(uuid);
        return false;
    }

    public void clear() {
        locks.clear();
    }

    public void clear(UUID uuid) {
        locks.remove(uuid);
    }
}