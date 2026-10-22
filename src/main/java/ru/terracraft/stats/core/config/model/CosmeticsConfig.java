package ru.terracraft.stats.core.config.model;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;

@Configuration
public record CosmeticsConfig(

        @Comment("Радужный эффект на сообщения")
        Rainbow rainbow,

        @Comment("Праздничный градиент")
        Festive festive

) {

    public CosmeticsConfig {
        if (rainbow == null) rainbow = new Rainbow(false, List.of());
        if (festive == null) festive = new Festive(false, List.of());
    }

    public record Rainbow(
            boolean enabled,
            @Comment("К каким ключам применять")
            List<String> applyTo
    ) {}

    public record Festive(
            boolean enabled,
            @Comment("К каким ключам применять")
            List<String> applyTo
    ) {}
}