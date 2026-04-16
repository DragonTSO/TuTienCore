package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.core.model.CuboidZone;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZoneManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, CuboidZone> zones = new HashMap<>();
    private File file;
    private FileConfiguration config;

    // Wand selections
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    
    // Wand material
    private final Material wandMaterial = Material.BLAZE_ROD;

    public ZoneManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadFile();
        loadZones();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadFile() {
        file = new File(plugin.getDataFolder(), "zones.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Cannot create zones.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void loadZones() {
        zones.clear();
        if (config.contains("zones")) {
            ConfigurationSection sec = config.getConfigurationSection("zones");
            for (String key : sec.getKeys(false)) {
                Map<String, Object> map = new HashMap<>();
                map.put("pos1", sec.getLocation(key + ".pos1"));
                map.put("pos2", sec.getLocation(key + ".pos2"));
                if (sec.contains(key + ".center")) {
                    map.put("center", sec.getLocation(key + ".center"));
                }
                CuboidZone zone = CuboidZone.deserialize(key, map);
                zones.put(key, zone);
            }
        }
    }

    public void saveZones() {
        config.set("zones", null);
        for (CuboidZone zone : zones.values()) {
            config.set("zones." + zone.getId() + ".pos1", zone.getPos1());
            config.set("zones." + zone.getId() + ".pos2", zone.getPos2());
            if (zone.getCenter() != null) {
                config.set("zones." + zone.getId() + ".center", zone.getCenter());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot save zones.yml");
        }
    }

    public void createZone(String id, Location pos1, Location pos2) {
        CuboidZone zone = new CuboidZone(id, pos1, pos2);
        zones.put(id, zone);
        saveZones();
    }

    public void deleteZone(String id) {
        zones.remove(id);
        saveZones();
    }

    public CuboidZone getZone(String id) {
        return zones.get(id);
    }

    public CuboidZone getZoneAt(Location loc) {
        for (CuboidZone zone : zones.values()) {
            if (zone.contains(loc)) {
                return zone;
            }
        }
        return null;
    }

    public Collection<CuboidZone> getAllZones() {
        return zones.values();
    }

    // Wand Logic
    public Material getWandMaterial() {
        return wandMaterial;
    }

    public Location getPos1(Player player) {
        return pos1Map.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2Map.get(player.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!player.hasPermission("tutiencore.admin")) return;
        
        if (player.getInventory().getItemInMainHand().getType() == wandMaterial) {
            // Check if holding the wand item (you can make it check display name too if needed)
            if (event.getItem() == null || !event.getItem().hasItemMeta() || 
                !event.getItem().getItemMeta().getDisplayName().contains("TuTien Zone Wand")) {
                return;
            }

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                Location loc = event.getClickedBlock().getLocation();
                pos1Map.put(player.getUniqueId(), loc);
                player.sendMessage("§aPos1 set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                Location loc = event.getClickedBlock().getLocation();
                pos2Map.put(player.getUniqueId(), loc);
                player.sendMessage("§aPos2 set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }
        }
    }
}
