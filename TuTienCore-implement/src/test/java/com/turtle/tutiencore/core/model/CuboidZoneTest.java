package com.turtle.tutiencore.core.model;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuboidZoneTest {

    @Test
    void serializesTuViBonusPercent() {
        CuboidZone zone = new CuboidZone("afk", new Location(null, 1, 2, 3), new Location(null, 4, 5, 6));

        zone.setTuViBonusPercent(35.5D);

        Map<String, Object> serialized = zone.serialize();
        CuboidZone copy = CuboidZone.deserialize("afk", serialized);

        assertEquals(35.5D, serialized.get("tuvi-bonus-percent"));
        assertEquals(35.5D, copy.getTuViBonusPercent());
    }

    @Test
    void clampsNegativeTuViBonusPercentToZero() {
        CuboidZone zone = new CuboidZone("afk", new Location(null, 1, 2, 3), new Location(null, 4, 5, 6));

        zone.setTuViBonusPercent(-10.0D);

        assertEquals(0.0D, zone.getTuViBonusPercent());
    }
}
