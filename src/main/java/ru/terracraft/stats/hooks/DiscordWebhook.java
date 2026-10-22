package ru.terracraft.stats.hooks;

import ru.terracraft.stats.PlayerStatsPlugin;
import ru.terracraft.stats.core.config.model.DiscordConfig;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class DiscordWebhook {

    private final PlayerStatsPlugin plugin;
    private volatile long lastSendTime = 0L;

    public DiscordWebhook(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    private DiscordConfig cfg() {
        return plugin.getConfigManager().discord();
    }

    public boolean isEnabled() {
        var c = cfg();
        if (c == null || !c.enabled()) return false;
        String url = c.webhookUrl();
        if (url == null || url.isBlank()) return false;
        return !url.equals("https://discord.com/api/webhooks/...");
    }

    public void send(String content) {
        if (!isEnabled()) return;
        var c = cfg();
        long rateLimitMs = c.rateLimitSeconds() * 1000L;
        long now = System.currentTimeMillis();
        if (rateLimitMs > 0 && (now - lastSendTime) < rateLimitMs) return;
        lastSendTime = now;

        plugin.getThreadManager().runAsync(() -> {
            try {
                URL url = new URL(c.webhookUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "PlayerStats-Webhook");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String json = buildJson(content, c);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) plugin.getLogger().warning("Discord Webhook: код " + code);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Discord Webhook: ошибка отправки", e);
            }
        });
    }

    private String buildJson(String content, DiscordConfig c) {
        StringBuilder json = new StringBuilder("{");

        String mention = normalizeMention(c.mention());
        String finalContent = content;
        if (mention != null) {
            finalContent = mention + " " + content;
        }

        json.append("\"content\":\"").append(escapeJson(finalContent)).append("\"");
        if (c.username() != null && !c.username().isEmpty())
            json.append(",\"username\":\"").append(escapeJson(c.username())).append("\"");
        if (c.avatarUrl() != null && !c.avatarUrl().isEmpty())
            json.append(",\"avatar_url\":\"").append(escapeJson(c.avatarUrl())).append("\"");
        if (c.useTimestamp())
            json.append(",\"timestamp\":\"").append(java.time.OffsetDateTime.now()).append("\"");

        // allowed_mentions: разрешаем только то, что запрошено
        json.append(",\"allowed_mentions\":{");
        if (mention == null) {
            json.append("\"parse\":[]");
        } else if (mention.equals("@here")) {
            json.append("\"parse\":[\"everyone\"]");
        } else if (mention.equals("@everyone")) {
            json.append("\"parse\":[\"everyone\"]");
        } else if (mention.startsWith("<@&")) {
            String id = mention.substring(3, mention.length() - 1);
            json.append("\"roles\":[\"").append(escapeJson(id)).append("\"]");
        } else if (mention.startsWith("<@")) {
            String id = mention.substring(2, mention.length() - 1);
            json.append("\"users\":[\"").append(escapeJson(id)).append("\"]");
        } else {
            json.append("\"parse\":[]");
        }
        json.append("}");

        json.append("}");
        return json.toString();
    }

    private String normalizeMention(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("none")) return null;
        if (v.equalsIgnoreCase("here")) return "@here";
        if (v.equalsIgnoreCase("everyone")) return "@everyone";
        return v;
    }

    public void sendTopChange(String category, String playerName, long value) {
        var c = cfg();
        if (c == null || c.events() == null || !c.events().getOrDefault("top-change", false)) return;
        send("👑 Новый #1 в **" + category + "**: **" + playerName + "** (" + value + ")");
    }

    public void sendRollback(String adminName, String playerName, String statName, int value) {
        var c = cfg();
        if (c == null || c.events() == null || !c.events().getOrDefault("rollback", false)) return;
        send("🔄 **" + adminName + "** откатил статистику **" + playerName + "** — `" + statName + "` = " + value);
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }
}