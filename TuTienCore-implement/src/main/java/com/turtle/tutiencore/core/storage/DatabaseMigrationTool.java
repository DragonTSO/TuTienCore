package com.turtle.tutiencore.core.storage;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.infusion.OwnedInfusion;
import com.turtle.tutiencore.core.manager.EquipmentMenuManager;
import com.turtle.tutiencore.core.manager.PlayerDataManager;
import com.turtle.tutiencore.core.manager.RealmManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Tool to migrate player data between YAML files and MySQL database.
 * Supports bidirectional migration: YAML → DB and DB → YAML.
 */
public class DatabaseMigrationTool {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PlayerDataManager playerDataManager;
    private final RealmManager realmManager;
    private final EquipmentMenuManager equipmentMenuManager;
    private final PlayerProgressDatabase playerProgressDb;
    private final EquipmentDatabase equipmentDb;

    public DatabaseMigrationTool(JavaPlugin plugin,
                                  PlayerDataManager playerDataManager,
                                  RealmManager realmManager,
                                  EquipmentMenuManager equipmentMenuManager,
                                  PlayerProgressDatabase playerProgressDb,
                                  EquipmentDatabase equipmentDb) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.playerDataManager = playerDataManager;
        this.realmManager = realmManager;
        this.equipmentMenuManager = equipmentMenuManager;
        this.playerProgressDb = playerProgressDb;
        this.equipmentDb = equipmentDb;
    }

    // ==========================================
    // YAML → DATABASE MIGRATION
    // ==========================================

    /**
     * Migrate all player data from YAML files to database.
     * Returns: [playersCount, equipmentCount, errors]
     */
    public MigrationResult migrateYamlToDatabase() {
        logger.info("[Migration] Starting YAML → Database migration...");
        
        AtomicInteger playersCount = new AtomicInteger(0);
        AtomicInteger equipmentCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try {
            // Initialize database tables
            playerProgressDb.initialize();
            equipmentDb.initialize();
            logger.info("[Migration] Database tables initialized");

            // Migrate player progress (Tu Vi, Realm)
            migratePlayerProgress(playersCount, errorCount);

            // Migrate equipment data
            migrateEquipmentData(equipmentCount, errorCount);

            logger.info("[Migration] YAML → Database migration completed!");
            logger.info("[Migration] Players: " + playersCount.get() + ", Equipment slots: " + equipmentCount.get() + ", Errors: " + errorCount.get());

        } catch (SQLException e) {
            logger.severe("[Migration] Database initialization failed: " + e.getMessage());
            e.printStackTrace();
            errorCount.incrementAndGet();
        }

        return new MigrationResult(playersCount.get(), equipmentCount.get(), errorCount.get());
    }

    private void migratePlayerProgress(AtomicInteger playersCount, AtomicInteger errorCount) {
        File playersFolder = new File(plugin.getDataFolder(), "data/players");
        File realmsFolder = new File(plugin.getDataFolder(), "data/realms");

        if (!playersFolder.exists() && !realmsFolder.exists()) {
            logger.warning("[Migration] No YAML player data found to migrate");
            return;
        }

        // Get all player UUIDs from files
        Set<UUID> allUuids = new HashSet<>();
        if (playersFolder.exists()) {
            File[] playerFiles = playersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (playerFiles != null) {
                for (File file : playerFiles) {
                    String uuidStr = file.getName().replace(".yml", "");
                    try {
                        allUuids.add(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        logger.info("[Migration] Found " + allUuids.size() + " player files to migrate");

        // Migrate each player
        for (UUID uuid : allUuids) {
            try {
                migratePlayerData(uuid);
                playersCount.incrementAndGet();
            } catch (Exception e) {
                logger.warning("[Migration] Failed to migrate player " + uuid + ": " + e.getMessage());
                errorCount.incrementAndGet();
            }
        }
    }

    private void migratePlayerData(UUID uuid) throws SQLException {
        // Load Tu Vi
        double tuvi = playerDataManager.getTuVi(uuid);

        // Load Realm data
        PlayerRealm playerRealm = realmManager.getPlayerRealm(uuid);
        if (playerRealm == null) {
            // Player might not have realm data yet, create default
            playerRealm = new PlayerRealm(1, SubRealm.SO_KY);
        }

        // Get player name
        String playerName = resolvePlayerName(uuid);

        // Save to database
        playerProgressDb.saveProgress(uuid, playerName, tuvi, 
            playerRealm.getRealmId(), playerRealm.getSubRealm().name());

        logger.fine("[Migration] Migrated player data: " + playerName + " (Tu Vi: " + tuvi + ", Realm: " + playerRealm.getRealmId() + ")");
    }

    private void migrateEquipmentData(AtomicInteger equipmentCount, AtomicInteger errorCount) {
        File equipmentFolder = new File(plugin.getDataFolder(), "data/equipment");
        
        if (!equipmentFolder.exists()) {
            logger.warning("[Migration] No equipment data found to migrate");
            return;
        }

        File[] equipmentFiles = equipmentFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (equipmentFiles == null || equipmentFiles.length == 0) {
            logger.warning("[Migration] No equipment files found");
            return;
        }

        logger.info("[Migration] Found " + equipmentFiles.length + " equipment files to migrate");

        for (File file : equipmentFiles) {
            String uuidStr = file.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(uuidStr);
                int slots = migratePlayerEquipment(uuid, file);
                equipmentCount.addAndGet(slots);
            } catch (IllegalArgumentException e) {
                logger.warning("[Migration] Invalid UUID in filename: " + file.getName());
                errorCount.incrementAndGet();
            } catch (Exception e) {
                logger.warning("[Migration] Failed to migrate equipment for " + file.getName() + ": " + e.getMessage());
                errorCount.incrementAndGet();
            }
        }
    }

    private int migratePlayerEquipment(UUID uuid, File file) throws SQLException {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection uuidSection = config.getConfigurationSection(uuid.toString());
        
        if (uuidSection == null) {
            return 0;
        }

        int migratedSlots = 0;

        // Migrate equipment slots
        for (String slotId : uuidSection.getKeys(false)) {
            if (slotId.equals("mmo-meta")) {
                // Skip metadata section, we'll process it below
                continue;
            }

            // Get item from slot
            ItemStack item = config.getItemStack(uuid + "." + slotId);
            
            // Get mmo-meta
            String mmoMetaPath = uuid + ".mmo-meta." + slotId;
            String mmoType = config.getString(mmoMetaPath + ".type");
            String mmoId = config.getString(mmoMetaPath + ".id");
            long remainingSeconds = config.getLong(mmoMetaPath + ".remaining-seconds", -1);
            long totalSeconds = config.getLong(mmoMetaPath + ".total-seconds", 0);

            // Save to database (even if item is null, to preserve slot state)
            equipmentDb.saveEquipmentSlot(uuid, slotId, item, mmoType, mmoId, remainingSeconds, totalSeconds);
            migratedSlots++;
        }

        logger.fine("[Migration] Migrated " + migratedSlots + " equipment slots for player " + uuid);
        return migratedSlots;
    }

    // ==========================================
    // DATABASE → YAML MIGRATION
    // ==========================================

    /**
     * Migrate all player data from database to YAML files.
     * This is useful for backup or reverting to YAML storage.
     */
    public MigrationResult migrateDatabaseToYaml() {
        logger.info("[Migration] Starting Database → YAML migration...");
        logger.warning("[Migration] This feature is not yet implemented!");
        
        // TODO: Implement DB → YAML migration
        // This is more complex as we need to query all players from database
        // and write them back to YAML files
        
        return new MigrationResult(0, 0, 1);
    }

    // ==========================================
    // UTILITY METHODS
    // ==========================================

    private String resolvePlayerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 16) : name;
    }

    // ==========================================
    // RESULT CLASS
    // ==========================================

    public record MigrationResult(int playersCount, int equipmentCount, int errorCount) {
        public boolean hasErrors() {
            return errorCount > 0;
        }

        public boolean isSuccess() {
            return errorCount == 0 && (playersCount > 0 || equipmentCount > 0);
        }
    }
}
