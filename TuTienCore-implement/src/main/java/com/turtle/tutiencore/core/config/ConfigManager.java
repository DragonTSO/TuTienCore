package com.turtle.tutiencore.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;

    private FileConfiguration msgConfig;
    private File msgFile;

    // Tu Luyen settings
    private int tuLuyenInterval;
    private double maxDistance;
    private TuViPointRange pointsPerInterval;
    private String giveCommand;
    private boolean lightningBonusEnabled;
    private double lightningBonusChancePercent;
    private double lightningBonusMultiplier;
    private boolean tuLuyenIntervalResetSoundEnabled;
    private String tuLuyenIntervalResetSound;
    private String tuLuyenIntervalResetSoundCategory;
    private float tuLuyenIntervalResetSoundVolume;
    private float tuLuyenIntervalResetSoundPitch;
    private int offlineIntervalSeconds;
    private int offlineClaimX2Cost;
    private boolean offlineClaimX2RequirePermission;
    private String offlineClaimX2Permission;
    private String offlinePermission;
    private double offlineDefaultMultiplier;
    private double offlinePermissionMultiplier;
    private int offlineMaxHours;
    private boolean offlineOpenAfterResourcePack;
    private int offlineOpenMinSeconds;
    private long offlineOpenDelayTicks;
    private long offlineOpenFallbackDelayTicks;

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
        pointsPerInterval = TuViPointRange.parse(config.getString("tu-luyen.points-per-interval", "10"), 10);
        giveCommand = config.getString("tu-luyen.give-command", "eco give %player% %points%");
        lightningBonusEnabled = config.getBoolean("tu-luyen.lightning-bonus.enabled", true);
        lightningBonusChancePercent = Math.max(0.0, Math.min(100.0, config.getDouble("tu-luyen.lightning-bonus.chance-percent", 20.0)));
        lightningBonusMultiplier = Math.max(1.0, config.getDouble("tu-luyen.lightning-bonus.multiplier", 2.0));
        tuLuyenIntervalResetSoundEnabled = config.getBoolean("tu-luyen.sounds.interval-reset.enabled", true);
        tuLuyenIntervalResetSound = config.getString("tu-luyen.sounds.interval-reset.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        tuLuyenIntervalResetSoundCategory = config.getString("tu-luyen.sounds.interval-reset.category", "MASTER");
        tuLuyenIntervalResetSoundVolume = (float) config.getDouble("tu-luyen.sounds.interval-reset.volume", 1.0);
        tuLuyenIntervalResetSoundPitch = (float) config.getDouble("tu-luyen.sounds.interval-reset.pitch", 1.15);
        offlineIntervalSeconds = Math.max(1, config.getInt("offline-tuluyen.interval-seconds", 60));
        offlineClaimX2Cost = config.getInt("offline-tuluyen.claim-x2-cost", 100);
        offlineClaimX2RequirePermission = config.getBoolean("offline-tuluyen.claim-x2.require-permission", true);
        offlineClaimX2Permission = config.getString("offline-tuluyen.claim-x2.permission", "tutiencore.tuluyen.offline.x2");
        offlinePermission = config.getString("offline-tuluyen.permission", "tutiencore.tuluyen.vip");
        offlineDefaultMultiplier = Math.max(0.0, config.getDouble("offline-tuluyen.default-multiplier", 0.5));
        offlinePermissionMultiplier = Math.max(0.0, config.getDouble("offline-tuluyen.permission-multiplier", 1.0));
        offlineMaxHours = Math.max(0, config.getInt("offline-tuluyen.max-offline-hours", 8));
        offlineOpenAfterResourcePack = config.getBoolean("offline-tuluyen.open-gui.after-resourcepack", true);
        offlineOpenMinSeconds = Math.max(0, config.getInt("offline-tuluyen.open-gui.min-offline-seconds", 60));
        offlineOpenDelayTicks = Math.max(0L, config.getLong("offline-tuluyen.open-gui.delay-ticks", 20L));
        offlineOpenFallbackDelayTicks = Math.max(0L, config.getLong("offline-tuluyen.open-gui.fallback-delay-ticks", 300L));

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
        if (!config.isConfigurationSection("class-colors")) {
            // Auto-inject default class colors for servers with old config.yml
            plugin.getLogger().info("[ClassColor] 'class-colors' section not found — injecting defaults...");
            config.set("class-colors.KIEMTON", java.util.Arrays.asList(255, 215, 0, 255, 140, 0));
            config.set("class-colors.VANPHAP", java.util.Arrays.asList(212, 160, 23, 139, 105, 20));
            config.set("class-colors.BATHE", java.util.Arrays.asList(68, 136, 255, 34, 68, 170));
            config.set("class-colors.DUOCTIEN", java.util.Arrays.asList(85, 255, 85, 0, 170, 68));
            config.set("class-colors.ANHSAT", java.util.Arrays.asList(255, 68, 68, 255, 102, 0));
            plugin.saveConfig();
        }
        for (String classId : config.getConfigurationSection("class-colors").getKeys(false)) {
            List<Integer> rgb = config.getIntegerList("class-colors." + classId);
            if (rgb.size() >= 6) {
                classColors.put(classId.toUpperCase(), new int[][]{
                    {rgb.get(0), rgb.get(1), rgb.get(2)},
                    {rgb.get(3), rgb.get(4), rgb.get(5)}
                });
            }
        }
        plugin.getLogger().info("[ClassColor] Loaded " + classColors.size() + " class colors: " + classColors.keySet());

        tuluyenModel = config.getString("tuluyen-model", "toado");
    }

    /**
     * Get class color mapping. Key = class ID (uppercase), Value = [primary RGB, secondary RGB].
     */
    public Map<String, int[][]> getClassColors() {
        return classColors;
    }

    public int getTuLuyenInterval() {
        return tuLuyenInterval;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public int getPointsPerInterval() {
        return pointsPerInterval.roll();
    }

    public String getGiveCommand() {
        return giveCommand;
    }

    public boolean isSphereEnabled() {
        return sphereEnabled;
    }

    public int getSphereInterval() {
        return sphereInterval;
    }

    public double getSphereRadius() {
        return sphereRadius;
    }

    public int getSpherePoints() {
        return spherePoints;
    }

    public int getSphereColorR() {
        return sphereColorR;
    }

    public int getSphereColorG() {
        return sphereColorG;
    }

    public int getSphereColorB() {
        return sphereColorB;
    }

    public double getLineSpacing() {
        return lineSpacing;
    }

    public int getLineDuration() {
        return lineDuration;
    }

    public int getLineColorR() {
        return lineColorR;
    }

    public int getLineColorG() {
        return lineColorG;
    }

    public int getLineColorB() {
        return lineColorB;
    }

    public boolean isCultHelixEnabled() {
        return cultHelixEnabled;
    }

    public boolean isCultRaysEnabled() {
        return cultRaysEnabled;
    }

    public boolean isCultLightningEnabled() {
        return cultLightningEnabled;
    }

    public boolean isCultAbsorptionEnabled() {
        return cultAbsorptionEnabled;
    }

    public boolean isCultGroundCircleEnabled() {
        return cultGroundCircleEnabled;
    }

    public boolean isCultPillarEnabled() {
        return cultPillarEnabled;
    }

    public boolean isCultAmbientEnabled() {
        return cultAmbientEnabled;
    }

    public String getTuluyenModel() {
        return tuluyenModel;
    }

    public int rollPointsPerInterval() {
        return pointsPerInterval.roll();
    }

    public boolean isLightningBonusEnabled() {
        return lightningBonusEnabled;
    }

    public double getLightningBonusChancePercent() {
        return lightningBonusChancePercent;
    }

    public double getLightningBonusMultiplier() {
        return lightningBonusMultiplier;
    }

    public boolean isTuLuyenIntervalResetSoundEnabled() {
        return tuLuyenIntervalResetSoundEnabled;
    }

    public String getTuLuyenIntervalResetSound() {
        return tuLuyenIntervalResetSound;
    }

    public String getTuLuyenIntervalResetSoundCategory() {
        return tuLuyenIntervalResetSoundCategory;
    }

    public float getTuLuyenIntervalResetSoundVolume() {
        return tuLuyenIntervalResetSoundVolume;
    }

    public float getTuLuyenIntervalResetSoundPitch() {
        return tuLuyenIntervalResetSoundPitch;
    }

    public int getOfflineIntervalSeconds() {
        return offlineIntervalSeconds;
    }

    public int getOfflineClaimX2Cost() {
        return offlineClaimX2Cost;
    }

    public boolean isOfflineClaimX2PermissionRequired() {
        return offlineClaimX2RequirePermission;
    }

    public String getOfflineClaimX2Permission() {
        return offlineClaimX2Permission;
    }

    public String getOfflinePermission() {
        return offlinePermission;
    }

    public double getOfflineDefaultMultiplier() {
        return offlineDefaultMultiplier;
    }

    public double getOfflinePermissionMultiplier() {
        return offlinePermissionMultiplier;
    }

    public int getOfflineMaxHours() {
        return offlineMaxHours;
    }

    public boolean isOfflineOpenAfterResourcePack() {
        return offlineOpenAfterResourcePack;
    }

    public int getOfflineOpenMinSeconds() {
        return offlineOpenMinSeconds;
    }

    public long getOfflineOpenDelayTicks() {
        return offlineOpenDelayTicks;
    }

    public long getOfflineOpenFallbackDelayTicks() {
        return offlineOpenFallbackDelayTicks;
    }

    public Map<String, List<String>> getCommandAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("ttc", getCommandAliases("ttc", List.of()));
        aliases.put("tuluyen", getCommandAliases("tuluyen", List.of("tl")));
        aliases.put("dotpha", getCommandAliases("dotpha", List.of("dp")));
        aliases.put("canhgioi", getCommandAliases("canhgioi", List.of("realm")));
        aliases.put("tuvi", getCommandAliases("tuvi", List.of()));
        aliases.put("thauthi", getCommandAliases("thauthi", List.of()));
        return aliases;
    }

    private List<String> getCommandAliases(String command, List<String> defaults) {
        String path = "commands." + command + ".aliases";
        if (!plugin.getConfig().contains(path)) {
            return new ArrayList<>(defaults);
        }
        return new ArrayList<>(plugin.getConfig().getStringList(path));
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
