package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.model.CuboidZone;
import com.turtle.tutiencore.core.task.TuLuyenParticleTask;
import com.turtle.tutiencore.api.TuTien;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TuLuyenManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ZoneManager zoneManager;
    private final TuLuyenParticleTask lineTask;

    private final Map<UUID, ArmorStand> tuLuyenPlayers = new HashMap<>();
    private final Map<UUID, CuboidZone> tuLuyenTargets = new HashMap<>(); // Store the zone for each player
    private BukkitRunnable task;

    public TuLuyenManager(JavaPlugin plugin, ConfigManager config, ZoneManager zoneManager, TuLuyenParticleTask lineTask) {
        this.plugin = plugin;
        this.configManager = config;
        this.zoneManager = zoneManager;
        this.lineTask = lineTask;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    public void startTask() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ArmorStand> entry : tuLuyenPlayers.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    CuboidZone zone = tuLuyenTargets.get(player.getUniqueId());

                    // Check if they left the zone
                    if (zone == null || !zone.contains(player.getLocation())) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            stopTuLuyen(player);
                            player.sendMessage(configManager.getMsgTooFar());
                        });
                        continue;
                    }

                    // Draw Line to center
                    Location center = zone.getCenter();
                    if (center != null) {
                        lineTask.drawLine(player, center);
                    }

                    // Calculate Tu Vi bonus from permissions
                    double basePoints = configManager.getPointsPerInterval();
                    double bonusPercent = getTuViBonus(player);
                    double totalPoints = basePoints * (1.0 + bonusPercent / 100.0);

                    // Give Points via API
                    TuTien.getApi().addTuVi(player.getUniqueId(), totalPoints);
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!configManager.getMsgReceived().isEmpty()) {
                            String msg = configManager.getMsgReceived()
                                    .replace("%points%", String.valueOf((int) totalPoints));
                            if (bonusPercent > 0) {
                                msg += " §a(+" + (int) bonusPercent + "% bonus)";
                            }
                            player.sendMessage(msg);
                        }
                    });
                }
            }
        };
        task.runTaskTimerAsynchronously(plugin, configManager.getTuLuyenInterval(), configManager.getTuLuyenInterval());
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ArmorStand stand : tuLuyenPlayers.values()) {
            stand.remove();
        }
        tuLuyenPlayers.clear();
        tuLuyenTargets.clear();
    }

    public boolean isTuLuyen(Player player) {
        return tuLuyenPlayers.containsKey(player.getUniqueId());
    }

    public void toggleTuLuyen(Player player) {
        if (isTuLuyen(player)) {
            stopTuLuyen(player);
        } else {
            startTuLuyen(player);
        }
    }

    public void startTuLuyen(Player player) {
        CuboidZone zone = zoneManager.getZoneAt(player.getLocation());
        if (zone == null) {
            player.sendMessage(configManager.getMsgTooFar());
            return;
        }

        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getLocation().add(0, 0.1, 0), EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setInvulnerable(true);
        
        // Model Engine Hook
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
            String modelId = configManager.getTuluyenModel();
            if (modelId != null && !modelId.trim().isEmpty()) {
                try {
                    com.ticxo.modelengine.api.model.ActiveModel activeModel = com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(modelId);
                    if (activeModel != null) {
                        com.ticxo.modelengine.api.model.ModeledEntity modeledEntity = com.ticxo.modelengine.api.ModelEngineAPI.createModeledEntity(stand);
                        modeledEntity.addModel(activeModel, true);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load ModelEngine model: " + modelId);
                }
            }
        }
        
        stand.addPassenger(player);

        tuLuyenPlayers.put(player.getUniqueId(), stand);
        tuLuyenTargets.put(player.getUniqueId(), zone);

        if (!configManager.getMsgStarted().isEmpty()) {
            player.sendMessage(configManager.getMsgStarted());
        }
    }

    public void stopTuLuyen(Player player) {
        ArmorStand stand = tuLuyenPlayers.remove(player.getUniqueId());
        tuLuyenTargets.remove(player.getUniqueId());
        if (stand != null) {
            stand.removePassenger(player);
            
            if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
                try {
                    com.ticxo.modelengine.api.model.ModeledEntity modeledEntity = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(stand);
                    if (modeledEntity != null) {
                        modeledEntity.destroy();
                    }
                } catch (Exception e) {}
            }
            
            stand.remove();
        }
        if (!configManager.getMsgStopped().isEmpty()) {
            player.sendMessage(configManager.getMsgStopped());
        }
    }

    public java.util.Collection<Player> getTuLuyenPlayers() {
        java.util.List<Player> players = new java.util.ArrayList<>();
        for (UUID uuid : tuLuyenPlayers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                players.add(p);
            }
        }
        return players;
    }

    @EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isTuLuyen(player)) {
                stopTuLuyen(player);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isTuLuyen(player)) {
                stopTuLuyen(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isTuLuyen(event.getPlayer())) {
            stopTuLuyen(event.getPlayer());
        }
    }

    /**
     * Get the highest Tu Vi bonus percentage from player permissions.
     * Permission format: tutiencore.tuvi.bonus.<percent>
     * 
     * Examples:
     *   tutiencore.tuvi.bonus.20  → +20%
     *   tutiencore.tuvi.bonus.50  → +50%
     *   tutiencore.tuvi.bonus.100 → +100% (double)
     * 
     * If player has multiple bonus perms, the highest value is used.
     * 
     * LuckPerms setup:
     *   /lp group vip permission set tutiencore.tuvi.bonus.20
     *   /lp group svip permission set tutiencore.tuvi.bonus.50
     */
    private double getTuViBonus(Player player) {
        double maxBonus = 0;
        String prefix = "tutiencore.tuvi.bonus.";

        for (org.bukkit.permissions.PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            String name = perm.getPermission();
            if (perm.getValue() && name.startsWith(prefix)) {
                try {
                    double val = Double.parseDouble(name.substring(prefix.length()));
                    if (val > maxBonus) {
                        maxBonus = val;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return maxBonus;
    }
}
