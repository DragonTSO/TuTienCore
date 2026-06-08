package com.turtle.tutiencore.core.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlySwordManagerTest {

    @Test
    void equippedSwordModelOverridesEvolutionLevelModel() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fly-sword.equipped.enabled", true);
        config.set("fly-sword.equipped.models.FLY_SWORD.NETHER_KIEM.model", "kiembay_nether");
        config.set("fly-sword.evolution.levels.3.model", "kiembay_3");

        String model = FlySwordManager.resolveModelId(config, "kiembay", 3, "fly-sword", "nether-kiem");

        assertEquals("kiembay_nether", model);
    }

    @Test
    void disabledEquippedSwordModelFallsBackToEvolutionLevelModel() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fly-sword.equipped.enabled", false);
        config.set("fly-sword.equipped.models.FLY_SWORD.NETHER_KIEM.model", "kiembay_nether");
        config.set("fly-sword.evolution.levels.3.model", "kiembay_3");

        String model = FlySwordManager.resolveModelId(config, "kiembay", 3, "FLY_SWORD", "NETHER_KIEM");

        assertEquals("kiembay_3", model);
    }

    @Test
    void missingEquippedSwordMappingFallsBackToDefaultModelWhenLevelMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fly-sword.equipped.enabled", true);

        String model = FlySwordManager.resolveModelId(config, "kiembay", 2, "FLY_SWORD", "UNKNOWN");

        assertEquals("kiembay", model);
    }

    @Test
    void builtInEquippedSwordMappingWorksWhenConfigMappingIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fly-sword.evolution.levels.1.model", "kiembay");

        String model = FlySwordManager.resolveModelId(config, "kiembay", 1, "FLY_SWORD", "DINH_BA_KIEM");

        assertEquals("kiembay_dinhba", model);
    }
}
