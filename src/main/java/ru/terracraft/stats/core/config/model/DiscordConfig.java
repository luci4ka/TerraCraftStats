package ru.terracraft.stats.core.config.model;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.Map;

@Configuration
public record DiscordConfig(

        @Comment("Включить Discord webhook")
        boolean enabled,

        @Comment("URL webhook")
        String webhookUrl,

        @Comment("Имя бота")
        String username,

        @Comment("URL аватара")
        String avatarUrl,

        @Comment("Минимальный интервал между сообщениями (сек)")
        long rateLimitSeconds,

        @Comment("Добавлять timestamp")
        boolean useTimestamp,

        @Comment("Упоминание: none | here | everyone | <@id> | <@&id>")
        String mention,

        @Comment("Какие события отправлять")
        Map<String, Boolean> events

) {

    public DiscordConfig {
        if (webhookUrl == null) webhookUrl = "https://discord.com/api/webhooks/...";
        if (username == null || username.isBlank()) username = "PlayerStats";
        if (avatarUrl == null) avatarUrl = "";
        if (rateLimitSeconds < 0) rateLimitSeconds = 5;
        if (mention == null) mention = "none";
        if (events == null) {
            events = Map.of(
                    "milestone", true,
                    "achievement-unlock", true,
                    "top-change", false,
                    "rollback", true
            );
        }
    }
}