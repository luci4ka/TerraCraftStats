package ru.terracraft.stats.core.config.model;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.Map;

@Configuration
public record SparklineConfig(

        @Comment("Включить модуль sparkline")
        boolean enabled,

        @Comment("Сколько дней истории показывать")
        int days,

        @Comment("Показывать sparkline в /stats")
        boolean showInStats,

        @Comment("Какие категории показывать")
        Map<String, Boolean> categories

) {

    public SparklineConfig {
        if (days <= 0) days = 7;
        if (categories == null) {
            categories = Map.of(
                    "kills", true, "deaths", true, "mobs", true,
                    "blocks", true, "playtime", true, "balance", true
            );
        }
    }
}