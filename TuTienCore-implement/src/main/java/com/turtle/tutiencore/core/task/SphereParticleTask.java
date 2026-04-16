package com.turtle.tutiencore.core.task;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.manager.ZoneManager;
import com.turtle.tutiencore.core.model.CuboidZone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedParticle;

public class SphereParticleTask {

    private final JavaPlugin plugin;
    private final ZoneManager zoneManager;
    private final ConfigManager configManager;
    private final ProtocolManager protocolManager;
    private BukkitRunnable task;
    private double rotationAngle = 0;

    public SphereParticleTask(JavaPlugin plugin, ZoneManager zoneManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.configManager = configManager;
        if (plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
        } else {
            this.protocolManager = null;
        }
    }

    public void start() {
        if (!configManager.isSphereEnabled()) return;

        int interval = configManager.getSphereInterval();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                rotationAngle += 0.1;
                if (rotationAngle > Math.PI * 2) rotationAngle -= Math.PI * 2;

                for (CuboidZone zone : zoneManager.getAllZones()) {
                    Location loc = zone.getCenter();
                    if (loc == null || loc.getWorld() == null) continue;

                    // Ideally only player near enough should see to optimize
                    drawSphere(loc);
                }
            }
        };
        task.runTaskTimerAsynchronously(plugin, 0L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void drawSphere(Location center) {
        if (protocolManager == null) return;
        double radius = configManager.getSphereRadius();
        int points = configManager.getSpherePoints();
        double phi = (1 + Math.sqrt(5)) / 2;

        for (int i = 0; i < points; i++) {
            double theta = Math.acos(1 - 2.0 * (i + 0.5) / points);
            double lambda = 2 * Math.PI * i / phi + rotationAngle;

            double x = center.getX() + radius * Math.sin(theta) * Math.cos(lambda);
            double y = center.getY() + radius * Math.cos(theta) + 1.5;
            double z = center.getZ() + radius * Math.sin(theta) * Math.sin(lambda);

            for (Player player : center.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(center) > 1600) continue; // 40 blocks
                sendDustPacket(player, x, y, z, configManager.getSphereColorR(), configManager.getSphereColorG(), configManager.getSphereColorB());
            }
        }
    }

    private void sendDustPacket(Player player, double x, double y, double z, int r, int g, int b) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.WORLD_PARTICLES);
            Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(r, g, b), 1.0f);
            WrappedParticle<?> wrappedParticle = WrappedParticle.create(Particle.DUST, dustOptions);
            packet.getNewParticles().write(0, wrappedParticle);
            packet.getBooleans().write(0, true);
            packet.getDoubles().write(0, x);
            packet.getDoubles().write(1, y);
            packet.getDoubles().write(2, z);
            packet.getFloat().write(0, 0f);
            packet.getFloat().write(1, 0f);
            packet.getFloat().write(2, 0f);
            packet.getFloat().write(3, 0f);
            packet.getIntegers().write(0, 1);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) { }
    }
}
