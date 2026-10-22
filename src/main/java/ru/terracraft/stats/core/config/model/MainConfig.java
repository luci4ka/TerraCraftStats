package ru.terracraft.stats.core.config.model;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public record MainConfig(
        @Comment("Версия конфига (не менять вручную)") int configVersion,
        @Comment("Язык по умолчанию") String language,
        Lang lang,
        @Comment("Уровень логирования: 0 — WARNING, 1 — INFO, 2 — ALL") int debugLevel,
        Database database,
        MultiServer multiServer,
        @Comment("Цвета/форматы групп") Map<String, String> groups,
        Pvp pvp,
        Rollback rollback,
        Tracking tracking,
        StatsClickCommands statsClickCommands,
        Modules modules,
        @Comment("Разрешить шаринг статистики (/share)") boolean enableStatSharing
) {
    public MainConfig {
        if (configVersion <= 0) configVersion = 6;
        if (language == null || language.isBlank()) language = "ru";
        if (lang == null) lang = new Lang("auto");
        if (debugLevel < 0 || debugLevel > 2) debugLevel = 1;
        if (database == null) database = new Database("playerstats");
        if (multiServer == null) multiServer = new MultiServer(false, "default");
        if (groups == null) groups = Map.of("default", "§rИгрок");
        if (pvp == null) pvp = new Pvp(true, 300L, true, 0);
        if (rollback == null) rollback = new Rollback(60, true, true, false);
        if (tracking == null) tracking = new Tracking(new Tracking.Stats(), List.of(),
                List.of("boss", "wither", "dragon", "elder"));
        if (statsClickCommands == null) statsClickCommands = new StatsClickCommands("", "", "", "", "", "");
        if (modules == null) modules = new Modules(true, true, true, true, true);
    }

    public record Lang(@Comment("auto | minimessage | legacy | json") String format) {}

    public record Database(
            @Comment("ID источника данных в plugins/MineSQL/config.yml") String id
    ) {}

    public record MultiServer(boolean enabled, String serverName) {}

    public record Pvp(boolean perVictimStats, long antifarmCooldownSeconds,
                      boolean ignoreExcludedVictims, int dailyLimitPerVictim) {}

    public record Rollback(int lockSeconds, boolean notifyPlayer, boolean logToConsole, boolean trackDeltas) {}

    public record Tracking(Stats stats, List<String> blocks, List<String> mobBossKeywords) {
        public record Stats(
                // === Существующие ===
                boolean playTime, boolean kills, boolean deaths, boolean mobsKilled,
                boolean blocksPlaced, boolean blocksBroken, boolean distanceWalked,
                boolean damageDealt, boolean damageTaken, boolean itemsCrafted,
                boolean foodConsumed, boolean arrowsShot, boolean fishCaught,
                boolean entitiesTamed, boolean entitiesBred, boolean teleports,
                boolean portalUses, boolean logins, boolean killstreak, boolean bestKillstreak,

                // === Новые (лёгкие) ===
                @Comment("Прыжки") boolean jumps,
                @Comment("Время в приседе (сек)") boolean sneakTime,
                @Comment("Время в беге (сек)") boolean sprintTime,
                @Comment("Дистанция на элитрах (см)") boolean elytraDistance,
                @Comment("Дистанция в лодке (см)") boolean boatDistance,
                @Comment("Дистанция в вагонетке (см)") boolean minecartDistance,
                @Comment("Урон, заблокированный щитом") boolean damageBlocked,
                @Comment("Зачаровано предметов") boolean itemsEnchanted,
                @Comment("Сломано инструментов") boolean itemsBroken,
                @Comment("Пострижено животных") boolean animalsSheared,
                @Comment("Сделок с жителями") boolean villagerTrades,
                @Comment("Собрано урожая") boolean cropsHarvested,
                @Comment("Посажено культур") boolean cropsPlanted,
                @Comment("Снов") boolean sleeps,
                @Comment("Использовано команд") boolean commandsUsed,
                @Comment("Отправлено сообщений в чат") boolean chatMessages,
                @Comment("Использовано тотемов") boolean totemsUsed,
                @Comment("Получено опыта") boolean experienceGained,
                @Comment("Получено уровней") boolean levelsGained,
                @Comment("Сварено зелий") boolean potionsBrewed,
                @Comment("Выпито зелий") boolean potionsDrunk,
                @Comment("Поставлено табличек") boolean signsPlaced,
                @Comment("Поставлено кроватей") boolean bedsPlaced,
                @Comment("Подожжено ТНТ") boolean tntIgnited,
                @Comment("Съедено тортов") boolean cakesEaten,
                @Comment("Построено снеговиков") boolean snowGolemsBuilt,
                @Comment("Добыто алмазов") boolean diamondsMined,
                @Comment("Добыто незерита") boolean netheriteMined,
                @Comment("Дистанция бегом (см)") boolean sprintDistance,
                @Comment("Дистанция в приседе (см)") boolean sneakDistance,
                @Comment("Пройдено по лестницам (см)") boolean climbedLadders,
                @Comment("Ударов в колокол") boolean bellsRung,
                @Comment("Накормлено дельфинов") boolean dolphinsFed
        ) {
            public Stats() {
                this(
                        // существующие
                        true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true,
                        // новые
                        true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true,
                        true, true, true, true, true, true, true, true, true, true,
                        true, true, true
                );
            }
        }
    }

    public record StatsClickCommands(String server, String time, String pve,
                                     String blocks, String pvp, String balance) {}

    public record Modules(boolean topChips, boolean topNotifications,
                          boolean sparkline, boolean discord, boolean cosmetics) {}
}