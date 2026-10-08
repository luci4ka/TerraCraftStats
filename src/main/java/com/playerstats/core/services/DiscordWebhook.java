package com.playerstats.core.services;

import com.playerstats.Main;
import com.playerstats.core.config.ConfigHandler;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class DiscordWebhook {

    private final Main plugin;

    /** Последняя отправка (для rate-limit). */
    private volatile long lastSendTime = 0L;

    public DiscordWebhook(Main plugin) {
        this.plugin = plugin;
    }

    private ConfigHandler cfg() {
        return plugin.getConfigHandler();
    }

    // ==================== ЛЕНИВЫЕ ГЕТТЕРЫ ====================

    public boolean isEnabled() {
        if (!cfg().isDiscordEnabled()) return false;

        String url = cfg().getDiscordWebhookUrl();
        if (url == null || url.isBlank()) return false;
        if (url.equals("https://discord.com/api/webhooks/...")) return false;

        return true;
    }

    private String getWebhookUrl()  { return cfg().getDiscordWebhookUrl(); }
    private String getUsername()    { return cfg().getDiscordUsername(); }
    private String getAvatarUrl()   { return cfg().getDiscordAvatarUrl(); }

    // ==================== ОТПРАВКА ====================

    /**
     * Отправить уведомление в Discord (с rate-limit).
     */
    public void send(String content) {
        if (!isEnabled()) return;

        // Rate-limit
        long rateLimitMs = cfg().getDiscordRateLimitSeconds() * 1000L;
        long now = System.currentTimeMillis();
        if (rateLimitMs > 0 && (now - lastSendTime) < rateLimitMs) {
            plugin.getLogger().fine("Discord Webhook: пропуск (rate-limit)");
            return;
        }
        lastSendTime = now;

        plugin.getThreadManager().runAsync(() -> {
            try {
                URL url = new URL(getWebhookUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "PlayerStats-Webhook");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String json = buildJson(content);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("Discord Webhook: код " + responseCode);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Discord Webhook: ошибка отправки", e);
            }
        });
    }

    private String buildJson(String content) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        // content
        json.append("\"content\":\"").append(escapeJson(content)).append("\"");

        // username
        String username = getUsername();
        if (username != null && !username.isEmpty()) {
            json.append(",\"username\":\"").append(escapeJson(username)).append("\"");
        }

        // avatar_url
        String avatar = getAvatarUrl();
        if (avatar != null && !avatar.isEmpty()) {
            json.append(",\"avatar_url\":\"").append(escapeJson(avatar)).append("\"");
        }

        // mention
        String mention = cfg().getDiscordMention();
        if (mention != null && !mention.isBlank() && !mention.equalsIgnoreCase("none")) {
            String prefix = switch (mention.toLowerCase()) {
                case "here" -> "@here ";
                case "everyone" -> "@everyone ";
                default -> mention.startsWith("<@") || mention.startsWith("<@&")
                        ? mention + " " : "";
            };
            if (!prefix.isEmpty() && !content.startsWith(prefix)) {
                json.setLength(json.length() - 1);   // убираем закрывающую "
                json.append(" ").append(escapeJson(prefix)).append("\"");
            }
        }

        // timestamp
        if (cfg().isDiscordUseTimestamp()) {
            json.append(",\"timestamp\":\"")
                    .append(java.time.OffsetDateTime.now().toString())
                    .append("\"");
        }

        json.append("}");
        return json.toString();
    }

    // ==================== СОБЫТИЯ ====================

    /**
     * Уведомление о вехе.
     */
    public void sendMilestone(String playerName, String statName, int value) {
        if (!cfg().isDiscordEventEnabled("milestone")) return;
        send("🏆 **" + playerName + "** достиг вехи: "
                + statName + " = " + value);
    }

    /**
     * Уведомление о разблокировке достижения.
     */
    public void sendAchievementUnlock(String playerName, String achievementName) {
        if (!cfg().isDiscordEventEnabled("achievement-unlock")) return;
        send("🏆 **" + playerName + "** получил достижение: **"
                + achievementName + "**");
    }

    /**
     * Уведомление о смене топ-1.
     */
    public void sendTopChange(String category, String playerName, long value) {
        if (!cfg().isDiscordEventEnabled("top-change")) return;
        send("👑 Новый #1 в **" + category + "**: **"
                + playerName + "** (" + value + ")");
    }

    /**
     * Уведомление об откате статистики.
     */
    public void sendRollback(String adminName, String playerName,
                             String statName, int value) {
        if (!cfg().isDiscordEventEnabled("rollback")) return;
        send("🔄 **" + adminName + "** откатил статистику **"
                + playerName + "** — `" + statName + "` = " + value);
    }

    // ==================== UTIL ====================

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}