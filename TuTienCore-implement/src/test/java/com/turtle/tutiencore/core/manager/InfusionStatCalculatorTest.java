package com.turtle.tutiencore.core.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfusionStatCalculatorTest {

    @Test
    void appliesTierAndEnhancementBonusesForAtkInfusion() {
        assertEquals(112, InfusionStatCalculator.calculateAtkDefGain(50, 10));
    }

    @Test
    void triplesBonusForPerfectPlusFifteenItem() {
        assertEquals(150, InfusionStatCalculator.calculateAtkDefGain(50, 15));
    }

    @Test
    void neverReturnsLessThanBaseValue() {
        assertEquals(1, InfusionStatCalculator.calculateAtkDefGain(1, 0));
    }
}
