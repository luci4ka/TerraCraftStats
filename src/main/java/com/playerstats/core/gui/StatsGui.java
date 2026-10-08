package com.playerstats.core.gui;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.utils.FormatUtil;
import com.playerstats.core.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatsGui {

    private static final int PAGE_SIZE = 45;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Main plugin;
    private final LanguageManager lang;

    public StatsGui(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    // ==================== OPEN ====================

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

                // ==================== Основная статистика ====================
                int blocksPlaced = statsDAO.getStat(playerId, serverId, "blocks_placed");
                int blocksBroken = statsDAO.getStat(playerId, serverId, "blocks_broken");
                int mobsKilled = statsDAO.getStat(playerId, serverId, "mobs_killed");
                int bossesKilled = statsDAO.getStat(playerId, serverId, "bosses_killed");
                int playTimeSec = statsDAO.getStat(playerId, serverId, "play_time");
                int kills = statsDAO.getStat(playerId, serverId, "kills");
                int deaths = statsDAO.getStat(playerId, serverId, "deaths");
                double balance = statsDAO.getStatDouble(playerId, serverId, "balance");
                String group = plugin.getGroupService().getPlayerGroup(player, playerId);

                // ==================== Новые статы ====================
                int killstreak = statsDAO.getStat(playerId, serverId, "killstreak");
                int bestKillstreak = statsDAO.getStat(playerId, serverId, "best_killstreak");
                int distanceWalked = statsDAO.getStat(playerId, serverId, "distance_walked");
                int damageDealt = statsDAO.getStat(playerId, serverId, "damage_dealt");
                int damageTaken = statsDAO.getStat(playerId, serverId, "damage_taken");
                int itemsCrafted = statsDAO.getStat(playerId, serverId, "items_crafted");
                int foodConsumed = statsDAO.getStat(playerId, serverId, "food_consumed");
                int arrowsShot = statsDAO.getStat(playerId, serverId, "arrows_shot");
                int fishCaught = statsDAO.getStat(playerId, serverId, "fish_caught");
                int entitiesTamed = statsDAO.getStat(playerId, serverId, "entities_tamed");
                int entitiesBred = statsDAO.getStat(playerId, serverId, "entities_bred");
                int teleports = statsDAO.getStat(playerId, serverId, "teleports");
                int portalUses = statsDAO.getStat(playerId, serverId, "portal_uses");
                int logins = statsDAO.getStat(playerId, serverId, "logins");

                // ==================== Префиксы ====================
                Map<String, Integer> mobStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "mob_killed_");
                Map<String, Integer> blocksPlacedStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "block_placed_");
                Map<String, Integer> blocksBrokenStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "block_broken_");
                Map<String, Integer> pvpStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "pvp_kill_");

                // ==================== Достижения ====================
                int unlockedAchievements = plugin.getAchievementDAO()
                        .getUnlockedAchievements(playerId).size();
                int totalAchievements = plugin.getAchievementManager().getTotal();

                final int finalUnlockedAch = unlockedAchievements;
                final int finalTotalAch = totalAchievements;

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    StatsGuiHolder holder = new StatsGuiHolder(page, subPage);

                    Inventory inv = Bukkit.createInventory(holder, 54,
                            lang.getComponent(player, "stats-gui.title"));
                    holder.setInventory(inv);

                    // Заполняем стеклом
                    ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
                    for (int i = 0; i < 54; i++) inv.setItem(i, filler);

                    // ==================== ТАБЫ ====================
                    inv.setItem(0, createItem(player, Material.NETHER_STAR,
                            "stats-gui.tab-main", "stats-gui.tab-main-hover"));

                    inv.setItem(1, createItem(player, Material.IRON_SWORD,
                            "stats-gui.tab-pvp", "stats-gui.tab-pvp-hover"));

                    inv.setItem(2, createItem(player, Material.NETHER_STAR,
                            "stats-gui.tab-achievements",
                            "stats-gui.tab-achievements-hover",
                            "unlocked", finalUnlockedAch,
                            "total", finalTotalAch));

                    // ==================== ГЛАВНАЯ ====================
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
                                pvpStats);
                    }
                    // ==================== PvP ====================
                    else if ("pvp".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-pvp-title",
                                pvpStats, "pvp_kill_", Material.PLAYER_HEAD, true);
                    }
                    // ==================== МОБЫ ====================
                    else if ("mobs".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-mobs-title",
                                mobStats, "mob_killed_", Material.DIAMOND_SWORD, false);
                    }
                    // ==================== БЛОКИ ====================
                    else if ("blocks_placed".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-blocks-placed-title",
                                blocksPlacedStats, "block_placed_", Material.BRICKS, false);
                    }
                    else if ("blocks_broken".equals(subPage)) {
                        openSubPage(player, inv, page, "stats-gui.sub-blocks-broken-title",
                                blocksBrokenStats, "block_broken_", Material.IRON_PICKAXE, false);
                    }
                    // ==================== KILLSTREAK ====================
                    else if ("killstreak".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-killstreak-title",
                                Material.BLAZE_POWDER,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-current",
                                                "value", killstreak),
                                        lang.getComponent(player, "stats-gui.sub-stat-best",
                                                "value", bestKillstreak)
                                ),
                                "stats-gui.sub-killstreak-desc");
                    }
                    // ==================== DISTANCE ====================
                    else if ("distance".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-distance-title",
                                Material.LEATHER_BOOTS,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-current",
                                                "value", FormatUtil.formatNumber(distanceWalked))
                                ),
                                "stats-gui.sub-distance-desc");
                    }
                    // ==================== DAMAGE ====================
                    else if ("damage".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-damage-title",
                                Material.DIAMOND_SWORD,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-dealt",
                                                "value", FormatUtil.formatNumber(damageDealt)),
                                        lang.getComponent(player, "stats-gui.sub-stat-taken",
                                                "value", FormatUtil.formatNumber(damageTaken)),
                                        lang.getComponent(player, "stats-gui.sub-stat-kd",
                                                "value", FormatUtil.formatKD(kills, deaths))
                                ),
                                "stats-gui.sub-damage-desc");
                    }
                    // ==================== CRAFT ====================
                    else if ("craft".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-craft-title",
                                Material.CRAFTING_TABLE,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-crafted",
                                                "value", FormatUtil.formatNumber(itemsCrafted)),
                                        lang.getComponent(player, "stats-gui.sub-stat-food",
                                                "value", FormatUtil.formatNumber(foodConsumed))
                                ),
                                "stats-gui.sub-craft-desc");
                    }
                    // ==================== ARROWS ====================
                    else if ("arrows".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-arrows-title",
                                Material.BOW,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-arrows",
                                                "value", FormatUtil.formatNumber(arrowsShot)),
                                        lang.getComponent(player, "stats-gui.sub-stat-fish",
                                                "value", FormatUtil.formatNumber(fishCaught))
                                ),
                                "stats-gui.sub-arrows-desc");
                    }
                    // ==================== ANIMALS ====================
                    else if ("animals".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-animals-title",
                                Material.BONE,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-tamed",
                                                "value", FormatUtil.formatNumber(entitiesTamed)),
                                        lang.getComponent(player, "stats-gui.sub-stat-bred",
                                                "value", FormatUtil.formatNumber(entitiesBred))
                                ),
                                "stats-gui.sub-animals-desc");
                    }
                    // ==================== MOVEMENT ====================
                    else if ("movement".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-movement-title",
                                Material.ENDER_PEARL,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-teleports",
                                                "value", FormatUtil.formatNumber(teleports)),
                                        lang.getComponent(player, "stats-gui.sub-stat-portals",
                                                "value", FormatUtil.formatNumber(portalUses))
                                ),
                                "stats-gui.sub-movement-desc");
                    }
                    // ==================== ECONOMY ====================
                    else if ("economy".equals(subPage)) {
                        openInfoPage(player, inv,
                                "stats-gui.sub-economy-title",
                                Material.GOLD_INGOT,
                                List.of(
                                        lang.getComponent(player, "stats-gui.sub-stat-current",
                                                "value", FormatUtil.formatBalance(plugin, lang,
                                                        player, balance))
                                ),
                                "stats-gui.sub-economy-desc");
                    }

                    // ==================== НИЖНЯЯ ПАНЕЛЬ ====================
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

    // ==================== ГЛАВНАЯ СТРАНИЦА ====================

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
                              Map<String, Integer> pvpStats) {

        // ==================== БЛОКИ ====================
        inv.setItem(10, createItem(viewer, Material.BRICKS,
                "stats-gui.cat-blocks", "stats-gui.cat-blocks-lore",
                "placed", blocksPlaced,
                "broken", blocksBroken,
                "total", blocksPlaced + blocksBroken));

        // ==================== PVE ====================
        inv.setItem(12, createItem(viewer, Material.DIAMOND_SWORD,
                "stats-gui.cat-pve", "stats-gui.cat-pve-lore",
                "mobs", mobsKilled,
                "bosses", bossesKilled));

        // ==================== ВРЕМЯ ====================
        inv.setItem(14, createItem(viewer, Material.CLOCK,
                "stats-gui.cat-time", "stats-gui.cat-time-lore",
                "time", TimeUtil.formatTime(lang, viewer, playTimeSec),
                "logins", logins));

        // ==================== PVP ====================
        int pvpTotal = pvpStats.values().stream().mapToInt(Integer::intValue).sum();
        String pvpLoreKey = pvpTotal > 0
                ? "stats-gui.cat-pvp-lore-with-players"
                : "stats-gui.cat-pvp-lore";

        inv.setItem(16, createItem(viewer, Material.IRON_SWORD,
                "stats-gui.cat-pvp", pvpLoreKey,
                "kills", kills,
                "deaths", deaths,
                "kd", FormatUtil.formatKD(kills, deaths),
                "unique", pvpStats.size()));

        // ==================== KILLSTREAK ====================
        inv.setItem(19, createItem(viewer, Material.BLAZE_POWDER,
                "stats-gui.cat-killstreak", "stats-gui.cat-killstreak-lore",
                "current", killstreak,
                "best", bestKillstreak));

        // ==================== ДИСТАНЦИЯ ====================
        inv.setItem(21, createItem(viewer, Material.LEATHER_BOOTS,
                "stats-gui.cat-distance", "stats-gui.cat-distance-lore",
                "value", FormatUtil.formatNumber(distanceWalked)));

        // ==================== УРОН ====================
        inv.setItem(23, createItem(viewer, Material.DIAMOND_SWORD,
                "stats-gui.cat-damage", "stats-gui.cat-damage-lore",
                "dealt", FormatUtil.formatNumber(damageDealt),
                "taken", FormatUtil.formatNumber(damageTaken)));

        // ==================== КРАФТ ====================
        inv.setItem(25, createItem(viewer, Material.CRAFTING_TABLE,
                "stats-gui.cat-craft", "stats-gui.cat-craft-lore",
                "crafted", FormatUtil.formatNumber(itemsCrafted),
                "food", FormatUtil.formatNumber(foodConsumed)));

        // ==================== СТРЕЛЬБА ====================
        inv.setItem(28, createItem(viewer, Material.BOW,
                "stats-gui.cat-arrows", "stats-gui.cat-arrows-lore",
                "arrows", FormatUtil.formatNumber(arrowsShot),
                "fish", FormatUtil.formatNumber(fishCaught)));

        // ==================== ЖИВОТНЫЕ ====================
        inv.setItem(30, createItem(viewer, Material.BONE,
                "stats-gui.cat-animals", "stats-gui.cat-animals-lore",
                "tamed", FormatUtil.formatNumber(entitiesTamed),
                "bred", FormatUtil.formatNumber(entitiesBred)));

        // ==================== ПЕРЕМЕЩЕНИЕ ====================
        inv.setItem(32, createItem(viewer, Material.ENDER_PEARL,
                "stats-gui.cat-movement", "stats-gui.cat-movement-lore",
                "teleports", FormatUtil.formatNumber(teleports),
                "portals", FormatUtil.formatNumber(portalUses)));

        // ==================== ЭКОНОМИКА ====================
        inv.setItem(34, createItem(viewer, Material.GOLD_INGOT,
                "stats-gui.cat-economy", "stats-gui.cat-economy-lore",
                "balance", FormatUtil.formatBalance(plugin, lang, viewer, balance),
                "group", group));
    }

    // ==================== ПОДСТРАНИЦА (СПИСОК) ====================

    private void openSubPage(Player viewer, Inventory inv, int page, String titleKey,
                             Map<String, Integer> stats, String prefix,
                             Material icon, boolean isPlayerHead) {
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
                String victimName = resolvePlayerName(rawKey);
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

    // ==================== ПОДСТРАНИЦА (ИНФО) ====================

    /**
     * Информационная подстраница (2-4 значения + описание).
     *
     * @param viewer     игрок
     * @param inv        инвентарь
     * @param titleKey   ключ заголовка
     * @param icon       иконка
     * @param statLines  готовые компоненты строк статистики
     * @param descKey    ключ описания
     */
    private void openInfoPage(Player viewer, Inventory inv,
                              String titleKey, Material icon,
                              List<Component> statLines,
                              String descKey) {
        // ==================== ЗАГОЛОВОК ====================
        inv.setItem(4, createItem(viewer, Material.PAPER,
                titleKey, "stats-gui.sub-empty-lore"));

        // ==================== ИКОНКА ====================
        inv.setItem(13, createItem(viewer, icon,
                titleKey, "stats-gui.sub-empty-lore"));

        // ==================== СТРОКИ ====================
        int slot = 20;
        for (Component line : statLines) {
            if (slot >= 26) break;

            ItemStack item = new ItemStack(Material.LIME_DYE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.empty());
                // ✅ line — уже Component, никакой сериализации
                meta.lore(List.of(line.decoration(TextDecoration.ITALIC, false)));
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            slot += 2;
        }

        // ==================== ОПИСАНИЕ ====================
        String descRaw = lang.get(viewer, descKey);
        List<String> descLore = new ArrayList<>();
        for (String line : descRaw.split("\n")) {
            descLore.add(line);  // ✅ raw MiniMessage
        }

        inv.setItem(40, createItem(Material.BOOK,
                lang.get(viewer, "stats-gui.sub-desc-title"),
                descLore));
    }

    // ==================== ITEM HELPERS ====================

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

            // ✅ Adventure API
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

    // ==================== UTIL ====================

    private String resolvePlayerName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName();
            if (name != null && !name.isEmpty()) return name;
            return uuidStr.substring(0, 8) + "…";
        } catch (IllegalArgumentException e) {
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

    // ==================== HOLDER ====================

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