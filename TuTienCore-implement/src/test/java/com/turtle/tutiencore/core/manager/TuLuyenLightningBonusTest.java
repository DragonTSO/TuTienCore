package com.turtle.tutiencore.core.manager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuLuyenLightningBonusTest {

    @Test
    void appliesMultiplierWhenRollIsWithinChance() {
        TuLuyenManager.LightningBonusResult result = TuLuyenManager.applyLightningBonus(10.0, true, 20.0, 2.0, 19.99);

        assertTrue(result.triggered());
        assertEquals(20.0, result.points());
    }

    @Test
    void doesNotApplyMultiplierWhenRollMissesChance() {
        TuLuyenManager.LightningBonusResult result = TuLuyenManager.applyLightningBonus(10.0, true, 20.0, 2.0, 20.0);

        assertFalse(result.triggered());
        assertEquals(10.0, result.points());
    }

    @Test
    void disabledBonusNeverTriggers() {
        TuLuyenManager.LightningBonusResult result = TuLuyenManager.applyLightningBonus(10.0, false, 100.0, 2.0, 0.0);

        assertFalse(result.triggered());
        assertEquals(10.0, result.points());
    }

    @Test
    void parsesTuViBonusPermission() {
        assertEquals(100.0, TuLuyenManager.parseTuViBonusPermission("tutiencore.tuvi.bonus.100"));
        assertEquals(12.5, TuLuyenManager.parseTuViBonusPermission("tutiencore.tuvi.bonus.12.5"));
        assertEquals(0.0, TuLuyenManager.parseTuViBonusPermission("tutiencore.tuvi.bonus.nope"));
        assertEquals(0.0, TuLuyenManager.parseTuViBonusPermission("other.permission"));
    }

    @Test
    void resolvesHighestTuViBonusPermission() {
        double bonus = TuLuyenManager.resolveHighestTuViBonus(List.of(
                "tutiencore.tuvi.bonus.20",
                "tutiencore.tuvi.bonus.100",
                "tutiencore.tuvi.bonus.50"
        ));

        assertEquals(100.0, bonus);
    }
}
