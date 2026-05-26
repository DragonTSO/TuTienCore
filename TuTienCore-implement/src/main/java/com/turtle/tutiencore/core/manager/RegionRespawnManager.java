package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.core.model.CuboidZone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RegionRespawnManager implements Listener {

    private static final String CONFIG_PATH = "region-respawn";

    private final JavaPlugin plugin;
    private final ZoneManager zoneManager;
    private final ConcurrentMap<UUID, Location> lastDeathLocations = new ConcurrentHashMap<>();
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private String checkLocationMode;
    private boolean debug;

    public RegionRespawnManager(JavaPlugin plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        loadConfigFile();
        enabled = config.getBoolean("enabled", false);
        checkLocationMode = config.getString("check-location", "death");
        if (checkLocationMode == null || checkLocationMode.isBlank()) {
            checkLocationMode = "death";
        }
        checkLocationMode = checkLocationMode.trim().toLowerCase(Locale.ROOT);
        debug = config.getBoolean("debug", false);
    }

    private void loadConfigFile() {
        configFile = new File(plugin.getDataFolder(), "region-respawn.yml");
        if (!configFile.exists()) {
            plugin.saveResource("region-respawn.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void stop() {
        lastDeathLocations.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }
        lastDeathLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation().clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!enabled) {
            return;
        }

        Location deathLocation = lastDeathLocations.remove(event.getPlayer().getUniqueId());
        Location checkLocation = "respawn".equals(checkLocationMode) || deathLocation == null
                ? event.getRespawnLocation()
                : deathLocation;
        RespawnRule rule = findRule(checkLocation);
        if (rule == null) {
            return;
        }

        event.setRespawnLocation(rule.location());
        if (debug) {
            plugin.getLogger().info("[RegionRespawn] " + event.getPlayer().getName()
                    + " matched region " + rule.key()
                    + " and respawns at " + formatLocation(rule.location()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastDeathLocations.remove(event.getPlayer().getUniqueId());
    }

    private RespawnRule findRule(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        ConfigurationSection regions = config.getConfigurationSection("regions");
        if (regions == null) {
            return null;
        }

        Set<String> regionIds = getRegionIds(location);
        if (regionIds.isEmpty()) {
            return null;
        }

        for (String key : regions.getKeys(false)) {
            ConfigurationSection ruleSection = regions.getConfigurationSection(key);
            if (ruleSection == null) {
                continue;
            }
            if (!matches(ruleSection, key, regionIds)) {
                continue;
            }

            Location respawnLocation = parseRespawnLocation(ruleSection, location.getWorld());
            if (respawnLocation == null) {
                plugin.getLogger().warning("[RegionRespawn] Invalid respawn location for region rule: " + key);
                continue;
            }
            return new RespawnRule(key, respawnLocation);
        }
        return null;
    }

    private Set<String> getRegionIds(Location location) {
        Set<String> ids = new HashSet<>();

        CuboidZone zone = zoneManager.getZoneAt(location);
        if (zone != null && zone.getId() != null) {
            ids.add(normalize(zone.getId()));
        }

        ids.addAll(getWorldGuardRegionIds(location));
        return ids;
    }

    private Set<String> getWorldGuardRegionIds(Location location) {
        Set<String> ids = new HashSet<>();
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedLocation = bukkitAdapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);
            Object applicableRegions = query.getClass()
                    .getMethod("getApplicableRegions", adaptedLocation.getClass())
                    .invoke(query, adaptedLocation);
            Iterable<?> regions = (Iterable<?>) applicableRegions.getClass().getMethod("getRegions").invoke(applicableRegions);
            for (Object region : regions) {
                String id = String.valueOf(region.getClass().getMethod("getId").invoke(region));
                ids.add(normalize(id));
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // WorldGuard is optional. TuTienCore zones still work without it.
        }
        return ids;
    }

    private boolean matches(ConfigurationSection ruleSection, String key, Set<String> regionIds) {
        Set<String> candidates = new HashSet<>();
        candidates.add(normalize(key));

        String region = ruleSection.getString("region");
        if (region != null && !region.isBlank()) {
            candidates.add(normalize(region));
        }

        String zone = ruleSection.getString("zone");
        if (zone != null && !zone.isBlank()) {
            candidates.add(normalize(zone));
        }

        List<String> regions = ruleSection.getStringList("regions");
        for (String id : regions) {
            if (id != null && !id.isBlank()) {
                candidates.add(normalize(id));
            }
        }

        for (String candidate : candidates) {
            if (regionIds.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Location parseRespawnLocation(ConfigurationSection section, World fallbackWorld) {
        String worldName = section.getString("world", fallbackWorld == null ? null : fallbackWorld.getName());
        World world = worldName == null ? fallbackWorld : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        if (!section.isDouble("x") && !section.isInt("x")) {
            return null;
        }
        if (!section.isDouble("y") && !section.isInt("y")) {
            return null;
        }
        if (!section.isDouble("z") && !section.isInt("z")) {
            return null;
        }

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName()
                + " " + location.getX()
                + " " + location.getY()
                + " " + location.getZ()
                + " yaw=" + location.getYaw()
                + " pitch=" + location.getPitch();
    }

    private record RespawnRule(String key, Location location) {
    }
}
