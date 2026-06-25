package com.turtle.tutiencore.core.storage;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.infusion.OwnedInfusion;
import com.turtle.tutiencore.core.manager.PlayerDataManager;
import com.turtle.tutiencore.core.manager.RealmManager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Central bridge that wires {@link PlayerDataManager} / {@link RealmManager} to
 * {@link PlayerProgressDatabase}.
 *
 * <p><b>Write path:</b> every mutation writes to {@link LocalDataCache} synchronously
 * (tiny JSON file, ~microseconds) and then to the DB asynchronously.
 *
 * <p><b>Read path (player join):</b> the JSON cache is read immediately on the main thread
 * so the player always starts with correct data, even if the DB async load takes a tick or two.
 *
 * <p><b>Crash recovery:</b> leftover JSON files from a crash are flushed into the DB at next
 * plugin enable, then deleted.
 */
public class PlayerProgressDatabaseSync {

    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final RealmManager realmManager;
    private final PlayerProgressDatabase database;
    private final LocalDataCache localCache;

    public PlayerProgressDatabaseSync(JavaPlugin plugin, PlayerDataManager playerDataManager,
                                      RealmManager realmManager, PlayerProgressDatabase database,
                                      LocalDataCache localCache) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.realmManager = realmManager;
        this.database = database;
        this.localCache = localCache;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────

    /** Creates all tables (async — called once on plugin enable). */
    public void initialize() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.initialize();
                plugin.getLogger().info("TuTienCore database initialized (PRIMARY storage).");
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not initialize TuTienCore database: " + e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads all data for a player. Reads the local JSON cache IMMEDIATELY on the main thread
     * (so the player always has correct data right away), then fires an async DB load ONLY as
     * a fallback when no cache exists (first-ever join or cache was evicted after clean shutdown).
     *
     * The cache is ALWAYS newer than the DB (written synchronously before every async DB write),
     * so when a cache file exists we trust it completely and skip DB entirely.
     */
    public void loadFromDatabase(UUID uuid) {
        // ── Step 1: instant cache read (main thread, no I/O wait) ─────────────
        LocalDataCache.PlayerProgressSnapshot cached = localCache.loadPlayerProgress(uuid);
        if (cached != null) {
            // Cache exists → apply immediately and DO NOT touch the DB load result.
            // The cache is always written BEFORE the async DB write, so it is always >= DB.
            applyProgressSnapshot(uuid, cached);
            // Still fire a DB write in background to make sure DB is up to date,
            // but we will NOT read the DB response back (skip=true below).
            return;
        }

        // ── Step 2: no cache → async DB load (first join or post-clean-shutdown) ─
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final PlayerProgressDatabase.PlayerData playerData = database.loadPlayerData(uuid);
                final PlayerProgressDatabase.RealmData realmData = database.loadRealmData(uuid);
                final List<OwnedInfusion> infusions = database.loadInfusions(uuid);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // Double-check: if cache appeared while we were loading (player data was
                    // saved between the cache-miss check and now), prefer the cache.
                    LocalDataCache.PlayerProgressSnapshot nowCached = localCache.loadPlayerProgress(uuid);
                    if (nowCached != null) {
                        applyProgressSnapshot(uuid, nowCached);
                        return;
                    }
                    if (playerData != null) {
                        playerDataManager.setTuVi(uuid, playerData.tuvi());
                        playerDataManager.setTuLuyenTotalSeconds(uuid, playerData.tuLuyenTotalSeconds());
                        playerDataManager.replaceInfusionInventory(uuid, infusions, playerData.equippedInfusionId());
                    }
                    if (realmData != null) {
                        SubRealm subRealm = SubRealm.SO_KY;
                        try { subRealm = SubRealm.valueOf(realmData.subRealm()); } catch (IllegalArgumentException ignored) {}
                        PlayerRealm realm = new PlayerRealm(realmData.realmId(), subRealm);
                        realm.setBreakthroughCount(realmData.breakthroughCount());
                        realm.setBreakthroughCooldown(realmData.breakthroughCooldown());
                        realmManager.setPlayerRealmObject(uuid, realm);
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not load player data for " + uuid + ": " + e.getMessage());
            }
        });
    }

    private void applyProgressSnapshot(UUID uuid, LocalDataCache.PlayerProgressSnapshot snap) {
        playerDataManager.setTuVi(uuid, snap.tuvi());
        playerDataManager.setTuLuyenTotalSeconds(uuid, snap.tuLuyenSeconds());

        // Convert cache infusion entries to OwnedInfusion
        List<OwnedInfusion> infusions = new ArrayList<>();
        for (LocalDataCache.InfusionEntry e : snap.infusions()) {
            infusions.add(new OwnedInfusion(e.id(), e.typeId(), e.rarityId(), e.createdAt()));
        }
        playerDataManager.replaceInfusionInventory(uuid, infusions, snap.equippedInfusionId());

        SubRealm subRealm = SubRealm.SO_KY;
        try { subRealm = SubRealm.valueOf(snap.subRealm()); } catch (IllegalArgumentException ignored) {}
        PlayerRealm realm = new PlayerRealm(snap.realmId(), subRealm);
        realm.setBreakthroughCount(snap.breakthroughCount());
        realm.setBreakthroughCooldown(snap.breakthroughCooldown());
        realmManager.setPlayerRealmObject(uuid, realm);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists all player data. Writes to local cache SYNCHRONOUSLY first (instant, ~microseconds),
     * then fires async DB writes.
     */
    public void sync(UUID uuid) {
        // Capture snapshot on main thread
        final String playerName = resolvePlayerName(uuid);
        final double tuvi = playerDataManager.getTuVi(uuid);
        final long tuLuyenSeconds = playerDataManager.getTuLuyenTotalSeconds(uuid);
        final List<OwnedInfusion> infusions = playerDataManager.getInfusionInventory(uuid);
        final String equippedInfusionId = playerDataManager.getEquippedInfusionId(uuid);
        final PlayerRealm realm = realmManager.getPlayerRealm(uuid);
        final int realmId = realm.getRealmId();
        final String subRealm = realm.getSubRealm().name();
        final int breakthroughCount = realm.getBreakthroughCount();
        final long cooldown = realm.getBreakthroughCooldown();

        // ── Write to JSON cache SYNCHRONOUSLY (protects against crash/fast relog) ──
        List<LocalDataCache.InfusionEntry> cacheInfusions = new ArrayList<>();
        for (OwnedInfusion inf : infusions) {
            cacheInfusions.add(new LocalDataCache.InfusionEntry(inf.id(), inf.typeId(), inf.rarityId(), inf.createdAt()));
        }
        localCache.savePlayerProgress(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfusionId,
                cacheInfusions, realmId, subRealm, breakthroughCount, cooldown);

        // ── Async DB write ────────────────────────────────────────────────────
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.savePlayerData(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfusionId);
                database.saveRealmData(uuid, realmId, subRealm, breakthroughCount, cooldown);
                database.saveInfusions(uuid, infusions);
                database.saveProgress(uuid, playerName, tuvi, realmId, subRealm);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not sync database for " + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * Blocking save — used on server shutdown when the scheduler is being torn down.
     * Also evicts the local cache after a successful DB write (clean shutdown).
     */
    public void syncBlocking(UUID uuid) {
        final String playerName = resolvePlayerName(uuid);
        final double tuvi = playerDataManager.getTuVi(uuid);
        final long tuLuyenSeconds = playerDataManager.getTuLuyenTotalSeconds(uuid);
        final List<OwnedInfusion> infusions = playerDataManager.getInfusionInventory(uuid);
        final String equippedInfusionId = playerDataManager.getEquippedInfusionId(uuid);
        final PlayerRealm realm = realmManager.getPlayerRealm(uuid);

        // Write JSON cache first (in case DB write fails)
        List<LocalDataCache.InfusionEntry> cacheInfusions = new ArrayList<>();
        for (OwnedInfusion inf : infusions) {
            cacheInfusions.add(new LocalDataCache.InfusionEntry(inf.id(), inf.typeId(), inf.rarityId(), inf.createdAt()));
        }
        localCache.savePlayerProgress(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfusionId,
                cacheInfusions, realm.getRealmId(), realm.getSubRealm().name(),
                realm.getBreakthroughCount(), realm.getBreakthroughCooldown());

        try {
            database.savePlayerData(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfusionId);
            database.saveRealmData(uuid, realm.getRealmId(), realm.getSubRealm().name(),
                    realm.getBreakthroughCount(), realm.getBreakthroughCooldown());
            database.saveInfusions(uuid, infusions);
            database.saveProgress(uuid, playerName, tuvi, realm.getRealmId(), realm.getSubRealm().name());
            // DB write succeeded → evict cache (clean shutdown)
            localCache.evictOnQuit(uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save database (shutdown) for " + playerName + ": " + e.getMessage());
            // Keep cache file — will be flushed to DB on next startup
        }
    }

    /**
     * Returns the local cache so callers (e.g. FlySwordManager, OfflineTuLuyenManager,
     * EquipmentMenuManager) can read/write their own sections.
     */
    public LocalDataCache getLocalCache() {
        return localCache;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLY SWORD
    // ─────────────────────────────────────────────────────────────────────────

    public void saveFlySwordLevel(UUID uuid, int level) {
        localCache.saveFlySword(uuid, level);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.saveFlySwordLevel(uuid, level);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not save fly sword level for " + uuid + ": " + e.getMessage());
            }
        });
    }

    /** Checks cache first, then DB (blocking). Returns -1 if no data. */
    public int loadFlySwordLevelBlocking(UUID uuid) {
        int cached = localCache.loadFlySwordLevel(uuid);
        if (cached >= 1) return cached;
        try {
            return database.loadFlySwordLevel(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load fly sword level for " + uuid + ": " + e.getMessage());
            return -1;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OFFLINE TU LUYEN
    // ─────────────────────────────────────────────────────────────────────────

    public void saveOfflineTuLuyen(UUID uuid, double pendingTuVi, long lastOfflineStart,
                                   long lastEarnedSeconds, long lastRealOfflineSeconds,
                                   double lastEarnedMultiplier) {
        localCache.saveOfflineTuLuyen(uuid, pendingTuVi, lastOfflineStart,
                lastEarnedSeconds, lastRealOfflineSeconds, lastEarnedMultiplier);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.saveOfflineTuLuyen(uuid, pendingTuVi, lastOfflineStart,
                        lastEarnedSeconds, lastRealOfflineSeconds, lastEarnedMultiplier);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not save offline tu luyen for " + uuid + ": " + e.getMessage());
            }
        });
    }

    /** Checks cache first, then DB (blocking). Returns null if no data. */
    public PlayerProgressDatabase.OfflineTuLuyenData loadOfflineTuLuyenBlocking(UUID uuid) {
        LocalDataCache.OfflineTuLuyenSnapshot snap = localCache.loadOfflineTuLuyen(uuid);
        if (snap != null) {
            return new PlayerProgressDatabase.OfflineTuLuyenData(uuid,
                    snap.pendingTuVi(), snap.lastOfflineStart(),
                    snap.lastEarnedSeconds(), snap.lastRealOfflineSeconds(),
                    snap.lastEarnedMultiplier());
        }
        try {
            return database.loadOfflineTuLuyen(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load offline tu luyen for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String resolvePlayerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 16) : name;
    }
}
