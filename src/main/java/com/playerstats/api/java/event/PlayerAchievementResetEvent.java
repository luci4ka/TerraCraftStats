package com.playerstats.api.java.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Вызывается, когда временное достижение сбрасывается
 * (например, {@code reset: daily} и прошло 24 часа).
 *
 * <p>После этого события игрок может получить достижение снова.</p>
 */
public class PlayerAchievementResetEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String achievementId;
    private final String resetPeriod;   // "daily" | "weekly" | "monthly"
    private final long lastResetAt;     // когда был предыдущий сброс

    public PlayerAchievementResetEvent(UUID playerUuid,
                                       String achievementId,
                                       String resetPeriod,
                                       long lastResetAt) {
        this.playerUuid = playerUuid;
        this.achievementId = achievementId;
        this.resetPeriod = resetPeriod;
        this.lastResetAt = lastResetAt;
    }

    public UUID getPlayerUuid()          { return playerUuid; }
    public String getAchievementId()     { return achievementId; }
    public String getResetPeriod()       { return resetPeriod; }
    public long getLastResetAt()         { return lastResetAt; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}