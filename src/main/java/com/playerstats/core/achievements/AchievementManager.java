package com.playerstats.core.achievements;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;
import com.playerstats.core.config.LanguageManager;
import com.playerstats.core.database.dao.AchievementDAO;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * Управляет достижениями.
 *
 * <p>Загружает достижения из achievements.yml (логика) + lang.yml (тексты).</p>
 */
public class AchievementManager {

    private final Main plugin;
    private final AchievementDAO achievementDAO;
    private final LanguageManager lang;

    /** ID → Achievement */
    private final Map<String, Achievement> achievements = new LinkedHashMap<>();

    /** UUID → Set<achievementId> (кэш разблокированных) */
    private final Map<UUID, Set<String>> unlockedCache = new HashMap<>();

    public AchievementManager(Main plugin, AchievementDAO achievementDAO) {
        this.plugin = plugin;
        this.achievementDAO = achievementDAO;
        this.lang = plugin.getLanguageManager();
        loadAchievements();
    }

    // ==================== ЗАГРУЗКА ====================

    private void loadAchievements() {
        achievements.clear();

        if (!plugin.getConfigHandler().isModuleEnabled("achievements")) {
            plugin.getLogger().info("Модуль достижений выключен.");
            return;
        }

        ConfigurationSection section = plugin.getConfigHandler().getAchievementsSection();
        if (section == null) {
            plugin.getLogger().info("Достижения не найдены в achievements.yml.");
            return;
        }

        int missingLang = 0;

        for (String id : section.getKeys(false)) {
            try {
                ConfigurationSection acSection = section.getConfigurationSection(id);
                if (acSection == null) continue;

                // ==================== NAME ====================
                String nameKey = "achievements.list." + id + ".name";
                String name = lang.getRaw(nameKey);

                if (name.startsWith("<red>Missing key")) {
                    // Fallback: prettify(id)
                    name = prettify(id);
                    missingLang++;
                }

                // ==================== DESCRIPTION ====================
                String descKey = "achievements.list." + id + ".description";
                String description = lang.getRaw(descKey);

                if (description.startsWith("<red>Missing key")) {
                    description = "";
                }

                // ==================== Создаём Achievement ====================
                Achievement ac = new Achievement(
                        id,
                        name,
                        description,
                        acSection.getString("icon", "PAPER"),
                        acSection.getString("stat", ""),
                        acSection.getInt("threshold", 1),
                        acSection.getStringList("commands"),
                        acSection.getBoolean("broadcast", true),
                        acSection.getString("category", "other")
                );

                if (ac.stat().isEmpty()) {
                    plugin.getLogger().warning("Достижение '" + id + "' без статистики — пропущено.");
                    continue;
                }

                achievements.put(id, ac);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка загрузки достижения '" + id + "'", e);
            }
        }

        plugin.getLogger().info("Загружено достижений: " + achievements.size()
                + (missingLang > 0 ? " (без перевода: " + missingLang + ")" : ""));
    }

    /**
     * Перезагрузить достижения.
     */
    public void reload() {
        loadAchievements();
        unlockedCache.clear();
    }

    // ==================== ПРОВЕРКА ====================

    /**
     * Проверить все достижения для игрока по конкретной статистике.
     * Вызывается из StatsListener.handleStatEvent.
     */
    public void check(Player player, int playerId, String statName, int value) {
        for (Achievement ac : achievements.values()) {
            if (!ac.stat().equals(statName)) continue;
            if (value < ac.threshold()) continue;

            try {
                if (isUnlocked(playerId, ac.id())) continue;
                unlock(player, playerId, ac);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Ошибка проверки достижения " + ac.id(), e);
            }
        }
    }

    private boolean isUnlocked(int playerId, String achievementId) throws SQLException {
        // Проверяем кэш
        UUID uuid = getPlayerUuid(playerId);
        if (uuid != null) {
            Set<String> cached = unlockedCache.get(uuid);
            if (cached != null && cached.contains(achievementId)) return true;
        }
        return achievementDAO.isUnlocked(playerId, achievementId);
    }

    private UUID getPlayerUuid(int playerId) {
        try {
            Player online = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> {
                        try {
                            return plugin.getPlayerDAO().getPlayerId(p.getUniqueId()) == playerId;
                        } catch (SQLException e) {
                            return false;
                        }
                    })
                    .findFirst().orElse(null);
            return online != null ? online.getUniqueId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== РАЗБЛОКИРОВКА ====================

    private void unlock(Player player, int playerId, Achievement ac) throws SQLException {
        // 1. Сохранить в БД
        achievementDAO.unlock(playerId, ac.id());

        // 2. Кэш
        unlockedCache.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>())
                .add(ac.id());

        // 3. Выполнить команды
        for (String cmd : ac.commands()) {
            String finalCmd = cmd.replace("%player%", player.getName());
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
        }

        // 4. Сообщение игроку (из lang.yml)
        Component unlockMessage = lang.getComponent("achievements.messages.unlock-self",
                "name", ac.name());
        player.sendMessage(unlockMessage);

        // Описание (если есть)
        if (ac.description() != null && !ac.description().isEmpty()) {
            Component descMessage = lang.getComponent(
                    "achievements.messages.unlock-description",
                    "description", ac.description());
            player.sendMessage(descMessage);
        }

        // 5. Broadcast
        if (ac.broadcast()) {
            Component broadcast = lang.getComponent(
                    "achievements.messages.unlock-broadcast",
                    "player", player.getName(),
                    "name", ac.name());
            Bukkit.getServer().sendMessage(broadcast);
        }

        // 6. Debug
        if (plugin.getConfigHandler().isVerboseDebug()) {
            plugin.getLogger().info("[DEBUG] Achievement unlocked: "
                    + player.getName() + " → " + ac.id());
        }
    }

    // ==================== ГЕТТЕРЫ ====================

    public Collection<Achievement> getAll() {
        return achievements.values();
    }

    public Achievement get(String id) {
        return achievements.get(id);
    }

    public Set<String> getUnlocked(Player player, int playerId) throws SQLException {
        Set<String> cached = unlockedCache.get(player.getUniqueId());
        if (cached != null) return cached;

        Set<String> result = achievementDAO.getUnlockedAchievements(playerId);
        unlockedCache.put(player.getUniqueId(), result);
        return result;
    }

    public Map<String, Achievement> getAchievements() {
        return achievements;
    }

    public int getTotal() {
        return achievements.size();
    }

    public void clearCache() {
        unlockedCache.clear();
    }

    public void clearCache(UUID uuid) {
        unlockedCache.remove(uuid);
    }

    // ==================== UTIL ====================

    /**
     * "first_kill" → "First Kill"
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
}