package com.turtle.tutiencore.core.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MMOCoreActionBarFilterTest {

    @Test
    void detectsMmocoreHudWithHealthManaAndDefense() {
        assertTrue(MMOCoreActionBarFilter.looksLikeMMOCoreHud("§c120/120 HP §8| §b80/80 Mana §8| §7Def 12"));
    }

    @Test
    void ignoresTuTienCultivationActionBar() {
        assertFalse(MMOCoreActionBarFilter.looksLikeMMOCoreHud("§6✦ +5 Tu Vi §8┃ §7Đang tu luyện..."));
    }
}
