package ru.terracraft.stats.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.LanguageManager;
import ru.terracraft.stats.core.config.model.AchievementsConfig;

import java.sql.SQLException;
import java.util.*;

/**
 * GUI достижений — отдельно от /stats gui.
 * <p>Структура:
 * <ul>
 *   <li>Строка 0 — табы категорий (по одной на каждый statKey)</li>
 *   <li>Строка 1-5 — список ачивок выбранной категории</li>
 *   <li>Строка 5 — навигация</li>
 * </ul>
 */
public class AchievementsGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int PAGE_SIZE = 28;

    private final PlayerStatsPlugin plugin;
    private final LanguageManager lang;

    public AchievementsGui(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void open(Player player) {
        openCategory(player, null, 0);
    }

    public void openCategory(Player player, String statKey, int page) {
        final String initialStatKey = statKey;
        final int initialPage = page;
        final UUID playerUuid = player.getUniqueId();

        plugin.getThreadManager().runAsync(() -> {
            AchievementsConfig cfg = plugin.getConfigManager().achievements();
            if (cfg == null || !cfg.enabled()) {
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(lang.getComponent(player, "achievements.disabled"));
                    }
                });
                return;
            }

            Set<String> received;
            try {
                received = plugin.getAchievementsDAO().loadReceived(playerUuid);
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка загрузки ачивок: " + e.getMessage());
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(lang.getComponent(player, "error"));
                    }
                });
                return;
            }

            List<String> categories = new ArrayList<>(cfg.achievements().keySet());
            Collections.sort(categories);

            final String resolvedStatKey;
            if (initialStatKey == null || !categories.contains(initialStatKey)) {
                resolvedStatKey = categories.isEmpty() ? null : categories.get(0);
            } else {
                resolvedStatKey = initialStatKey;
            }

            final Set<String> finalReceived = received;

            plugin.getThreadManager().runSync(() -> {
                if (!player.isOnline()) return;

                AchievementsGuiHolder holder = new AchievementsGuiHolder(resolvedStatKey, initialPage);
                Inventory inv = Bukkit.createInventory(holder, SIZE,
                        lang.getComponent(player, "achievements.gui-title"));
                holder.setInventory(inv);

                // --- Табы категорий (строка 0) ---
                int tabSlot = 0;
                for (String cat : categories) {
                    if (tabSlot >= 9) break;
                    boolean active = cat.equals(resolvedStatKey);
                    ItemStack tab = createCategoryTab(player, cat, active);
                    inv.setItem(tabSlot++, tab);
                }

                // --- Список ачивок выбранной категории ---
                if (resolvedStatKey != null) {
                    List<AchievementsConfig.Entry> entries = cfg.achievements().get(resolvedStatKey);
                    if (entries != null) {
                        fillAchievementList(player, inv, resolvedStatKey, entries, finalReceived, initialPage);
                    }
                }

                // --- Навигация ---
                inv.setItem(49, createItem(Material.BARRIER,
                        lang.get(player, "achievements.gui-close"),
                        List.of()));
                if (initialPage > 0) {
                    inv.setItem(48, createItem(Material.ARROW,
                            lang.get(player, "achievements.gui-prev"),
                            List.of(lang.get(player, "achievements.gui-prev-hover",
                                    "page", initialPage))));
                }

                List<AchievementsConfig.Entry> list = cfg.achievements().get(resolvedStatKey);
                int total = list == null ? 0 : list.size();
                int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
                if (initialPage < totalPages - 1) {
                    inv.setItem(50, createItem(Material.ARROW,
                            lang.get(player, "achievements.gui-next"),
                            List.of(lang.get(player, "achievements.gui-next-hover",
                                    "page", initialPage + 2))));
                }

                player.openInventory(inv);
            });
        });
    }

    private void fillAchievementList(Player player, Inventory inv, String statKey,
                                     List<AchievementsConfig.Entry> entries,
                                     Set<String> received, int page) {
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        int slot = 9; // начинаем со второй строки
        for (int i = start; i < end && slot < 45; i++) {
            AchievementsConfig.Entry entry = entries.get(i);
            String id = statKey + ":" + entry.threshold();
            boolean unlocked = received.contains(id);

            ItemStack item = createAchievementItem(player, entry, unlocked);
            inv.setItem(slot++, item);
        }
    }

    private ItemStack createAchievementItem(Player player, AchievementsConfig.Entry entry, boolean unlocked) {
        Material mat = unlocked ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String nameKey = unlocked ? "achievements.gui-unlocked" : "achievements.gui-locked";
        String displayName = lang.get(player, nameKey, "name", entry.displayName());
        meta.displayName(MM.deserialize(displayName).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<dark_gray>Порог: <white>" + entry.threshold())
                .decoration(TextDecoration.ITALIC, false));
        if (entry.goal() != null && !entry.goal().isBlank()) {
            lore.add(Component.empty());
            lore.add(MM.deserialize("<gray>Цель: <white>" + entry.goal())
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (entry.message() != null && !entry.message().isBlank()) {
            lore.add(Component.empty());
            lore.add(MM.deserialize("<gray>" + entry.message())
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (entry.reward() != null && (!entry.reward().command().isBlank() || entry.reward().money() > 0)) {
            lore.add(Component.empty());
            lore.add(MM.deserialize("<dark_gray>Награда:")
                    .decoration(TextDecoration.ITALIC, false));
            if (entry.reward().money() > 0) {
                lore.add(MM.deserialize("<gray>  • <gold>" + entry.reward().money() + " монет")
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (entry.reward().command() != null && !entry.reward().command().isBlank()) {
                lore.add(MM.deserialize("<gray>  • <white>" + entry.reward().command())
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        lore.add(MM.deserialize(unlocked
                        ? "<green>✓ Получено"
                        : "<red>✗ Не получено")
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        if (unlocked) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCategoryTab(Player player, String statKey, boolean active) {
        Material mat = active ? Material.ENCHANTED_BOOK : Material.BOOK;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        var cfg = plugin.getConfigManager().achievements();
        int total = cfg.achievements().getOrDefault(statKey, List.of()).size();
        int received;
        try {
            received = (int) plugin.getAchievementsDAO()
                    .loadReceived(player.getUniqueId()).stream()
                    .filter(id -> id.startsWith(statKey + ":"))
                    .count();
        } catch (SQLException e) {
            received = 0;
        }

        meta.displayName(MM.deserialize("<yellow>" + prettify(statKey))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Получено: <green>" + received
                        + "<gray>/<white>" + total)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
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

    public static class AchievementsGuiHolder implements InventoryHolder {
        private final String statKey;
        private final int page;
        private Inventory inventory;

        public AchievementsGuiHolder(String statKey, int page) {
            this.statKey = statKey;
            this.page = page;
        }

        public String getStatKey() { return statKey; }
        public int getPage() { return page; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }
}