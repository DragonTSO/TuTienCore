package com.turtle.tutiencore.core;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.core.command.CanhGioiCommand;
import com.turtle.tutiencore.core.command.DotPhaCommand;
import com.turtle.tutiencore.core.command.TuTienCommand;
import com.turtle.tutiencore.core.command.TuViCommand;
import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.gui.RealmListGUI;
import com.turtle.tutiencore.core.hook.MMOCoreActionBarSuppressor;
import com.turtle.tutiencore.core.hook.MMOItemsRealmRequirementHook;
import com.turtle.tutiencore.core.manager.BreakthroughManager;
import com.turtle.tutiencore.core.manager.FlySwordManager;
import com.turtle.tutiencore.core.manager.OfflineTuLuyenManager;
import com.turtle.tutiencore.core.manager.RealmManager;
import com.turtle.tutiencore.core.manager.ZoneManager;
import com.turtle.tutiencore.core.manager.PlayerDataManager;
import com.turtle.tutiencore.core.manager.TuLuyenManager;
import com.turtle.tutiencore.core.task.SphereParticleTask;
import com.turtle.tutiencore.core.task.TuLuyenParticleTask;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

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
    private RealmListGUI realmListGUI;
    private DotPhaCommand dotPhaCommand;
    private MMOCoreActionBarSuppressor actionBarSuppressor;
    private MMOItemsRealmRequirementHook mmoItemsRealmRequirementHook;
    
    private SphereParticleTask sphereParticleTask;
    private TuLuyenParticleTask lineParticleTask;
    
    public TuTienCore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        plugin.getLogger().info("Loading TuTienCore managers...");
        
        this.configManager = new ConfigManager(plugin);
        
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
        this.realmListGUI = new RealmListGUI(plugin, realmManager);

        this.lineParticleTask = new TuLuyenParticleTask(plugin, configManager);
        this.tuLuyenManager = new TuLuyenManager(plugin, configManager, zoneManager, lineParticleTask, realmManager);
        this.lineParticleTask.setTuLuyenManager(this.tuLuyenManager);
        this.lineParticleTask.setRealmManager(this.realmManager);
        this.lineParticleTask.startAuraTask();

        this.actionBarSuppressor = new MMOCoreActionBarSuppressor(plugin, tuLuyenManager);
        this.tuLuyenManager.setActionBarSuppressor(this.actionBarSuppressor);
        this.actionBarSuppressor.register();

        this.mmoItemsRealmRequirementHook = new MMOItemsRealmRequirementHook(plugin, realmManager);
        this.mmoItemsRealmRequirementHook.register();

        this.sphereParticleTask = new SphereParticleTask(plugin, zoneManager, configManager);
        this.sphereParticleTask.start();

        // Inject managers into API impl so it can delegate calls
        this.playerDataManager.injectManagers(realmManager, breakthroughManager, tuLuyenManager);

        // Register commands
        // Register /dotpha command
        this.dotPhaCommand = new DotPhaCommand(plugin, realmManager, breakthroughManager, realmListGUI);
        if (plugin.getCommand("dotpha") != null) {
            plugin.getCommand("dotpha").setExecutor(dotPhaCommand);
        }

        TuTienCommand commandHandler = new TuTienCommand(tuLuyenManager, zoneManager, configManager, dotPhaCommand, flySwordManager, realmManager);
        if (plugin.getCommand("ttc") != null) {
            plugin.getCommand("ttc").setExecutor(commandHandler);
        }
        if (plugin.getCommand("tuluyen") != null) {
            plugin.getCommand("tuluyen").setExecutor(commandHandler);
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

        // Register Placeholders
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.turtle.tutiencore.core.hook.TuTienPlaceholder(plugin, realmManager).register();
            plugin.getLogger().info("Registered PlaceholderAPI expansion");
        }
    }

    public void onDisable() {
        plugin.getLogger().info("Shutting down TuTienCore managers...");
        
        // Unregister listeners to avoid memory leaks with Plugman
        HandlerList.unregisterAll(plugin);

        if (playerDataManager != null) {
            playerDataManager.saveAll();
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
            offlineTuLuyenManager.save();
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
