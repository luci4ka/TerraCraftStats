package com.playerstats.core.services;

import com.playerstats.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Проверка обновлений через GitHub Releases API.
 */
public class UpdateChecker {

    private final Main plugin;
    private final String githubRepo; // "owner/repo"
    private final String currentVersion;

    private String latestVersion;
    private String downloadUrl;
    private boolean updateAvailable = false;

    public UpdateChecker(Main plugin, String githubRepo) {
        this.plugin = plugin;
        this.githubRepo = githubRepo;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

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
        try {
            URL url = new URL("https://api.github.com/repos/" + githubRepo + "/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "PlayerStats-UpdateChecker");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
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
            latestVersion = extractJsonValue(json, "tag_name");
            downloadUrl = extractJsonValue(json, "html_url");

            if (latestVersion == null) {
                plugin.getLogger().warning("UpdateChecker: не удалось получить версию.");
                return;
            }

            // Убираем префикс "v" из тега (v1.0 → 1.0)
            if (latestVersion.startsWith("v")) {
                latestVersion = latestVersion.substring(1);
            }

            // Сравниваем версии
            if (isNewerVersion(latestVersion, currentVersion)) {
                updateAvailable = true;
                notifyUpdate();
            } else {
                plugin.getLogger().info("UpdateChecker: плагин актуален (v" + currentVersion + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "UpdateChecker: ошибка проверки обновлений", e);
        }
    }

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
                if (player.hasPermission("playerstats.update")) {
                    player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            .color(NamedTextColor.DARK_GRAY));
                    player.sendMessage(Component.text("📢 Доступно обновление PlayerStats!")
                            .color(NamedTextColor.GOLD));
                    player.sendMessage(Component.text("  Текущая: v" + currentVersion)
                            .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("  Новая: v" + latestVersion)
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("  Скачать: " + downloadUrl)
                            .color(NamedTextColor.AQUA));
                    player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            .color(NamedTextColor.DARK_GRAY));
                }
            }
        });
    }

    /**
     * Сравнить версии: latest > current?
     * Поддерживает формат "1.0.0", "1.0", "1.0.0-beta".
     */
    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("[.\\-]");
        String[] currentParts = current.split("[.\\-]");

        int length = Math.max(latestParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;

            if (latestPart > currentPart) return true;
            if (latestPart < currentPart) return false;
        }

        return false;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Простой парсинг значения из JSON по ключу.
     * Ищет "key": "value" или "key": value.
     */
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

    // ==================== Геттеры ====================

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }
}