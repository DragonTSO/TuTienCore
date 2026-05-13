package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.RealmTier;
import com.turtle.tutiencore.api.realm.SubRealm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDataManagerTuViCapTest {

    @Test
    void capsTuViAtNextSubRealmRequirement() {
        Realm realm = new Realm(1, "Pham Nhan", "&7Pham Nhan", "Mortal", RealmTier.PHAM_GIOI,
                250, 0, 0, "&7",
                0, 50, 100, 150, 200,
                0, 0, 100, Map.of());

        double result = PlayerDataManager.capTuViAfterAdd(45, 20, new PlayerRealm(1, SubRealm.SO_KY), realm, null);

        assertEquals(50, result);
    }

    @Test
    void capsVienManAtNextMajorRealmRequirement() {
        Realm currentRealm = new Realm(1, "Pham Nhan", "&7Pham Nhan", "Mortal", RealmTier.PHAM_GIOI,
                250, 0, 0, "&7",
                0, 50, 100, 150, 200,
                0, 0, 100, Map.of());
        Realm nextRealm = new Realm(2, "Luyen Khi", "&aLuyen Khi", "Qi Refining", RealmTier.PHAM_GIOI,
                250, 0, 0, "&a",
                250, 500, 750, 1000, 1250,
                0, 0, 100, Map.of());

        double result = PlayerDataManager.capTuViAfterAdd(240, 40, new PlayerRealm(1, SubRealm.VIEN_MAN), currentRealm, nextRealm);

        assertEquals(250, result);
    }

    @Test
    void doesNotClampTuViReduction() {
        Realm realm = new Realm(1, "Pham Nhan", "&7Pham Nhan", "Mortal", RealmTier.PHAM_GIOI,
                250, 0, 0, "&7",
                0, 50, 100, 150, 200,
                0, 0, 100, Map.of());

        double result = PlayerDataManager.capTuViAfterAdd(60, -20, new PlayerRealm(1, SubRealm.SO_KY), realm, null);

        assertEquals(40, result);
    }
}
