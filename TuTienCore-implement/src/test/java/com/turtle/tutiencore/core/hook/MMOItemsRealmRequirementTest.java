package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.api.realm.SubRealm;

import net.Indyuce.mmoitems.api.item.build.LoreBuilder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MMOItemsRealmRequirementTest {

    @Test
    void parsesMajorRealmOnlyRequirement() {
        MMOItemsRealmRequirement requirement = MMOItemsRealmRequirement.parse("4");

        assertEquals(4, requirement.realmId());
        assertTrue(requirement.subRealm().isEmpty());
    }

    @Test
    void parsesMajorRealmAndSubRealmRequirement() {
        MMOItemsRealmRequirement requirement = MMOItemsRealmRequirement.parse("4:trung-ky");

        assertEquals(4, requirement.realmId());
        assertEquals(SubRealm.TRUNG_KY, requirement.subRealm().orElseThrow());
    }

    @Test
    void acceptsPlayerAboveRequiredRealm() {
        MMOItemsRealmRequirement requirement = MMOItemsRealmRequirement.parse("4:vien-man");

        assertTrue(requirement.isMetBy(5, SubRealm.SO_KY));
    }

    @Test
    void rejectsPlayerBelowRequiredSubRealm() {
        MMOItemsRealmRequirement requirement = MMOItemsRealmRequirement.parse("4:trung-ky");

        assertFalse(requirement.isMetBy(4, SubRealm.SO_KY));
    }

    @Test
    void statProvidesDefaultFormatBeforeMmoItemsConfigLoads() {
        MMOItemsRealmRequirementStat stat = new MMOItemsRealmRequirementStat(null);

        assertEquals("{value}", stat.getGeneralStatFormat());
    }

    @Test
    void appendsRequirementLoreWhenMmoItemsFormatHasNoStatMarker() {
        MMOItemsRealmRequirementStat stat = new MMOItemsRealmRequirementStat(null);
        LoreBuilder lore = new LoreBuilder(List.of("Existing lore"));

        stat.applyRequirementLore(lore, MMOItemsRealmRequirement.parse("4"));

        assertEquals(2, lore.getLore().size());
        assertTrue(lore.getLore().get(1).contains("4"));
    }

    @Test
    void weaponRestrictionChecksMainHandOnDirectPlayerDamage() {
        assertTrue(MMOItemsRealmRequirementHook.shouldCheckMainHandForDamage(true));
    }

    @Test
    void readsUnparsedCanUseLevelRequirementFromLore() {
        assertEquals(20, MMOItemsRealmRequirementHook.canUseLoreRequirement("{can-use} Cấp độ 20", "cap do"));
    }

    @Test
    void readsUnparsedCanUseRealmRequirementFromLore() {
        assertEquals(5, MMOItemsRealmRequirementHook.canUseLoreRequirement("{can-use} Cảnh giới 5 - [Nguyên Anh]", "canh gioi"));
    }
}
