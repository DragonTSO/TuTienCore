package com.turtle.tutiencore.core.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * One-time migrator: splits the legacy monolithic YAML files into per-player files under
 * {@code <plugin>/data/<area>/<uuid>.yml}.
 *
 * <p>Runs once at startup, <b>before</b> any manager loads its data. It is idempotent: if the
 * legacy file is absent (already migrated) it does nothing. After a successful split the legacy file
 * is renamed to {@code <name>.migrated-<timestamp>.bak} (kept, never deleted) so the original data is
 * always recoverable.
 *
 * <p>Layout handled:
 * <ul>
 *   <li>{@code players.yml}        → {@code data/players/<uuid>.yml}        (top-level keys are UUIDs)</li>
 *   <li>{@code player-realms.yml}  → {@code data/realms/<uuid>.yml}        (top-level keys are UUIDs)</li>
 *   <li>{@code equipment-data.yml} → {@code data/equipment/<uuid>.yml}    (top-level keys are UUIDs)</li>
 *   <li>{@code offline-tuluyen.yml}→ {@code data/offline-tuluyen/<uuid>.yml} (top-level keys are UUIDs)</li>
 *   <li>{@code fly-swords.yml}     → {@code data/fly-swords/<uuid>.yml}    (UUIDs nested under {@code players.})</li>
 * </ul>
 */
public final class DataMigrator {

    private final JavaPlugin plugin;

    public DataMigrator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Runs all migrations. Safe to call every startup; each is a no-op once migrated. */
    public void migrateAll() {
        File dataFolder = plugin.getDataFolder();
        // Top-level-UUID files: each top-level section is a player.
        migrateFlat(new File(dataFolder, "players.yml"), "players");
        migrateFlat(new File(dataFolder, "player-realms.yml"), "realms");
        migrateFlat(new File(dataFolder, "equipment-data.yml"), "equipment");
        migrateFlat(new File(dataFolder, "offline-tuluyen.yml"), "offline-tuluyen");
        // Nested layout: UUIDs live under "players.<uuid>".
        migrateNested(new File(dataFolder, "fly-swords.yml"), "fly-swords", "players");
    }

    /**
     * Splits a legacy file whose top-level keys are player UUIDs. Each {@code <uuid>} section becomes
     * the root of {@code data/<area>/<uuid>.yml}. Non-UUID top-level keys are preserved together in a
     * single {@code data/<area>/_shared.yml} so no data is ever dropped.
     */
    private void migrateFlat(File legacyFile, String areaName) {
        if (!legacyFile.isFile()) {
            return; // Already migrated or never existed.
        }
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        PerPlayerYamlStore store = new PerPlayerYamlStore(plugin, areaName);

        int migrated = 0;
        YamlConfiguration shared = new YamlConfiguration();
        boolean hasShared = false;

        for (String key : legacy.getKeys(false)) {
            UUID uuid = parseUuid(key);
            Object value = legacy.get(key);
            if (uuid != null && value instanceof ConfigurationSection section) {
                // Keep the <uuid> prefix inside the per-player file so manager read/write code
                // (which uses uuid-prefixed paths) works unchanged.
                YamlConfiguration out = new YamlConfiguration();
                copySection(section, out.createSection(key));
                if (writeFile(store.fileFor(uuid), out)) {
                    migrated++;
                }
            } else {
                // Non-UUID key (legacy metadata, comments-as-keys, etc.) — keep it.
                shared.set(key, value);
                hasShared = true;
            }
        }

        if (hasShared) {
            writeFile(new File(store.getFolder(), "_shared.yml"), shared);
        }
        finish(legacyFile, areaName, migrated);
    }

    /**
     * Splits a legacy file whose player UUIDs are nested under {@code parentPath} (e.g.
     * {@code players.<uuid>}). Each {@code <uuid>} subsection becomes the root of the per-player file,
     * so callers reading the new per-player file no longer need the parent prefix.
     */
    private void migrateNested(File legacyFile, String areaName, String parentPath) {
        if (!legacyFile.isFile()) {
            return;
        }
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        PerPlayerYamlStore store = new PerPlayerYamlStore(plugin, areaName);

        int migrated = 0;
        ConfigurationSection parent = legacy.getConfigurationSection(parentPath);
        if (parent != null) {
            for (String key : parent.getKeys(false)) {
                UUID uuid = parseUuid(key);
                Object value = parent.get(key);
                if (uuid != null && value instanceof ConfigurationSection section) {
                    // Preserve the "<parentPath>.<uuid>" path inside the per-player file so the
                    // manager's read/write code (which uses that prefix) works unchanged.
                    YamlConfiguration out = new YamlConfiguration();
                    copySection(section, out.createSection(parentPath + "." + key));
                    if (writeFile(store.fileFor(uuid), out)) {
                        migrated++;
                    }
                } else if (uuid != null) {
                    // Scalar value directly under <parentPath>.<uuid> (rare) — preserve the path.
                    YamlConfiguration out = new YamlConfiguration();
                    out.set(parentPath + "." + key, value);
                    if (writeFile(store.fileFor(uuid), out)) {
                        migrated++;
                    }
                }
            }
        }
        finish(legacyFile, areaName, migrated);
    }

    /** Renames the legacy file to a kept {@code .migrated-<ts>.bak} and logs the result. */
    private void finish(File legacyFile, String areaName, int migrated) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backup = new File(legacyFile.getParentFile(), legacyFile.getName() + ".migrated-" + stamp + ".bak");
        if (legacyFile.renameTo(backup)) {
            plugin.getLogger().info("[DataMigrator] " + areaName + ": migrated " + migrated
                    + " player file(s); legacy file kept as " + backup.getName());
        } else {
            plugin.getLogger().warning("[DataMigrator] " + areaName + ": migrated " + migrated
                    + " player file(s) but could NOT rename " + legacyFile.getName()
                    + " — it will be re-migrated next start. Please move it manually.");
        }
    }

    /** Deep-copies a configuration section into a target config root, preserving ItemStacks. */
    private void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            Object value = from.get(key);
            if (value instanceof ConfigurationSection section) {
                copySection(section, to.createSection(key));
            } else {
                to.set(key, value);
            }
        }
    }

    private boolean writeFile(File file, YamlConfiguration config) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), config.saveToString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[DataMigrator] Could not write " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
