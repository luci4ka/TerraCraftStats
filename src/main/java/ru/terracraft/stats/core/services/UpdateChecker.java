package ru.terracraft.stats.core.services;

import ru.terracraft.stats.PlayerStatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Проверка обновлений через GitHub Releases API
 * (библиотека {@code com.github.ezplugins:github-update-checker}).
 *
 * <p>Логика уведомления (lang.yml, права, анти-спам) — своя.
 * Библиотека делает только HTTP-запрос и сравнение версий.</p>
 */
public class UpdateChecker {

    /** GitHub owner/org. */
    private static final String GITHUB_OWNER = "luci4ka";
    /** GitHub repo. */
    private static final String GITHUB_REPO = "TerraCraftStats";

    private final PlayerStatsPlugin plugin;
    private final String currentVersion;

    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile boolean updateAvailable = false;

    /** Уже уведомлённые админы (чтобы не спамить). */
    private final Set<UUID> notifiedAdmins = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public UpdateChecker(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Запустить асинхронную проверку. Не блокирует главный поток.
     */
    public void checkAsync() {
        com.github.ezplugins.updater.UpdateChecker checker;
        try {
            checker = com.github.ezplugins.updater.UpdateChecker
                    .builder(GITHUB_OWNER, GITHUB_REPO, currentVersion)
                    .timeout(Duration.ofSeconds(8))
                    .userAgent("PlayerStats/" + currentVersion + " (Paper)")
                    .build();
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось создать UpdateChecker: " + e.getMessage());
            return;
        }

        checker.checkNowAsync().thenAccept(result -> {
            if (result == null) {
                plugin.getLogger().warning("UpdateChecker: проверка вернула null.");
                return;
            }

            if (result.hasError()) {
                String msg = result.getError()
                        .map(Throwable::getMessage)
                        .orElse("неизвестная ошибка");
                plugin.getLogger().log(Level.WARNING,
                        "UpdateChecker: проверка не удалась: " + msg);
                return;
            }

            this.updateAvailable = result.isUpdateAvailable();
            this.latestVersion = result.getLatestTag().orElse(null);
            this.downloadUrl = result.getReleaseUrl().orElse(null);

            if (updateAvailable) {
                notifyUpdate();
            } else {
                plugin.getLogger().info("UpdateChecker: плагин актуален (v"
                        + currentVersion + ")");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "UpdateChecker: ошибка проверки обновлений", ex);
            return null;
        });
    }

    // ==================== УВЕДОМЛЕНИЕ ====================

    private void notifyUpdate() {
        plugin.getLogger().info("════════════════════════════════════════════");
        plugin.getLogger().info("Доступно обновление PlayerStats!");
        plugin.getLogger().info("Текущая версия: v" + currentVersion);
        plugin.getLogger().info("Новая версия: v" + latestVersion);
        plugin.getLogger().info("Скачать: " + downloadUrl);
        plugin.getLogger().info("════════════════════════════════════════════");

        plugin.getThreadManager().runSync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("playerstats.update")) continue;
                if (notifiedAdmins.contains(player.getUniqueId())) continue;

                sendUpdateMessage(player);
                notifiedAdmins.add(player.getUniqueId());
            }
        });
    }

    private void sendUpdateMessage(Player player) {
        var lang = plugin.getLanguageManager();

        player.sendMessage(lang.getComponent(player, "update.header"));
        player.sendMessage(lang.getComponent(player, "update.available"));
        player.sendMessage(lang.getComponent(player, "update.current",
                "current", currentVersion));
        player.sendMessage(lang.getComponent(player, "update.latest",
                "latest", latestVersion));
        player.sendMessage(lang.getComponent(player, "update.download",
                "url", downloadUrl == null ? "" : downloadUrl));
        player.sendMessage(lang.getComponent(player, "update.header"));
    }

    // ==================== ГЕТТЕРЫ ====================

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    @Nullable
    public String getLatestVersion() {
        return latestVersion;
    }

    @Nullable
    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }
}