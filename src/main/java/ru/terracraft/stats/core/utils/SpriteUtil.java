package ru.terracraft.stats.core.utils;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;

/**
 * Утилита для вставки ванильных спрайтов через {@link ObjectContents}.
 *
 * <p><b>Версия 1.21.11:</b> предметы переехали из {@code minecraft:blocks}
 * в {@code minecraft:items}. Блоки остались в {@code minecraft:blocks}.
 * Спрайты работают только на клиенте 1.21.9+; для старых — {@code fallback}.</p>
 */
public final class SpriteUtil {

    private SpriteUtil() {}

    /**
     * Спрайт предмета (атлас {@code minecraft:items}).
     *
     * @param spritePath путь внутри атласа, например {@code minecraft:item/diamond}
     * @param fallbackEmoji эмодзи для старых клиентов
     */
    public static Component item(String spritePath, String fallbackEmoji) {
        return sprite("minecraft:items", spritePath, fallbackEmoji);
    }

    /**
     * Спрайт блока (атлас {@code minecraft:blocks}).
     */
    public static Component block(String spritePath, String fallbackEmoji) {
        return sprite("minecraft:blocks", spritePath, fallbackEmoji);
    }

    /**
     * Универсальный спрайт.
     */
    public static Component sprite(String atlas, String spritePath, String fallbackEmoji) {
        try {
            return Component.object(
                    ObjectContents.sprite(
                            Key.key(atlas),
                            Key.key(spritePath)
                    )
            );
        } catch (Throwable t) {
            return Component.text(fallbackEmoji);
        }
    }
}