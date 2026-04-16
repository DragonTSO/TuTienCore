package com.turtle.tutiencore.core;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.core.command.TuTienCommand;
import com.turtle.tutiencore.core.config.ConfigManager;
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
        
        // Register commands
        TuTienCommand commandHandler = new TuTienCommand(tuLuyenManager, zoneManager, configManager);
        if (plugin.getCommand("ttc") != null) {
            plugin.getCommand("ttc").setExecutor(commandHandler);
        }
        if (plugin.getCommand("tuluyen") != null) {
            plugin.getCommand("tuluyen").setExecutor(commandHandler);
        }

        // Register Placeholders
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.turtle.tutiencore.core.hook.TuTienPlaceholder().register();
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
