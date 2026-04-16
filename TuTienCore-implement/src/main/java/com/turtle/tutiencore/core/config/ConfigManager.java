package com.turtle.tutiencore.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;

import java.io.File;

@Getter
public class ConfigManager {

    private final JavaPlugin plugin;

    private FileConfiguration msgConfig;
    private File msgFile;

    // Tu Luyen settings
    private int tuLuyenInterval;
    private double maxDistance;
    private int pointsPerInterval;
    private String giveCommand;

    // Particles Settings
    private boolean sphereEnabled;
    private int sphereInterval;
    private double sphereRadius;
    private int spherePoints;
    private int sphereColorR;
    private int sphereColorG;
    private int sphereColorB;

    private double lineSpacing;
    private int lineDuration;
    private int lineColorR;
    private int lineColorG;
    private int lineColorB;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        
        msgFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!msgFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        load();
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        msgConfig = YamlConfiguration.loadConfiguration(msgFile);

        tuLuyenInterval = config.getInt("tu-luyen.interval", 100);
        maxDistance = config.getDouble("tu-luyen.max-distance", 10.0);
        pointsPerInterval = config.getInt("tu-luyen.points-per-interval", 10);
        giveCommand = config.getString("tu-luyen.give-command", "eco give %player% %points%");

        sphereEnabled = config.getBoolean("particles.sphere.enabled", true);
        sphereInterval = config.getInt("particles.sphere.interval", 5);
        sphereRadius = config.getDouble("particles.sphere.radius", 1.5);
        spherePoints = config.getInt("particles.sphere.points", 100);
        sphereColorR = config.getInt("particles.sphere.color.r", 66);
        sphereColorG = config.getInt("particles.sphere.color.g", 245);
        sphereColorB = config.getInt("particles.sphere.color.b", 227);

        lineSpacing = config.getDouble("particles.line.spacing", 0.2);
        lineDuration = config.getInt("particles.line.duration", 10);
        lineColorR = config.getInt("particles.line.color.r", 255);
        lineColorG = config.getInt("particles.line.color.g", 255);
        lineColorB = config.getInt("particles.line.color.b", 255);

        tuluyenModel = config.getString("tuluyen-model", "toado");
    }

    private String tuluyenModel;

    // Message access utilities

    public String getMsgStarted() { return getMessage("started", "&aBạn đã bắt đầu tu luyện!"); }
    public String getMsgStopped() { return getMessage("stopped", "&cBạn đã ngừng tu luyện."); }
    public String getMsgTooFar() { return getMessage("too-far", "&cBạn phải ở bên trong Khu Vực Tu Luyện!"); }
    public String getMsgReceived() { return getMessage("received", "&a+ %points% Tu Vi"); }

    public String getMessage(String path, String def) {
        if (msgConfig == null) return parseColor(def);
        return parseColor(msgConfig.getString(path, def));
    }

    private String parseColor(String msg) {
        if (msg == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
}
