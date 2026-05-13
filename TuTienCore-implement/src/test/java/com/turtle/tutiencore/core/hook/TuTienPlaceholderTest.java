package com.turtle.tutiencore.core.hook;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TuTienPlaceholderTest {

    @Test
    void formatsDotPhaReadyWithConfiguredDisplayNames() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("placeholders.dotpha-ready.ready-display-name", "&aREADY");
        config.set("placeholders.dotpha-ready.not-ready-display-name", "&cNO");

        assertEquals("§aREADY", TuTienPlaceholder.formatDotPhaReady(config, true));
        assertEquals("§cNO", TuTienPlaceholder.formatDotPhaReady(config, false));
    }

    @Test
    void formatsNextTuViRequiredWithConfiguredDisplayName() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("placeholders.dotpha-next-tuvi-required.display-name", "&e{value} Tu Vi");

        assertEquals("§e50,000 Tu Vi", TuTienPlaceholder.formatNextTuViRequired(config, "50,000"));
    }

    @Test
    void formatsNextTuViRequiredAsValueByDefault() {
        YamlConfiguration config = new YamlConfiguration();

        assertEquals("50,000", TuTienPlaceholder.formatNextTuViRequired(config, "50,000"));
    }
}
