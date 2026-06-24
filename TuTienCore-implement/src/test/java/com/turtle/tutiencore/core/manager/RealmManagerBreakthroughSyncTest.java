package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.SubRealm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealmManagerBreakthroughSyncTest {

    @Test
    void syncsBreakthroughCountToCurrentRealmProgression() {
        PlayerRealm playerRealm = new PlayerRealm(4, SubRealm.HAU_KY);
        playerRealm.setBreakthroughCount(3);

        RealmManager.BreakthroughCountSyncResult result = RealmManager.syncBreakthroughCount(playerRealm);

        assertTrue(result.changed());
        assertEquals(3, result.oldCount());
        assertEquals(17, result.newCount());
        assertEquals(17, playerRealm.getBreakthroughCount());
    }

    @Test
    void leavesAlreadyCorrectBreakthroughCountUnchanged() {
        PlayerRealm playerRealm = new PlayerRealm(2, SubRealm.SO_KY);
        playerRealm.setBreakthroughCount(5);

        RealmManager.BreakthroughCountSyncResult result = RealmManager.syncBreakthroughCount(playerRealm);

        assertFalse(result.changed());
        assertEquals(5, result.oldCount());
        assertEquals(5, result.newCount());
        assertEquals(5, playerRealm.getBreakthroughCount());
    }

    @Test
    void syncsBreakthroughCountDownWhenAdminSetsLowerRealm() {
        PlayerRealm playerRealm = new PlayerRealm(2, SubRealm.SO_KY);
        playerRealm.setBreakthroughCount(49);

        RealmManager.BreakthroughCountSyncResult result = RealmManager.syncBreakthroughCount(playerRealm);

        assertTrue(result.changed());
        assertEquals(49, result.oldCount());
        assertEquals(5, result.newCount());
        assertEquals(5, playerRealm.getBreakthroughCount());
    }
}
