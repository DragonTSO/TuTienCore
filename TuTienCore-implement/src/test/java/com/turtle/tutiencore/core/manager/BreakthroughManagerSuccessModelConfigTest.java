package com.turtle.tutiencore.core.manager;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreakthroughManagerSuccessModelConfigTest {

    @Test
    void readsMajorSuccessModelList() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("breakthrough.major-success-models", List.of("dragon_aura", "light_pillar"));

        List<String> models = BreakthroughManager.getSuccessModelIds(config, true);

        assertEquals(List.of("dragon_aura", "light_pillar"), models);
    }

    @Test
    void fallsBackToLegacyMajorSuccessModel() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("breakthrough.major-success-model", "light_pillar");

        List<String> models = BreakthroughManager.getSuccessModelIds(config, true);

        assertEquals(List.of("light_pillar"), models);
    }

    @Test
    void ignoresBlankModelEntries() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("breakthrough.sub-success-models", List.of("dragon_aura", "", "  ", "small_aura"));

        List<String> models = BreakthroughManager.getSuccessModelIds(config, false);

        assertEquals(List.of("dragon_aura", "small_aura"), models);
    }

    @Test
    void activeBreakthroughUsesMythicMobsConfigKey() {
        assertEquals("breakthrough.active-mobs", BreakthroughManager.ACTIVE_BREAKTHROUGH_MOBS_KEY);
    }

    @Test
    void nearbyBreakthroughDamageUsesConfiguredMultiplier() {
        assertEquals(3.5, BreakthroughManager.calculateNearbyPlayerDamage(10.0, 0.35));
    }

    @Test
    void nearbyBreakthroughDamageMultiplierIsClamped() {
        assertEquals(0.0, BreakthroughManager.calculateNearbyPlayerDamage(10.0, -1.0));
        assertEquals(10.0, BreakthroughManager.calculateNearbyPlayerDamage(10.0, 2.0));
    }

    @Test
    void movementLockKeepsHorizontalPositionButAllowsLevitationHeight() {
        Location locked = new Location(null, 10.0, 64.0, -5.0, 0.0f, 0.0f);
        Location attempted = new Location(null, 12.0, 70.0, -8.0, 90.0f, 35.0f);

        Location constrained = BreakthroughManager.constrainBreakthroughMovement(locked, attempted);

        assertEquals(10.0, constrained.getX());
        assertEquals(70.0, constrained.getY());
        assertEquals(-5.0, constrained.getZ());
        assertEquals(90.0f, constrained.getYaw());
        assertEquals(35.0f, constrained.getPitch());
    }
}
