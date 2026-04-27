package com.turtle.tutiencore.core.task;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.manager.TuLuyenManager;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Tu Luyện Particle Effect System
 * 
 * Creates an epic cultivation meditation visual effect:
 * - Double helix spiral aura (cyan energy swirls)
 * - Energy burst rays shooting outward
 * - Lightning-like electric arcs around the player
 * - Inward absorption particles (spiritual energy gathering)
 * - Ground runic circle
 * - Vertical energy pillar
 */
public class TuLuyenParticleTask {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private TuLuyenManager tuLuyenManager;
    private final Random random = new Random();
    private BukkitRunnable auraTask;

    // Animation state
    private double globalTick = 0;

    public TuLuyenParticleTask(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void setTuLuyenManager(TuLuyenManager tuLuyenManager) {
        this.tuLuyenManager = tuLuyenManager;
    }

    // Keep this method if it was called elsewhere, but we won't do much inside it now
    public void drawLine(Player player, Location center) {
        // Obsolete: We are using a continuous aura task instead of periodic lines
    }

    public void startAuraTask() {
        auraTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (tuLuyenManager == null) return;

                globalTick += 1;

                for (Player player : tuLuyenManager.getTuLuyenPlayers()) {
                    try {
                        spawnCultivationEffect(player);
                    } catch (Exception e) {
                        // Silently ignore particle errors for version compatibility
                    }
                }
            }
        };
        auraTask.runTaskTimerAsynchronously(plugin, 0L, 1L); // Every tick for smooth animation
    }

    /**
     * Main effect orchestrator - spawns all cultivation visual effects
     */
    private void spawnCultivationEffect(Player player) {
        Location base = player.getLocation();
        World world = player.getWorld();

        // === LAYER 1: Double Helix Energy Spiral ===
        if (configManager.isCultHelixEnabled()) {
            spawnDoubleHelix(world, base);
        }

        // === LAYER 2: Energy Burst Rays (every 3 ticks) ===
        if (configManager.isCultRaysEnabled() && (int) globalTick % 3 == 0) {
            spawnEnergyBurstRays(world, base);
        }

        // === LAYER 3: Lightning/Electric Arcs (every 5 ticks) ===
        if (configManager.isCultLightningEnabled() && (int) globalTick % 5 == 0) {
            spawnLightningArcs(world, base);
        }

        // === LAYER 4: Inward Absorption Particles (every 2 ticks) ===
        if (configManager.isCultAbsorptionEnabled() && (int) globalTick % 2 == 0) {
            spawnAbsorptionParticles(world, base);
        }

        // === LAYER 5: Ground Runic Circle (every 4 ticks) ===
        if (configManager.isCultGroundCircleEnabled() && (int) globalTick % 4 == 0) {
            spawnGroundCircle(world, base);
        }

        // === LAYER 6: Vertical Energy Pillar (every 6 ticks) ===
        if (configManager.isCultPillarEnabled() && (int) globalTick % 6 == 0) {
            spawnEnergyPillar(world, base);
        }

        // === LAYER 7: Ambient Floating Particles ===
        if (configManager.isCultAmbientEnabled() && (int) globalTick % 3 == 0) {
            spawnAmbientFloatingParticles(world, base);
        }
    }

    /**
     * LAYER 1: Double Helix - Two intertwined spiral arms rotating around the player
     * Creates the main swirling energy effect seen in the reference image
     */
    private void spawnDoubleHelix(World world, Location base) {
        double speed = globalTick * 0.15; // Rotation speed
        double heightRange = 2.5; // Total height of helix

        for (int arm = 0; arm < 2; arm++) {
            double armOffset = arm * Math.PI; // 180 degree offset between arms

            // Each arm has multiple points for a smooth trail
            for (int i = 0; i < 8; i++) {
                double t = speed + (i * 0.25) + armOffset;
                double radius = 1.0 + Math.sin(globalTick * 0.05 + i * 0.3) * 0.3; // Pulsing radius
                double x = radius * Math.cos(t);
                double z = radius * Math.sin(t);
                double y = (((globalTick * 0.08 + i * 0.15) % heightRange));

                Location loc = base.clone().add(x, y, z);

                // Main cyan/aqua color
                spawnColoredDust(world, loc, 0, 220, 255, 1.2f);

                // Secondary white glow trail
                if (i % 2 == 0) {
                    spawnColoredDust(world, loc, 180, 240, 255, 0.8f);
                }
            }
        }
    }

    /**
     * LAYER 2: Energy Burst Rays - Tia năng lượng bắn ra từ tâm
     * Creates the explosive ray effect spreading outward from the player
     */
    private void spawnEnergyBurstRays(World world, Location base) {
        Location chest = base.clone().add(0, 1.2, 0);
        int rayCount = 6 + random.nextInt(4); // 6-9 rays

        for (int i = 0; i < rayCount; i++) {
            // Random direction for each ray
            double angle = random.nextDouble() * Math.PI * 2;
            double pitch = (random.nextDouble() - 0.3) * Math.PI * 0.6; // Slightly upward bias

            double dirX = Math.cos(angle) * Math.cos(pitch);
            double dirY = Math.sin(pitch) * 0.5 + 0.2; // Upward tendency
            double dirZ = Math.sin(angle) * Math.cos(pitch);

            // Each ray has multiple points along its length
            double rayLength = 1.5 + random.nextDouble() * 2.5;
            int points = (int) (rayLength * 4);

            for (int j = 0; j < points; j++) {
                double dist = (j / (double) points) * rayLength;
                Location rayLoc = chest.clone().add(dirX * dist, dirY * dist, dirZ * dist);

                // Fade from bright cyan to transparent as distance increases
                float fade = 1.0f - (float) (dist / rayLength) * 0.7f;
                int r = (int) (30 * fade);
                int g = (int) (200 + 55 * fade);
                int b = 255;

                spawnColoredDust(world, rayLoc, r, g, b, fade * 1.0f);
            }
        }
    }

    /**
     * LAYER 3: Lightning/Electric Arcs - Sét điện quanh người
     * Creates jagged lightning bolts circling around the player
     */
    private void spawnLightningArcs(World world, Location base) {
        int arcCount = 2 + random.nextInt(3);

        for (int arc = 0; arc < arcCount; arc++) {
            // Random start point on a sphere around the player
            double startAngle = random.nextDouble() * Math.PI * 2;
            double startY = 0.3 + random.nextDouble() * 2.0;
            double startRadius = 0.8 + random.nextDouble() * 0.8;

            Location start = base.clone().add(
                    startRadius * Math.cos(startAngle),
                    startY,
                    startRadius * Math.sin(startAngle)
            );

            // Random end point
            double endAngle = startAngle + (random.nextDouble() - 0.5) * Math.PI;
            double endY = startY + (random.nextDouble() - 0.5) * 1.5;
            double endRadius = startRadius + (random.nextDouble() - 0.5) * 0.6;

            Location end = base.clone().add(
                    endRadius * Math.cos(endAngle),
                    Math.max(0.1, endY),
                    endRadius * Math.sin(endAngle)
            );

            // Draw jagged line between start and end
            int segments = 6 + random.nextInt(5);
            Location prev = start.clone();

            for (int i = 1; i <= segments; i++) {
                double progress = i / (double) segments;

                // Interpolate with random jitter
                double jitterX = (random.nextDouble() - 0.5) * 0.3;
                double jitterY = (random.nextDouble() - 0.5) * 0.3;
                double jitterZ = (random.nextDouble() - 0.5) * 0.3;

                Location point = new Location(world,
                        start.getX() + (end.getX() - start.getX()) * progress + jitterX,
                        start.getY() + (end.getY() - start.getY()) * progress + jitterY,
                        start.getZ() + (end.getZ() - start.getZ()) * progress + jitterZ
                );

                // Draw line from prev to point
                double dist = prev.distance(point);
                int linePoints = Math.max(2, (int) (dist * 6));
                for (int j = 0; j < linePoints; j++) {
                    double t = j / (double) linePoints;
                    Location lineLoc = new Location(world,
                            prev.getX() + (point.getX() - prev.getX()) * t,
                            prev.getY() + (point.getY() - prev.getY()) * t,
                            prev.getZ() + (point.getZ() - prev.getZ()) * t
                    );

                    // Bright white-cyan for lightning
                    spawnColoredDust(world, lineLoc, 200, 240, 255, 0.6f);
                }

                prev = point;
            }
        }
    }

    /**
     * LAYER 4: Absorption Particles - Linh khí bị hấp thụ vào người
     * Particles fly inward towards the player's chest from far away
     */
    private void spawnAbsorptionParticles(World world, Location base) {
        Location chest = base.clone().add(0, 1.0, 0);

        for (int i = 0; i < 4; i++) {
            // Spawn from random positions in a 4-block radius
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 2.0 + random.nextDouble() * 3.0;
            double y = random.nextDouble() * 3.0;

            Location outerLoc = base.clone().add(
                    radius * Math.cos(angle),
                    y,
                    radius * Math.sin(angle)
            );

            // Direction pointing inward to player chest
            Vector dir = chest.toVector().subtract(outerLoc.toVector()).normalize();

            // Use END_ROD for a glowing flying effect toward the player
            try {
                world.spawnParticle(Particle.END_ROD, outerLoc, 0,
                        dir.getX(), dir.getY(), dir.getZ(), 0.12);
            } catch (Exception ignored) {}

            // Also spawn some with Dust for color
            spawnColoredDust(world, outerLoc, 100, 255, 230, 1.0f);
        }
    }

    /**
     * LAYER 5: Ground Runic Circle - Vòng tròn pháp trận dưới chân
     * Creates a glowing circle on the ground beneath the player
     */
    private void spawnGroundCircle(World world, Location base) {
        double radius = 1.8;
        double rotation = globalTick * 0.08; // Slowly rotating
        int points = 24;

        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i / points) + rotation;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location loc = base.clone().add(x, 0.05, z);

            // Alternating colors for a mystical look
            if (i % 3 == 0) {
                spawnColoredDust(world, loc, 0, 255, 220, 0.8f); // Cyan
            } else {
                spawnColoredDust(world, loc, 100, 180, 255, 0.6f); // Light blue
            }
        }

        // Inner circle
        double innerRadius = 0.9;
        for (int i = 0; i < 12; i++) {
            double angle = (2 * Math.PI * i / 12) - rotation * 1.5; // Counter-rotation
            double x = innerRadius * Math.cos(angle);
            double z = innerRadius * Math.sin(angle);

            Location loc = base.clone().add(x, 0.05, z);
            spawnColoredDust(world, loc, 200, 255, 255, 0.5f); // White-cyan
        }
    }

    /**
     * LAYER 6: Vertical Energy Pillar - Cột năng lượng chiếu thẳng lên trời
     * A thin beam of energy shooting upward from the player
     */
    private void spawnEnergyPillar(World world, Location base) {
        double pillarHeight = 4.0 + Math.sin(globalTick * 0.03) * 1.5; // Pulsing height
        int points = (int) (pillarHeight * 3);

        for (int i = 0; i < points; i++) {
            double y = (i / (double) points) * pillarHeight;
            double wobble = Math.sin(globalTick * 0.1 + y * 2) * 0.1;

            Location loc = base.clone().add(wobble, y, wobble);

            // Fade from bright at bottom to transparent at top
            float intensity = 1.0f - (float) (y / pillarHeight) * 0.8f;
            int r = (int) (50 * intensity);
            int g = (int) (220 * intensity);
            int b = 255;

            spawnColoredDust(world, loc, r, g, b, intensity * 0.7f);
        }
    }

    /**
     * LAYER 7: Ambient Floating Particles - Hạt năng lượng bay lơ lửng
     * Small glowing specs floating around the player
     */
    private void spawnAmbientFloatingParticles(World world, Location base) {
        for (int i = 0; i < 3; i++) {
            double x = (random.nextDouble() - 0.5) * 4;
            double y = random.nextDouble() * 3;
            double z = (random.nextDouble() - 0.5) * 4;

            Location loc = base.clone().add(x, y, z);

            // Slow upward float
            try {
                world.spawnParticle(Particle.END_ROD, loc, 0, 0, 0.02, 0, 0.01);
            } catch (Exception ignored) {}

            // Enchantment table particles (spiritual energy text)
            try {
                world.spawnParticle(Particle.ENCHANT, base.clone().add(0, 1, 0), 3,
                        1.5, 1.0, 1.5, 0.5);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Utility: Spawn colored dust particle with version compatibility
     */
    private void spawnColoredDust(World world, Location loc, int r, int g, int b, float size) {
        try {
            Particle.DustOptions dust = new Particle.DustOptions(
                    Color.fromRGB(
                            Math.max(0, Math.min(255, r)),
                            Math.max(0, Math.min(255, g)),
                            Math.max(0, Math.min(255, b))
                    ),
                    size
            );
            world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
        } catch (Exception e) {
            // Fallback for older versions: use REDSTONE
            try {
                world.spawnParticle(Particle.valueOf("REDSTONE"), loc, 0,
                        r / 255.0, g / 255.0, b / 255.0, 1);
            } catch (Exception ignored) {}
        }
    }

    public void stopAuraTask() {
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
    }
}
