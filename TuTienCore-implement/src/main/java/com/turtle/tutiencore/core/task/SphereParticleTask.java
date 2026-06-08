package com.turtle.tutiencore.core.task;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.manager.ZoneManager;
import com.turtle.tutiencore.core.model.CuboidZone;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public class SphereParticleTask {

    private static final double VIEW_DISTANCE_SQUARED = 40.0D * 40.0D;

    private final JavaPlugin plugin;
    private final ZoneManager zoneManager;
    private final ConfigManager configManager;
    private BukkitRunnable task;

    public SphereParticleTask(JavaPlugin plugin, ZoneManager zoneManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.configManager = configManager;
    }

    public void start() {
        stop();
        if (!configManager.isSphereEnabled()) {
            return;
        }

        int interval = Math.max(1, configManager.getSphereInterval());
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (CuboidZone zone : zoneManager.getAllZones()) {
                    drawWhiteAsh(zone);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void drawWhiteAsh(CuboidZone zone) {
        Location pos1 = zone.getPos1();
        Location pos2 = zone.getPos2();
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
            return;
        }

        World world = pos1.getWorld();
        Location center = midpoint(pos1, pos2);
        WhiteAshParticleSettings settings = resolveWhiteAshParticleSettings(configManager.getSpherePoints());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        double minX = Math.min(pos1.getBlockX(), pos2.getBlockX()) + 0.15D;
        double maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 0.85D;
        double minY = Math.min(pos1.getBlockY(), pos2.getBlockY()) + 0.8D;
        double maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1.8D;
        double minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ()) + 0.15D;
        double maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 0.85D;

        for (int i = 0; i < settings.points(); i++) {
            double x = randomBetween(random, minX, maxX);
            double y = randomBetween(random, minY, maxY);
            double z = randomBetween(random, minZ, maxZ);
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(center) > VIEW_DISTANCE_SQUARED) {
                    continue;
                }
                player.spawnParticle(Particle.WHITE_ASH, x, y, z, settings.count(),
                        settings.offsetX(), settings.offsetY(), settings.offsetZ(), settings.extra());
            }
        }
    }

    static WhiteAshParticleSettings resolveWhiteAshParticleSettings(int configuredPoints) {
        int basePoints = Math.max(1, configuredPoints);
        return new WhiteAshParticleSettings(basePoints * 2, 2, 0.28D, 0.20D, 0.28D, 0.02D);
    }

    private Location midpoint(Location pos1, Location pos2) {
        return new Location(pos1.getWorld(),
                (pos1.getX() + pos2.getX()) / 2.0D,
                (pos1.getY() + pos2.getY()) / 2.0D,
                (pos1.getZ() + pos2.getZ()) / 2.0D);
    }

    private double randomBetween(ThreadLocalRandom random, double min, double max) {
        if (max <= min) {
            return min;
        }
        return random.nextDouble(min, max);
    }

    record WhiteAshParticleSettings(int points, int count, double offsetX, double offsetY, double offsetZ, double extra) {
    }
}
