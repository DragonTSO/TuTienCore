package com.turtle.tutiencore.core.manager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentMenuManagerTest {

    @Test
    void ignoresBlankUpgradeCommands() {
        assertEquals(List.of("say upgraded", "ttc reload"),
                EquipmentMenuManager.executableUpgradeCommands(List.of("", "  ", " say upgraded ", "ttc reload")));
    }

    @Test
    void appliesEquipmentMessagePlaceholders() {
        assertEquals("&dTrang sức &fLinh Ngọc &7đã nhập mạch trong &evĩnh viễn.",
                EquipmentMenuManager.applyEquipmentMessagePlaceholders(
                        "&d%slot_display% &f%item% &7đã nhập mạch trong &e%duration%.",
                        "accessory",
                        "Trang sức",
                        "Linh Ngọc",
                        "vĩnh viễn"));
    }

    @Test
    void rejectsEquipmentWhenRealmRequirementFailsEvenIfMmoItemsAllowsUse() {
        assertEquals(false, EquipmentMenuManager.allEquipmentRequirementsPass(true, false));
    }

    @Test
    void readsUnparsedCanUseLevelRequirementFromLore() {
        assertEquals(20, EquipmentMenuManager.canUseLoreRequirement("{can-use} Cấp độ 20", "cap do"));
    }

    @Test
    void readsUnparsedCanUseRealmRequirementFromLore() {
        assertEquals(4, EquipmentMenuManager.canUseLoreRequirement("{can-use} Cảnh giới 4 - [Kim Đan]", "canh gioi"));
    }
}
