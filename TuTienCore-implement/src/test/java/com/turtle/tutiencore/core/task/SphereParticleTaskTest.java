package com.turtle.tutiencore.core.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SphereParticleTaskTest {

    @Test
    void whiteAshSettingsMakeParticlesDenserAndLarger() {
        SphereParticleTask.WhiteAshParticleSettings settings =
                SphereParticleTask.resolveWhiteAshParticleSettings(100);

        assertEquals(200, settings.points());
        assertEquals(2, settings.count());
        assertEquals(0.28D, settings.offsetX());
        assertEquals(0.20D, settings.offsetY());
        assertEquals(0.28D, settings.offsetZ());
        assertEquals(0.02D, settings.extra());
    }
}
