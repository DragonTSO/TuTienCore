package com.turtle.tutiencore.core;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.core.command.CanhGioiCommand;
import com.turtle.tutiencore.core.command.CommandAliasManager;
import com.turtle.tutiencore.core.command.DotPhaCommand;
import com.turtle.tutiencore.core.command.NhapThanCommand;
import com.turtle.tutiencore.core.command.RankupCommand;
import com.turtle.tutiencore.core.command.TuTienCommand;
import com.turtle.tutiencore.core.command.TuViCommand;
import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.gui.RealmListGUI;
import com.turtle.tutiencore.core.hook.LinhDuocDropRateHook;
import com.turtle.tutiencore.core.hook.MMOCoreActionBarSuppressor;
import com.turtle.tutiencore.core.hook.MMOItemsMaxHealthPercentHook;
import com.turtle.tutiencore.core.hook.MMOItemsMMOCoreStatsHook;
import com.turtle.tutiencore.core.hook.MMOItemsRealmRequirementHook;
import com.turtle.tutiencore.core.hook.MythicMobsMoneyBonusHook;
import com.turtle.tutiencore.core.infusion.InfusionManager;
import com.turtle.tutiencore.core.manager.ActionBarManager;
import com.turtle.tutiencore.core.manager.AfkKickManager;
import com.turtle.tutiencore.core.manager.BreakthroughManager;
import com.turtle.tutiencore.core.manager.DeathTipManager;
import com.turtle.tutiencore.core.manager.EquipmentMenuManager;
import com.turtle.tutiencore.core.manager.FlySwordManager;
import com.turtle.tutiencore.core.manager.HotbarCommandItemManager;
import com.turtle.tutiencore.core.manager.KillRewardHologramManager;
import com.turtle.tutiencore.core.manager.OfflineTuLuyenManager;
import com.turtle.tutiencore.core.manager.PlayerDataManager;
import com.turtle.tutiencore.core.manager.PlayerHologramManager;
import com.turtle.tutiencore.core.manager.RealmManager;
import com.turtle.tutiencore.core.manager.RegionRespawnManager;
import com.turtle.tutiencore.core.manager.ThauThiManager;
import com.turtle.tutiencore.core.manager.TuLuyenManager;
import com.turtle.tutiencore.core.manager.ZoneManager;
import com.turtle.tutiencore.core.storage.DataMigrator;
import com.turtle.tutiencore.core.storage.DatabaseMigrationTool;
import com.turtle.tutiencore.core.storage.DatabaseSettings;
import com.turtle.tutiencore.core.storage.DriverManagerDataSource;
import com.turtle.tutiencore.core.storage.EquipmentDatabase;
import com.turtle.tutiencore.core.storage.PlayerProgressDatabase;
import com.turtle.tutiencore.core.storage.PlayerProgressDatabaseSync;
import com.turtle.tutiencore.core.task.SphereParticleTask;
import com.turtle.tutiencore.core.task.TuLuyenParticleTask;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

import lombok.Getter;

@Getter
public class TuTienCore {

    private final JavaPlugin plugin;

    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private ZoneManager zoneManager;
    private TuLuyenManager tuLuyenManager;
    private RealmManager realmManager;
    private BreakthroughManager breakthroughManager;
    private FlySwordManager flySwordManager;
    private OfflineTuLuyenManager offlineTuLuyenManager;
    private PlayerHologramManager playerHologramManager;
    private ActionBarManager actionBarManager;
    private RealmListGUI realmListGUI;
    private InfusionManager infusionManager;
    private AfkKickManager afkKickManager;
    private DeathTipManager deathTipManager;
    private RegionRespawnManager regionRespawnManager;
    private EquipmentMenuManager equipmentMenuManager;
    private HotbarCommandItemManager hotbarCommandItemManager;
    private DotPhaCommand dotPhaCommand;
    private MMOCoreActionBarSuppressor actionBarSuppressor;
    private MMOItemsMMOCoreStatsHook mmoItemsMMOCoreStatsHook;
    private MMOItemsRealmRequirementHook mmoItemsRealmRequirementHook;
    private MMOItemsMaxHealthPercentHook mmoItemsMaxHealthPercentHook;
    private LinhDuocDropRateHook linhDuocDropRateHook;
    private MythicMobsMoneyBonusHook mythicMobsMoneyBonusHook;
    private KillRewardHologramManager killRewardHologramManager;
    private CommandAliasManager commandAliasManager;
    private ThauThiManager thauThiManager;
    private PlayerProgressDatabaseSync databaseSync;
    private com.turtle.tutiencore.core.storage.LocalDataCache localCache;
    
    private SphereParticleTask sphereParticleTask;
    private TuLuyenParticleTask lineParticleTask;
    
    public TuTienCore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        plugin.getLogger().info("Loading TuTienCore managers...");
        if (!new File(plugin.getDataFolder(), "rankup.yml").exists()) {
            plugin.saveResource("rankup.yml", false);
        }
        
        this.configManager = new ConfigManager(plugin);

        // Migrate legacy monolithic YAML files into per-player files BEFORE any manager loads its
        // data. Idempotent: a no-op once migrated (legacy files are renamed to *.migrated-*.bak).
        new DataMigrator(plugin).migrateAll();

        // Setup API and Player Data
        this.playerDataManager = new PlayerDataManager(plugin);
        TuTien.setApi(playerDataManager);

        // Map and Zones
        this.zoneManager = new ZoneManager(plugin);
        
        // Realm & Breakthrough System
        this.realmManager = new RealmManager(plugin);
        this.breakthroughManager = new BreakthroughManager(plugin, realmManager);
        this.flySwordManager = new FlySwordManager(plugin);
        this.offlineTuLuyenManager = new OfflineTuLuyenManager(plugin, configManager);
        this.playerHologramManager = new PlayerHologramManager(plugin, configManager, realmManager);
        this.actionBarManager = new ActionBarManager(plugin);
        this.actionBarManager.start();
        this.realmListGUI = new RealmListGUI(plugin, realmManager);
        this.infusionManager = new InfusionManager(plugin, playerDataManager);
        this.afkKickManager = new AfkKickManager(plugin);
        this.deathTipManager = new DeathTipManager(plugin);
        this.regionRespawnManager = new RegionRespawnManager(plugin, zoneManager);
        this.equipmentMenuManager = new EquipmentMenuManager(plugin, realmManager);
        this.flySwordManager.setEquipmentMenuManager(equipmentMenuManager);
        // Let breakthrough suspend/restore flight via FlySwordManager.
        this.breakthroughManager.setFlySwordManager(flySwordManager);
        this.hotbarCommandItemManager = new HotbarCommandItemManager(plugin);
        this.thauThiManager = new ThauThiManager(plugin, realmManager);

        this.lineParticleTask = new TuLuyenParticleTask(plugin, configManager);
        this.tuLuyenManager = new TuLuyenManager(plugin, configManager, zoneManager, lineParticleTask, realmManager, infusionManager, equipmentMenuManager);
        this.playerHologramManager.setTuLuyenManager(this.tuLuyenManager);
        this.lineParticleTask.setTuLuyenManager(this.tuLuyenManager);
        this.lineParticleTask.setRealmManager(this.realmManager);
        this.lineParticleTask.startAuraTask();

        this.actionBarSuppressor = new MMOCoreActionBarSuppressor(plugin, tuLuyenManager);
        this.tuLuyenManager.setActionBarSuppressor(this.actionBarSuppressor);
        this.actionBarSuppressor.register();

        this.mmoItemsMMOCoreStatsHook = new MMOItemsMMOCoreStatsHook(plugin);
        this.mmoItemsMMOCoreStatsHook.register();
        this.mmoItemsRealmRequirementHook = new MMOItemsRealmRequirementHook(plugin, realmManager, configManager);
        this.mmoItemsRealmRequirementHook.register();
        this.mmoItemsMaxHealthPercentHook = new MMOItemsMaxHealthPercentHook(plugin);
        this.mmoItemsMaxHealthPercentHook.register();
        this.linhDuocDropRateHook = new LinhDuocDropRateHook(plugin);
        this.linhDuocDropRateHook.register();
        this.killRewardHologramManager = new KillRewardHologramManager(plugin);
        this.mythicMobsMoneyBonusHook = new MythicMobsMoneyBonusHook(plugin, actionBarManager, killRewardHologramManager, equipmentMenuManager);
        this.mythicMobsMoneyBonusHook.register();

        this.sphereParticleTask = new SphereParticleTask(plugin, zoneManager, configManager);
        this.sphereParticleTask.start();

        // Inject managers into API impl so it can delegate calls
        this.playerDataManager.injectManagers(realmManager, breakthroughManager, tuLuyenManager);

        // ── Local JSON cache (always created, no DB required) ─────────────────
        this.localCache = new com.turtle.tutiencore.core.storage.LocalDataCache(plugin);

        DatabaseSettings databaseSettings = DatabaseSettings.from(plugin.getConfig());
        DatabaseSettings equipmentDbSettings = DatabaseSettings.fromEquipment(plugin.getConfig());
        PlayerProgressDatabase playerProgressDb = null;
        EquipmentDatabase equipmentDb = null;

        if (databaseSettings.enabled()) {
            if (!"mysql".equalsIgnoreCase(databaseSettings.type())) {
                plugin.getLogger().warning("Unsupported TuTienCore database.type: " + databaseSettings.type());
            } else {
                try {
                    DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseSettings);
                    playerProgressDb = new PlayerProgressDatabase(dataSource);
                    playerProgressDb.initialize();
                    plugin.getLogger().info("TuTienCore player database initialized.");

                    this.databaseSync = new PlayerProgressDatabaseSync(plugin, playerDataManager, realmManager, playerProgressDb, localCache);
                    this.playerDataManager.setDatabaseSync(databaseSync);
                    this.realmManager.setDatabaseSync(databaseSync);
                    this.flySwordManager.setDatabaseSync(databaseSync);
                    this.offlineTuLuyenManager.setDatabaseSync(databaseSync);
                    plugin.getLogger().info("TuTienCore database sync enabled (PRIMARY storage).");

                    // ── Crash recovery: flush leftover JSON cache files into DB ──
                    final PlayerProgressDatabase finalDb = playerProgressDb;
                    final PlayerProgressDatabaseSync finalSync = databaseSync;
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        java.util.List<java.util.UUID> pending = localCache.getPendingFlushUUIDs();
                        if (pending.isEmpty()) return;
                        plugin.getLogger().info("[LocalDataCache] Flushing " + pending.size() + " leftover cache file(s) to database...");
                        for (java.util.UUID uuid : pending) {
                            com.turtle.tutiencore.core.storage.LocalDataCache.PlayerProgressSnapshot snap =
                                    localCache.loadPlayerProgress(uuid);
                            if (snap != null) {
                                try {
                                    java.util.List<com.turtle.tutiencore.core.infusion.OwnedInfusion> infusions = new java.util.ArrayList<>();
                                    for (com.turtle.tutiencore.core.storage.LocalDataCache.InfusionEntry e : snap.infusions()) {
                                        infusions.add(new com.turtle.tutiencore.core.infusion.OwnedInfusion(e.id(), e.typeId(), e.rarityId(), e.createdAt()));
                                    }
                                    finalDb.savePlayerData(uuid, snap.playerName(), snap.tuvi(),
                                            snap.tuLuyenSeconds(), snap.equippedInfusionId());
                                    finalDb.saveRealmData(uuid, snap.realmId(), snap.subRealm(),
                                            snap.breakthroughCount(), snap.breakthroughCooldown());
                                    finalDb.saveInfusions(uuid, infusions);
                                    finalDb.saveProgress(uuid, snap.playerName(), snap.tuvi(), snap.realmId(), snap.subRealm());
                                    localCache.evict(uuid);
                                    plugin.getLogger().info("[LocalDataCache] Flushed progress for " + snap.playerName() + " (" + uuid + ")");
                                } catch (java.sql.SQLException e) {
                                    plugin.getLogger().warning("[LocalDataCache] Failed to flush progress for " + uuid + ": " + e.getMessage());
                                }
                            }
                        }
                    });
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().severe("Failed to initialize TuTienCore player database: " + e.getMessage());
                }
            }
        }

        if (equipmentDbSettings.enabled()) {
            if (!"mysql".equalsIgnoreCase(equipmentDbSettings.type())) {
                plugin.getLogger().warning("Unsupported equipment-database.type: " + equipmentDbSettings.type());
            } else {
                try {
                    DriverManagerDataSource equipDataSource = new DriverManagerDataSource(equipmentDbSettings);
                    equipmentDb = new EquipmentDatabase(equipDataSource);
                    equipmentDb.initialize();
                    this.equipmentMenuManager.setLocalCache(localCache);
                    this.equipmentMenuManager.setEquipmentDatabase(equipmentDb);
                    plugin.getLogger().info("Equipment database initialized (PRIMARY storage).");
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().severe("Failed to initialize equipment database: " + e.getMessage());
                }
            }
        } else {
            // Wire cache even without DB so equipment is protected on no-DB setups
            this.equipmentMenuManager.setLocalCache(localCache);
        }

        // Create migration tool (null-safe if database is not enabled)
        DatabaseMigrationTool migrationTool = playerProgressDb != null && equipmentDb != null
            ? new DatabaseMigrationTool(plugin, playerDataManager, realmManager, equipmentMenuManager, playerProgressDb, equipmentDb)
            : null;

        // Register commands
        // Register /dotpha command
        this.dotPhaCommand = new DotPhaCommand(plugin, realmManager, breakthroughManager, realmListGUI);
        if (plugin.getCommand("dotpha") != null) {
            plugin.getCommand("dotpha").setExecutor(dotPhaCommand);
        }

        TuTienCommand commandHandler = new TuTienCommand(plugin, tuLuyenManager, zoneManager, configManager, dotPhaCommand,
                flySwordManager, realmManager, playerHologramManager, actionBarManager,
                infusionManager, afkKickManager, deathTipManager, regionRespawnManager, equipmentMenuManager,
                hotbarCommandItemManager, thauThiManager, migrationTool, this::reloadCommandAliases);
        if (plugin.getCommand("ttc") != null) {
            plugin.getCommand("ttc").setExecutor(commandHandler);
            plugin.getCommand("ttc").setTabCompleter(commandHandler);
        }
        if (plugin.getCommand("tuluyen") != null) {
            plugin.getCommand("tuluyen").setExecutor(commandHandler);
            plugin.getCommand("tuluyen").setTabCompleter(commandHandler);
        }

        // Register /canhgioi command
        CanhGioiCommand canhGioiCommand = new CanhGioiCommand(realmManager, realmListGUI);
        if (plugin.getCommand("canhgioi") != null) {
            plugin.getCommand("canhgioi").setExecutor(canhGioiCommand);
            plugin.getCommand("canhgioi").setTabCompleter(canhGioiCommand);
        }

        // Register /tuvi command
        TuViCommand tuViCommand = new TuViCommand(playerDataManager);
        if (plugin.getCommand("tuvi") != null) {
            plugin.getCommand("tuvi").setExecutor(tuViCommand);
            plugin.getCommand("tuvi").setTabCompleter(tuViCommand);
        }

        NhapThanCommand nhapThanCommand = new NhapThanCommand(infusionManager);
        if (plugin.getCommand("nhapthan") != null) {
            plugin.getCommand("nhapthan").setExecutor(nhapThanCommand);
            plugin.getCommand("nhapthan").setTabCompleter(nhapThanCommand);
        }
        if (plugin.getCommand("luathan") != null) {
            plugin.getCommand("luathan").setExecutor(nhapThanCommand);
            plugin.getCommand("luathan").setTabCompleter(nhapThanCommand);
        }
        if (plugin.getCommand("trangbi") != null) {
            plugin.getCommand("trangbi").setExecutor(equipmentMenuManager);
        }
        RankupCommand rankupCommand = new RankupCommand(plugin);
        if (plugin.getCommand("rankup") != null) {
            plugin.getCommand("rankup").setExecutor(rankupCommand);
            plugin.getCommand("rankup").setTabCompleter(rankupCommand);
        }
        if (plugin.getCommand("thauthi") != null) {
            plugin.getCommand("thauthi").setExecutor(thauThiManager);
        }
        reloadCommandAliases();

        // Register Placeholders
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.turtle.tutiencore.core.hook.TuTienPlaceholder(plugin, realmManager).register();
            plugin.getLogger().info("Registered PlaceholderAPI expansion");
        }
    }

    public void reloadCommandAliases() {
        if (this.commandAliasManager == null) {
            this.commandAliasManager = new CommandAliasManager(plugin);
        }
        this.commandAliasManager.registerAliases(configManager.getCommandAliases());
    }

    public void onDisable() {
        plugin.getLogger().info("Shutting down TuTienCore managers...");
        
        // Unregister listeners to avoid memory leaks with Plugman
        HandlerList.unregisterAll(plugin);

        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (infusionManager != null) {
            infusionManager.saveConfigFile();
        }
        if (equipmentMenuManager != null) {
            equipmentMenuManager.saveAll();
            equipmentMenuManager.removeAllOnlineModifiers();
        }
        if (realmManager != null) {
            realmManager.saveAllPlayerRealms();
        }
        if (breakthroughManager != null) {
            breakthroughManager.cleanup();
        }
        if (flySwordManager != null) {
            flySwordManager.stopTask();
        }
        if (offlineTuLuyenManager != null) {
            offlineTuLuyenManager.saveAll();
        }
        if (playerHologramManager != null) {
            playerHologramManager.stop();
        }
        if (actionBarManager != null) {
            actionBarManager.stop();
        }
        if (killRewardHologramManager != null) {
            killRewardHologramManager.removeAll();
        }
        if (thauThiManager != null) {
            thauThiManager.stop();
        }
        if (afkKickManager != null) {
            afkKickManager.stop();
        }
        if (deathTipManager != null) {
            deathTipManager.stop();
        }
        if (regionRespawnManager != null) {
            regionRespawnManager.stop();
        }
        if (zoneManager != null) {
            zoneManager.saveZones();
        }
        if (tuLuyenManager != null) {
            tuLuyenManager.stopTask();
        }
        if (actionBarSuppressor != null) {
            actionBarSuppressor.unregister();
        }
        if (mmoItemsMaxHealthPercentHook != null) {
            mmoItemsMaxHealthPercentHook.removeAllOnlineModifiers();
        }
        if (commandAliasManager != null) {
            commandAliasManager.unregisterAliases();
            commandAliasManager = null;
        }
        if (lineParticleTask != null) {
            lineParticleTask.stopAuraTask();
        }
        if (sphereParticleTask != null) {
            sphereParticleTask.stop();
        }

        // Forcibly cancel any lingering tasks registered by this plugin
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
