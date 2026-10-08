package com.playerstats.api.java.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerMilestoneEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String stat;
    private final int threshold;

    public PlayerMilestoneEvent(UUID playerUuid, String stat, int threshold) {
        this.playerUuid = playerUuid;
        this.stat = stat;
        this.threshold = threshold;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getStat() { return stat; }
    public int getThreshold() { return threshold; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}