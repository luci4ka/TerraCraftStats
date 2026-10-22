package ru.terracraft.stats.core.config.model;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public record TopChipsConfig(

        @Comment("Включить модуль top-chips")
        boolean enabled,

        @Comment("Как часто обновлять (сек)")
        long refreshSeconds,

        @Comment("Порядок категорий")
        List<String> order,

        @Comment("Значки для категорий (category → иконка)")
        Map<String, String> chips

) {

    public TopChipsConfig {
        if (refreshSeconds <= 0) refreshSeconds = 300;
        if (order == null || order.isEmpty()) {
            order = List.of("kills", "mobs", "blocks", "playtime", "balance");
        }
        if (chips == null) {
            chips = Map.of(
                    "kills", "⚔",
                    "mobs", "☠",
                    "blocks", "⛏",
                    "playtime", "⌛",
                    "balance", "💰"
            );
        }
    }
}