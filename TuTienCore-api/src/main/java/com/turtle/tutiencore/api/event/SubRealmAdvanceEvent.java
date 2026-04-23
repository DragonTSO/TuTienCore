package com.turtle.tutiencore.api.event;

import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired when a player advances to a new sub-realm (tầng nhỏ).
 * Example: Sơ Kỳ → Trung Kỳ
 * This event is NOT cancellable.
 */
public class SubRealmAdvanceEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Realm realm;
    private final SubRealm fromSubRealm;
    private final SubRealm toSubRealm;

    public SubRealmAdvanceEvent(Player player, Realm realm, SubRealm fromSubRealm, SubRealm toSubRealm) {
        super(player);
        this.realm = realm;
        this.fromSubRealm = fromSubRealm;
        this.toSubRealm = toSubRealm;
    }

    /**
     * The current major realm.
     */
    public Realm getRealm() { return realm; }

    /**
     * The sub-realm the player was at before.
     */
    public SubRealm getFromSubRealm() { return fromSubRealm; }

    /**
     * The sub-realm the player has advanced to.
     */
    public SubRealm getToSubRealm() { return toSubRealm; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
