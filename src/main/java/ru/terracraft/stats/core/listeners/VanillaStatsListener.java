package ru.terracraft.stats.core.listeners;

import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import ru.terracraft.stats.PlayerStatsPlugin;

import java.util.Set;

/**
 * Единый листенер для всех ванильных стат.
 * <p>Покрывает:
 * <ul>
 *   <li>все {@link Statistic}, которые можно маппить на наш statKey</li>
 *   <li>advancements_completed</li>
 *   <li>crops_harvested (через {@link PlayerHarvestBlockEvent})</li>
 *   <li>elite_mobs_killed</li>
 * </ul>
 * <p>НЕ дублирует то, что уже есть в {@link StatsListener} — только то, чего там нет.
 * <p>Версия Paper: 26.3+ / Java 25. Используются только актуальные имена {@link Statistic}.
 */
public class VanillaStatsListener implements Listener {

    /** Элитные мобы (бывшие «боссы»). */
    private static final Set<EntityType> ELITE_MOBS = Set.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.ELDER_GUARDIAN,
            EntityType.WARDEN
    );

    private final PlayerStatsPlugin plugin;

    public VanillaStatsListener(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== 1. Универсальный маппинг Statistic → statKey ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStatisticIncrement(PlayerStatisticIncrementEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        String statKey = mapStatistic(event.getStatistic());
        if (statKey == null) return;

        int delta = event.getNewValue() - event.getPreviousValue();
        if (delta > 0) handleStat(player, statKey, delta);
    }

    private String mapStatistic(Statistic statistic) {
        // Paper 26.3+ / Java 25: актуальные имена
        return switch (statistic) {
            case JUMP                   -> "jumps";
            case WALK_ONE_CM            -> "distance_walked";
            case SPRINT_ONE_CM          -> "sprint_distance";
            case CROUCH_ONE_CM          -> "sneak_distance";
            case AVIATE_ONE_CM          -> "elytra_distance";
            case BOAT_ONE_CM            -> "boat_distance";
            case MINECART_ONE_CM        -> "minecart_distance";
            case CLIMB_ONE_CM           -> "climbed_ladders";
            case ANIMALS_BRED           -> "entities_bred";
            case FISH_CAUGHT            -> "fish_caught";
            case ITEM_ENCHANTED         -> "items_enchanted";
            case PLAY_ONE_MINUTE        -> "play_time";
            case RAID_WIN               -> "raids_won";
            case SLEEP_IN_BED           -> "sleeps";
            case BELL_RING              -> "bells_rung";
            case TARGET_HIT             -> "arrows_shot";        // все попадания
            case INTERACT_WITH_ANVIL    -> "anvils_used";
            case TRADED_WITH_VILLAGER   -> "villager_trades";
            default -> null;
        };
    }

    // ==================== 2. Выполненные advancements ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        // Не считаем собственные advancements плагина
        String key = event.getAdvancement().getKey().getKey();
        if (key.startsWith("aach_") || key.startsWith("advanced_achievements")) return;

        handleStat(player, "advancements_completed", 1);
    }

    // ==================== 3. Сбор урожая ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (!shouldCount(player)) return;

        int total = event.getItemsHarvested().stream()
                .mapToInt(item -> item.getAmount())
                .sum();
        if (total > 0) handleStat(player, "crops_harvested", total);
    }

    // ==================== 4. Элитные мобы ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEliteMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !shouldCount(killer)) return;

        if (ELITE_MOBS.contains(event.getEntity().getType())) {
            handleStat(killer, "elite_mobs_killed", 1);
        }
    }

    // ==================== Хелперы ====================

    private boolean shouldCount(Player player) {
        // CREATIVE/SPECTATOR не считаем
        var mode = player.getGameMode();
        return mode != org.bukkit.GameMode.CREATIVE
                && mode != org.bukkit.GameMode.SPECTATOR;
    }

    private void handleStat(Player player, String statKey, int amount) {
        var statsListener = plugin.getStatsListener();
        if (statsListener != null) {
            statsListener.triggerStatEvent(player, statKey, amount);
        }
    }
}