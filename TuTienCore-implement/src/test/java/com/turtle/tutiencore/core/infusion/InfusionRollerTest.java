package com.turtle.tutiencore.core.infusion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfusionRollerTest {

    @Test
    void selectsEntryByWeightRange() {
        List<WeightedInfusionOption> options = List.of(
                new WeightedInfusionOption("thuong", 70),
                new WeightedInfusionOption("hiem", 25),
                new WeightedInfusionOption("than_thoai", 5)
        );

        assertEquals("thuong", InfusionRoller.selectWeighted(options, 0).id());
        assertEquals("thuong", InfusionRoller.selectWeighted(options, 69.99D).id());
        assertEquals("hiem", InfusionRoller.selectWeighted(options, 70).id());
        assertEquals("than_thoai", InfusionRoller.selectWeighted(options, 99.99D).id());
    }

    @Test
    void clampsRollPastTotalWeightToLastEntry() {
        List<WeightedInfusionOption> options = List.of(
                new WeightedInfusionOption("a", 1),
                new WeightedInfusionOption("b", 1)
        );

        assertEquals("b", InfusionRoller.selectWeighted(options, 999).id());
    }
}
