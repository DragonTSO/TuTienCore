package com.turtle.tutiencore.core.model;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class CuboidZone {
    private String id;
    private Location pos1;
    private Location pos2;
    private Location center;
    private double tuViBonusPercent;

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
        map.put("tuvi-bonus-percent", tuViBonusPercent);
        return map;
    }

    public static CuboidZone deserialize(String id, Map<String, Object> map) {
        Location p1 = (Location) map.get("pos1");
        Location p2 = (Location) map.get("pos2");
        Location center = map.containsKey("center") ? (Location) map.get("center") : null;
        double tuViBonusPercent = 0.0D;
        Object rawBonus = map.get("tuvi-bonus-percent");
        if (rawBonus instanceof Number number) {
            tuViBonusPercent = number.doubleValue();
        }
        
        CuboidZone zone = new CuboidZone(id, p1, p2);
        zone.setCenter(center);
        zone.setTuViBonusPercent(tuViBonusPercent);
        return zone;
    }

    public String getId() {
        return id;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public double getTuViBonusPercent() {
        return tuViBonusPercent;
    }

    public void setTuViBonusPercent(double tuViBonusPercent) {
        this.tuViBonusPercent = Math.max(0.0D, tuViBonusPercent);
    }
}
