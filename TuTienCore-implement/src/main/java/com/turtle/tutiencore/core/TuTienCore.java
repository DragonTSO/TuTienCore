package com.turtle.tutiencore.core;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.core.command.CanhGioiCommand;
import com.turtle.tutiencore.core.command.DotPhaCommand;
import com.turtle.tutiencore.core.command.TuTienCommand;
import com.turtle.tutiencore.core.command.TuViCommand;
import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.gui.RealmListGUI;
import com.turtle.tutiencore.core.manager.BreakthroughManager;
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
    private RealmListGUI realmListGUI;
    
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
        
        this.lineParticleTask = new TuLuyenParticleTask(plugin, configManager);
        this.tuLuyenManager = new TuLuyenManager(plugin, configManager, zoneManager, lineParticleTask);
        this.lineParticleTask.setTuLuyenManager(this.tuLuyenManager);
        this.lineParticleTask.startAuraTask();
        
        this.sphereParticleTask = new SphereParticleTask(plugin, zoneManager, configManager);
        this.sphereParticleTask.start();
        
        // Realm & Breakthrough System
        this.realmManager = new RealmManager(plugin);
        this.breakthroughManager = new BreakthroughManager(plugin, realmManager);
        this.realmListGUI = new RealmListGUI(plugin, realmManager);

        // Register commands
        TuTienCommand commandHandler = new TuTienCommand(tuLuyenManager, zoneManager, configManager);
        if (plugin.getCommand("ttc") != null) {
            plugin.getCommand("ttc").setExecutor(commandHandler);
        }
        if (plugin.getCommand("tuluyen") != null) {
            plugin.getCommand("tuluyen").setExecutor(commandHandler);
        }

        // Register /dotpha command
        DotPhaCommand dotPhaCommand = new DotPhaCommand(plugin, realmManager, breakthroughManager, realmListGUI);
        if (plugin.getCommand("dotpha") != null) {
            plugin.getCommand("dotpha").setExecutor(dotPhaCommand);
        }

        // Register /canhgioi command
        CanhGioiCommand canhGioiCommand = new CanhGioiCommand(realmManager);
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
            new com.turtle.tutiencore.core.hook.TuTienPlaceholder(realmManager).register();
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
        if (zoneManager != null) {
            zoneManager.saveZones();
        }
        if (tuLuyenManager != null) {
            tuLuyenManager.stopTask();
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
