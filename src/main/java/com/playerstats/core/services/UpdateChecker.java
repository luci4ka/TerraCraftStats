package com.playerstats.core.services;

import com.playerstats.Main;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Проверка обновлений через GitHub Releases API.
 *
 * <p>Проверяет {@code https://api.github.com/repos/<owner>/<repo>/releases/latest},
 * сравнивает версии и уведомляет админов.</p>
 */
public class UpdateChecker {

    private final Main plugin;
    private final String githubRepo; // "owner/repo"
    private final String currentVersion;

    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile boolean updateAvailable = false;

    /** Уже уведомлённые админы (чтобы не спамить). */
    private final Set<UUID> notifiedAdmins = new HashSet<>();

    public UpdateChecker(Main plugin, String githubRepo) {
        this.plugin = plugin;
        this.githubRepo = githubRepo;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    // ==================== ЗАПУСК ====================

    /**
     * Запустить асинхронную проверку обновлений.
     */
    public void checkAsync() {
        plugin.getThreadManager().runAsync(this::check);
    }

    /**
     * Синхронная проверка (выполняется в async-потоке).
     */
    private void check() {
        // ===== СБРОС ПЕРЕД ПРОВЕРКОЙ =====
        this.latestVersion = null;
        this.downloadUrl = null;
        this.updateAvailable = false;

        try {
            URL url = new URL("https://api.github.com/repos/" + githubRepo + "/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "PlayerStats-UpdateChecker");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                plugin.getLogger().warning("UpdateChecker: репозиторий не найден или нет релизов ("
                        + githubRepo + ")");
                return;
            }
            if (responseCode != 200) {
                plugin.getLogger().warning("UpdateChecker: GitHub API вернул код " + responseCode);
                return;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Простой парсинг JSON без библиотек
            String json = response.toString();
            String tag = extractJsonValue(json, "tag_name");
            this.downloadUrl = extractJsonValue(json, "html_url");

            if (tag == null || tag.isBlank()) {
                plugin.getLogger().warning("UpdateChecker: не удалось получить версию.");
                return;
            }

            // Убираем префикс "v" из тега (v1.0 → 1.0)
            if (tag.startsWith("v") || tag.startsWith("V")) {
                tag = tag.substring(1);
            }
            this.latestVersion = tag;

            // Сравниваем версии
            if (isNewerVersion(latestVersion, currentVersion)) {
                this.updateAvailable = true;
                notifyUpdate();
            } else {
                plugin.getLogger().info("UpdateChecker: плагин актуален (v"
                        + currentVersion + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "UpdateChecker: ошибка проверки обновлений", e);
        }
    }

    // ==================== УВЕДОМЛЕНИЕ ====================

    /**
     * Уведомить о доступном обновлении.
     */
    private void notifyUpdate() {
        // Логируем в консоль
        plugin.getLogger().info("════════════════════════════════════════════");
        plugin.getLogger().info("Доступно обновление PlayerStats!");
        plugin.getLogger().info("Текущая версия: v" + currentVersion);
        plugin.getLogger().info("Новая версия: v" + latestVersion);
        plugin.getLogger().info("Скачать: " + downloadUrl);
        plugin.getLogger().info("════════════════════════════════════════════");

        // Уведомляем онлайн-админов (в главном потоке)
        plugin.getThreadManager().runSync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("playerstats.update")) continue;

                // Не спамим повторно
                if (notifiedAdmins.contains(player.getUniqueId())) continue;

                sendUpdateMessage(player);
                notifiedAdmins.add(player.getUniqueId());
            }
        });
    }

    /**
     * Отправить сообщение игроку через lang.yml.
     */
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

    // ==================== СРАВНЕНИЕ ВЕРСИЙ ====================

    /**
     * Сравнить версии: latest > current?
     *
     * <p>Поддерживает: {@code 1.0.0}, {@code 1.0}, {@code 1.0.0-beta},
     * {@code 1.0.0-beta.2}.</p>
     *
     * <p>Правила:</p>
     * <ul>
     *   <li>Числовые части — обычное сравнение.</li>
     *   <li>Pre-release ({@code -beta}, {@code -alpha}) — МЕНЬШЕ релиза:
     *       {@code 1.0.0-beta < 1.0.0}.</li>
     *   <li>Больше частей = больше информации, отсутствие = 0.</li>
     * </ul>
     */
    private boolean isNewerVersion(String latest, String current) {
        // Разделяем на numeric-часть и pre-release
        String latestNum = latest.split("-")[0];
        String currentNum = current.split("-")[0];

        String[] latestParts = latestNum.split("\\.");
        String[] currentParts = currentNum.split("\\.");

        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int lp = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int cp = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;

            if (lp > cp) return true;
            if (lp < cp) return false;
        }

        // Числовые части равны — сравниваем pre-release
        boolean latestPre  = latest.contains("-");
        boolean currentPre = current.contains("-");

        // latest — релиз, current — pre-release → latest > current
        if (!latestPre && currentPre) return true;
        // latest — pre-release, current — релиз → latest < current
        if (latestPre && !currentPre) return false;

        // Оба pre-release — лексикографически
        if (latestPre) {
            String lp = latest.substring(latest.indexOf('-') + 1);
            String cp = current.substring(current.indexOf('-') + 1);
            return lp.compareToIgnoreCase(cp) > 0;
        }

        return false;
    }

    private int parseVersionPart(String part) {
        try {
            String digits = part.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== JSON ====================

    /**
     * Простой парсинг значения из JSON по ключу.
     * Ищет "key": "value" или "key": value.
     */
    @Nullable
    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex == -1) return null;

        int startIndex = colonIndex + 1;
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }

        if (startIndex >= json.length()) return null;

        char firstChar = json.charAt(startIndex);

        if (firstChar == '"') {
            int endIndex = json.indexOf('"', startIndex + 1);
            if (endIndex == -1) return null;
            return json.substring(startIndex + 1, endIndex);
        } else {
            int endIndex = startIndex;
            while (endIndex < json.length()
                    && json.charAt(endIndex) != ','
                    && json.charAt(endIndex) != '}') {
                endIndex++;
            }
            return json.substring(startIndex, endIndex).trim();
        }
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