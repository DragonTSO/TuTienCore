package com.turtle.tutiencore.core.manager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBarManagerTest {

    @Test
    void appliesBuiltInHealthPlaceholders() {
        assertEquals("HP 19/20", ActionBarManager.applyBuiltInPlaceholders("HP {health}/{max_health}", 18.6D, 20.0D));
    }

    @Test
    void appliesExpGainPlaceholders() {
        assertEquals(
                " +12,345 EXP from Zombie",
                ActionBarManager.applyExpGainPlaceholders(" +{exp_formatted} EXP from {mob}", 12345L, "MythicMob:Zombie")
        );
    }

    @Test
    void appliesMoneyGainPlaceholders() {
        assertEquals(
                " +360 LS (goc 300, bonus 60, 20%) from SoiHoang",
                ActionBarManager.applyMoneyGainPlaceholders(
                        " +{money_formatted} LS (goc {base_money_formatted}, bonus {bonus_money_formatted}, {bonus_percent}%) from {mob}",
                        300L,
                        360L,
                        60L,
                        "MythicMob:SoiHoang")
        );
    }

    @Test
    void filtersExpGainSourcesByPrefix() {
        assertTrue(ActionBarManager.isSourceAllowed("MythicMob:Zombie", List.of("MythicMob:")));
        assertFalse(ActionBarManager.isSourceAllowed("command", List.of("MythicMob:")));
        assertTrue(ActionBarManager.isSourceAllowed("command", List.of()));
    }

    @Test
    void rotatingNeverRepeatsPreviousIndex() {
        int current = 0;
        for (int i = 0; i < 1000; i++) {
            int next = ActionBarManager.pickNextRotatingIndex(current, 4);
            assertTrue(next >= 0 && next < 4, "index out of range: " + next);
            assertFalse(next == current, "rotating message repeated index " + next);
            current = next;
        }
    }

    @Test
    void rotatingSingleMessageAlwaysReturnsZero() {
        assertEquals(0, ActionBarManager.pickNextRotatingIndex(-1, 1));
        assertEquals(0, ActionBarManager.pickNextRotatingIndex(0, 1));
    }
}
