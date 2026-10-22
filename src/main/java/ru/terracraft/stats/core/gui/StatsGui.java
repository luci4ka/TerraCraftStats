package ru.terracraft.stats.core.gui;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.database.dao.PlayerDAO;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO;
import ru.terracraft.stats.core.database.dao.PlayerStatsDAO.StatValue;
import ru.terracraft.stats.core.utils.FormatUtil;
import ru.terracraft.stats.core.utils.Sparkline;
import ru.terracraft.stats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatsGui {

    private static final int PAGE_SIZE = 45;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final StatValue EMPTY = new StatValue(0, 0.0, null);

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;

    public StatsGui(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void open(Player player) {
        openPage(player, 0, null);
    }

    public void openPage(Player player, int page, String subPage) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                PlayerDAO playerDAO = plugin.getPlayerDAO();
                PlayerStatsDAO statsDAO = plugin.getStatsDAO();
                int serverId = plugin.getServerId();

                int playerId = playerDAO.getOrCreatePlayerId(
                        player.getUniqueId(), player.getName());

                Map<String, StatValue> allStats = statsDAO.getAllStatsDetailed(playerId, serverId);

                // === Статы ===
                int blocksPlaced = intOf(allStats, "blocks_placed");
                int blocksBroken = intOf(allStats, "blocks_broken");
                int mobsKilled = intOf(allStats, "mobs_killed");
                int bossesKilled = intOf(allStats, "bosses_killed");
                int playTimeSec = intOf(allStats, "play_time");
                int kills = intOf(allStats, "kills");
                int deaths = intOf(allStats, "deaths");
                int killstreak = intOf(allStats, "killstreak");
                int bestKillstreak = intOf(allStats, "best_killstreak");
                int distanceWalked = intOf(allStats, "distance_walked");
                int damageDealt = intOf(allStats, "damage_dealt");
                int damageTaken = intOf(allStats, "damage_taken");
                int itemsCrafted = intOf(allStats, "items_crafted");
                int foodConsumed = intOf(allStats, "food_consumed");
                int arrowsShot = intOf(allStats, "arrows_shot");
                int fishCaught = intOf(allStats, "fish_caught");
                int entitiesTamed = intOf(allStats, "entities_tamed");
                int entitiesBred = intOf(allStats, "entities_bred");
                int teleports = intOf(allStats, "teleports");
                int portalUses = intOf(allStats, "portal_uses");
                int logins = intOf(allStats, "logins");

                int jumps = intOf(allStats, "jumps");
                int sneakTime = intOf(allStats, "sneak_time");
                int sprintTime = intOf(allStats, "sprint_time");
                int elytraDistance = intOf(allStats, "elytra_distance");
                int boatDistance = intOf(allStats, "boat_distance");
                int minecartDistance = intOf(allStats, "minecart_distance");
                int damageBlocked = intOf(allStats, "damage_blocked");
                int itemsEnchanted = intOf(allStats, "items_enchanted");
                int itemsBroken = intOf(allStats, "items_broken");
                int animalsSheared = intOf(allStats, "animals_sheared");
                int villagerTrades = intOf(allStats, "villager_trades");
                int cropsHarvested = intOf(allStats, "crops_harvested");
                int cropsPlanted = intOf(allStats, "crops_planted");
                int sleeps = intOf(allStats, "sleeps");
                int commandsUsed = intOf(allStats, "commands_used");
                int chatMessages = intOf(allStats, "chat_messages");
                int totemsUsed = intOf(allStats, "totems_used");
                int experienceGained = intOf(allStats, "experience_gained");
                int levelsGained = intOf(allStats, "levels_gained");
                int potionsBrewed = intOf(allStats, "potions_brewed");
                int potionsDrunk = intOf(allStats, "potions_drunk");
                int signsPlaced = intOf(allStats, "signs_placed");
                int bedsPlaced = intOf(allStats, "beds_placed");
                int tntIgnited = intOf(allStats, "tnt_ignited");
                int cakesEaten = intOf(allStats, "cakes_eaten");
                int snowGolemsBuilt = intOf(allStats, "snow_golems_built");
                int diamondsMined = intOf(allStats, "diamonds_mined");
                int netheriteMined = intOf(allStats, "netherite_mined");
                int sprintDistance = intOf(allStats, "sprint_distance");
                int sneakDistance = intOf(allStats, "sneak_distance");
                int climbedLadders = intOf(allStats, "climbed_ladders");
                int bellsRung = intOf(allStats, "bells_rung");
                int dolphinsFed = intOf(allStats, "dolphins_fed");

                double balance = doubleOf(allStats, "balance");

                String group = plugin.getGroupService().getPlayerGroup(player, playerId);

                Map<String, Integer> mobStats = prefixOf(allStats, "mob_killed_");
                Map<String, Integer> blocksPlacedStats = prefixOf(allStats, "block_placed_");
                Map<String, Integer> blocksBrokenStats = prefixOf(allStats, "block_broken_");
                Map<String, Integer> pvpStats = prefixOf(allStats, "pvp_kill_");

                Map<String, String> pvpNames = new HashMap<>();
                for (String key : pvpStats.keySet()) {
                    String uuid = key.substring("pvp_kill_".length());
                    pvpNames.put(uuid, resolvePlayerNameAsync(uuid));
                }

                final Map<String, String> pvpNamesFinal = pvpNames;

                // === Bar chart: подгружаем историю для нужной подстраницы ===
                List<Integer> historyForChart = List.of();
                if (subPage != null) {
                    String statForHistory = switch (subPage) {
                        case "killstreak" -> "killstreak";
                        case "damage" -> "damage_dealt";
                        case "movement" -> "distance_walked";
                        case "craft" -> "items_crafted";
                        case "arrows" -> "arrows_shot";
                        case "animals" -> "entities_tamed";
                        case "actions" -> "jumps";
                        case "combat" -> "totems_used";
                        case "block_extra" -> "diamonds_mined";
                        case "distance" -> "distance_walked";
                        default -> null;
                    };
                    if (statForHistory != null) {
                        historyForChart = loadHistory(playerId, statForHistory);
                    }
                }
                final List<Integer> historyFinal = historyForChart;

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    StatsGuiHolder holder = new StatsGuiHolder(page, subPage);

                    Inventory inv = Bukkit.createInventory(holder, 54,
                            lang.getComponent(player, "stats-gui.title"));
                    holder.setInventory(inv);

                    fillBorder(inv);

                    ItemStack mainTab = createItem(player, Material.NETHER_STAR,
                            "stats-gui.tab-main", "stats-gui.tab-main-hover");
                    if (subPage == null) addGlow(mainTab);
                    inv.setItem(0, mainTab);

                    ItemStack pvpTab = createItem(player, Material.IRON_SWORD,
                            "stats-gui.tab-pvp", "stats-gui.tab-pvp-hover");
                    if ("pvp".equals(subPage)) addGlow(pvpTab);
                    inv.setItem(1, pvpTab);

                    if (page == 0 && subPage == null) {
                        openMainPage(player, inv,
                                blocksPlaced, blocksBroken,
                                mobsKilled, bossesKilled,
                                playTimeSec, kills, deaths, balance, group,
                                killstreak, bestKillstreak, distanceWalked,
                                damageDealt, damageTaken,
                                itemsCrafted, foodConsumed, arrowsShot,
                                fishCaught, entitiesTamed, entitiesBred,
                                teleports, portalUses, logins,
                                pvpStats,
                                jumps, sneakTime, sprintTime,
                                elytraDistance, boatDistance, minecartDistance,
                                damageBlocked, itemsEnchanted, itemsBroken,
                                animalsSheared, villagerTrades,
                                cropsHarvested, cropsPlanted, sleeps,
                                commandsUsed, chatMessages, totemsUsed,
                                experienceGained, levelsGained,
                                potionsBrewed, potionsDrunk,
                                signsPlaced, bedsPlaced, tntIgnited, cakesEaten,
                                snowGolemsBuilt, diamondsMined, netheriteMined,
                                sprintDistance, sneakDistance, climbedLadders,
                                bellsRung, dolphinsFed
                        );
                    } else if ("pvp".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-pvp-title",
                                pvpStats, "pvp_kill_", Material.PLAYER_HEAD, true, pvpNamesFinal);
                    } else if ("mobs".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-mobs-title",
                                mobStats, "mob_killed_", Material.DIAMOND_SWORD, false, Map.of());
                    } else if ("blocks_placed".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-blocks-placed-title",
                                blocksPlacedStats, "block_placed_", Material.BRICKS, false, Map.of());
                    } else if ("blocks_broken".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-blocks-broken-title",
                                blocksBrokenStats, "block_broken_", Material.IRON_PICKAXE, false, Map.of());
                    } else if ("killstreak".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-killstreak-title",
                                Material.BLAZE_POWDER,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-current",
                                                "value", killstreak),
                                        lang.getComponent(player, "stats-gui.sub-stat-best",
                                                "value", bestKillstreak)
                                ),
                                "stats-gui.sub-killstreak-desc",
                                historyFinal);
                    } else if ("distance".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-distance-title",
                                Material.LEATHER_BOOTS,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-current",
                                                "value", FormatUtil.formatNumber(distanceWalked)),
                                        lang.getComponent(player, "stats-gui.sub-stat-sprint-distance",
                                                "value", formatCm(sprintDistance)),
                                        lang.getComponent(player, "stats-gui.sub-stat-sneak-distance",
                                                "value", formatCm(sneakDistance)),
                                        lang.getComponent(player, "stats-gui.sub-stat-climbed",
                                                "value", formatCm(climbedLadders)),
                                        lang.getComponent(player, "stats-gui.sub-stat-elytra",
                                                "value", formatCm(elytraDistance)),
                                        lang.getComponent(player, "stats-gui.sub-stat-boat",
                                                "value", formatCm(boatDistance)),
                                        lang.getComponent(player, "stats-gui.sub-stat-minecart",
                                                "value", formatCm(minecartDistance))
                                ),
                                "stats-gui.sub-distance-desc",
                                historyFinal);
                    } else if ("damage".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-damage-title",
                                Material.DIAMOND_SWORD,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-dealt",
                                                "value", FormatUtil.formatNumber(damageDealt)),
                                        lang.getComponent(player, "stats-gui.sub-stat-taken",
                                                "value", FormatUtil.formatNumber(damageTaken)),
                                        lang.getComponent(player, "stats-gui.sub-stat-blocked",
                                                "value", FormatUtil.formatNumber(damageBlocked)),
                                        lang.getComponent(player, "stats-gui.sub-stat-kd",
                                                "value", FormatUtil.formatKD(lang, player, kills, deaths))
                                ),
                                "stats-gui.sub-damage-desc",
                                historyFinal);
                    } else if ("craft".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-craft-title",
                                Material.CRAFTING_TABLE,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-crafted",
                                                "value", FormatUtil.formatNumber(itemsCrafted)),
                                        lang.getComponent(player, "stats-gui.sub-stat-food",
                                                "value", FormatUtil.formatNumber(foodConsumed)),
                                        lang.getComponent(player, "stats-gui.sub-stat-enchanted",
                                                "value", FormatUtil.formatNumber(itemsEnchanted)),
                                        lang.getComponent(player, "stats-gui.sub-stat-potions-brewed",
                                                "value", FormatUtil.formatNumber(potionsBrewed)),
                                        lang.getComponent(player, "stats-gui.sub-stat-potions-drunk",
                                                "value", FormatUtil.formatNumber(potionsDrunk)),
                                        lang.getComponent(player, "stats-gui.sub-stat-cakes",
                                                "value", FormatUtil.formatNumber(cakesEaten)),
                                        lang.getComponent(player, "stats-gui.sub-stat-items-broken",
                                                "value", FormatUtil.formatNumber(itemsBroken))
                                ),
                                "stats-gui.sub-craft-desc",
                                historyFinal);
                    } else if ("arrows".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-arrows-title",
                                Material.BOW,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-arrows",
                                                "value", FormatUtil.formatNumber(arrowsShot)),
                                        lang.getComponent(player, "stats-gui.sub-stat-fish",
                                                "value", FormatUtil.formatNumber(fishCaught))
                                ),
                                "stats-gui.sub-arrows-desc",
                                historyFinal);
                    } else if ("animals".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-animals-title",
                                Material.BONE,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-tamed",
                                                "value", FormatUtil.formatNumber(entitiesTamed)),
                                        lang.getComponent(player, "stats-gui.sub-stat-bred",
                                                "value", FormatUtil.formatNumber(entitiesBred)),
                                        lang.getComponent(player, "stats-gui.sub-stat-sheared",
                                                "value", FormatUtil.formatNumber(animalsSheared))
                                ),
                                "stats-gui.sub-animals-desc",
                                historyFinal);
                    } else if ("movement".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-movement-title",
                                Material.ENDER_PEARL,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-teleports",
                                                "value", FormatUtil.formatNumber(teleports)),
                                        lang.getComponent(player, "stats-gui.sub-stat-portals",
                                                "value", FormatUtil.formatNumber(portalUses))
                                ),
                                "stats-gui.sub-movement-desc",
                                historyFinal);
                    } else if ("economy".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-economy-title",
                                Material.GOLD_INGOT,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-current",
                                                "value", FormatUtil.formatBalance(plugin, lang,
                                                        player, balance))
                                ),
                                "stats-gui.sub-economy-desc",
                                historyFinal);
                    } else if ("actions".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-actions-title",
                                Material.FEATHER,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-jumps",
                                                "value", FormatUtil.formatNumber(jumps)),
                                        lang.getComponent(player, "stats-gui.sub-stat-sneak-time",
                                                "value", formatDuration(sneakTime)),
                                        lang.getComponent(player, "stats-gui.sub-stat-sprint-time",
                                                "value", formatDuration(sprintTime)),
                                        lang.getComponent(player, "stats-gui.sub-stat-sleeps",
                                                "value", FormatUtil.formatNumber(sleeps)),
                                        lang.getComponent(player, "stats-gui.sub-stat-bells",
                                                "value", FormatUtil.formatNumber(bellsRung)),
                                        lang.getComponent(player, "stats-gui.sub-stat-commands",
                                                "value", FormatUtil.formatNumber(commandsUsed)),
                                        lang.getComponent(player, "stats-gui.sub-stat-chat",
                                                "value", FormatUtil.formatNumber(chatMessages)),
                                        lang.getComponent(player, "stats-gui.sub-stat-trades",
                                                "value", FormatUtil.formatNumber(villagerTrades)),
                                        lang.getComponent(player, "stats-gui.sub-stat-exp",
                                                "value", FormatUtil.formatNumber(experienceGained)),
                                        lang.getComponent(player, "stats-gui.sub-stat-levels",
                                                "value", FormatUtil.formatNumber(levelsGained))
                                ),
                                "stats-gui.sub-actions-desc",
                                historyFinal);
                    } else if ("combat".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-combat-title",
                                Material.SHIELD,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-blocked",
                                                "value", FormatUtil.formatNumber(damageBlocked)),
                                        lang.getComponent(player, "stats-gui.sub-stat-totems",
                                                "value", FormatUtil.formatNumber(totemsUsed)),
                                        lang.getComponent(player, "stats-gui.sub-stat-dolphins",
                                                "value", FormatUtil.formatNumber(dolphinsFed))
                                ),
                                "stats-gui.sub-combat-desc",
                                historyFinal);
                    } else if ("block_extra".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-block-extra-title",
                                Material.DIAMOND_ORE,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-crops-harvested",
                                                "value", FormatUtil.formatNumber(cropsHarvested)),
                                        lang.getComponent(player, "stats-gui.sub-stat-crops-planted",
                                                "value", FormatUtil.formatNumber(cropsPlanted)),
                                        lang.getComponent(player, "stats-gui.sub-stat-diamonds",
                                                "value", FormatUtil.formatNumber(diamondsMined)),
                                        lang.getComponent(player, "stats-gui.sub-stat-netherite",
                                                "value", FormatUtil.formatNumber(netheriteMined)),
                                        lang.getComponent(player, "stats-gui.sub-stat-signs",
                                                "value", FormatUtil.formatNumber(signsPlaced)),
                                        lang.getComponent(player, "stats-gui.sub-stat-beds",
                                                "value", FormatUtil.formatNumber(bedsPlaced)),
                                        lang.getComponent(player, "stats-gui.sub-stat-tnt",
                                                "value", FormatUtil.formatNumber(tntIgnited)),
                                        lang.getComponent(player, "stats-gui.sub-stat-snow-golems",
                                                "value", FormatUtil.formatNumber(snowGolemsBuilt))
                                ),
                                "stats-gui.sub-block-extra-desc",
                                historyFinal);
                    }

                    if (subPage != null) {
                        inv.setItem(49, createItem(player, Material.ARROW,
                                "stats-gui.back", "stats-gui.back-hover"));
                    } else {
                        inv.setItem(49, createItem(player, Material.BARRIER,
                                "stats-gui.close", "stats-gui.close-hover"));
                    }

                    player.openInventory(inv);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка GUI для " + player.getName() + ": " + e.getMessage());
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(lang.getComponent(player,
                                "stats-gui.error-loading"));
                    }
                });
            }
        });
    }

    private void openMainPage(Player viewer, Inventory inv,
                              int blocksPlaced, int blocksBroken,
                              int mobsKilled, int bossesKilled,
                              int playTimeSec, int kills, int deaths,
                              double balance, String group,
                              int killstreak, int bestKillstreak, int distanceWalked,
                              int damageDealt, int damageTaken,
                              int itemsCrafted, int foodConsumed, int arrowsShot,
                              int fishCaught, int entitiesTamed, int entitiesBred,
                              int teleports, int portalUses, int logins,
                              Map<String, Integer> pvpStats,
                              int jumps, int sneakTime, int sprintTime,
                              int elytraDistance, int boatDistance, int minecartDistance,
                              int damageBlocked, int itemsEnchanted, int itemsBroken,
                              int animalsSheared, int villagerTrades,
                              int cropsHarvested, int cropsPlanted, int sleeps,
                              int commandsUsed, int chatMessages, int totemsUsed,
                              int experienceGained, int levelsGained,
                              int potionsBrewed, int potionsDrunk,
                              int signsPlaced, int bedsPlaced, int tntIgnited, int cakesEaten,
                              int snowGolemsBuilt, int diamondsMined, int netheriteMined,
                              int sprintDistance, int sneakDistance, int climbedLadders,
                              int bellsRung, int dolphinsFed) {

        // === Заголовок: голова игрока + сводка ===
        inv.setItem(4, createHeader(viewer, kills, deaths, playTimeSec, group,
                blocksPlaced + blocksBroken));

        inv.setItem(10, createItem(viewer, Material.BRICKS,
                "stats-gui.cat-blocks", "stats-gui.cat-blocks-lore",
                "placed", blocksPlaced,
                "broken", blocksBroken,
                "total", blocksPlaced + blocksBroken));

        inv.setItem(12, createItem(viewer, Material.ZOMBIE_HEAD,
                "stats-gui.cat-pve", "stats-gui.cat-pve-lore",
                "mobs", mobsKilled,
                "bosses", bossesKilled));

        inv.setItem(14, createItem(viewer, Material.CLOCK,
                "stats-gui.cat-time", "stats-gui.cat-time-lore",
                "time", TimeUtil.formatTime(lang, viewer, playTimeSec),
                "logins", logins));

        int pvpTotal = pvpStats.values().stream().mapToInt(Integer::intValue).sum();
        String pvpLoreKey = pvpTotal > 0
                ? "stats-gui.cat-pvp-lore-with-players"
                : "stats-gui.cat-pvp-lore";

        inv.setItem(16, createItem(viewer, Material.NETHERITE_SWORD,
                "stats-gui.cat-pvp", pvpLoreKey,
                "kills", kills,
                "deaths", deaths,
                "kd", FormatUtil.formatKD(lang, viewer, kills, deaths),
                "unique", pvpStats.size()));

        inv.setItem(19, createItem(viewer, Material.BLAZE_POWDER,
                "stats-gui.cat-killstreak", "stats-gui.cat-killstreak-lore",
                "current", killstreak,
                "best", bestKillstreak));

        inv.setItem(21, createItem(viewer, Material.LEATHER_BOOTS,
                "stats-gui.cat-distance", "stats-gui.cat-distance-lore",
                "value", FormatUtil.formatNumber(distanceWalked)));

        inv.setItem(23, createItem(viewer, Material.DIAMOND_SWORD,
                "stats-gui.cat-damage", "stats-gui.cat-damage-lore",
                "dealt", FormatUtil.formatNumber(damageDealt),
                "taken", FormatUtil.formatNumber(damageTaken),
                "blocked", FormatUtil.formatNumber(damageBlocked)));

        inv.setItem(25, createItem(viewer, Material.CRAFTING_TABLE,
                "stats-gui.cat-craft", "stats-gui.cat-craft-lore",
                "crafted", FormatUtil.formatNumber(itemsCrafted),
                "food", FormatUtil.formatNumber(foodConsumed),
                "enchanted", FormatUtil.formatNumber(itemsEnchanted),
                "potions", FormatUtil.formatNumber(potionsBrewed)));

        inv.setItem(28, createItem(viewer, Material.BOW,
                "stats-gui.cat-arrows", "stats-gui.cat-arrows-lore",
                "arrows", FormatUtil.formatNumber(arrowsShot),
                "fish", FormatUtil.formatNumber(fishCaught)));

        inv.setItem(30, createItem(viewer, Material.BONE,
                "stats-gui.cat-animals", "stats-gui.cat-animals-lore",
                "tamed", FormatUtil.formatNumber(entitiesTamed),
                "bred", FormatUtil.formatNumber(entitiesBred),
                "sheared", FormatUtil.formatNumber(animalsSheared)));

        inv.setItem(32, createItem(viewer, Material.ENDER_PEARL,
                "stats-gui.cat-movement", "stats-gui.cat-movement-lore",
                "teleports", FormatUtil.formatNumber(teleports),
                "portals", FormatUtil.formatNumber(portalUses),
                "sprint", formatCm(sprintDistance),
                "sneak", formatCm(sneakDistance),
                "ladders", formatCm(climbedLadders)));

        inv.setItem(34, createItem(viewer, Material.GOLD_INGOT,
                "stats-gui.cat-economy", "stats-gui.cat-economy-lore",
                "balance", FormatUtil.formatBalance(plugin, lang, viewer, balance),
                "group", group));

        inv.setItem(37, createItem(viewer, Material.FEATHER,
                "stats-gui.cat-actions", "stats-gui.cat-actions-lore",
                "jumps", FormatUtil.formatNumber(jumps),
                "sleeps", FormatUtil.formatNumber(sleeps),
                "commands", FormatUtil.formatNumber(commandsUsed),
                "chat", FormatUtil.formatNumber(chatMessages)));

        inv.setItem(39, createItem(viewer, Material.SHIELD,
                "stats-gui.cat-combat", "stats-gui.cat-combat-lore",
                "blocked", FormatUtil.formatNumber(damageBlocked),
                "totems", FormatUtil.formatNumber(totemsUsed),
                "dolphins", FormatUtil.formatNumber(dolphinsFed)));

        inv.setItem(41, createItem(viewer, Material.DIAMOND_ORE,
                "stats-gui.cat-block-extra", "stats-gui.cat-block-extra-lore",
                "diamonds", FormatUtil.formatNumber(diamondsMined),
                "netherite", FormatUtil.formatNumber(netheriteMined),
                "crops", FormatUtil.formatNumber(cropsHarvested),
                "snow_golems", FormatUtil.formatNumber(snowGolemsBuilt)));
    }

    private void openSubPage(Player viewer, Inventory inv, int page, String titleKey,
                             Map<String, Integer> stats, String prefix,
                             Material icon, boolean isPlayerHead,
                             Map<String, String> namesByUuid) {
        inv.setItem(4, createItem(viewer, Material.PAPER,
                titleKey, "stats-gui.sub-total-types",
                "count", stats.size()));

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(stats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, sorted.size());

        int slot = 9;
        for (int i = start; i < end && slot < 45; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            String rawKey = entry.getKey().substring(prefix.length());

            if (isPlayerHead) {
                String victimName = namesByUuid.getOrDefault(rawKey, rawKey);
                inv.setItem(slot, createPlayerHead(viewer,
                        rawKey,
                        lang.get(viewer, "stats-gui.sub-player-prefix") + victimName,
                        "stats-gui.sub-player-head-lore",
                        "count", entry.getValue(),
                        "uuid", rawKey));
            } else {
                String displayName = prettify(rawKey);
                inv.setItem(slot, createItem(viewer, icon,
                        lang.get(viewer, "stats-gui.sub-item-prefix") + displayName,
                        "stats-gui.sub-item-lore",
                        "value", entry.getValue(),
                        "key", entry.getKey()));
            }
            slot++;
        }

        int totalPages = (int) Math.ceil((double) sorted.size() / PAGE_SIZE);

        if (page > 0) {
            inv.setItem(48, createItem(viewer, Material.LIME_DYE,
                    "stats-gui.page-prev", "stats-gui.page-prev-hover",
                    "page", page,
                    "total", totalPages));
        }

        if (page < totalPages - 1) {
            inv.setItem(50, createItem(viewer, Material.LIME_DYE,
                    "stats-gui.page-next", "stats-gui.page-next-hover",
                    "page", page + 2,
                    "total", totalPages));
        }
    }

    private void openInfoPage(Player viewer, Inventory inv,
                              String titleKey, Material icon,
                              List<Component> statLines,
                              String descKey,
                              List<Integer> history) {
        inv.setItem(4, createItem(viewer, Material.PAPER,
                titleKey, "stats-gui.sub-empty-lore"));

        inv.setItem(13, createItem(viewer, icon,
                titleKey, "stats-gui.sub-empty-lore"));

        // Строки: слоты 19, 21, 23, 25, 27, 29, 31, 33, 35, 37 — до 10 строк.
        int slot = 19;
        for (Component line : statLines) {
            if (slot > 37) break;
            ItemStack item = new ItemStack(Material.LIME_DYE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.empty());
                meta.lore(List.of(line.decoration(TextDecoration.ITALIC, false)));
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            slot += 2;
        }

        String descRaw = lang.get(viewer, descKey);
        List<String> descLore = new ArrayList<>();
        for (String line : descRaw.split("\n")) {
            descLore.add(line);
        }

        inv.setItem(40, createItem(Material.BOOK,
                lang.get(viewer, "stats-gui.sub-desc-title"),
                descLore));

        // === Bar chart + sparkline ===
        if (history != null && history.size() >= 2) {
            String sparkline = Sparkline.generatePretty(history);
            List<String> chartLore = new ArrayList<>();
            chartLore.add(sparkline);
            chartLore.add("");
            chartLore.addAll(barChartLines(history));

            // Собираем Component-строки
            List<Component> chartComponents = new ArrayList<>();
            for (String s : chartLore) {
                if (s.isEmpty()) {
                    chartComponents.add(Component.empty());
                } else {
                    chartComponents.add(lang.getComponentFromString(s)
                            .decoration(TextDecoration.ITALIC, false));
                }
            }

            ItemStack chartItem = new ItemStack(Material.LIME_DYE);
            ItemMeta chartMeta = chartItem.getItemMeta();
            if (chartMeta != null) {
                chartMeta.displayName(lang.getComponent(viewer, "stats-gui.sparkline-title")
                        .decoration(TextDecoration.ITALIC, false));
                chartMeta.lore(chartComponents);
                chartItem.setItemMeta(chartMeta);
            }
            inv.setItem(42, chartItem);
        }
    }

    // ==================== UTIL ====================

    /** Заполняет рамку (края) чёрным стеклом, оставляя центр пустым. */
    private void fillBorder(Inventory inv) {
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        int[] borderSlots = {
                0,1,2,3,4,5,6,7,8,
                9,17,
                18,26,
                27,35,
                36,44,
                45,46,47,48,49,50,51,52,53
        };
        for (int s : borderSlots) {
            inv.setItem(s, border);
        }
    }

    private ItemStack createHeader(Player viewer, int kills, int deaths,
                                   int playTimeSec, String group, int blocksTotal) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(viewer);

            skull.displayName(lang.getComponent(viewer, "stats-gui.header-name",
                            "player", viewer.getName())
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent(viewer, "stats-gui.header-kd",
                            "value", FormatUtil.formatKD(lang, viewer, kills, deaths))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(lang.getComponent(viewer, "stats-gui.header-time",
                            "value", TimeUtil.formatTime(lang, viewer, playTimeSec))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(lang.getComponent(viewer, "stats-gui.header-blocks",
                            "value", FormatUtil.formatNumber(blocksTotal))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(lang.getComponent(viewer, "stats-gui.header-group",
                            "value", group)
                    .decoration(TextDecoration.ITALIC, false));

            skull.lore(lore);
            head.setItemMeta(skull);
        }
        return head;
    }

    private static void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private String formatCm(int cm) {
        if (cm <= 0) return "0";
        return String.format(java.util.Locale.ROOT, "%.2f", cm / 100.0);
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "0";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return h + "ч " + m + "м";
        if (m > 0) return m + "м " + s + "с";
        return s + "с";
    }

    private ItemStack createItem(Player viewer, Material material,
                                 String nameKey, String loreKey,
                                 Object... lorePlaceholders) {
        String name = lang.get(viewer, nameKey);
        String loreRaw = lang.get(viewer, loreKey);

        for (int i = 0; i < lorePlaceholders.length - 1; i += 2) {
            String placeholder = "{" + lorePlaceholders[i] + "}";
            String value = String.valueOf(lorePlaceholders[i + 1]);
            loreRaw = loreRaw.replace(placeholder, value);
        }

        List<String> lore = new ArrayList<>();
        for (String line : loreRaw.split("\n")) {
            lore.add(line);
        }

        return createItem(material, name, lore);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));

            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    if (line == null || line.isEmpty()) {
                        loreComponents.add(Component.empty());
                    } else {
                        loreComponents.add(MM.deserialize(line)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
                meta.lore(loreComponents);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayerHead(Player viewer, String uuidStr,
                                       String name, String loreKey,
                                       Object... lorePlaceholders) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                skullMeta.setOwningPlayer(offline);
            } catch (IllegalArgumentException ignored) {
            }

            String loreRaw = lang.get(viewer, loreKey);
            for (int i = 0; i < lorePlaceholders.length - 1; i += 2) {
                loreRaw = loreRaw.replace("{" + lorePlaceholders[i] + "}",
                        String.valueOf(lorePlaceholders[i + 1]));
            }

            skullMeta.displayName(MM.deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreRaw.split("\n")) {
                if (line.isEmpty()) {
                    loreComponents.add(Component.empty());
                } else {
                    loreComponents.add(MM.deserialize(line)
                            .decoration(TextDecoration.ITALIC, false));
                }
            }
            skullMeta.lore(loreComponents);

            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private static Map<String, Integer> prefixOf(Map<String, StatValue> all, String prefix) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, StatValue> e : all.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                result.put(e.getKey(), e.getValue().valueInt());
            }
        }
        return result;
    }

    private static int intOf(Map<String, StatValue> all, String key) {
        return all.getOrDefault(key, EMPTY).valueInt();
    }

    private static double doubleOf(Map<String, StatValue> all, String key) {
        return all.getOrDefault(key, EMPTY).valueDouble();
    }

    private String resolvePlayerNameAsync(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            String name = plugin.getPlayerDAO().getPlayerName(uuid);
            if (name != null && !name.isEmpty()) return name;
            return uuidStr.substring(0, 8) + "…";
        } catch (Exception e) {
            return uuidStr;
        }
    }

    private String prettify(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] parts = input.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase())
                    .append(" ");
        }
        return sb.toString().trim();
    }

    // ==================== SPARKLINE / BAR CHART ====================

    /** Загрузить историю для bar chart (список значений, N дней). */
    private List<Integer> loadHistory(int playerId, String statName) {
        try {
            var cm = plugin.getConfigManager();
            if (!cm.main().modules().sparkline() || cm.sparkline() == null) return List.of();

            int statTypeId = plugin.getStatTypeDAO().createOrGetStatType(statName);
            return plugin.getStatHistoryDAO().getHistory(
                    playerId, statTypeId, plugin.getServerId(),
                    cm.sparkline().days());
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка истории для " + statName + ": " + e.getMessage());
            return List.of();
        }
    }

    /** ASCII bar chart по истории: N строк с "████░░░░ N". */
    private List<String> barChartLines(List<Integer> history) {
        if (history == null || history.isEmpty()) return List.of();

        int max = history.stream().mapToInt(Integer::intValue).max().orElse(1);
        if (max <= 0) max = 1;

        List<String> lines = new ArrayList<>();
        for (int value : history) {
            int filled = (int) Math.round((double) value / max * 8);
            int empty = 8 - filled;
            String bar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
            lines.add(bar + " " + value);
        }
        return lines;
    }

    public static class StatsGuiHolder implements InventoryHolder {
        private final int page;
        private final String subPage;
        private Inventory inventory;

        public StatsGuiHolder(int page, String subPage) {
            this.page = page;
            this.subPage = subPage;
        }

        public int getPage() { return page; }
        public String getSubPage() { return subPage; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }
}