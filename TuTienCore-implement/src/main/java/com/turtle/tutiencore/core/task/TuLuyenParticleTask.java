package com.turtle.tutiencore.core.task;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.manager.TuLuyenManager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public class TuLuyenParticleTask {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private TuLuyenManager tuLuyenManager;
    private final Random random = new Random();
    private BukkitRunnable auraTask;

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
            double phi = 0;
            @Override
            public void run() {
                if (tuLuyenManager == null) return;
                
                phi += Math.PI / 8;
                
                for (Player player : tuLuyenManager.getTuLuyenPlayers()) {
                    Location chest = player.getLocation().add(0, 1.0, 0);
                    
                    // 1. Swirling particles (Pink / White aura base)
                    for (int i = 0; i < 2; i++) {
                        double t = phi + (i * Math.PI);
                        double r = 1.2;
                        double x = r * Math.cos(t);
                        double z = r * Math.sin(t);
                        double y = Math.sin(phi * 0.5) * 0.5;
                        
                        Location loc = chest.clone().add(x, y, z);
                        
                        // Vector pointing inward so they appear to absorb or move slightly
                        Vector dir = chest.toVector().subtract(loc.toVector()).normalize().multiply(0.05);

                        try {
                            player.getWorld().spawnParticle(Particle.valueOf("CHERRY_LEAVES"), loc, 1, 0.1, 0.1, 0.1, 0);
                        } catch (Exception e) {
                            // Fallback if CHERRY_LEAVES isn't found
                            player.getWorld().spawnParticle(Particle.END_ROD, loc, 0, dir.getX(), dir.getY(), dir.getZ(), 0.05);
                        }
                    }

                    // 2. Absorption specs (Flying inward towards the player's chest)
                    for (int i = 0; i < 3; i++) {
                        // Spawn randomly in a 3-block radius
                        double rx = (random.nextDouble() - 0.5) * 6;
                        double ry = (random.nextDouble() * 3);
                        double rz = (random.nextDouble() - 0.5) * 6;
                        Location outerLoc = player.getLocation().add(rx, ry, rz);
                        
                        Vector dir = chest.toVector().subtract(outerLoc.toVector()).normalize();
                        
                        try {
                            player.getWorld().spawnParticle(Particle.valueOf("FIREWORK"), outerLoc, 0, dir.getX(), dir.getY(), dir.getZ(), 0.15);
                        } catch (Exception e) {
                            player.getWorld().spawnParticle(Particle.END_ROD, outerLoc, 0, dir.getX(), dir.getY(), dir.getZ(), 0.15);
                        }
                    }
                }
            }
        };
        auraTask.runTaskTimerAsynchronously(plugin, 0L, 2L);
    }

    public void stopAuraTask() {
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
    }
}
