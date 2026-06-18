package com.turtle.tutiencore.core.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-player YAML store: one {@code <uuid>.yml} file per player under a single area folder
 * (e.g. {@code data/players}, {@code data/realms}).
 *
 * <p>Replaces the old monolithic single-file model where every player's data lived in one YAML.
 * A monolithic save re-serialized <em>all</em> players on every quit; with per-player files a quit
 * serializes only that player's (small) file, which removes the mass-quit serialization spikes that
 * Spark attributed to {@code saveToString} on the server thread.
 *
 * <p>Write model mirrors the proven pattern already used in the managers:
 * <ul>
 *   <li>The caller serializes a {@link YamlConfiguration} to a String on the main thread (consistent
 *       snapshot of Bukkit-owned objects), then hands the String here.</li>
 *   <li>{@link #writeAsync} pushes the disk write off-thread; {@link #writeSync} writes inline for
 *       shutdown (when the scheduler is gone).</li>
 *   <li>Each player file has its own monotonic sequence guard so a slow async write can never
 *       overwrite a newer snapshot of that same player.</li>
 * </ul>
 */
public final class PerPlayerYamlStore {

    private final JavaPlugin plugin;
    private final File folder;
    private final String areaName;

    // Per-file write sequencing: uuid -> last issued seq / last written seq.
    private final ConcurrentHashMap<UUID, AtomicLong> saveSeq = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastWrittenSeq = new ConcurrentHashMap<>();
    // One lock object per player so concurrent writes to different players never block each other,
    // while writes to the same player are serialized.
    private final ConcurrentHashMap<UUID, Object> writeLocks = new ConcurrentHashMap<>();

    /**
     * @param plugin   owning plugin (for scheduler + logging)
     * @param areaName subfolder name under {@code data/}, e.g. {@code "players"}
     */
    public PerPlayerYamlStore(JavaPlugin plugin, String areaName) {
        this.plugin = plugin;
        this.areaName = areaName;
        this.folder = new File(new File(plugin.getDataFolder(), "data"), areaName);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create data folder: " + folder.getPath());
        }
    }

    /** @return the backing folder ({@code <plugin>/data/<area>}). */
    public File getFolder() {
        return folder;
    }

    /** @return the file for a player, whether or not it exists yet. */
    public File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    /** @return {@code true} if a data file already exists for this player. */
    public boolean exists(UUID uuid) {
        return fileFor(uuid).isFile();
    }

    /**
     * Loads a player's YAML. Returns an empty (but valid) configuration if the file does not exist,
     * so callers can treat "new player" and "loaded player" uniformly.
     */
    public YamlConfiguration load(UUID uuid) {
        return YamlConfiguration.loadConfiguration(fileFor(uuid));
    }

    /**
     * Reserves the next write sequence for a player. Call this on the main thread right after taking
     * the serialized snapshot, then pass the returned value to {@link #writeAsync}/{@link #writeSync}.
     */
    public long nextSeq(UUID uuid) {
        return saveSeq.computeIfAbsent(uuid, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Writes a pre-serialized snapshot off the main thread. Stale snapshots (older {@code seq} than
     * one already written for this player) are dropped.
     */
    public void writeAsync(UUID uuid, String serialized, long seq) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeInternal(uuid, serialized, seq));
    }

    /**
     * Writes a pre-serialized snapshot on the calling thread. Use on shutdown, where the scheduler
     * is being torn down and an async task would never run.
     */
    public void writeSync(UUID uuid, String serialized, long seq) {
        writeInternal(uuid, serialized, seq);
    }

    private void writeInternal(UUID uuid, String serialized, long seq) {
        Object lock = writeLocks.computeIfAbsent(uuid, k -> new Object());
        synchronized (lock) {
            Long last = lastWrittenSeq.get(uuid);
            if (last != null && seq <= last) {
                return; // A newer snapshot already hit disk; this one is stale.
            }
            try {
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                Files.write(fileFor(uuid).toPath(), serialized.getBytes(StandardCharsets.UTF_8));
                lastWrittenSeq.put(uuid, seq);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save " + areaName + "/" + uuid + ".yml: " + e.getMessage());
            }
        }
    }

    /**
     * Deletes a player's data file (e.g. when their data is intentionally cleared). Best-effort.
     */
    public void delete(UUID uuid) {
        File file = fileFor(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + areaName + "/" + uuid + ".yml");
        }
    }
}
