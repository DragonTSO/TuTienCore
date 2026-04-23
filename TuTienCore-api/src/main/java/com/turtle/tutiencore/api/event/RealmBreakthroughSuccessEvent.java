package com.turtle.tutiencore.api.event;

import com.turtle.tutiencore.api.realm.Realm;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired AFTER a player successfully breakthroughs to a new major realm.
 * This event is NOT cancellable — the breakthrough has already happened.
 */
public class RealmBreakthroughSuccessEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Realm fromRealm;
    private final Realm toRealm;

    public RealmBreakthroughSuccessEvent(Player player, Realm fromRealm, Realm toRealm) {
        super(player);
        this.fromRealm = fromRealm;
        this.toRealm = toRealm;
    }

    /**
     * The realm the player was at before the breakthrough.
     */
    public Realm getFromRealm() { return fromRealm; }

    /**
     * The realm the player has successfully advanced to.
     */
    public Realm getToRealm() { return toRealm; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
