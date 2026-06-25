package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTienAPI;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.infusion.OwnedInfusion;
import com.turtle.tutiencore.core.storage.PerPlayerYamlStore;
import com.turtle.tutiencore.core.storage.PlayerProgressDatabaseSync;
import com.turtle.tutiencore.core.storage.LocalDataCache;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerDataManager implements Listener, TuTienAPI {
    private static final int MAX_INFUSION_INVENTORY = 270;

    private final JavaPlugin plugin;
    private FileConfiguration config;
    // Per-player file store (data/players/<uuid>.yml). The in-memory `config` is kept as an
    // aggregate of every player's section (loaded from all per-player files at startup) so the
    // leaderboard can scan all players cheaply; saves, however, write only the single player's
    // section to their own file instead of re-serializing every player.
    private PerPlayerYamlStore store;
    // Guards mutations of the aggregate `config` so a per-player snapshot is always consistent.
    private final Object configLock = new Object();
    private final Map<UUID, Double> tuviCache = new HashMap<>();
    private final Map<UUID, Long> tuLuyenTotalSecondsCache = new HashMap<>();
    private final Map<UUID, List<OwnedInfusion>> infusionInventoryCache = new HashMap<>();
    private final Map<UUID, String> equippedInfusionIdCache = new HashMap<>();

    // These are injected after construction
    private RealmManager realmManager;
    private BreakthroughManager breakthroughManager;
    private TuLuyenManager tuLuyenManager;
    private PlayerProgressDatabaseSync databaseSync;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Auto-save online player data periodically to prevent data loss on crash.
        // With database-primary mode this is a DB upsert, so keep interval reasonable.
        long intervalSeconds = Math.max(10L, Math.min(600L, plugin.getConfig().getLong("auto-save-interval-seconds", 30L)));
        if (intervalSeconds > 0) {
            long intervalTicks = intervalSeconds * 20L;
            plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveOnlinePlayers, intervalTicks, intervalTicks);
            plugin.getLogger().info("PlayerDataManager auto-save enabled: every " + intervalSeconds + " seconds");
        } else {
            plugin.getLogger().warning("PlayerDataManager auto-save is DISABLED! Data will be lost on crash.");
        }

        // Load online players (in case of plugin reload).
        // Database loads happen after databaseSync is injected via setDatabaseSync(); for a reload
        // while players are online we trigger an immediate DB load for each online player.
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }
    }

    /**
     * Inject managers after they are constructed (avoids circular dependency).
     */
    public void injectManagers(RealmManager realmManager, BreakthroughManager breakthroughManager, TuLuyenManager tuLuyenManager) {
        this.realmManager = realmManager;
        this.breakthroughManager = breakthroughManager;
        this.tuLuyenManager = tuLuyenManager;
    }

    public void setDatabaseSync(PlayerProgressDatabaseSync databaseSync) {
        this.databaseSync = databaseSync;
        // If players are already online (reload scenario), trigger DB load for each now that
        // the database sync is available.
        for (Player player : Bukkit.getOnlinePlayers()) {
            databaseSync.loadFromDatabase(player.getUniqueId());
        }
    }

    /**
     * Replaces the in-memory infusion inventory and equipped-infusion-id for a player.
     * Called from the database load callback (main thread).
     */
    public void replaceInfusionInventory(UUID uuid, java.util.List<OwnedInfusion> inventory, String equippedInfusionId) {
        infusionInventoryCache.put(uuid, new ArrayList<>(inventory));
        if (equippedInfusionId == null || equippedInfusionId.isBlank()) {
            equippedInfusionIdCache.remove(uuid);
        } else {
            equippedInfusionIdCache.put(uuid, equippedInfusionId);
        }
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        store = new PerPlayerYamlStore(plugin, "players");
        // Aggregate in-memory config, built from every per-player file. Disk writes go to the
        // per-player store, not to a single monolithic file.
        config = new YamlConfiguration();
        loadAllPlayerFiles();
    }

    /**
     * Loads every {@code data/players/<uuid>.yml} into the aggregate {@link #config}. Each per-player
     * file already stores its data under the {@code <uuid>.*} prefix (preserved by the migrator), so
     * merging is a straight section copy and all existing uuid-prefixed read code keeps working.
     */
    private void loadAllPlayerFiles() {
        File folder = store.getFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        synchronized (configLock) {
            for (File playerFile : files) {
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                for (String key : playerConfig.getKeys(false)) {
                    config.set(key, playerConfig.get(key));
                }
            }
        }
    }

    /**
     * Serializes a single player's {@code <uuid>.*} subtree from the aggregate config and writes it
     * to {@code data/players/<uuid>.yml}. Returns the serialized snapshot + a fresh write sequence,
     * or {@code null} if the player has no data. Caller must hold {@link #configLock}.
     */
    private SinglePlayerSnapshot snapshotPlayer(UUID uuid) {
        ConfigurationSection section = config.getConfigurationSection(uuid.toString());
        YamlConfiguration out = new YamlConfiguration();
        if (section != null) {
            // Re-nest under the uuid prefix so the on-disk layout matches what loadAllPlayerFiles
            // expects and what the migrator produced.
            ConfigurationSection target = out.createSection(uuid.toString());
            for (String key : section.getKeys(false)) {
                target.set(key, section.get(key));
            }
        }
        String serialized;
        try {
            serialized = out.saveToString();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not serialize players/" + uuid + ".yml: " + e.getMessage());
            return null;
        }
        return new SinglePlayerSnapshot(serialized, store.nextSeq(uuid));
    }

    private record SinglePlayerSnapshot(String serialized, long seq) {
    }

    public void saveAll() {
        // Flush caches into config for leaderboard reads
        synchronized (configLock) {
            for (Map.Entry<UUID, Double> entry : tuviCache.entrySet()) {
                config.set(entry.getKey().toString() + ".tuvi", entry.getValue());
            }
            for (Map.Entry<UUID, Long> entry : tuLuyenTotalSecondsCache.entrySet()) {
                writeTuLuyenTime(entry.getKey(), entry.getValue());
            }
            Set<UUID> uuids = new HashSet<>();
            uuids.addAll(infusionInventoryCache.keySet());
            uuids.addAll(equippedInfusionIdCache.keySet());
            for (UUID uuid : uuids) {
                writeInfusionState(uuid);
            }
        }
        // Force-write JSON cache then DB (blocking) for every loaded player on shutdown
        Set<UUID> all = new HashSet<>();
        all.addAll(tuviCache.keySet());
        all.addAll(tuLuyenTotalSecondsCache.keySet());
        for (UUID uuid : all) {
            forceSyncPlayerCache(uuid);
            if (databaseSync != null) {
                databaseSync.syncBlocking(uuid);
            }
        }
    }

    /**
     * Auto-save for online players only, called periodically to prevent data loss on crash.
     * Flushes in-memory caches to config (for leaderboard), then saves to database async.
     */
    private void saveOnlinePlayers() {
        synchronized (configLock) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (tuviCache.containsKey(uuid)) {
                    config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
                }
                if (tuLuyenTotalSecondsCache.containsKey(uuid)) {
                    writeTuLuyenTime(uuid, tuLuyenTotalSecondsCache.get(uuid));
                }
                if (infusionInventoryCache.containsKey(uuid) || equippedInfusionIdCache.containsKey(uuid)) {
                    writeInfusionState(uuid);
                }
            }
        }
        // Force-write JSON cache for every online player (guarantees cache is always current)
        // then fire async DB saves
        for (Player player : Bukkit.getOnlinePlayers()) {
            forceSyncPlayerCache(player.getUniqueId());
            syncDatabase(player.getUniqueId());
        }
    }

    public void savePlayer(UUID uuid) {
        synchronized (configLock) {
            if (tuviCache.containsKey(uuid)) {
                config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
            }
            if (tuLuyenTotalSecondsCache.containsKey(uuid)) {
                writeTuLuyenTime(uuid, tuLuyenTotalSecondsCache.get(uuid));
            }
            if (infusionInventoryCache.containsKey(uuid) || equippedInfusionIdCache.containsKey(uuid)) {
                writeInfusionState(uuid);
            }
        }
        forceSyncPlayerCache(uuid);
        syncDatabase(uuid);
    }

    /**
     * Like {@link #savePlayer(UUID)} but uses async DB write.
     * The JSON cache is written SYNCHRONOUSLY first (before caches are evicted) so a fast
     * relog always has a valid cache to read from.
     * Do NOT use on shutdown.
     */
    public void savePlayerAsync(UUID uuid) {
        synchronized (configLock) {
            if (tuviCache.containsKey(uuid)) {
                config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
            }
            if (tuLuyenTotalSecondsCache.containsKey(uuid)) {
                writeTuLuyenTime(uuid, tuLuyenTotalSecondsCache.get(uuid));
            }
            if (infusionInventoryCache.containsKey(uuid) || equippedInfusionIdCache.containsKey(uuid)) {
                writeInfusionState(uuid);
            }
        }
        // Force-write JSON cache synchronously BEFORE async DB and BEFORE caches are evicted.
        // This is the critical write that prevents Tu Vi → 0 on fast relog.
        forceSyncPlayerCache(uuid);
        syncDatabase(uuid);
    }

    public boolean savePlayerSafely(UUID uuid) {
        synchronized (configLock) {
            if (tuviCache.containsKey(uuid)) {
                config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
            }
            if (tuLuyenTotalSecondsCache.containsKey(uuid)) {
                writeTuLuyenTime(uuid, tuLuyenTotalSecondsCache.get(uuid));
            }
            if (infusionInventoryCache.containsKey(uuid) || equippedInfusionIdCache.containsKey(uuid)) {
                writeInfusionState(uuid);
            }
        }
        forceSyncPlayerCache(uuid);
        syncDatabase(uuid);
        return true;
    }

    /**
     * Flushes in-memory caches to the config map without serializing or writing to disk.
     * Use this when you need the config to reflect current cache state (e.g., for top queries)
     * but don't need a full disk save. Avoids the expensive sanitize + YAML serialization.
     */
    public void flushCachesToConfig() {
        synchronized (configLock) {
            for (Map.Entry<UUID, Double> entry : tuviCache.entrySet()) {
                config.set(entry.getKey().toString() + ".tuvi", entry.getValue());
            }
            for (Map.Entry<UUID, Long> entry : tuLuyenTotalSecondsCache.entrySet()) {
                writeTuLuyenTime(entry.getKey(), entry.getValue());
            }
            Set<UUID> uuids = new HashSet<>();
            uuids.addAll(infusionInventoryCache.keySet());
            uuids.addAll(equippedInfusionIdCache.keySet());
            for (UUID uuid : uuids) {
                writeInfusionState(uuid);
            }
        }
    }

    public void loadPlayer(UUID uuid) {
        // ── Step 1: seed from YAML aggregate (immediate, always available) ──
        synchronized (configLock) {
            double tuvi = config.getDouble(uuid.toString() + ".tuvi", 0.0);
            tuviCache.put(uuid, tuvi);
            tuLuyenTotalSecondsCache.put(uuid, readTuLuyenTotalSeconds(uuid));
            InfusionState state = readInfusionState(uuid);
            infusionInventoryCache.put(uuid, new ArrayList<>(state.inventory()));
            if (state.equippedId() == null || state.equippedId().isBlank()) {
                equippedInfusionIdCache.remove(uuid);
            } else {
                equippedInfusionIdCache.put(uuid, state.equippedId());
            }
        }

        // ── Step 2: overwrite with JSON cache SYNCHRONOUSLY (no async, no wait) ──
        // The JSON cache is always written before async DB, so it is always >= YAML and >= DB.
        // Reading it here prevents Tu Vi from ever showing 0 to the player.
        if (databaseSync != null) {
            LocalDataCache cache = databaseSync.getLocalCache();
            if (cache != null) {
                LocalDataCache.PlayerProgressSnapshot snap = cache.loadPlayerProgress(uuid);
                if (snap != null) {
                    tuviCache.put(uuid, snap.tuvi());
                    tuLuyenTotalSecondsCache.put(uuid, snap.tuLuyenSeconds());
                    List<OwnedInfusion> infusions = new ArrayList<>();
                    for (LocalDataCache.InfusionEntry e : snap.infusions()) {
                        infusions.add(new OwnedInfusion(e.id(), e.typeId(), e.rarityId(), e.createdAt()));
                    }
                    replaceInfusionInventory(uuid, infusions, snap.equippedInfusionId());
                    // Apply realm from cache immediately (no async needed)
                    if (realmManager != null) {
                        com.turtle.tutiencore.api.realm.SubRealm subRealm = com.turtle.tutiencore.api.realm.SubRealm.SO_KY;
                        try { subRealm = com.turtle.tutiencore.api.realm.SubRealm.valueOf(snap.subRealm()); }
                        catch (IllegalArgumentException ignored) {}
                        com.turtle.tutiencore.api.realm.PlayerRealm realm =
                                new com.turtle.tutiencore.api.realm.PlayerRealm(snap.realmId(), subRealm);
                        realm.setBreakthroughCount(snap.breakthroughCount());
                        realm.setBreakthroughCooldown(snap.breakthroughCooldown());
                        realmManager.setPlayerRealmObject(uuid, realm);
                    }
                    // Cache hit → no DB load needed (cache is always newer)
                    return;
                }
            }
        }

        // ── Step 3: no cache → async DB load (first join or post-clean-shutdown) ──
        if (databaseSync != null) {
            databaseSync.loadFromDatabase(uuid);
        }
    }

    public List<OwnedInfusion> getInfusionInventory(UUID uuid) {
        return List.copyOf(infusionInventoryCache.getOrDefault(uuid, Collections.emptyList()));
    }

    public Optional<OwnedInfusion> getEquippedInfusion(UUID uuid) {
        String equippedId = equippedInfusionIdCache.get(uuid);
        if (equippedId == null || equippedId.isBlank()) {
            return Optional.empty();
        }
        return getInfusionInventory(uuid).stream()
                .filter(owned -> equippedId.equals(owned.id()))
                .findFirst();
    }

    public Optional<OwnedInfusion> findInfusion(UUID uuid, String infusionId) {
        if (infusionId == null || infusionId.isBlank()) {
            return Optional.empty();
        }
        return getInfusionInventory(uuid).stream()
                .filter(owned -> infusionId.equals(owned.id()))
                .findFirst();
    }

    public boolean canAddInfusion(UUID uuid) {
        return getInfusionInventory(uuid).size() < MAX_INFUSION_INVENTORY;
    }

    public boolean addInfusion(UUID uuid, OwnedInfusion infusion) {
        if (infusion == null || !canAddInfusion(uuid)) {
            return false;
        }

        List<OwnedInfusion> inventory = new ArrayList<>(infusionInventoryCache.getOrDefault(uuid, Collections.emptyList()));
        inventory.add(infusion);
        infusionInventoryCache.put(uuid, inventory);
        return savePlayerSafely(uuid);
    }

    public boolean setEquippedInfusionId(UUID uuid, String infusionId) {
        if (infusionId != null && !infusionId.isBlank()) {
            boolean exists = getInfusionInventory(uuid).stream().anyMatch(owned -> infusionId.equals(owned.id()));
            if (!exists) {
                return false;
            }
            equippedInfusionIdCache.put(uuid, infusionId);
        } else {
            equippedInfusionIdCache.remove(uuid);
        }
        return savePlayerSafely(uuid);
    }

    public String getEquippedInfusionId(UUID uuid) {
        return equippedInfusionIdCache.get(uuid);
    }

    private long readTuLuyenTotalSeconds(UUID uuid) {
        String path = uuid.toString() + ".tuluyen";
        if (config.contains(path + ".total-seconds")) {
            return Math.max(0L, config.getLong(path + ".total-seconds", 0L));
        }

        // Legacy-friendly fallback if a previous/manual config used total-time.
        return Math.max(0L, config.getLong(path + ".total-time", 0L));
    }

    private void writeTuLuyenTime(UUID uuid, long seconds) {
        config.set(uuid.toString() + ".tuluyen.total-seconds", Math.max(0L, seconds));
    }

    private InfusionState readInfusionState(UUID uuid) {
        String path = uuid.toString() + ".infusion";

        List<OwnedInfusion> inventory = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        List<Map<?, ?>> raw = config.getMapList(path + ".inventory");
        for (Map<?, ?> row : raw) {
            String id = asString(row.get("id"));
            String typeId = asString(row.get("type"));
            String rarityId = asString(row.get("rarity"));
            long createdAt = asLong(row.get("created-at"), System.currentTimeMillis());

            if (typeId.isBlank() || rarityId.isBlank()) {
                continue;
            }

            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            while (!seenIds.add(id)) {
                id = UUID.randomUUID().toString();
            }

            inventory.add(new OwnedInfusion(id, typeId, rarityId, createdAt));
        }

        // Legacy migration: claimed/type/rarity/created-at
        if (inventory.isEmpty() && config.getBoolean(path + ".claimed", false)) {
            String typeId = config.getString(path + ".type", "");
            String rarityId = config.getString(path + ".rarity", "");
            if (!typeId.isBlank() && !rarityId.isBlank()) {
                inventory.add(OwnedInfusion.create(typeId, rarityId, config.getLong(path + ".created-at", System.currentTimeMillis())));
            }
        }

        String equippedId = config.getString(path + ".equipped-id", "");
        boolean equippedExists = false;
        if (!equippedId.isBlank()) {
            for (OwnedInfusion owned : inventory) {
                if (equippedId.equals(owned.id())) {
                    equippedExists = true;
                    break;
                }
            }
        }
        if (equippedId.isBlank() || !equippedExists) {
            equippedId = null;
        }

        return new InfusionState(inventory, equippedId);
    }

    private void writeInfusionState(UUID uuid) {
        String path = uuid.toString() + ".infusion";

        List<OwnedInfusion> inventory = infusionInventoryCache.getOrDefault(uuid, Collections.emptyList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OwnedInfusion infusion : inventory) {
            if (infusion == null) {
                continue;
            }
            String id = asString(infusion.id());
            String typeId = asString(infusion.typeId());
            String rarityId = asString(infusion.rarityId());
            if (id.isBlank() || typeId.isBlank() || rarityId.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("type", typeId);
            row.put("rarity", rarityId);
            row.put("created-at", infusion.createdAt());
            rows.add(row);
        }
        config.set(path + ".inventory", rows);

        String equippedId = equippedInfusionIdCache.get(uuid);
        if (equippedId == null || equippedId.isBlank()) {
            config.set(path + ".equipped-id", null);
        } else {
            config.set(path + ".equipped-id", equippedId);
        }
    }

    /**
     * Synchronously serializes and writes ONE player's data file ({@code data/players/<uuid>.yml}).
     * Use on shutdown / safe-save paths where the write must complete before returning. Only this
     * player's small subtree is serialized, not every player.
     */
    private boolean writePlayerSync(UUID uuid) {
        final SinglePlayerSnapshot snap;
        synchronized (configLock) {
            sanitizePlayerBeforeSave(uuid);
            snap = snapshotPlayer(uuid);
        }
        if (snap == null) {
            return false;
        }
        store.writeSync(uuid, snap.serialized(), snap.seq());
        return true;
    }

    /**
     * Serializes ONE player's data under {@code configLock} (consistent snapshot) then writes it to
     * disk off the main thread. Use on player quit so the YAML write never blocks the tick.
     *
     * <p>Must NOT be used on shutdown: the Bukkit scheduler is torn down before the async task can
     * run, which would lose the write. Use {@link #writePlayerSync(UUID)} there instead.
     */
    private void writePlayerAsync(UUID uuid) {
        final SinglePlayerSnapshot snap;
        synchronized (configLock) {
            sanitizePlayerBeforeSave(uuid);
            snap = snapshotPlayer(uuid);
        }
        if (snap == null) {
            return;
        }
        store.writeAsync(uuid, snap.serialized(), snap.seq());
    }

    private void sanitizeConfigBeforeSave() {
        sanitizeInfusionConfigBeforeSave();

        List<String> paths = new ArrayList<>(config.getKeys(true));
        for (String path : paths) {
            Object value = config.get(path);
            if (value == null || value instanceof ConfigurationSection) {
                continue;
            }

            Object sanitized = sanitizeYamlValue(value);
            if (sanitized == null) {
                config.set(path, null);
                continue;
            }
            if (!Objects.equals(value, sanitized)) {
                config.set(path, sanitized);
            }
        }
    }

    /**
     * Sanitizes only ONE player's subtree ({@code <uuid>.*}) before a per-player save, mirroring
     * {@link #sanitizeConfigBeforeSave()} but scoped to a single player so a quit save never has to
     * walk every player's keys. Caller must hold {@link #configLock}.
     */
    private void sanitizePlayerBeforeSave(UUID uuid) {
        ConfigurationSection section = config.getConfigurationSection(uuid.toString());
        if (section == null) {
            return;
        }

        // Infusion inventory rows: coerce to clean string/long maps (drops malformed rows).
        String inventoryPath = uuid + ".infusion.inventory";
        if (config.isList(inventoryPath)) {
            List<?> rawRows = config.getList(inventoryPath, Collections.emptyList());
            List<Map<String, Object>> sanitizedRows = new ArrayList<>();
            for (Object rawRow : rawRows) {
                if (!(rawRow instanceof Map<?, ?> row)) {
                    continue;
                }
                String id = asString(row.get("id"));
                String typeId = asString(row.get("type"));
                String rarityId = asString(row.get("rarity"));
                if (id.isBlank() || typeId.isBlank() || rarityId.isBlank()) {
                    continue;
                }
                Map<String, Object> sanitizedRow = new LinkedHashMap<>();
                sanitizedRow.put("id", id);
                sanitizedRow.put("type", typeId);
                sanitizedRow.put("rarity", rarityId);
                sanitizedRow.put("created-at", asLong(row.get("created-at"), System.currentTimeMillis()));
                sanitizedRows.add(sanitizedRow);
            }
            config.set(inventoryPath, sanitizedRows);
        }

        // Sanitize every scalar leaf under this player's subtree.
        for (String relative : new ArrayList<>(section.getKeys(true))) {
            Object value = section.get(relative);
            if (value == null || value instanceof ConfigurationSection) {
                continue;
            }
            Object sanitized = sanitizeYamlValue(value);
            if (sanitized == null) {
                section.set(relative, null);
            } else if (!Objects.equals(value, sanitized)) {
                section.set(relative, sanitized);
            }
        }
    }

    private Object sanitizeYamlValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Character) {
            return value;
        }
        if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d)) {
                return 0.0D;
            }
            if (number instanceof Float f && !Float.isFinite(f)) {
                return 0.0F;
            }
            return value;
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof ConfigurationSerializable serializable) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            sanitized.put("==", ConfigurationSerialization.getAlias(serializable.getClass()));
            for (Map.Entry<String, Object> entry : serializable.serialize().entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                Object child = sanitizeYamlValue(entry.getValue());
                if (child != null) {
                    sanitized.put(entry.getKey(), child);
                }
            }
            return sanitized;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object rawKey = entry.getKey();
                if (rawKey == null) {
                    continue;
                }
                String key = String.valueOf(rawKey).trim();
                if (key.isBlank()) {
                    continue;
                }
                Object child = sanitizeYamlValue(entry.getValue());
                if (child != null) {
                    sanitized.put(key, child);
                }
            }
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                Object child = sanitizeYamlValue(item);
                if (child != null) {
                    sanitized.add(child);
                }
            }
            return sanitized;
        }
        if (value.getClass().isArray()) {
            List<Object> sanitized = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object child = sanitizeYamlValue(java.lang.reflect.Array.get(value, i));
                if (child != null) {
                    sanitized.add(child);
                }
            }
            return sanitized;
        }
        return String.valueOf(value);
    }

    private void sanitizeInfusionConfigBeforeSave() {
        for (String key : config.getKeys(false)) {
            String inventoryPath = key + ".infusion.inventory";
            if (!config.isList(inventoryPath)) {
                continue;
            }

            List<?> rawRows = config.getList(inventoryPath, Collections.emptyList());
            List<Map<String, Object>> sanitizedRows = new ArrayList<>();
            boolean changed = false;

            for (Object rawRow : rawRows) {
                if (!(rawRow instanceof Map<?, ?> row)) {
                    changed = true;
                    continue;
                }

                String id = asString(row.get("id"));
                String typeId = asString(row.get("type"));
                String rarityId = asString(row.get("rarity"));
                if (id.isBlank() || typeId.isBlank() || rarityId.isBlank()) {
                    changed = true;
                    continue;
                }

                Map<String, Object> sanitizedRow = new LinkedHashMap<>();
                sanitizedRow.put("id", id);
                sanitizedRow.put("type", typeId);
                sanitizedRow.put("rarity", rarityId);
                sanitizedRow.put("created-at", asLong(row.get("created-at"), System.currentTimeMillis()));
                sanitizedRows.add(sanitizedRow);

                if (!Objects.equals(row, sanitizedRow)) {
                    changed = true;
                }
            }

            if (changed) {
                config.set(inventoryPath, sanitizedRows);
            }
        }
    }

    private static String asString(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }

    private static long asLong(Object input, long fallback) {
        if (input instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(asString(input));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record InfusionState(List<OwnedInfusion> inventory, String equippedId) {
    }

    // ==========================================
    // TU VI OPERATIONS
    // ==========================================

    @Override
    public double getTuVi(UUID uuid) {
        return tuviCache.getOrDefault(uuid, 0.0);
    }

    @Override
    public void setTuVi(UUID uuid, double amount) {
        tuviCache.put(uuid, amount);
        // Write to JSON cache immediately so data survives fast relog/crash.
        // Throttled: only write if the value changed by at least 1 point OR it's been 5s since
        // last write — avoids excessive file I/O during tu luyen (called every interval).
        writeTuViCacheThrottled(uuid, amount);
    }

    /**
     * Force-writes the complete player snapshot to the JSON cache, bypassing throttle.
     * Called on quit to guarantee the cache is always up-to-date before caches are evicted.
     */
    private void forceSyncPlayerCache(UUID uuid) {
        if (databaseSync == null) return;
        LocalDataCache cache = databaseSync.getLocalCache();
        if (cache == null) return;

        double tuvi = tuviCache.getOrDefault(uuid, 0.0);
        long tuLuyenSeconds = tuLuyenTotalSecondsCache.getOrDefault(uuid, 0L);
        String equippedInfId = equippedInfusionIdCache.get(uuid);
        List<OwnedInfusion> inventoryList = infusionInventoryCache.getOrDefault(uuid, Collections.emptyList());
        List<LocalDataCache.InfusionEntry> infusions = new ArrayList<>();
        for (OwnedInfusion inf : inventoryList) {
            infusions.add(new LocalDataCache.InfusionEntry(inf.id(), inf.typeId(), inf.rarityId(), inf.createdAt()));
        }

        int realmId = 1;
        String subRealm = "SO_KY";
        int btCount = 0;
        long btCooldown = 0L;
        String playerName = null;

        if (realmManager != null) {
            com.turtle.tutiencore.api.realm.PlayerRealm pr = realmManager.getPlayerRealm(uuid);
            realmId = pr.getRealmId();
            subRealm = pr.getSubRealm().name();
            btCount = pr.getBreakthroughCount();
            btCooldown = pr.getBreakthroughCooldown();
        }

        // Try to get player name
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) playerName = op.getName();

        // Update throttle state so writeTuViCacheThrottled knows we just wrote
        lastTuViCacheWrite.put(uuid, System.currentTimeMillis());
        lastTuViCacheValue.put(uuid, tuvi);

        cache.savePlayerProgress(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfId,
                infusions, realmId, subRealm, btCount, btCooldown);
    }

    private final Map<UUID, Long> lastTuViCacheWrite = new HashMap<>();
    private final Map<UUID, Double> lastTuViCacheValue = new HashMap<>();
    private static final long TUVI_CACHE_WRITE_INTERVAL_MS = 5000L;
    private static final double TUVI_CACHE_WRITE_THRESHOLD = 1.0;

    /**
     * Writes Tu Vi to JSON cache, throttled to avoid excessive disk I/O during tu luyen.
     * Always writes immediately on manual set (e.g. admin command), throttles during cultivation.
     */
    private void writeTuViCacheThrottled(UUID uuid, double tuvi) {
        if (databaseSync == null) return;
        LocalDataCache cache = databaseSync.getLocalCache();
        if (cache == null) return;

        long now = System.currentTimeMillis();
        long lastWrite = lastTuViCacheWrite.getOrDefault(uuid, 0L);
        double lastValue = lastTuViCacheValue.getOrDefault(uuid, Double.NaN);
        double delta = Double.isNaN(lastValue) ? Double.MAX_VALUE : Math.abs(tuvi - lastValue);

        boolean shouldWrite = delta >= TUVI_CACHE_WRITE_THRESHOLD
                || (now - lastWrite) >= TUVI_CACHE_WRITE_INTERVAL_MS;
        if (!shouldWrite) return;

        lastTuViCacheWrite.put(uuid, now);
        lastTuViCacheValue.put(uuid, tuvi);
        writeTuViCache(uuid, tuvi);
    }

    /**
     * Writes only the Tu Vi value to the JSON cache.
     * Preserves existing realm/infusion/etc data from the cache.
     */
    private void writeTuViCache(UUID uuid, double tuvi) {
        if (databaseSync == null) return;
        LocalDataCache cache = databaseSync.getLocalCache();
        if (cache == null) return;

        LocalDataCache.PlayerProgressSnapshot existing = cache.loadPlayerProgress(uuid);
        int realmId = 1;
        String subRealm = "SO_KY";
        int btCount = 0;
        long btCooldown = 0L;
        long tuLuyenSeconds = tuLuyenTotalSecondsCache.getOrDefault(uuid, 0L);
        List<LocalDataCache.InfusionEntry> infusions = new ArrayList<>();
        String equippedInfId = equippedInfusionIdCache.get(uuid);
        String playerName = null;

        if (existing != null) {
            realmId = existing.realmId();
            subRealm = existing.subRealm();
            btCount = existing.breakthroughCount();
            btCooldown = existing.breakthroughCooldown();
            infusions = existing.infusions();
            playerName = existing.playerName();
        } else if (realmManager != null) {
            PlayerRealm pr = realmManager.getPlayerRealm(uuid);
            realmId = pr.getRealmId();
            subRealm = pr.getSubRealm().name();
            btCount = pr.getBreakthroughCount();
            btCooldown = pr.getBreakthroughCooldown();
        }

        cache.savePlayerProgress(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfId,
                infusions, realmId, subRealm, btCount, btCooldown);
    }

    private void syncDatabase(UUID uuid) {
        if (databaseSync != null && realmManager != null) {
            databaseSync.sync(uuid);
        }
    }

    @Override
    public void addTuVi(UUID uuid, double amount) {
        double current = getTuVi(uuid);
        if (realmManager == null || amount <= 0) {
            setTuVi(uuid, current + amount);
            return;
        }

        PlayerRealm playerRealm = realmManager.getPlayerRealm(uuid);
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        Realm nextRealm = realmManager.getNextRealm(uuid);
        setTuVi(uuid, capTuViAfterAdd(current, amount, playerRealm, currentRealm, nextRealm));
    }

    static double capTuViAfterAdd(double currentTuVi, double amount, PlayerRealm playerRealm, Realm currentRealm, Realm nextRealm) {
        double result = currentTuVi + amount;
        if (amount <= 0 || playerRealm == null || currentRealm == null) return result;

        SubRealm nextSubRealm = playerRealm.getSubRealm().next();
        long cap = nextSubRealm != null
                ? currentRealm.getTuViForSubRealm(nextSubRealm)
                : nextRealm != null ? nextRealm.getTuViRequired() : currentRealm.getTuViForSubRealm(SubRealm.VIEN_MAN);

        return cap > 0 ? Math.min(result, cap) : result;
    }

    @Override
    public void takeTuVi(UUID uuid, double amount) {
        double current = getTuVi(uuid);
        if (current - amount < 0) {
            setTuVi(uuid, 0);
        } else {
            setTuVi(uuid, current - amount);
        }
    }

    // ==========================================
    // REALM ACCESS (delegates to RealmManager)
    // ==========================================

    @Override
    public int getRealmId(UUID uuid) {
        if (realmManager == null) return 1;
        return realmManager.getPlayerRealm(uuid).getRealmId();
    }

    @Override
    public Realm getRealm(UUID uuid) {
        if (realmManager == null) return null;
        return realmManager.getPlayerCurrentRealm(uuid);
    }

    @Override
    public Realm getRealmById(int realmId) {
        if (realmManager == null) return null;
        return realmManager.getRealm(realmId);
    }

    @Override
    public Map<Integer, Realm> getAllRealms() {
        if (realmManager == null) return Collections.emptyMap();
        return realmManager.getAllRealms();
    }

    @Override
    public int getMaxRealmId() {
        if (realmManager == null) return 19;
        return realmManager.getMaxRealmId();
    }

    @Override
    public PlayerRealm getPlayerRealmData(UUID uuid) {
        if (realmManager == null) return new PlayerRealm(1, SubRealm.SO_KY);
        return realmManager.getPlayerRealm(uuid);
    }

    @Override
    public SubRealm getSubRealm(UUID uuid) {
        if (realmManager == null) return SubRealm.SO_KY;
        return realmManager.getPlayerRealm(uuid).getSubRealm();
    }

    @Override
    public void setRealm(UUID uuid, int realmId, SubRealm subRealm) {
        if (realmManager == null) return;
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        pr.setRealmId(realmId);
        pr.setSubRealm(subRealm);
        // Keep the breakthrough counter in sync with the realm set directly (e.g. admin command),
        // otherwise the stacking cultivation bonus stays stuck at the old value until the player
        // performs a real breakthrough. Use max() so earned breakthroughs from past failures are
        // never wiped when the realm is set.
        int derived = RealmManager.deriveBreakthroughCount(realmId, subRealm);
        pr.setBreakthroughCount(Math.max(pr.getBreakthroughCount(), derived));
        realmManager.savePlayerRealm(uuid);
    }

    @Override
    public boolean isMaxRealm(UUID uuid) {
        if (realmManager == null) return false;
        return realmManager.isMaxRealm(uuid);
    }

    // ==========================================
    // REALM DISPLAY (delegates to RealmManager)
    // ==========================================

    @Override
    public String getRealmDisplay(UUID uuid) {
        if (realmManager == null) return "§7[Phàm Nhân — Sơ Kỳ]";
        return realmManager.getPlayerRealmDisplay(uuid);
    }

    @Override
    public String getRealmDisplayName(UUID uuid) {
        if (realmManager == null) return "§7「Phàm Nhân」";
        return realmManager.getPlayerDisplayName(uuid);
    }

    @Override
    public String getRealmName(UUID uuid) {
        if (realmManager == null) return "§7Phàm Nhân";
        return realmManager.getPlayerRealmName(uuid);
    }

    @Override
    public String getSubRealmName(UUID uuid) {
        if (realmManager == null) return "Sơ Kỳ";
        return realmManager.getPlayerSubRealmName(uuid);
    }

    @Override
    public String getRealmTierName(UUID uuid) {
        if (realmManager == null) return "Phàm Giới";
        Realm realm = realmManager.getPlayerCurrentRealm(uuid);
        return realm != null ? realm.getTier().getDisplayName() : "Phàm Giới";
    }

    // ==========================================
    // BREAKTHROUGH (delegates to BreakthroughManager)
    // ==========================================

    @Override
    public boolean isInBreakthrough(UUID uuid) {
        if (breakthroughManager == null) return false;
        return breakthroughManager.isInBreakthrough(uuid);
    }

    @Override
    public boolean isOnBreakthroughCooldown(UUID uuid) {
        if (realmManager == null) return false;
        return realmManager.getPlayerRealm(uuid).isOnCooldown();
    }

    @Override
    public long getBreakthroughCooldownRemaining(UUID uuid) {
        if (realmManager == null) return 0;
        return realmManager.getPlayerRealm(uuid).getRemainingCooldownSeconds();
    }

    @Override
    public boolean canBreakthrough(UUID uuid) {
        if (realmManager == null) return false;
        return realmManager.checkBreakthroughConditions(uuid).isEmpty();
    }

    // ==========================================
    // TU LUYEN (delegates to TuLuyenManager)
    // ==========================================

    @Override
    public boolean isTuLuyen(UUID uuid) {
        if (tuLuyenManager == null) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        return tuLuyenManager.isTuLuyen(player);
    }

    @Override
    public Collection<UUID> getTuLuyenPlayers() {
        if (tuLuyenManager == null) return Collections.emptyList();
        List<UUID> uuids = new ArrayList<>();
        for (Player p : tuLuyenManager.getTuLuyenPlayers()) {
            uuids.add(p.getUniqueId());
        }
        return uuids;
    }

    @Override
    public long getTuLuyenTotalSeconds(UUID uuid) {
        Long cached = tuLuyenTotalSecondsCache.get(uuid);
        if (cached != null) {
            return Math.max(0L, cached);
        }
        synchronized (configLock) {
            return readTuLuyenTotalSeconds(uuid);
        }
    }

    @Override
    public void setTuLuyenTotalSeconds(UUID uuid, long seconds) {
        tuLuyenTotalSecondsCache.put(uuid, Math.max(0L, seconds));
    }

    @Override
    public void addTuLuyenTotalSeconds(UUID uuid, long seconds) {
        if (seconds <= 0L) {
            return;
        }
        tuLuyenTotalSecondsCache.put(uuid, getTuLuyenTotalSeconds(uuid) + seconds);
    }

    @Override
    public long getTuLuyenSessionSeconds(UUID uuid) {
        if (tuLuyenManager == null) return 0L;
        return tuLuyenManager.getSessionSeconds(uuid);
    }

    // ==========================================
    // UTILITY
    // ==========================================

    @Override
    public String formatNumber(long number) {
        return RealmManager.formatNumber(number);
    }

    // ==========================================
    // EVENTS
    // ==========================================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isClone(event.getPlayer())) {
            return; // TurtleClone fakes are packet-only; never create real data for them.
        }
        synchronized (configLock) {
            config.set(event.getPlayer().getUniqueId().toString() + ".name", event.getPlayer().getName());
        }
        loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isClone(event.getPlayer())) {
            return;
        }
        // Save to DB async then evict caches
        savePlayerAsync(event.getPlayer().getUniqueId());
        UUID uuid = event.getPlayer().getUniqueId();
        tuviCache.remove(uuid);
        tuLuyenTotalSecondsCache.remove(uuid);
        infusionInventoryCache.remove(uuid);
        equippedInfusionIdCache.remove(uuid);
        lastTuViCacheWrite.remove(uuid);
        lastTuViCacheValue.remove(uuid);
    }

    /**
     * @return {@code true} if the player is a fake/NPC (e.g. a TurtleClone packet-fake or a Citizens
     * NPC). Such "players" must never have real data created or saved. TurtleClone fakes are
     * packet-only and normally never reach these listeners, but this guard is defensive.
     */
    private boolean isClone(Player player) {
        return player == null || player.hasMetadata("NPC");
    }

    // --- TOP SYSTEM ---
    private List<Map.Entry<String, Double>> topCache = new ArrayList<>();
    private long lastTopUpdate = 0;
    private List<Map.Entry<String, Long>> topTuLuyenTimeCache = new ArrayList<>();
    private long lastTopTuLuyenTimeUpdate = 0;

    public void updateTop() {
        flushCachesToConfig(); // Flush caches to config map (no disk write, no serialization)
        Map<String, Double> allTuVi = new HashMap<>();

        synchronized (configLock) {
            for (String key : config.getKeys(false)) {
                double tuvi = config.getDouble(key + ".tuvi", 0.0);
                String name = config.getString(key + ".name", "Unknown");
                allTuVi.put(name, tuvi);
            }
        }

        topCache = allTuVi.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        lastTopUpdate = System.currentTimeMillis();
    }

    public List<Map.Entry<String, Double>> getTopTuVi() {
        // Update top cache every 5 minutes max
        if (System.currentTimeMillis() - lastTopUpdate > 300000) {
            if (Bukkit.isPrimaryThread()) {
                updateTop();
            } else {
                Bukkit.getScheduler().runTask(plugin, this::updateTop);
            }
        }
        if (topCache.isEmpty() && lastTopUpdate == 0) {
            updateTop(); // Initial synchronous update if empty
        }
        return topCache;
    }

    public void updateTopTuLuyenTime() {
        flushCachesToConfig(); // Flush caches to config map (no disk write, no serialization)
        Map<String, Long> allTimes = new HashMap<>();

        synchronized (configLock) {
            for (String key : config.getKeys(false)) {
                long seconds = Math.max(0L, config.getLong(key + ".tuluyen.total-seconds",
                        config.getLong(key + ".tuluyen.total-time", 0L)));
                String name = config.getString(key + ".name", "Unknown");
                allTimes.put(name, seconds);
            }
        }

        topTuLuyenTimeCache = allTimes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());
        lastTopTuLuyenTimeUpdate = System.currentTimeMillis();
    }

    @Override
    public List<Map.Entry<String, Long>> getTopTuLuyenTime() {
        // Update top cache every 5 minutes max
        if (System.currentTimeMillis() - lastTopTuLuyenTimeUpdate > 300000) {
            if (Bukkit.isPrimaryThread()) {
                updateTopTuLuyenTime();
            } else {
                Bukkit.getScheduler().runTask(plugin, this::updateTopTuLuyenTime);
            }
        }
        if (topTuLuyenTimeCache.isEmpty() && lastTopTuLuyenTimeUpdate == 0) {
            updateTopTuLuyenTime(); // Initial synchronous update if empty
        }
        return topTuLuyenTimeCache;
    }
}
