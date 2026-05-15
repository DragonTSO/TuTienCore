package com.turtle.tutiencore;

import com.turtle.tutiencore.core.TuTienCore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TuTienCorePlugin extends JavaPlugin {

    private TuTienCore core;

    @Override
    public void onLoad() {
        bootstrapMMOItemsCustomStats();
    }

    @Override
    public void onEnable() {
        if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().severe("ProtocolLib not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        core = new TuTienCore(this);
        core.onEnable();
        getLogger().info("TuTienCore enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.onDisable();
        }
        getLogger().info("TuTienCore disabled successfully.");
    }

    private void bootstrapMMOItemsCustomStats() {
        File pluginsDir = getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return;
        }

        File customStatsFile = new File(new File(pluginsDir, "MMOItems"), "custom-stats.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(customStatsFile);
        boolean changed = false;

        changed |= setIfMissing(config, "TUTIEN_REALM_REQUIREMENT.name", "TuTien Realm Requirement");
        changed |= setIfMissing(config, "TUTIEN_REALM_REQUIREMENT.type", "text");
        changed |= setIfMissing(config, "TUTIEN_REALM_REQUIREMENT.lore", List.of(
                "Minimum TuTien realm required to use the item.",
                "Examples: 4 or 4:trung-ky."
        ));

        changed |= setIfMissing(config, "MAX_HEALTH_PERCENT.name", "Max Health Percent");
        changed |= setIfMissingOrText(config, "MAX_HEALTH_PERCENT.type", "double");
        changed |= setIfMissing(config, "MAX_HEALTH_PERCENT.lore", List.of(
                "Increases MythicLib MAX_HEALTH by percent.",
                "Example: 10 means +10% max health."
        ));

        if (!changed) {
            return;
        }

        File parent = customStatsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            getLogger().warning("Could not create MMOItems folder for custom stats bootstrap.");
            return;
        }

        try {
            config.save(customStatsFile);
            getLogger().info("Bootstrapped MMOItems custom stats for TuTienCore.");
        } catch (IOException exception) {
            getLogger().warning("Could not save MMOItems custom-stats.yml: " + exception.getMessage());
        }
    }

    private boolean setIfMissing(YamlConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private boolean setIfMissingOrText(YamlConfiguration config, String path, String value) {
        if (!config.contains(path) || "text".equalsIgnoreCase(config.getString(path, ""))) {
            config.set(path, value);
            return true;
        }
        return false;
    }
}
