package com.playerstats.api.java.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Вызывается при изменении любой статы игрока.
 */
public class PlayerStatChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String stat;
    private final int oldValue;
    private int newValue;
    private boolean cancelled;

    public PlayerStatChangeEvent(UUID playerUuid, String stat, int oldValue, int newValue) {
        this.playerUuid = playerUuid;
        this.stat = stat;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getStat() { return stat; }
    public int getOldValue() { return oldValue; }
    public int getNewValue() { return newValue; }

    public void setNewValue(int newValue) { this.newValue = newValue; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}