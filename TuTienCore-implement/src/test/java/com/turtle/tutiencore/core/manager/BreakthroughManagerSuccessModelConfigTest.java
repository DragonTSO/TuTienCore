package com.turtle.tutiencore.core.manager;

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
}
