package com.turtle.tutiencore.core.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSettingsTest {

    @Test
    void readsPterodactylMysqlSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.enabled", true);
        config.set("database.type", "mysql");
        config.set("database.mysql.host", "172.18.0.1");
        config.set("database.mysql.port", 3306);
        config.set("database.mysql.database", "s123_tutien");
        config.set("database.mysql.username", "u123_tutien");
        config.set("database.mysql.password", "secret");
        config.set("database.mysql.use-ssl", false);

        DatabaseSettings settings = DatabaseSettings.from(config);

        assertTrue(settings.enabled());
        assertEquals("mysql", settings.type());
        assertEquals("172.18.0.1", settings.host());
        assertEquals(3306, settings.port());
        assertEquals("s123_tutien", settings.database());
        assertEquals("u123_tutien", settings.username());
        assertEquals("secret", settings.password());
        assertFalse(settings.useSsl());
    }

    @Test
    void defaultsToDisabledMysql() {
        DatabaseSettings settings = DatabaseSettings.from(new YamlConfiguration());

        assertFalse(settings.enabled());
        assertEquals("mysql", settings.type());
        assertEquals("localhost", settings.host());
        assertEquals(3306, settings.port());
    }
}
