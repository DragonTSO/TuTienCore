package com.turtle.tutiencore.core.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlySwordManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, ArmorStand> flyingPlayers = new HashMap<>();
    private BukkitRunnable followTask;

    private boolean enabled;
    private String modelId;
    private double yOffset;
    private double scale;
    private boolean requirePermission;
    private String permission;

    public FlySwordManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startFollowTask();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("fly-sword.enabled", true);
        modelId = plugin.getConfig().getString("fly-sword.model", "kiembay");
        yOffset = plugin.getConfig().getDouble("fly-sword.y-offset", -0.05);
        scale = plugin.getConfig().getDouble("fly-sword.scale", 1.5);
        requirePermission = plugin.getConfig().getBoolean("fly-sword.require-permission", false);
        permission = plugin.getConfig().getString("fly-sword.permission", "tutiencore.flysword");

        if (!enabled) {
            cleanupAll();
        }
    }

    public void cleanupAll() {
        for (UUID uuid : new java.util.ArrayList<>(flyingPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            stop(player, false);
        }
        flyingPlayers.clear();
    }

    public void stopTask() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        cleanupAll();
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isFlying()) {
                start(player);
            } else if (!player.isOnline() || !player.getAllowFlight()) {
                stop(player, true);
            }
        });
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        stop(player, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isFlying()) {
                start(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ArmorStand stand = flyingPlayers.get(player.getUniqueId());
        Location to = event.getTo();
        if (stand != null && player.isFlying() && !stand.isDead() && to != null) {
            teleportSword(player, stand, to);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stop(event.getEntity(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }

    private void start(Player player) {
        if (!enabled || flyingPlayers.containsKey(player.getUniqueId())) return;
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) return;
        if (modelId == null || modelId.trim().isEmpty()) return;
        if (requirePermission && !player.hasPermission(permission)) return;

        try {
            Location loc = player.getLocation().add(0, yOffset, 0);
            ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            lockEquipment(stand);
            setDurationIfPresent(stand, "setInterpolationDuration", 0);
            setDurationIfPresent(stand, "setTeleportDuration", 0);

            com.ticxo.modelengine.api.model.ActiveModel activeModel =
                    com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(modelId);
            if (activeModel == null) {
                stand.remove();
                plugin.getLogger().warning("ModelEngine fly sword model not found: " + modelId);
                return;
            }
            activeModel.setScale(scale);

            com.ticxo.modelengine.api.model.ModeledEntity modeledEntity =
                    com.ticxo.modelengine.api.ModelEngineAPI.createModeledEntity(stand);
            modeledEntity.addModel(activeModel, true);
            flyingPlayers.put(player.getUniqueId(), stand);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn fly sword model '" + modelId + "': " + e.getMessage());
        }
    }

    private void stop(Player player, boolean keepFlying) {
        if (player == null) return;
        ArmorStand stand = flyingPlayers.remove(player.getUniqueId());
        if (stand == null) return;

        try {
            com.ticxo.modelengine.api.model.ModeledEntity modeledEntity =
                    com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(stand);
            if (modeledEntity != null) {
                modeledEntity.destroy();
            }
        } catch (Exception ignored) {}

        stand.remove();
        if (keepFlying && player.isOnline() && player.getAllowFlight()) {
            player.setFlying(true);
        }
    }

    private void startFollowTask() {
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new java.util.ArrayList<>(flyingPlayers.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    ArmorStand stand = flyingPlayers.get(uuid);
                    if (player == null || !player.isOnline() || !player.isFlying() || stand == null || stand.isDead()) {
                        if (player != null) stop(player, false);
                        else flyingPlayers.remove(uuid);
                        continue;
                    }
                    teleportSword(player, stand, player.getLocation());
                }
            }
        };
        followTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void teleportSword(Player player, ArmorStand stand, Location baseLocation) {
        Location loc = baseLocation.clone().add(0, yOffset, 0);
        loc.setPitch(0.0F);
        stand.teleport(loc);
        stand.setRotation(loc.getYaw(), 0.0F);
        stand.setVelocity(player.getVelocity());
    }

    private void setDurationIfPresent(ArmorStand stand, String methodName, int duration) {
        try {
            Method method = stand.getClass().getMethod(methodName, int.class);
            method.invoke(stand, duration);
        } catch (ReflectiveOperationException ignored) {
            // ArmorStand does not expose Display interpolation settings on most Bukkit APIs.
        }
    }

    private void lockEquipment(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING);
        }
    }
}
