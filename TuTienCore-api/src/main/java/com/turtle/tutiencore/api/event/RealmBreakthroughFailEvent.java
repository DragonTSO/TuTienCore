package com.turtle.tutiencore.api.event;

import com.turtle.tutiencore.api.realm.Realm;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired when a player fails a major realm breakthrough (died during Thiên Lôi Kiếp).
 * This event is NOT cancellable — the failure has already happened.
 */
public class RealmBreakthroughFailEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Realm currentRealm;
    private final Realm targetRealm;

    public RealmBreakthroughFailEvent(Player player, Realm currentRealm, Realm targetRealm) {
        super(player);
        this.currentRealm = currentRealm;
        this.targetRealm = targetRealm;
    }

    /**
     * The realm the player stays at after failing.
     */
    public Realm getCurrentRealm() { return currentRealm; }

    /**
     * The realm the player was trying to reach.
     */
    public Realm getTargetRealm() { return targetRealm; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
