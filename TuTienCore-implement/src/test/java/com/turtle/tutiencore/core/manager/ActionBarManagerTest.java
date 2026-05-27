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
    void filtersExpGainSourcesByPrefix() {
        assertTrue(ActionBarManager.isSourceAllowed("MythicMob:Zombie", List.of("MythicMob:")));
        assertFalse(ActionBarManager.isSourceAllowed("command", List.of("MythicMob:")));
        assertTrue(ActionBarManager.isSourceAllowed("command", List.of()));
    }
}
