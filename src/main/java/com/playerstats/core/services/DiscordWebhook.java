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
    private final ConfigHandler config;
    private final String webhookUrl;
    private final String username;
    private final String avatarUrl;

    public DiscordWebhook(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigHandler.getInstance(plugin);
        this.webhookUrl = config.getConfig().getString("discord.webhook-url", "");
        this.username = config.getConfig().getString("discord.username", "PlayerStats");
        this.avatarUrl = config.getConfig().getString("discord.avatar-url", "");
    }

    public boolean isEnabled() {
        return config.getConfig().getBoolean("discord.enabled", false)
                && webhookUrl != null
                && !webhookUrl.isEmpty()
                && !webhookUrl.equals("https://discord.com/api/webhooks/...");
    }

    /**
     * Отправить уведомление в Discord.
     */
    public void send(String content) {
        if (!isEnabled()) return;

        plugin.getThreadManager().runAsync(() -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "PlayerStats-Webhook");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                // Простой JSON
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"content\":\"").append(escapeJson(content)).append("\"");
                json.append(",\"username\":\"").append(escapeJson(username)).append("\"");
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    json.append(",\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\"");
                }
                json.append("}");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
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

    /**
     * Уведомление о вехе.
     */
    public void sendMilestone(String playerName, String statName, int value) {
        if (!config.getConfig().getBoolean("discord.events.milestone", true)) return;

        String message = "🏆 **" + playerName + "** достиг вехи: "
                + statName + " = " + value;
        send(message);
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}