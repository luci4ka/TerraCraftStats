package ru.terracraft.stats.core.config.model;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public record AchievementsConfig(
        @Comment("Включить модуль достижений") boolean enabled,
        @Comment("Настройки уведомлений при получении достижения") Notify notifications,
        @Comment("""
                Достижения по statKey (например, blocks_broken).
                Ключ — имя статистики из stat_types.
                Значение — список порогов, каждый со своим названием и наградой.
                """)
        Map<String, List<Entry>> achievements
) {
    public AchievementsConfig {
        if (notifications == null)
            notifications = new Notify(true, true, false, "entity.player.levelup", true);
        if (achievements == null) achievements = Map.of();
    }

    public record Notify(
            @Comment("Отправлять сообщение в чат") boolean chat,
            @Comment("Показывать Title (большой текст по центру экрана)") boolean title,
            @Comment("Показывать сообщение в action bar (над хотбаром)") boolean actionBar,
            @Comment("""
                    Звук при получении достижения (ключ Bukkit).
                    Примеры: entity.player.levelup, ui.toast.challenge_complete, block.note_block.bell
                    """) String sound,
            @Comment("Запускать фейерверк при получении достижения") boolean firework
    ) {}

    public record Entry(
            @Comment("""
                    Порог срабатывания. Когда значение статистики >= threshold,
                    достижение считается полученным.
                    """) long threshold,
            @Comment("""
                    Название достижения (MiniMessage).
                    Поддерживает цвета: <yellow>, <gold>, <gradient:...>, <rainbow>.
                    Пример: "<yellow>10 блоков"
                    """) String displayName,
            @Comment("""
                    Сообщение при получении (MiniMessage).
                    Пример: "<gray>Вы сломали 10 блоков!"
                    """) String message,
            @Comment("""
                    Описание цели (MiniMessage). Используется в GUI /aach list.
                    Пример: "<gray>Сломайте 10 блоков"
                    """) String goal,
            @Comment("""
                    Награда за достижение (опционально).
                    Если null — достижение выдаётся без награды.
                    """) Reward reward
    ) {}

    public record Reward(
            @Comment("""
                    Деньги через Vault. Работает только если Vault и экономический плагин установлены.
                    При /aach give НЕ выдаются (защита от абуза).
                    """) int money,
            @Comment("""
                    Команда от консоли при получении. %player% заменяется на ник.
                    Пример: "give %player% diamond 1"
                    При /aach give НЕ выполняется (защита от абуза).
                    """) String command
    ) {
        public Reward {
            if (command == null) command = "";
        }
    }
}