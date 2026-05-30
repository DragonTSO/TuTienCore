package com.turtle.tutiencore.core.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MythicMobsMoneyBonusHookTest {

    @Test
    void parsesMoneyBonusPermission() {
        assertEquals(20.0, MythicMobsMoneyBonusHook.parseMoneyBonusPermission(
                "tutiencore.mythicmoney.bonus.20",
                "tutiencore.mythicmoney.bonus."));
        assertEquals(12.5, MythicMobsMoneyBonusHook.parseMoneyBonusPermission(
                "tutiencore.mythicmoney.bonus.12.5",
                "tutiencore.mythicmoney.bonus."));
        assertEquals(0.0, MythicMobsMoneyBonusHook.parseMoneyBonusPermission(
                "tutiencore.mythicmoney.bonus.nope",
                "tutiencore.mythicmoney.bonus."));
        assertEquals(0.0, MythicMobsMoneyBonusHook.parseMoneyBonusPermission(
                "other.permission",
                "tutiencore.mythicmoney.bonus."));
    }

    @Test
    void calculatesBonusMoney() {
        assertEquals(60, MythicMobsMoneyBonusHook.calculateBonusMoney(300, 20.0));
        assertEquals(38, MythicMobsMoneyBonusHook.calculateBonusMoney(150, 25.0));
        assertEquals(0, MythicMobsMoneyBonusHook.calculateBonusMoney(300, 0.0));
    }
}
