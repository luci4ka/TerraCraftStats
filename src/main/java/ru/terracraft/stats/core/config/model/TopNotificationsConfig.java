package ru.terracraft.stats.core.config.model;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.Map;

@Configuration
public record TopNotificationsConfig(

        @Comment("Включить модуль top-notifications")
        boolean enabled,

        @Comment("Как часто проверять (сек)")
        long checkSeconds,

        @Comment("Уведомлять только о смене #1")
        boolean onlyFirstPlace,

        @Comment("Дублировать в Discord")
        boolean sendToDiscord,

        @Comment("Какие категории уведомлять")
        Map<String, Boolean> categories,

        @Comment("Тексты уведомлений")
        Messages messages

) {

    public TopNotificationsConfig {
        if (checkSeconds <= 0) checkSeconds = 300;
        if (categories == null) {
            categories = Map.of(
                    "kills", true, "mobs", true, "blocks", true,
                    "playtime", true, "balance", true
            );
        }
        if (messages == null) messages = new Messages();
    }

    public record Messages(
            @Comment("Смена #1")
            String firstPlace,
            @Comment("Вход в топ-3")
            String top3Enter,
            @Comment("Выход из топ-3")
            String top3Leave,
            @Comment("Отображаемые названия категорий")
            Map<String, String> categories
    ) {
        public Messages() {
            this("👑 {player} обогнал {previous} в {category}!",
                    "🏆 {player} вошёл в топ-3 {category} (#{rank})",
                    "📉 {player} покинул топ-3 {category}",
                    Map.of("kills", "убийствам", "mobs", "мобам",
                            "blocks", "блокам", "playtime", "времени",
                            "balance", "балансу"));
        }
    }
}