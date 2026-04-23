package com.turtle.tutiencore.api.event;

import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired BEFORE a player's major realm breakthrough begins.
 * Cancel this event to prevent the breakthrough from starting.
 */
public class RealmBreakthroughEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Realm fromRealm;
    private final Realm toRealm;

    public RealmBreakthroughEvent(Player player, Realm fromRealm, Realm toRealm) {
        super(player);
        this.fromRealm = fromRealm;
        this.toRealm = toRealm;
    }

    /**
     * The realm the player is currently at.
     */
    public Realm getFromRealm() { return fromRealm; }

    /**
     * The realm the player is trying to breakthrough to.
     */
    public Realm getToRealm() { return toRealm; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
