package com.turtle.tutiencore.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Cultivation Effect settings
    private int cultPrimaryR;
    private int cultPrimaryG;
    private int cultPrimaryB;
    private boolean cultHelixEnabled;
    private boolean cultRaysEnabled;
    private boolean cultLightningEnabled;
    private boolean cultAbsorptionEnabled;
    private boolean cultGroundCircleEnabled;
    private boolean cultPillarEnabled;
    private boolean cultAmbientEnabled;

    // Class-based particle colors (loaded from config)
    private final Map<String, int[][]> classColors = new HashMap<>();

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

        // Cultivation Effects
        cultPrimaryR = config.getInt("cultivation-effects.primary-color.r", 0);
        cultPrimaryG = config.getInt("cultivation-effects.primary-color.g", 220);
        cultPrimaryB = config.getInt("cultivation-effects.primary-color.b", 255);
        cultHelixEnabled = config.getBoolean("cultivation-effects.helix.enabled", true);
        cultRaysEnabled = config.getBoolean("cultivation-effects.rays.enabled", true);
        cultLightningEnabled = config.getBoolean("cultivation-effects.lightning.enabled", true);
        cultAbsorptionEnabled = config.getBoolean("cultivation-effects.absorption.enabled", true);
        cultGroundCircleEnabled = config.getBoolean("cultivation-effects.ground-circle.enabled", true);
        cultPillarEnabled = config.getBoolean("cultivation-effects.pillar.enabled", true);
        cultAmbientEnabled = config.getBoolean("cultivation-effects.ambient.enabled", true);

        // Load class colors
        classColors.clear();
        if (config.isConfigurationSection("class-colors")) {
            for (String classId : config.getConfigurationSection("class-colors").getKeys(false)) {
                List<Integer> rgb = config.getIntegerList("class-colors." + classId);
                if (rgb.size() >= 6) {
                    classColors.put(classId.toUpperCase(), new int[][]{
                        {rgb.get(0), rgb.get(1), rgb.get(2)},
                        {rgb.get(3), rgb.get(4), rgb.get(5)}
                    });
                }
            }
        }

        tuluyenModel = config.getString("tuluyen-model", "toado");
    }

    /**
     * Get class color mapping. Key = class ID (uppercase), Value = [primary RGB, secondary RGB].
     */
    public Map<String, int[][]> getClassColors() {
        return classColors;
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
