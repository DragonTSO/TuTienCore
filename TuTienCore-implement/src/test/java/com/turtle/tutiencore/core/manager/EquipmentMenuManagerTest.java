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
}
