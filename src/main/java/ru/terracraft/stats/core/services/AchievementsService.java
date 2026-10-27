package ru.terracraft.stats.core.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.model.AchievementsConfig;
import ru.terracraft.stats.core.database.dao.AchievementsDAO;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class AchievementsService {

    private final PlayerStatsPlugin plugin;
    private final AchievementsDAO dao;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public AchievementsService(PlayerStatsPlugin plugin, AchievementsDAO dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    /** Вызывается из слушателя PlayerStatChangeEvent — обычное получение (с наградой). */
    public void check(Player player, String statKey, long newValue) {
        var cfg = plugin.getConfigManager().achievements();
        if (cfg == null || !cfg.enabled()) return;

        List<AchievementsConfig.Entry> entries = cfg.achievements().get(statKey);
        if (entries == null || entries.isEmpty()) return;

        try {
            Set<String> received = dao.loadReceived(player.getUniqueId());

            for (AchievementsConfig.Entry entry : entries) {
                String id = statKey + ":" + entry.threshold();
                if (newValue >= entry.threshold() && !received.contains(id)) {
                    // Логируем «битые» записи, чтобы админ сразу видел проблему в achievements.yml
                    if (entry.displayName() == null || entry.displayName().isBlank()) {
                        plugin.getLogger().warning(
                                "Достижение " + id + " без displayName — пропущено. " +
                                        "Проверьте achievements.yml.");
                        continue;
                    }
                    award(player, entry);
                    dao.saveReceived(player.getUniqueId(), id);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось проверить достижения для " + player.getName(), e);
        }
    }

    /**
     * Ручная выдача через /aach give.
     * <p><b>ВАЖНО:</b> не выдаёт награды (money/command) и не показывает уведомления.
     * Только фиксирует факт получения в БД. Так админ не может абузить экономику.
     *
     * @return true если выдача успешна, false если ачивка не найдена или уже была
     */
    public boolean grantAdmin(UUID uuid, String achievementId) {
        var cfg = plugin.getConfigManager().achievements();
        if (cfg == null || !cfg.enabled()) return false;

        // Ищем достижение по ID (формат: statKey:threshold)
        String[] parts = achievementId.split(":");
        if (parts.length != 2) return false;

        String statKey = parts[0];
        long threshold;
        try {
            threshold = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        List<AchievementsConfig.Entry> entries = cfg.achievements().get(statKey);
        if (entries == null) return false;

        // Проверяем, существует ли такое достижение в конфиге
        boolean exists = entries.stream().anyMatch(e -> e.threshold() == threshold);
        if (!exists) return false;

        try {
            // Проверяем, не получено ли уже
            if (dao.loadReceived(uuid).contains(achievementId)) return false;

            // Пишем в БД БЕЗ награды и уведомлений
            dao.saveReceived(uuid, achievementId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Ошибка выдачи ачивки " + achievementId + " для " + uuid, e);
            return false;
        }
    }

    /** Обычное получение — с наградой и уведомлениями. */
    private void award(Player player, AchievementsConfig.Entry entry) {
        var notify = plugin.getConfigManager().achievements().notifications();

        // Defensive: даже если Entry пришёл минуя компактный конструктор — не падаем.
        Component name = deserializeOr(entry.displayName(),
                Component.text("Достижение " + entry.threshold()));
        Component message = deserializeOr(entry.message(), Component.empty());

        if (notify.chat()) {
            player.sendMessage(Component.text()
                    .append(mm.deserialize("<gray>[<gradient:gold:yellow>✓</gradient>] "))
                    .append(name)
                    .append(Component.text(" — "))
                    .append(message)
                    .build());
        }
        if (notify.title()) {
            player.showTitle(Title.title(name, message,
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2),
                            Duration.ofMillis(500))));
        }
        if (notify.actionBar()) player.sendActionBar(name);

        if (notify.sound() != null && !notify.sound().isBlank()) {
            try { player.playSound(player.getLocation(), notify.sound(), 1f, 1f); }
            catch (Exception ignored) {}
        }

        if (notify.firework()) {
            try {
                Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .withColor(Color.PURPLE)
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .build());
                meta.setPower(0);
                fw.setFireworkMeta(meta);
            } catch (Exception ignored) {}
        }

        var reward = entry.reward();
        if (reward != null) {
            if (reward.money() > 0) {
                var vault = plugin.getHooksManager().getVaultHook();
                if (vault != null && vault.isEnabled()) vault.deposit(player, reward.money());
            }
            if (reward.command() != null && !reward.command().isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        reward.command().replace("%player%", player.getName()));
            }
        }
    }

    /** Безопасный разбор MiniMessage: null/пусто → fallback, битая строка → plain text. */
    private Component deserializeOr(String raw, Component fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return mm.deserialize(raw);
        } catch (Exception e) {
            plugin.getLogger().warning("Некорректный MiniMessage в достижении: " + raw);
            return Component.text(raw);
        }
    }

    // ==================== Для команд ====================

    public int countReceived(UUID uuid) {
        try { return dao.countReceived(uuid); }
        catch (SQLException e) { return 0; }
    }

    public void reset(UUID uuid) {
        try { dao.removeAll(uuid); }
        catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка сброса ачивок " + uuid, e);
        }
    }

    public int totalCount() {
        var cfg = plugin.getConfigManager().achievements();
        if (cfg == null || !cfg.enabled()) return 0;
        int total = 0;
        for (var list : cfg.achievements().values()) total += list.size();
        return total;
    }

    public Map<String, List<AchievementsConfig.Entry>> allCategories() {
        var cfg = plugin.getConfigManager().achievements();
        return cfg == null ? Map.of() : cfg.achievements();
    }

    public void unload(UUID uuid) {
        dao.unloadCache(uuid);
    }
}