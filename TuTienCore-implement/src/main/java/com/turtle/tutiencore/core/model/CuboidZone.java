package com.turtle.tutiencore.core.model;

import lombok.Data;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

@Data
public class CuboidZone {
    private String id;
    private Location pos1;
    private Location pos2;
    private Location center;

    public CuboidZone(String id, Location pos1, Location pos2) {
        this.id = id;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public boolean contains(Location loc) {
        if (loc == null || pos1 == null || pos2 == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().equals(pos1.getWorld())) return false;

        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("pos1", pos1);
        map.put("pos2", pos2);
        map.put("center", center);
        return map;
    }

    public static CuboidZone deserialize(String id, Map<String, Object> map) {
        Location p1 = (Location) map.get("pos1");
        Location p2 = (Location) map.get("pos2");
        Location center = map.containsKey("center") ? (Location) map.get("center") : null;
        
        CuboidZone zone = new CuboidZone(id, p1, p2);
        zone.setCenter(center);
        return zone;
    }
}
