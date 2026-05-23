package com.turtle.tutiencore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired when Tu Vi is about to be added to a player.
 * Cancel to prevent the Tu Vi from being added.
 * Modify the amount to change how much is added.
 */
public class TuViGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private double amount;
    private final String source;
    private boolean externalBonusIncluded = false;

    /**
     * @param player The player gaining Tu Vi
     * @param amount Amount of Tu Vi being gained
     * @param source Source description (e.g. "dungeon", "mine", "farm", "tuluyen", "command")
     */
    public TuViGainEvent(Player player, double amount, String source) {
        super(player);
        this.amount = amount;
        this.source = source;
    }

    /**
     * Get the amount of Tu Vi being gained.
     */
    public double getAmount() { return amount; }

    /**
     * Set the amount of Tu Vi to be gained (allows modification by other plugins).
     */
    public void setAmount(double amount) { this.amount = amount; }

    /**
     * Get the source of this Tu Vi gain.
     * Common values: "dungeon", "mine", "farm", "tuluyen", "command", "quest"
     */
    public String getSource() { return source; }

    /**
     * True when the amount already includes bonus supplied by another plugin.
     * Event listeners can use this to avoid applying the same external bonus twice.
     */
    public boolean isExternalBonusIncluded() { return externalBonusIncluded; }

    /**
     * Mark this gain as already including an external bonus.
     */
    public void setExternalBonusIncluded(boolean externalBonusIncluded) {
        this.externalBonusIncluded = externalBonusIncluded;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
