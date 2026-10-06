package com.playerstats.core.gui;

import com.playerstats.Main;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.PlayerDAO;
import com.playerstats.core.database.dao.PlayerStatsDAO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatsGui {

    private final Main plugin;
    private final LanguageManager lang;

    /**
     * Размер одной страницы (27 слотов + 27 навигация = 54).
     */
    private static final int PAGE_SIZE = 45;

    public StatsGui(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    /**
     * Открыть GUI со статистикой игрока (главная страница).
     */
    public void open(Player player) {
        openPage(player, 0, null);
    }

    /**
     * Открыть GUI с указанной страницей.
     *
     * @param page    номер страницы (0 = главная)
     * @param subPage подстраница (mobs / blocks_placed / blocks_broken / pvp) или null
     */
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

                // ==================== Статистика по мобам ====================
                Map<String, Integer> mobStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "mob_killed_");

                // ==================== Статистика по блокам ====================
                Map<String, Integer> blocksPlacedStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "block_placed_");
                Map<String, Integer> blocksBrokenStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "block_broken_");

                // ==================== PvP-статистика (по жертвам) ====================
                Map<String, Integer> pvpStats = statsDAO.getStatsByPrefix(
                        playerId, serverId, "pvp_kill_");

                plugin.getThreadManager().runSync(() -> {
                    if (!player.isOnline()) return;

                    StatsGuiHolder holder = new StatsGuiHolder(page, subPage);

                    Inventory inv = Bukkit.createInventory(holder, 54,
                            Component.text("📊 Статистика", NamedTextColor.DARK_GRAY));
                    holder.setInventory(inv);

                    // Заполняем стеклом
                    ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
                    for (int i = 0; i < 54; i++) {
                        inv.setItem(i, filler);
                    }

                    // ==================== ГЛАВНАЯ СТРАНИЦА ====================
                    if (page == 0 && subPage == null) {
                        openMainPage(inv, blocksPlaced, blocksBroken,
                                mobsKilled, bossesKilled, playTimeSec,
                                kills, deaths, balance, group,
                                mobStats, blocksPlacedStats, blocksBrokenStats,
                                pvpStats);
                    }
                    // ==================== ПОДСТРАНИЦА: МОБЫ ====================
                    else if ("mobs".equals(subPage)) {
                        openSubPage(inv, page, "Убийства мобов", mobStats,
                                "mob_killed_", Material.DIAMOND_SWORD, holder, false);
                    }
                    // ==================== ПОДСТРАНИЦА: ПОСТАВЛЕННЫЕ БЛОКИ ====================
                    else if ("blocks_placed".equals(subPage)) {
                        openSubPage(inv, page, "Поставлено блоков", blocksPlacedStats,
                                "block_placed_", Material.BRICKS, holder, false);
                    }
                    // ==================== ПОДСТРАНИЦА: СЛОМАННЫЕ БЛОКИ ====================
                    else if ("blocks_broken".equals(subPage)) {
                        openSubPage(inv, page, "Сломано блоков", blocksBrokenStats,
                                "block_broken_", Material.IRON_PICKAXE, holder, false);
                    }
                    // ==================== ПОДСТРАНИЦА: PvP ====================
                    else if ("pvp".equals(subPage)) {
                        openSubPage(inv, page, "Убийства игроков", pvpStats,
                                "pvp_kill_", Material.PLAYER_HEAD, holder, true);
                    }

                    // ==================== КНОПКА "НАЗАД" / "ЗАКРЫТЬ" ====================
                    if (subPage != null) {
                        inv.setItem(49, createItem(Material.ARROW,
                                "§e◀ Назад",
                                List.of("§7Вернуться к основной статистике")));
                    } else {
                        inv.setItem(49, createItem(Material.BARRIER,
                                "§cЗакрыть",
                                List.of("§7Нажмите, чтобы закрыть")));
                    }

                    player.openInventory(inv);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка GUI для " + player.getName() + ": " + e.getMessage());
                plugin.getThreadManager().runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(Component.text(
                                "Ошибка загрузки статистики.", NamedTextColor.RED));
                    }
                });
            }
        });
    }

    // ==================== ГЛАВНАЯ СТРАНИЦА ====================

    private void openMainPage(Inventory inv,
                              int blocksPlaced, int blocksBroken,
                              int mobsKilled, int bossesKilled,
                              int playTimeSec, int kills, int deaths,
                              double balance, String group,
                              Map<String, Integer> mobStats,
                              Map<String, Integer> blocksPlacedStats,
                              Map<String, Integer> blocksBrokenStats,
                              Map<String, Integer> pvpStats) {
        // Слот 10 — Блоки
        inv.setItem(10, createItem(Material.BRICKS,
                "§e🧱 Блоки",
                List.of(
                        "§7Поставлено: §f" + blocksPlaced,
                        "§7Сломано: §f" + blocksBroken,
                        "§7Всего: §f" + (blocksPlaced + blocksBroken),
                        "",
                        "§a▶ Нажмите для подробностей"
                )));

        // Слот 12 — PvE
        inv.setItem(12, createItem(Material.DIAMOND_SWORD,
                "§e☠ PvE",
                List.of(
                        "§7Мобы: §f" + mobsKilled,
                        "§7Боссы: §f" + bossesKilled,
                        "",
                        "§a▶ Нажмите для подробностей"
                )));

        // Слот 14 — Время
        inv.setItem(14, createItem(Material.CLOCK,
                "§e⏳ Время",
                List.of("§7Играл: §f" + formatTime(playTimeSec))));

        // Слот 16 — PvP (теперь кликабельный)
        int pvpTotal = pvpStats.values().stream().mapToInt(Integer::intValue).sum();
        List<String> pvpLore = new ArrayList<>();
        pvpLore.add("§7Убийств (честных): §f" + kills);
        pvpLore.add("§7Смертей: §f" + deaths);
        pvpLore.add("§7K/D: §f" + formatKD(kills, deaths));
        if (pvpTotal > 0) {
            pvpLore.add("");
            pvpLore.add("§7Убито игроков (уник.): §f" + pvpStats.size());
            pvpLore.add("§7Всего убийств игроков: §f" + pvpTotal);
            pvpLore.add("");
            pvpLore.add("§a▶ Нажмите для подробностей");
        } else {
            pvpLore.add("");
            pvpLore.add("§8Нет данных");
        }
        inv.setItem(16, createItem(Material.IRON_SWORD,
                "§e⚔ PvP",
                pvpLore));

        // Слот 28 — Баланс
        inv.setItem(28, createItem(Material.GOLD_INGOT,
                "§e💰 Баланс",
                List.of("§7Баланс: §6" + String.format("%.2f₽", balance))));

        // Слот 30 — Группа
        inv.setItem(30, createItem(Material.NETHER_STAR,
                "§e🎖 Привилегия",
                List.of("§7Группа: " + group)));

        // Слот 32 — Поставленные блоки (подробно)
        if (!blocksPlacedStats.isEmpty()) {
            inv.setItem(32, createItem(Material.BRICKS,
                    "§e🏗 Поставленные блоки",
                    List.of(
                            "§7Всего типов: §f" + blocksPlacedStats.size(),
                            "",
                            "§a▶ Нажмите для подробностей"
                    )));
        }

        // Слот 34 — Сломанные блоки (подробно)
        if (!blocksBrokenStats.isEmpty()) {
            inv.setItem(34, createItem(Material.IRON_PICKAXE,
                    "§e⛏ Сломанные блоки",
                    List.of(
                            "§7Всего типов: §f" + blocksBrokenStats.size(),
                            "",
                            "§a▶ Нажмите для подробностей"
                    )));
        }
    }

    // ==================== ПОДСТРАНИЦА ====================

    /**
     * @param isPlayerHead если true — рисуем голову игрока (для PvP),
     *                     резолвим UUID → имя
     */
    private void openSubPage(Inventory inv, int page, String title,
                             Map<String, Integer> stats, String prefix,
                             Material icon, StatsGuiHolder holder,
                             boolean isPlayerHead) {
        // Заголовок
        inv.setItem(4, createItem(Material.PAPER,
                "§e" + title,
                List.of("§7Всего типов: §f" + stats.size())));

        // Сортируем по убыванию значения
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(stats.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Вычисляем диапазон
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, sorted.size());

        // Заполняем слоты 9-44 (36 слотов)
        int slot = 9;
        for (int i = start; i < end && slot < 45; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            String rawKey = entry.getKey().substring(prefix.length());

            if (isPlayerHead) {
                // rawKey = UUID жертвы
                String victimName = resolvePlayerName(rawKey);
                inv.setItem(slot, createPlayerHead(
                        rawKey,
                        "§e☠ " + victimName,
                        List.of(
                                "§7Убито раз: §f" + entry.getValue(),
                                "§7UUID: §8" + rawKey
                        )));
            } else {
                String displayName = prettify(rawKey);
                inv.setItem(slot, createItem(icon,
                        "§e" + displayName,
                        List.of(
                                "§7Значение: §f" + entry.getValue(),
                                "§7Ключ: §8" + entry.getKey()
                        )));
            }
            slot++;
        }

        // Навигация
        int totalPages = (int) Math.ceil((double) sorted.size() / PAGE_SIZE);

        if (page > 0) {
            inv.setItem(48, createItem(Material.LIME_DYE,
                    "§a◀ Предыдущая",
                    List.of("§7Страница " + page + " / " + totalPages)));
        }

        if (page < totalPages - 1) {
            inv.setItem(50, createItem(Material.LIME_DYE,
                    "§aСледующая ▶",
                    List.of("§7Страница " + (page + 2) + " / " + totalPages)));
        }
    }

    // ==================== УТИЛИТЫ ====================

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Создать голову игрока по UUID (с фоллбэком на имя, если UUID невалиден).
     */
    private ItemStack createPlayerHead(String uuidStr, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                skullMeta.setOwningPlayer(offline);
            } catch (IllegalArgumentException ignored) {
                // некорректный UUID — оставим дефолтную голову
            }
            skullMeta.setDisplayName(name);
            if (lore != null) {
                skullMeta.setLore(lore);
            }
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    /**
     * Резолв UUID → имя игрока. Если не получилось — вернуть короткий UUID.
     */
    private String resolvePlayerName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
            return uuidStr.substring(0, 8) + "…";
        } catch (IllegalArgumentException e) {
            return uuidStr;
        }
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "ч " + minutes + "м";
        return minutes + "м";
    }

    private String formatKD(int kills, int deaths) {
        if (deaths == 0) return kills > 0 ? "∞" : "0.00";
        return String.format("%.2f", (double) kills / deaths);
    }

    /**
     * "diamond_ore" → "Diamond Ore".
     */
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

    /**
     * Holder для GUI — позволяет определить, какой инвентарь открыт.
     */
    public static class StatsGuiHolder implements InventoryHolder {
        private final int page;
        private final String subPage;
        private Inventory inventory;

        public StatsGuiHolder(int page, String subPage) {
            this.page = page;
            this.subPage = subPage;
        }

        public int getPage() {
            return page;
        }

        public String getSubPage() {
            return subPage;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}