package ru.terracraft.stats.core.listeners;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.block.*;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.api.event.PlayerStatChangeEvent;
import ru.terracraft.stats.core.commands.ExcludeCommand;
import ru.terracraft.stats.core.config.TrackedBlocks;
import ru.terracraft.stats.core.config.model.MainConfig;
import ru.terracraft.stats.core.database.dao.PlayerDAO;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;
import ru.terracraft.stats.core.database.dao.ServerDAO;
import ru.terracraft.stats.core.database.dao.ServerStatsDAO;
import ru.terracraft.stats.core.multithreading.ThreadManager;
import ru.terracraft.stats.core.pvp.PvPTracker;
import ru.terracraft.stats.hooks.TerraCoreBridge;
import ru.terracraft.stats.hooks.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.PotionMeta;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class StatsListener implements Listener {

    private static final int MAX_CAS_RETRIES = 5;
    /** Таймаут ожидания PlayerStatChangeEvent: 2 сек (было 5 — слишком долго для пула). */
    private static final long EVENT_WAIT_SECONDS = 2L;
    private static final long FLUSH_PERIOD_SECONDS = 2L;

    private static final long DELTAS_CLEANUP_INITIAL_SECONDS = 60L * 60;
    private static final long DELTAS_CLEANUP_PERIOD_SECONDS = 6L * 60 * 60;
    private static final long DELTAS_TTL_MS = 30L * 24 * 60 * 60 * 1000;

    private static final List<EntityType> BOSS_TYPES = List.of(
            EntityType.WITHER,
            EntityType.ENDER_DRAGON,
            EntityType.ELDER_GUARDIAN,
            EntityType.WARDEN
    );

    private final PlayerStatsPlugin plugin;
    private final PlayerDAO playerDAO;
    private final PlayerStatsDAO statsDAO;
    private final ServerStatsDAO serverStatsDAO;
    private final ThreadManager threadManager;
    private final VaultHook vaultHook;
    private final TerraCoreBridge terraCore;
    private PvPTracker pvpTracker;
    private final int serverId;
    private final boolean verboseDebug;

    private volatile boolean trackDeltas;

    private final Map<UUID, Double> distanceBuffer = new ConcurrentHashMap<>();
    private final Map<String, Integer> incrementBufferActive = new ConcurrentHashMap<>();
    private final Map<String, Integer> incrementBufferDraining = new ConcurrentHashMap<>();
    private final Map<String, UUID> bufferOwner = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerIdCache = new ConcurrentHashMap<>();

    /** Время начала sneak/sprint — для таймеров. */
    private final Map<UUID, Long> sneakStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sprintStart = new ConcurrentHashMap<>();

    private volatile TrackedBlocks trackedBlocks = new TrackedBlocks(List.of());

    private ExcludeCommand excludeCommand;

    public StatsListener(PlayerStatsPlugin plugin,
                         PlayerDAO playerDAO,
                         PlayerStatsDAO statsDAO,
                         ServerStatsDAO serverStatsDAO,
                         ServerDAO serverDAO,
                         ThreadManager threadManager,
                         VaultHook vaultHook,
                         TerraCoreBridge terraCore) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
        this.statsDAO = statsDAO;
        this.serverStatsDAO = serverStatsDAO;
        this.threadManager = threadManager;
        this.vaultHook = vaultHook;
        this.terraCore = terraCore;
        this.serverId = plugin.getServerId();
        this.verboseDebug = plugin.getConfigManager().main().debugLevel() >= 2;
        this.trackDeltas = plugin.getConfigManager().main().rollback().trackDeltas();

        reloadTrackedBlocks();

        startPeriodicUpdate();
        startDistanceFlusher();
        startIncrementFlusher();
        startDeltasCleanup();
    }

    public void setExcludeCommand(ExcludeCommand excludeCommand) { this.excludeCommand = excludeCommand; }
    public void setPvpTracker(PvPTracker pvpTracker) { this.pvpTracker = pvpTracker; }

    public void reloadTrackedBlocks() {
        List<String> blocks = cfg().tracking().blocks();
        this.trackedBlocks = new TrackedBlocks(blocks != null ? blocks : List.of());
        this.trackDeltas = cfg().rollback().trackDeltas();
    }

    private MainConfig cfg() { return plugin.getConfigManager().main(); }

    // ==================== Периодическое обновление ====================

    private void startPeriodicUpdate() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline()) continue;
                if (!shouldCount(player)) continue;
                // createSnapshot трогает Vault (не thread-safe) — оставляем на main,
                // но результат-запись уходит в async через updatePlayerStats.
                PlayerSnapshot snapshot = createSnapshot(player);
                if (snapshot == null || snapshot.playerId == -1) continue;
                threadManager.runAsync(() -> updatePlayerStats(snapshot));
            }
        }, 20L, 20L * 60);
    }

    private static class PlayerSnapshot {
        final UUID uuid;
        final String name;
        final int playerId;
        final double balance;
        final String group;

        PlayerSnapshot(UUID uuid, String name, int playerId, double balance, String group) {
            this.uuid = uuid;
            this.name = name;
            this.playerId = playerId;
            this.balance = balance;
            this.group = group;
        }
    }

    private PlayerSnapshot createSnapshot(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        int playerId;
        try {
            playerId = resolvePlayerId(uuid, name);
        } catch (SQLException e) {
            logError("Не удалось получить ID игрока: " + name, e);
            return null;
        }

        double balance = 0.0;
        if (vaultHook != null && vaultHook.isEnabled()) {
            try { balance = vaultHook.getBalance(player); }
            catch (Throwable e) { logError("Не удалось получить баланс: " + name, e); }
        }

        String group = null;
        try { group = plugin.getGroupService().getPlayerGroup(player, playerId); }
        catch (Exception e) { logError("Не удалось получить группу: " + name, e); }

        if (verboseDebug) {
            plugin.getLogger().info("[DEBUG] Снимок: " + name + " (ID=" + playerId
                    + ", balance=" + balance + ", group=" + group + ")");
        }

        return new PlayerSnapshot(uuid, name, playerId, balance, group);
    }

    private void updatePlayerStats(PlayerSnapshot snap) {
        if (snap.playerId == -1) return;
        try {
            int pid = snap.playerId;
            int sid = serverId;
            statsDAO.incrementStat(pid, sid, "play_time", 60);
            if (vaultHook != null && vaultHook.isEnabled()) {
                statsDAO.setStatDouble(pid, sid, "balance", snap.balance);
            }
            if (snap.group != null && !snap.group.isEmpty()) {
                statsDAO.setStatString(pid, sid, "group", snap.group);
            }
            serverStatsDAO.incrementStat(sid, "play_time", 60);

            invalidatePlaceholderCache(snap.uuid);
        } catch (SQLException e) {
            logError("Ошибка обновления периодической статистики для " + snap.name, e);
        }
    }

    // ==================== Проверки ====================

    private boolean shouldCount(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;

        if (terraCore != null && terraCore.isEnabled()) {
            if (terraCore.isAfk(player)) return false;
            if (terraCore.isGod(player)) return false;
        }

        if (excludeCommand != null && excludeCommand.isExcluded(player.getUniqueId())) return false;
        return true;
    }

    private boolean isRollbackLocked(Player player, String statKey) {
        if (plugin.getRollbackLockService() == null) return false;
        boolean locked = plugin.getRollbackLockService().isLocked(player.getUniqueId(), statKey);
        if (locked && verboseDebug) {
            plugin.getLogger().info("[DEBUG] Rollback lock: ignoring " + statKey + " for " + player.getName());
        }
        return locked;
    }

    // ==================== playerId кэш ====================

    private int resolvePlayerId(UUID uuid, String name) throws SQLException {
        Integer cached = playerIdCache.get(uuid);
        if (cached != null) return cached;
        int id = playerDAO.getOrCreatePlayerId(uuid, name);
        playerIdCache.put(uuid, id);
        return id;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerIdCache.remove(uuid);
        sneakStart.remove(uuid);
        sprintStart.remove(uuid);
        // Сбрасываем накопленный хвост дистанции, чтобы не висел в памяти.
        distanceBuffer.remove(uuid);
    }

    // ==================== Сохранение онлайн ====================

    public void saveAllOnline() {
        try {
            flushIncrements();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка флаша буфера", e);
        }

        plugin.getLogger().info("Сохранение статистики онлайн-игроков...");

        // Vault + group — только на main. Запись в БД — в async.
        List<PlayerSnapshot> snapshots = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldCount(player)) continue;
            PlayerSnapshot snap = createSnapshot(player);
            if (snap != null && snap.playerId != -1) snapshots.add(snap);
        }

        List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        for (PlayerSnapshot snap : snapshots) {
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    statsDAO.incrementStat(snap.playerId, serverId, "play_time", 60);
                    serverStatsDAO.incrementStat(serverId, "play_time", 60);
                    if (vaultHook != null && vaultHook.isEnabled()) {
                        statsDAO.setStatDouble(snap.playerId, serverId, "balance", snap.balance);
                    }
                    if (snap.group != null && !snap.group.isEmpty()) {
                        statsDAO.setStatString(snap.playerId, serverId, "group", snap.group);
                    }
                    invalidatePlaceholderCache(snap.uuid);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Не удалось сохранить " + snap.name, e);
                }
            }));
        }

        try {
            java.util.concurrent.CompletableFuture
                    .allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            plugin.getLogger().warning("saveAllOnline: таймаут 10с.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка saveAllOnline", e);
        }
        plugin.getLogger().info("Статистика онлайн-игроков сохранена.");
    }

    // ==================== БЛОКИ ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        handleStatEvent(player, "blocks_placed");

        TrackedBlocks tb = trackedBlocks;
        if (tb.isTracked(type)) {
            handleStatEvent(player, "block_placed_" + type.name().toLowerCase());
        }

        if (type.name().endsWith("_BED")) {
            handleStatEvent(player, "beds_placed");
        }

        if (Tag.CROPS.isTagged(type) || type == Material.NETHER_WART
                || type.name().endsWith("_SAPLING")) {
            handleStatEvent(player, "crops_planted");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        handleStatEvent(player, "blocks_broken");

        TrackedBlocks tb = trackedBlocks;
        if (tb.isTracked(type)) {
            handleStatEvent(player, "block_broken_" + type.name().toLowerCase());
        }

        if (Tag.CROPS.isTagged(type) || type == Material.NETHER_WART) {
            Block block = event.getBlock();
            if (block.getBlockData() instanceof Ageable ageable
                    && ageable.getAge() >= ageable.getMaximumAge()) {
                handleStatEvent(player, "crops_harvested");
            }
        }

        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            handleStatEvent(player, "diamonds_mined");
        }
        if (type == Material.ANCIENT_DEBRIS) {
            handleStatEvent(player, "netherite_mined");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "signs_placed");
    }

    // ==================== PVP ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        handleStatEvent(dead, "deaths");
        handleKillstreakReset(dead);

        if (killer == null || killer == dead) return;
        if (!shouldCount(killer)) return;

        if (cfg().pvp().ignoreExcludedVictims()
                && excludeCommand != null
                && excludeCommand.isExcluded(dead.getUniqueId())) {
            return;
        }

        UUID killerUuid = killer.getUniqueId();
        UUID victimUuid = dead.getUniqueId();

        boolean countFullKill = true;
        if (pvpTracker != null && cfg().pvp().perVictimStats()) {
            if (!pvpTracker.shouldCount(killerUuid, victimUuid)) {
                countFullKill = false;
            }
        }

        if (countFullKill) {
            handleStatEvent(killer, "kills");
            handleKillstreakIncrement(killer);
        }

        if (cfg().pvp().perVictimStats()) {
            handleStatEvent(killer, "pvp_kill_" + victimUuid);
        }
    }

    // ==================== KILLSTREAK ====================

    private void handleKillstreakIncrement(Player killer) {
        if (!cfg().tracking().stats().killstreak()) return;
        if (isRollbackLocked(killer, "killstreak")) return;
        UUID uuid = killer.getUniqueId();
        String name = killer.getName();
        threadManager.runAsync(() -> {
            try {
                int killerId = resolvePlayerId(uuid, name);
                statsDAO.incrementStat(killerId, serverId, "killstreak", 1);
                int current = statsDAO.getStat(killerId, serverId, "killstreak");
                int best = statsDAO.getStat(killerId, serverId, "best_killstreak");
                if (current > best) statsDAO.setStat(killerId, serverId, "best_killstreak", current);
            } catch (SQLException e) { logError("Ошибка killstreak", e); }
        });
    }

    private void handleKillstreakReset(Player dead) {
        if (!cfg().tracking().stats().killstreak()) return;
        if (!shouldCount(dead)) return;
        if (isRollbackLocked(dead, "killstreak")) return;
        UUID uuid = dead.getUniqueId();
        String name = dead.getName();
        threadManager.runAsync(() -> {
            try {
                int victimId = resolvePlayerId(uuid, name);
                statsDAO.setStat(victimId, serverId, "killstreak", 0);
            } catch (SQLException e) { logError("Ошибка сброса killstreak", e); }
        });
    }

    // ==================== МОБЫ ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !shouldCount(killer)) return;

        handleStatEvent(killer, "mobs_killed");

        EntityType mobType = event.getEntity().getType();
        // ARMOR_STAND (и подобные) тоже isAlive() == true — исключаем.
        if (mobType.isAlive()
                && mobType != EntityType.PLAYER
                && !(event.getEntity() instanceof ArmorStand)) {
            handleStatEvent(killer, "mob_killed_" + mobType.name().toLowerCase());
        }
        if (isBossMob(event.getEntity())) handleStatEvent(killer, "bosses_killed");
    }

    // ==================== handleStatEvent ====================

    private void handleStatEvent(Player player, String statKey) {
        handleStatEvent(player, statKey, 1);
    }

    private void handleStatEvent(Player player, String statKey, int amount) {
        if (!shouldCount(player)) return;
        if (isRollbackLocked(player, statKey)) return;

        boolean hasListeners = PlayerStatChangeEvent.getHandlerList()
                .getRegisteredListeners().length > 0;

        if (!hasListeners) {
            final UUID uuid = player.getUniqueId();
            final String name = player.getName();
            threadManager.runAsync(() -> {
                try {
                    int playerId = resolvePlayerId(uuid, name);
                    queueIncrement(uuid, playerId, statKey, amount);
                } catch (SQLException e) {
                    logError("queueIncrement: не удалось получить playerId для " + name, e);
                }
            });
            return;
        }

        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final int serverIdLocal = serverId;

        threadManager.runAsync(() -> {
            try {
                int playerId = resolvePlayerId(uuid, name);
                int statTypeId = plugin.getStatTypeDAO().createOrGetStatType(statKey);

                for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
                    final int oldValue = statsDAO.getStat(playerId, serverIdLocal, statTypeId);
                    final int computedNewValue = oldValue + amount;

                    final int[] decided = new int[]{computedNewValue};
                    final boolean[] cancelled = new boolean[]{false};

                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && online.isOnline()) {
                        CountDownLatch latch = new CountDownLatch(1);
                        plugin.getThreadManager().runSync(() -> {
                            try {
                                Player p = Bukkit.getPlayer(uuid);
                                if (p == null || !p.isOnline()) return;

                                PlayerStatChangeEvent event = new PlayerStatChangeEvent(
                                        uuid, statKey, oldValue, computedNewValue);
                                Bukkit.getPluginManager().callEvent(event);

                                if (event.isCancelled()) {
                                    cancelled[0] = true;
                                } else {
                                    decided[0] = event.getNewValue();
                                }
                            } finally {
                                latch.countDown();
                            }
                        });

                        if (!latch.await(EVENT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                            if (verboseDebug) {
                                plugin.getLogger().warning("[STAT] Event timeout для '"
                                        + statKey + "' (" + name + ")");
                            }
                        }
                    }

                    if (cancelled[0]) return;
                    if (decided[0] == oldValue) return;

                    boolean ok = statsDAO.compareAndSetStat(
                            playerId, serverIdLocal, statTypeId, oldValue, decided[0]);

                    if (!ok) {
                        if (verboseDebug) {
                            plugin.getLogger().info("[STAT] CAS retry #" + (attempt + 1)
                                    + " для '" + statKey + "' (" + name + "), old=" + oldValue);
                        }
                        continue;
                    }

                    final int delta = decided[0] - oldValue;
                    if (delta != 0) {
                        serverStatsDAO.incrementStat(serverIdLocal, statKey, delta);
                        if (trackDeltas) {
                            try {
                                statsDAO.recordDelta(playerId, statKey, delta);
                            } catch (SQLException e) {
                                if (verboseDebug) {
                                    plugin.getLogger().warning("[DELTA] Не удалось записать дельту '"
                                            + statKey + "': " + e.getMessage());
                                }
                            }
                        }
                    }

                    invalidatePlaceholderCache(uuid);
                    if (plugin.getTopCache() != null) plugin.getTopCache().invalidate(statKey);

                    return;
                }

                logError("CAS retries exhausted для '" + statKey + "' / " + name, null);

            } catch (SQLException e) {
                logError("Ошибка записи '" + statKey + "' для " + name, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logError("Прервано ожидание события для '" + statKey + "' / " + name, e);
            }
        });
    }

    // ==================== Батч-инкременты ====================

    private void queueIncrement(UUID owner, int playerId, String statKey, int amount) {
        if (amount == 0) return;
        String key = playerId + ":" + serverId + ":" + statKey;
        incrementBufferActive.merge(key, amount, Integer::sum);
        bufferOwner.put(key, owner);
    }

    private void startIncrementFlusher() {
        threadManager.runAsyncRepeating(this::flushIncrements, 2, FLUSH_PERIOD_SECONDS);
    }

    private void flushIncrements() {
        synchronized (this) {
            if (incrementBufferActive.isEmpty()) return;
            for (Map.Entry<String, Integer> e : incrementBufferActive.entrySet()) {
                incrementBufferDraining.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            incrementBufferActive.clear();
        }

        if (incrementBufferDraining.isEmpty()) return;

        Map<String, Map<String, Integer>> byPlayer = new HashMap<>();
        for (Map.Entry<String, Integer> e : incrementBufferDraining.entrySet()) {
            String[] parts = e.getKey().split(":", 3);
            if (parts.length != 3) continue;
            String playerKey = parts[0] + ":" + parts[1];
            byPlayer.computeIfAbsent(playerKey, k -> new HashMap<>())
                    .merge(parts[2], e.getValue(), Integer::sum);
        }

        for (Map.Entry<String, Map<String, Integer>> entry : byPlayer.entrySet()) {
            String[] pp = entry.getKey().split(":");
            int playerId;
            int sid;
            try {
                playerId = Integer.parseInt(pp[0]);
                sid = Integer.parseInt(pp[1]);
            } catch (NumberFormatException nfe) {
                continue;
            }
            Map<String, Integer> increments = entry.getValue();

            UUID owner = null;
            for (String statKey : increments.keySet()) {
                UUID u = bufferOwner.remove(entry.getKey() + ":" + statKey);
                if (u != null) owner = u;
            }

            try {
                statsDAO.incrementStatsBatch(playerId, sid, increments);

                for (Map.Entry<String, Integer> inc : increments.entrySet()) {
                    if (inc.getValue() != 0) {
                        serverStatsDAO.incrementStat(sid, inc.getKey(), inc.getValue());
                    }
                }
            } catch (SQLException e) {
                for (Map.Entry<String, Integer> inc : increments.entrySet()) {
                    if (inc.getValue() == 0) continue;
                    String key = entry.getKey() + ":" + inc.getKey();
                    incrementBufferActive.merge(key, inc.getValue(), Integer::sum);
                    if (owner != null) bufferOwner.put(key, owner);
                }
                logError("Батч-флаш упал для playerId=" + playerId, e);
                continue;
            }

            if (trackDeltas) {
                for (Map.Entry<String, Integer> inc : increments.entrySet()) {
                    int delta = inc.getValue();
                    if (delta == 0) continue;
                    try {
                        statsDAO.recordDelta(playerId, inc.getKey(), delta);
                    } catch (SQLException e) {
                        if (verboseDebug) {
                            plugin.getLogger().warning("[DELTA] Не удалось записать дельту '"
                                    + inc.getKey() + "': " + e.getMessage());
                        }
                    }
                }
            }

            if (owner != null) {
                invalidatePlaceholderCache(owner);
            }
            for (String statKey : increments.keySet()) {
                if (plugin.getTopCache() != null) plugin.getTopCache().invalidate(statKey);
            }
        }

        incrementBufferDraining.clear();
    }

    // ==================== DISTANCE / SPRINT / SNEAK / CLIMB / ELYTRA / BOAT / MINECART ====================

    private void startDistanceFlusher() {
        threadManager.runAsyncRepeating(() -> {
            if (distanceBuffer.isEmpty()) return;
            Map<UUID, Double> snapshot = new ConcurrentHashMap<>(distanceBuffer);

            for (Map.Entry<UUID, Double> entry : snapshot.entrySet()) {
                UUID uuid = entry.getKey();
                int blocks = (int) Math.floor(entry.getValue());
                if (blocks <= 0) {
                    continue;
                }
                try {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;
                    if (isRollbackLocked(player, "distance_walked")) continue;
                    int playerId = resolvePlayerId(uuid, player.getName());
                    statsDAO.incrementStat(playerId, serverId, "distance_walked", blocks);
                    serverStatsDAO.incrementStat(serverId, "distance_walked", blocks);
                    if (trackDeltas) {
                        try {
                            statsDAO.recordDelta(playerId, "distance_walked", blocks);
                        } catch (SQLException e) {
                            if (verboseDebug) {
                                plugin.getLogger().warning("[DELTA] distance_walked: " + e.getMessage());
                            }
                        }
                    }
                    invalidatePlaceholderCache(uuid);
                    distanceBuffer.merge(uuid, -(double) blocks, Double::sum);
                } catch (SQLException e) {
                    logError("Ошибка distance_walked (will retry)", e);
                }
            }
            distanceBuffer.entrySet().removeIf(e -> e.getValue() <= 0);
        }, 30, 30);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        double distance = from.distance(to);
        if (distance <= 0 || distance > 10) return;

        var stats = cfg().tracking().stats();

        if (stats.distanceWalked()) {
            distanceBuffer.merge(player.getUniqueId(), distance, Double::sum);
        }
        if (player.isSprinting()) {
            handleStatEvent(player, "sprint_distance", (int) Math.round(distance * 100));
        }
        if (player.isSneaking()) {
            handleStatEvent(player, "sneak_distance", (int) Math.round(distance * 100));
        }
        if (player.isClimbing()) {
            handleStatEvent(player, "climbed_ladders", (int) Math.round(distance * 100));
        }
        if (player.isGliding()) {
            handleStatEvent(player, "elytra_distance", (int) Math.round(distance * 100));
        }
        if (player.getVehicle() instanceof Boat) {
            handleStatEvent(player, "boat_distance", (int) Math.round(distance * 100));
        }
        if (player.getVehicle() instanceof Minecart) {
            handleStatEvent(player, "minecart_distance", (int) Math.round(distance * 100));
        }
    }

    // ==================== JUMPS ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "jumps");
    }

    // ==================== SNEAK / SPRINT TIMERS ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        UUID uuid = player.getUniqueId();
        if (event.isSneaking()) {
            sneakStart.put(uuid, System.currentTimeMillis());
        } else {
            Long start = sneakStart.remove(uuid);
            if (start != null) {
                long seconds = (System.currentTimeMillis() - start) / 1000;
                if (seconds > 0) handleStatEvent(player, "sneak_time", (int) seconds);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        UUID uuid = player.getUniqueId();
        if (event.isSprinting()) {
            sprintStart.put(uuid, System.currentTimeMillis());
        } else {
            Long start = sprintStart.remove(uuid);
            if (start != null) {
                long seconds = (System.currentTimeMillis() - start) / 1000;
                if (seconds > 0) handleStatEvent(player, "sprint_time", (int) seconds);
            }
        }
    }

    // ==================== DAMAGE ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (cfg().tracking().stats().damageDealt()
                && event.getDamager() instanceof Player attacker
                && shouldCount(attacker)) {
            int damage = (int) Math.round(event.getFinalDamage());
            if (damage > 0) handleStatEvent(attacker, "damage_dealt", damage);
        }
        if (cfg().tracking().stats().damageTaken()
                && event.getEntity() instanceof Player victim
                && shouldCount(victim)) {
            int damage = (int) Math.round(event.getFinalDamage());
            if (damage > 0) handleStatEvent(victim, "damage_taken", damage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageBlock(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!shouldCount(player)) return;
        if (!player.isBlocking()) return;
        if (event.getDamage() > event.getFinalDamage()) {
            int blocked = (int) Math.round(event.getDamage() - event.getFinalDamage());
            if (blocked > 0) handleStatEvent(player, "damage_blocked", blocked);
        }
    }

    // ==================== CRAFT / CONSUME / ARROWS / FISH / TAME / BREED / TP / LOGIN ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!cfg().tracking().stats().itemsCrafted()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!shouldCount(player)) return;
        int amount = getCraftAmount(event);
        if (amount > 0) handleStatEvent(player, "items_crafted", amount);
    }

    private int getCraftAmount(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();
        if (event.isShiftClick()) {
            int max = result.getMaxStackSize();
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item != null && !item.getType().isAir()) max = Math.min(max, item.getAmount());
            }
            amount *= max;
        }
        return amount;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        Material type = event.getItem().getType();

        if (cfg().tracking().stats().foodConsumed() && type.isEdible()) {
            handleStatEvent(player, "food_consumed", 1);
        }

        if (type == Material.POTION || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION) {
            if (event.getItem().getItemMeta() instanceof PotionMeta) {
                handleStatEvent(player, "potions_drunk");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!cfg().tracking().stats().arrowsShot()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!shouldCount(player)) return;
        handleStatEvent(player, "arrows_shot", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!cfg().tracking().stats().fishCaught()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "fish_caught", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!cfg().tracking().stats().entitiesTamed()) return;
        if (!(event.getOwner() instanceof Player player)) return;
        if (!shouldCount(player)) return;
        handleStatEvent(player, "entities_tamed", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!cfg().tracking().stats().entitiesBred()) return;
        if (!(event.getBreeder() instanceof Player player)) return;
        if (!shouldCount(player)) return;
        handleStatEvent(player, "entities_bred", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        if (cfg().tracking().stats().teleports()) handleStatEvent(player, "teleports", 1);
        if (cfg().tracking().stats().portalUses()) {
            var cause = event.getCause();
            if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                    || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                    || cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
                handleStatEvent(player, "portal_uses", 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg().tracking().stats().logins()) return;
        Player player = event.getPlayer();
        if (isRollbackLocked(player, "logins")) return;
        handleStatEvent(player, "logins", 1);
    }

    // ==================== ENCHANT / BREAK ITEM / SHEAR / TRADE ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "items_enchanted");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "items_broken");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "animals_sheared");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof MerchantInventory)) return;
        if (!shouldCount(player)) return;
        if (event.getRawSlot() == 2 && event.getCurrentItem() != null
                && !event.getCurrentItem().getType().isAir()) {
            handleStatEvent(player, "villager_trades");
        }
    }

    // ==================== SLEEP / COMMANDS / CHAT ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        // 1.21.11: BedEnterResult.OK — единственный «успешный» исход.
        // ignoreCancelled + OK-фильтр отсекают «не удалось лечь».
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "sleeps");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        // Не считаем команды самого плагина (открытие /stats, /top и т.п. раздувает commands_used).
        String message = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.startsWith("/playerstats")
                || message.startsWith("/ps ")
                || message.equals("/ps")
                || message.startsWith("/stats")
                || message.startsWith("/top")
                || message.startsWith("/share")
                || message.startsWith("/compare")
                || message.startsWith("/serverstats")
                || message.startsWith("/exclude")) {
            return;
        }

        handleStatEvent(player, "commands_used");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // 1.21.11: AsyncPlayerChatEvent deprecated, используем Paper AsyncChatEvent.
        // getPlayer() возвращает Player (Adventure Identity), поток — async.
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        handleStatEvent(player, "chat_messages");
    }

    // ==================== TOTEM / EXP / LEVELS / BREW ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!shouldCount(player)) return;
        handleStatEvent(player, "totems_used");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        int amount = event.getAmount();
        if (amount > 0) handleStatEvent(player, "experience_gained", amount);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;
        int diff = event.getNewLevel() - event.getOldLevel();
        if (diff > 0) handleStatEvent(player, "levels_gained", diff);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Location loc = event.getBlock().getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Player nearest = null;
        double best = 9.0;
        for (Player p : world.getPlayers()) {
            if (!shouldCount(p)) continue;
            double d = p.getLocation().distanceSquared(loc);
            if (d < best) { best = d; nearest = p; }
        }
        if (nearest != null) handleStatEvent(nearest, "potions_brewed");
    }

    // ==================== INTERACT (cakes / tnt / bell / dolphin) ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!shouldCount(player)) return;

        Material type = block.getType();

        if (type == Material.CAKE) {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Cake cake
                    && cake.getBites() < cake.getMaximumBites()) {
                handleStatEvent(player, "cakes_eaten");
            }
            return;
        }

        if (type == Material.BELL) {
            handleStatEvent(player, "bells_rung");
            return;
        }

        if (type == Material.TNT) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.FLINT_AND_STEEL
                    || hand.getType() == Material.FIRE_CHARGE) {
                handleStatEvent(player, "tnt_ignited");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        Entity target = event.getRightClicked();

        if (target.getType() == EntityType.DOLPHIN) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isEdible()
                    && Tag.ITEMS_FISHES.isTagged(hand.getType())) {
                handleStatEvent(player, "dolphins_fed");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getEntity().getType() != EntityType.SNOW_GOLEM) return;
        Player player = event.getPlayer();
        if (player == null || !shouldCount(player)) return;
        handleStatEvent(player, "snow_golems_built");
    }

    // ==================== DELTA CLEANUP ====================

    private void startDeltasCleanup() {
        threadManager.runAsyncRepeating(() -> {
            try {
                int removed = statsDAO.cleanupDeltasOlderThan(DELTAS_TTL_MS);
                if (removed > 0 && verboseDebug) {
                    plugin.getLogger().info("[DELTA] Очищено " + removed + " старых записей.");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[DELTA] Ошибка очистки stat_deltas: " + e.getMessage());
            }
        }, DELTAS_CLEANUP_INITIAL_SECONDS, DELTAS_CLEANUP_PERIOD_SECONDS);
    }

    // ==================== UTIL ====================

    private void invalidatePlaceholderCache(UUID uuid) {
        var hooks = plugin.getHooksManager();
        if (hooks != null && hooks.getPlaceholderAPIHook() != null) {
            hooks.getPlaceholderAPIHook().invalidate(uuid);
        }
    }

    private boolean isBossMob(Entity entity) {
        if (BOSS_TYPES.contains(entity.getType())) return true;
        String name = entity.getCustomName();
        if (name == null) return false;
        for (String keyword : cfg().tracking().mobBossKeywords()) {
            if (name.toLowerCase().contains(keyword.toLowerCase())) return true;
        }
        return false;
    }

    private void logError(String message, Throwable e) {
        if (e == null) {
            plugin.getLogger().severe(message);
        } else {
            plugin.getLogger().log(Level.SEVERE, message, e);
        }
    }
}