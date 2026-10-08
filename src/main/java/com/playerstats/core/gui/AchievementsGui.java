package com.playerstats.core.gui;

import com.playerstats.Main;
import com.playerstats.core.achievements.Achievement;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import com.playerstats.core.utils.FormatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class AchievementsGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Main plugin;
    private final LanguageManager lang;

    public AchievementsGui(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void open(Player player) {
        plugin.getThreadManager().runAsync(() -> {
            try {
                int playerId = plugin.getPlayerDAO()
                        .getOrCreatePlayerId(player.getUniqueId(), player.getName());

                Collection<Achievement> all = plugin.getAchievementManager().getAll();

                Map<String, Long> unlockedWithDates = plugin.getAchievementDAO()
                        .getUnlockedWithDates(playerId);

                PlayerStatsDAO statsDAO = plugin.getStatsDAO();
                int serverId = plugin.getServerId();

                Map<String, Integer> currentStats = new HashMap<>();
                for (Achievement ac : all) {
                    if (!currentStats.containsKey(ac.stat())) {
                        try {
                            int value = statsDAO.getStat(playerId, serverId, ac.stat());
                            currentStats.put(ac.stat(), value);
                        } catch (SQLException e) {
                            currentStats.put(ac.stat(), 0);
                        }
                    }
                }

                // ==================== RESET TIMES ====================
                // Для временных достижений — получить reset_at из БД.
                Map<String, Long> lastResetMap = new HashMap<>();
                for (Achievement ac : all) {
                    if (!ac.isTemporary()) continue;
                    try {
                        long resetAt = plugin.getAchievementDAO()
                                .getLastReset(playerId, ac.id());
                        lastResetMap.put(ac.id(), resetAt);
                    } catch (SQLException e) {
                        lastResetMap.put(ac.id(), -1L);
                    }
                }

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    AchievementsGuiHolder holder = new AchievementsGuiHolder();
                    Inventory inv = Bukkit.createInventory(holder, 54,
                            lang.getComponent(player, "achievements.title"));
                    holder.setInventory(inv);

                    ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE,
                            " ", null);
                    for (int i = 0; i < 54; i++) inv.setItem(i, filler);

                    // ==================== ЗАГОЛОВОК ====================
                    int unlockedCount = unlockedWithDates.size();
                    int totalCount = all.size();
                    int percent = totalCount > 0 ? (unlockedCount * 100) / totalCount : 0;

                    String bar = buildProgressBar(unlockedCount, totalCount, 20, '▰', '▱');

                    List<String> headerLore = new ArrayList<>();
                    headerLore.add(lang.get(player, "achievements.header.unlocked",
                            "unlocked", unlockedCount,
                            "total", totalCount));
                    headerLore.add(lang.get(player, "achievements.header.percent",
                            "percent", percent));
                    headerLore.add(lang.get(player, "achievements.header.bar",
                            "bar", bar));
                    headerLore.add("");
                    headerLore.add(lang.get(player, "achievements.header.hint"));

                    inv.setItem(4, createItem(Material.NETHER_STAR,
                            lang.get(player, "achievements.header.name"),
                            headerLore));

                    // ==================== СПИСОК ====================
                    int slot = 10;
                    for (Achievement ac : all) {
                        if (slot >= 44) break;
                        if (slot == 17 || slot == 26 || slot == 35) slot += 2;

                        Long unlockTime = unlockedWithDates.get(ac.id());
                        boolean isUnlocked = unlockTime != null;

                        String date = null;
                        if (isUnlocked && unlockTime > 0) {
                            date = formatDate(player, unlockTime);
                        }

                        int current = currentStats.getOrDefault(ac.stat(), 0);
                        long lastReset = lastResetMap.getOrDefault(ac.id(), -1L);

                        inv.setItem(slot, buildAchievementItem(
                                player, ac, isUnlocked, date, current, ac.threshold(), lastReset));
                        slot++;
                    }

                    inv.setItem(49, createItem(Material.ARROW,
                            lang.get(player, "achievements.back"),
                            List.of(lang.get(player, "achievements.back-hover"))));

                    player.openInventory(inv);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка GUI достижений: " + e.getMessage());
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(lang.getComponent(player,
                                "achievements.error-loading"));
                    }
                });
            }
        });
    }

    // ==================== ITEM ====================

    private ItemStack buildAchievementItem(Player viewer, Achievement ac,
                                           boolean unlocked,
                                           String unlockDate, int current, int threshold,
                                           long lastResetAt) {
        Material icon;
        try {
            icon = Material.valueOf(ac.icon().toUpperCase());
        } catch (Exception e) {
            icon = Material.PAPER;
        }

        // Статус
        String status = unlocked
                ? lang.get(viewer, "achievements.status-prefix-unlocked")
                : lang.get(viewer, "achievements.status-prefix-locked");

        String rawName = status + ac.name();

        // Цвет описания — из lang.yml
        String descColor = lang.get(viewer, "achievements.description-color");
        if (descColor.startsWith("<red>Missing key")) {
            descColor = "<gray>";
        }

        List<String> rawLore = new ArrayList<>();

        // Описание
        rawLore.add(descColor + ac.description());
        rawLore.add("");

        // Категория
        rawLore.add(lang.get(viewer, "achievements.category",
                "category", translateCategory(viewer, ac.category())));

        // Прогресс
        int percent = threshold > 0 ? Math.min(100, (current * 100) / threshold) : 0;

        String progressColor;
        if (percent >= 100) progressColor = "<green>";
        else if (percent >= 50) progressColor = "<yellow>";
        else if (percent >= 25) progressColor = "<gold>";
        else progressColor = "<red>";

        rawLore.add(lang.get(viewer, "achievements.progress",
                "color", progressColor,
                "current", current,
                "required", threshold,
                "percent", percent));   // ← добавили {percent}

        rawLore.add(lang.get(viewer, "achievements.progress-bar",
                "bar", buildProgressBar(current, threshold, 20, '▰', '▱', progressColor)));

        // Требование
        rawLore.add(lang.get(viewer, "achievements.requires",
                "stat", translateStat(viewer, ac.stat()),
                "required", threshold));

        rawLore.add("");

        if (unlocked) {
            rawLore.add(lang.get(viewer, "achievements.status-unlocked"));
            if (unlockDate != null) {
                rawLore.add(lang.get(viewer, "achievements.date", "date", unlockDate));
            }
        } else {
            rawLore.add(lang.get(viewer, "achievements.status-locked"));
        }

         // ==================== ВРЕМЕННЫЕ ====================
        if (ac.isTemporary()) {
            rawLore.add("");

            // Тип сброса
            String resetLabel = lang.get(viewer, "achievements.reset." + ac.reset(),
                    "default", ac.reset());
            if (resetLabel.startsWith("<red>Missing key")) {
                resetLabel = ac.reset();
            }

            rawLore.add(lang.get(viewer, "achievements.reset.label",
                    "period", resetLabel));

            // Награда — повторяемая?
            rawLore.add(ac.repeatReward()
                    ? lang.get(viewer, "achievements.reset.reward-repeatable")
                    : lang.get(viewer, "achievements.reset.reward-once"));

            // Осталось до сброса
            if (lastResetAt > 0) {
                long interval = ac.resetIntervalMs();
                long elapsed = System.currentTimeMillis() - lastResetAt;
                long remaining = interval - elapsed;

                if (remaining <= 0) {
                    rawLore.add(lang.get(viewer, "achievements.reset.ready"));
                } else {
                    rawLore.add(lang.get(viewer, "achievements.reset.in",
                            "time", FormatUtil.formatDurationCompact(remaining)));
                }
            } else {
                // Ещё ни разу не сбрасывалось
                rawLore.add(lang.get(viewer, "achievements.reset.not-started"));
            }
        }

        // ✅ Adventure API — передаём RAW MiniMessage, парсинг в createItem
        return createItem(icon, rawName, rawLore);
    }

    /**
     * ✅ Adventure API — правильный способ для Paper.
     *    meta.displayName(Component) + meta.lore(List<Component>)
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Имя
            meta.displayName(MM.deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));

            // Lore
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

    // ==================== DATE ====================

    private String formatDate(Player viewer, long timestamp) {
        String pattern = lang.getRaw(viewer, "achievements.date-format");

        if (pattern.startsWith("<red>Missing key")) {
            pattern = "dd.MM.yyyy HH:mm";
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            return format.format(new Date(timestamp));
        } catch (Exception e) {
            return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
        }
    }

    // ==================== PROGRESS BAR ====================

    private String buildProgressBar(int current, int max, int length,
                                    char filled, char empty) {
        return buildProgressBar(current, max, length, filled, empty, "<green>");
    }

    private String buildProgressBar(int current, int max, int length,
                                    char filled, char empty, String color) {
        if (max <= 0) max = 1;

        double ratio = Math.min(1.0, (double) current / max);
        int filledCount = (int) Math.round(ratio * length);
        int emptyCount = length - filledCount;

        StringBuilder sb = new StringBuilder();
        sb.append(color);
        for (int i = 0; i < filledCount; i++) sb.append(filled);
        sb.append("<dark_gray>");
        for (int i = 0; i < emptyCount; i++) sb.append(empty);

        return sb.toString();
    }

    // ==================== TRANSLATE ====================

    private String translateStat(Player viewer, String stat) {
        String direct = lang.get(viewer, "achievements.stats." + stat);
        if (!direct.startsWith("<red>Missing key")) {
            return direct;
        }

        if (stat.startsWith("mob_killed_")) {
            return lang.get(viewer, "achievements.stat-prefixes.mob_killed",
                    "name", prettify(stat.substring("mob_killed_".length())));
        }
        if (stat.startsWith("block_broken_")) {
            return lang.get(viewer, "achievements.stat-prefixes.block_broken",
                    "name", prettify(stat.substring("block_broken_".length())));
        }
        if (stat.startsWith("block_placed_")) {
            return lang.get(viewer, "achievements.stat-prefixes.block_placed",
                    "name", prettify(stat.substring("block_placed_".length())));
        }

        return prettify(stat);
    }

    private String translateCategory(Player viewer, String category) {
        String key = "achievements.categories." + category.toLowerCase();
        String result = lang.get(viewer, key);
        if (!result.startsWith("<red>Missing key")) {
            return result;
        }
        return prettify(category);
    }

    // ==================== UTIL ====================

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

    public static class AchievementsGuiHolder implements InventoryHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }
}