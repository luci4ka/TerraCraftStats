package ru.terracraft.stats.api;

import ru.terracraft.stats.api.model.LeaderboardEntry;
import ru.terracraft.stats.api.model.PlayerStatsSnapshot;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface PlayerStatsApi {

    // ==================== STATS ====================

    /**
     * @return значение статы или 0, если игрок не найден / стата не записана
     */
    int getStat(Player player, String stat);

    /**
     * @return значение статы или 0, если игрок не найден / стата не записана
     */
    int getStat(UUID uuid, String stat);

    /**
     * @return значение double-статы (например, {@code balance}) или 0.0
     */
    double getStatDouble(UUID uuid, String stat);

    /**
     * @return значение string-статы или {@code null}
     */
    String getStatString(UUID uuid, String stat);

    /**
     * Снимок всех стат игрока.
     *
     * @return снимок или {@code null}, если игрок не найден
     */
    PlayerStatsSnapshot getSnapshot(UUID uuid);

    // ==================== TOP ====================

    /**
     * Топ по стате.
     *
     * <p>Порядок источников: in-memory кэш {@code TopCache} (если стата кэшируется),
     * иначе — прямой SQL-запрос.</p>
     *
     * @param limit максимум записей ({@code limit <= 0} → пустой список)
     * @return иммутабельный список (может быть пустым)
     */
    List<LeaderboardEntry> getTop(String stat, int limit);

    /**
     * Позиция игрока в топе.
     *
     * @return 1-based ранг или 0, если игрок не найден / нет записи по стате
     */
    int getRank(UUID uuid, String stat);

    // ==================== PLAYERS ====================

    /**
     * @return UUID игрока или {@code null}, если не найден
     */
    UUID getUuidByName(String name);

    /**
     * @return имя игрока или {@code null}, если не найден
     */
    String getNameByUuid(UUID uuid);

    /**
     * @return ID игрока в БД или -1, если не найден
     */
    int getPlayerId(UUID uuid);

    // ==================== UTIL ====================

    boolean isExcluded(UUID uuid);

    int getServerId();

    String getServerName();
}