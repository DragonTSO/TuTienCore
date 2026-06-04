package com.turtle.tutiencore.core.infusion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfusionTypeTest {

    @Test
    void keepsRarityTuViBonusPercent() {
        InfusionRarity rarity = new InfusionRarity(
                "RARE",
                "Hiem",
                "&9",
                1.0,
                1.2,
                7.5
        );

        assertEquals(7.5, rarity.tuViBonusPercent());
    }

    @Test
    void tuViBonusCombinesTypeStatAndRarityBonus() {
        InfusionRarity rarity = new InfusionRarity(
                "EPIC",
                "Sieu Hiem",
                "&5",
                1.0,
                1.5,
                7.5
        );

        double bonus = InfusionManager.computeTuViBonusPercent(Map.of("TU_VI_BONUS", 10.0), rarity);

        assertEquals(22.5, bonus);
    }

    @Test
    void keepsTypeTuViBonusPercent() {
        InfusionType type = new InfusionType(
                "TU_VI_THIEN_HOA",
                "Tu Vi Thien Hoa",
                "GLOWSTONE_DUST",
                1.0,
                Map.of(),
                12.5,
                InfusionType.TuluyenDropConfig.disabled()
        );

        assertEquals(12.5, type.tuViBonusPercent());
    }

    @Test
    void tuViBonusCombinesRarityAndTypeBonus() {
        InfusionType type = new InfusionType(
                "TU_VI_THIEN_HOA",
                "Tu Vi Thien Hoa",
                "GLOWSTONE_DUST",
                1.0,
                Map.of("TU_VI_BONUS", 2.0),
                10.0,
                InfusionType.TuluyenDropConfig.disabled()
        );
        InfusionRarity rarity = new InfusionRarity(
                "EPIC",
                "Sieu Hiem",
                "&5",
                1.0,
                1.5,
                7.5
        );

        double bonus = InfusionManager.computeTuViBonusPercent(type, rarity);

        assertEquals(25.5, bonus);
    }

    @Test
    void keepsTuluyenDropConfigOnType() {
        InfusionType.TuluyenDropItem drop = new InfusionType.TuluyenDropItem(
                "MMOITEMS",
                "MATERIAL",
                "LINH_QUANG",
                5.0,
                2
        );
        InfusionType.TuluyenDropConfig dropConfig = new InfusionType.TuluyenDropConfig(
                true,
                2,
                true,
                List.of(drop)
        );

        InfusionType type = new InfusionType(
                "THAI_DUONG_CHAN_HOA",
                "Thái Dương Chân Hỏa",
                "FIRE_CHARGE",
                1.0,
                Map.of("ATTACK_DAMAGE", 8.0),
                dropConfig
        );

        assertTrue(type.tuluyenDrops().enabled());
        assertEquals(2, type.tuluyenDrops().rollsPerInterval());
        assertTrue(type.tuluyenDrops().requireTurtleIslandBonus());
        assertEquals(drop, type.tuluyenDrops().items().getFirst());
    }

    @Test
    void defaultTuluyenDropConfigIsDisabled() {
        InfusionType type = new InfusionType(
                "NAM_MINH_LY_HOA",
                "Nam Minh Ly Hỏa",
                "BLAZE_POWDER",
                1.0,
                Map.of()
        );

        assertFalse(type.tuluyenDrops().enabled());
        assertEquals(1, type.tuluyenDrops().rollsPerInterval());
        assertTrue(type.tuluyenDrops().items().isEmpty());
    }

    @Test
    void chanceRollUsesStrictLessThanChance() {
        assertTrue(InfusionManager.rollChance(5.0, 4.99));
        assertFalse(InfusionManager.rollChance(5.0, 5.0));
        assertFalse(InfusionManager.rollChance(0.0, 0.0));
        assertTrue(InfusionManager.rollChance(100.0, 99.99));
    }

    @Test
    void flameDisplayNameShowsClearRarityBadge() {
        InfusionType type = new InfusionType(
                "THAI_DUONG_CHAN_HOA",
                "Thái Dương Chân Hỏa",
                "MUSIC_DISC_CAT",
                1.0,
                Map.of()
        );
        InfusionRarity rarity = new InfusionRarity(
                "LEGENDARY",
                "Thiên Hỏa",
                "&6",
                1.0,
                1.9,
                15.0
        );

        assertEquals("&6&lThái Dương Chân Hỏa &8[&6&lThiên Hỏa&8]",
                InfusionManager.buildInfusionDisplayName(type, rarity));
    }

    @Test
    void statDisplayNamesKeepVietnameseAccents() {
        assertEquals("Linh lực tối đa", InfusionManager.statDisplayNameForTest("MAX_MANA"));
        assertEquals("Hồi phục linh lực", InfusionManager.statDisplayNameForTest("MANA_REGENERATION"));
    }
}
