package com.turtle.tutiencore.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TuViPointRangeTest {

    @Test
    void parsesFixedAmount() {
        TuViPointRange range = TuViPointRange.parse("10", 1);

        assertEquals(10, range.min());
        assertEquals(10, range.max());
    }

    @Test
    void parsesMinMaxAmount() {
        TuViPointRange range = TuViPointRange.parse("5-10", 1);

        assertEquals(10, range.min());
        assertEquals(10, range.max());
    }

    @Test
    void usesLastAmountForLegacyRange() {
        TuViPointRange range = TuViPointRange.parse("10-5", 1);

        assertEquals(5, range.min());
        assertEquals(5, range.max());
    }

    @Test
    void fallsBackForInvalidAmount() {
        TuViPointRange range = TuViPointRange.parse("abc", 7);

        assertEquals(7, range.min());
        assertEquals(7, range.max());
    }
}
