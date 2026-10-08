package com.playerstats.api.java.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerAchievementUnlockEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String achievementId;
    private final boolean repeat;   // ← НОВОЕ

    public PlayerAchievementUnlockEvent(UUID playerUuid,
                                        String achievementId,
                                        boolean repeat) {
        this.playerUuid = playerUuid;
        this.achievementId = achievementId;
        this.repeat = repeat;
    }

    public UUID getPlayerUuid()      { return playerUuid; }
    public String getAchievementId() { return achievementId; }
    public boolean isRepeat()        { return repeat; }   // ← НОВОЕ

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}