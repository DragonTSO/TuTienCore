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
import java.util.List;
import java.util.UUID;

/**
 * Central bridge that wires {@link PlayerDataManager} / {@link RealmManager} to
 * {@link PlayerProgressDatabase}.
 *
 * Database is ALWAYS the primary storage.  YAML files are no longer read or written
 * by this class.  The only YAML interaction remaining in the plugin is the DataMigrator
 * one-time import of legacy files on first start.
 */
public class PlayerProgressDatabaseSync {

    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final RealmManager realmManager;
    private final PlayerProgressDatabase database;

    public PlayerProgressDatabaseSync(JavaPlugin plugin, PlayerDataManager playerDataManager,
                                      RealmManager realmManager, PlayerProgressDatabase database) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.realmManager = realmManager;
        this.database = database;
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
     * Loads all data for a player from the database and injects it into the in-memory managers.
     * Runs async; main-thread callbacks update the managers.
     */
    public void loadFromDatabase(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final PlayerProgressDatabase.PlayerData playerData = database.loadPlayerData(uuid);
                final PlayerProgressDatabase.RealmData realmData = database.loadRealmData(uuid);
                final List<OwnedInfusion> infusions = database.loadInfusions(uuid);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (playerData != null) {
                        playerDataManager.setTuVi(uuid, playerData.tuvi());
                        playerDataManager.setTuLuyenTotalSeconds(uuid, playerData.tuLuyenTotalSeconds());
                        // Infusion inventory
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

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists all player data (player_data, realm_data, infusions, legacy progress) async.
     * Snapshot is captured synchronously on the calling (main) thread before the write goes async.
     */
    public void sync(UUID uuid) {
        // Capture snapshot synchronously on main thread
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

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.savePlayerData(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfusionId);
                database.saveRealmData(uuid, realmId, subRealm, breakthroughCount, cooldown);
                database.saveInfusions(uuid, infusions);
                // Keep legacy cross-server table in sync
                database.saveProgress(uuid, playerName, tuvi, realmId, subRealm);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not sync database for " + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * Blocking (synchronous) save — used on server shutdown when the scheduler is being torn down.
     */
    public void syncBlocking(UUID uuid) {
        final String playerName = resolvePlayerName(uuid);
        final double tuvi = playerDataManager.getTuVi(uuid);
        final long tuLuyenSeconds = playerDataManager.getTuLuyenTotalSeconds(uuid);
        final List<OwnedInfusion> infusions = playerDataManager.getInfusionInventory(uuid);
        final String equippedInfusionId = playerDataManager.getEquippedInfusionId(uuid);
        final PlayerRealm realm = realmManager.getPlayerRealm(uuid);
        try {
            database.savePlayerData(uuid, playerName, tuvi, tuLuyenSeconds, equippedInfusionId);
            database.saveRealmData(uuid, realm.getRealmId(), realm.getSubRealm().name(),
                    realm.getBreakthroughCount(), realm.getBreakthroughCooldown());
            database.saveInfusions(uuid, infusions);
            database.saveProgress(uuid, playerName, tuvi, realm.getRealmId(), realm.getSubRealm().name());
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save database (shutdown) for " + playerName + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLY SWORD
    // ─────────────────────────────────────────────────────────────────────────

    public void saveFlySwordLevel(UUID uuid, int level) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.saveFlySwordLevel(uuid, level);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not save fly sword level for " + uuid + ": " + e.getMessage());
            }
        });
    }

    /** Returns -1 if no data exists (caller should use default level). Blocking — call async. */
    public int loadFlySwordLevelBlocking(UUID uuid) {
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.saveOfflineTuLuyen(uuid, pendingTuVi, lastOfflineStart,
                        lastEarnedSeconds, lastRealOfflineSeconds, lastEarnedMultiplier);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not save offline tu luyen for " + uuid + ": " + e.getMessage());
            }
        });
    }

    /** Blocking — call from async context. Returns null if no data. */
    public PlayerProgressDatabase.OfflineTuLuyenData loadOfflineTuLuyenBlocking(UUID uuid) {
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
