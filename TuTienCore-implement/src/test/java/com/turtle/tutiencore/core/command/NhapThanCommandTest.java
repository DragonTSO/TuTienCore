package com.turtle.tutiencore.core.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NhapThanCommandTest {

    @Test
    void parseUuidReturnsUuidWhenInputValid() {
        UUID expected = UUID.randomUUID();

        UUID parsed = NhapThanCommand.parseUuid(expected.toString());

        assertEquals(expected, parsed);
    }

    @Test
    void parseUuidReturnsNullWhenInputInvalid() {
        assertNull(NhapThanCommand.parseUuid("not-a-uuid"));
    }

    @Test
    void filterByPrefixMatchesIgnoringCase() {
        List<String> input = List.of("COMMON", "RARE", "EPIC", "LEGENDARY");

        List<String> filtered = NhapThanCommand.filterByPrefix(input, "e");

        assertEquals(List.of("EPIC"), filtered);
    }

    @Test
    void filterByPrefixReturnsAllWhenPrefixEmpty() {
        List<String> input = List.of("NAM_MINH_LY_HOA", "THAI_DUONG_CHAN_HOA");

        List<String> filtered = NhapThanCommand.filterByPrefix(input, "");

        assertEquals(input, filtered);
    }
}
