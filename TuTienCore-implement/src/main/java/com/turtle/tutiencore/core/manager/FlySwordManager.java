package com.turtle.tutiencore.core.manager;

import io.lumine.mythic.lib.api.item.NBTItem;
import com.turtle.tutiencore.core.storage.PerPlayerYamlStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FlySwordManager implements Listener {

    private static final Map<String, String> DEFAULT_EQUIPPED_MODELS = Map.of(
            "FLY_SWORD.THANH_PHONG_KIEM", "kiembay",
            "FLY_SWORD.DINH_BA_KIEM", "kiembay_dinhba",
            "FLY_SWORD.HA_CAM_KIEM", "kiembay_hacam",
            "FLY_SWORD.NETHER_KIEM", "kiembay_nether"
    );

    private final JavaPlugin plugin;
    private final Map<UUID, ArmorStand> flyingPlayers = new HashMap<>();
    private final Map<UUID, com.ticxo.modelengine.api.model.ActiveModel> flySwordModels = new HashMap<>();
    private final Map<UUID, String> flySwordAnimations = new HashMap<>();
    private final Map<UUID, String> flySwordModelIds = new HashMap<>();
    private final Map<UUID, Location> lastFlySwordLocations = new HashMap<>();
    private final Map<UUID, Long> flightLockedUntil = new HashMap<>();
    private final Map<UUID, Long> flightLockMessageCooldowns = new HashMap<>();
    // Players whose flight is suspended during a breakthrough (đột phá).
    // Maps to the flight state snapshot to restore afterwards.
    private final Map<UUID, boolean[]> breakthroughFlightSnapshots = new HashMap<>();
    private YamlConfiguration data;
    // Per-player file store (data/fly-swords/<uuid>.yml). `data` is an in-memory aggregate (loaded
    // from every per-player file at startup, keyed under players.<uuid> like the legacy layout);
    // setLevel writes only the changed player's file.
    private PerPlayerYamlStore store;
    private BukkitRunnable followTask;
    private EquipmentMenuManager equipmentMenuManager;

    private boolean enabled;
    private String modelId;
    private String anchor;
    private double yOffset;
    private double scale;
    private boolean followPitch;
    private boolean animationEnabled;
    private String idleAnimation;
    private String movingAnimation;
    private double movingVelocityThreshold;
    private boolean requirePermission;
    private String permission;
    private boolean hideWhileSpectator;
    private boolean evolutionEnabled;
    private boolean autoFlightEnabled;
    private boolean autoFlightRequirePermission;
    private boolean autoFlightDisableOutsideWorld;
    private String autoFlightPermission;
    private boolean equippedSwordEnabled;
    private String equippedSwordSlot;
    private Set<String> autoFlightWorlds = new HashSet<>();
    private boolean combatLockEnabled;
    private long combatLockDurationMillis;
    private boolean combatLockDisableFlying;
    private boolean combatLockDisableAllowFlight;
    private boolean combatLockBlockFlyCommand;
    private boolean combatLockBlockToggleFlight;
    private boolean combatLockEnforceFlightState;
    private long combatLockToggleMessageCooldownMillis;
    private Set<String> combatLockCommandLabels = new HashSet<>();

    public FlySwordManager(JavaPlugin plugin) {
        this(plugin, null);
    }

    public FlySwordManager(JavaPlugin plugin, EquipmentMenuManager equipmentMenuManager) {
        this.plugin = plugin;
        this.equipmentMenuManager = equipmentMenuManager;
        loadData();
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startFollowTask();
    }

    public void setEquipmentMenuManager(EquipmentMenuManager equipmentMenuManager) {
        this.equipmentMenuManager = equipmentMenuManager;
    }

    /**
     * Suspend a player's flight during a breakthrough (đột phá).
     * Snapshots the current flight state, force-disables flight, and prevents
     * auto-flight from re-granting it until {@link #resumeFlightForBreakthrough(Player)}.
     */
    public void suspendFlightForBreakthrough(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (!breakthroughFlightSnapshots.containsKey(uuid)) {
            breakthroughFlightSnapshots.put(uuid, new boolean[]{player.getAllowFlight(), player.isFlying()});
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(false);
        stop(player, false);
    }

    /**
     * Resume a player's flight after a breakthrough ends, restoring the
     * flight state captured by {@link #suspendFlightForBreakthrough(Player)}.
     */
    public void resumeFlightForBreakthrough(Player player) {
        if (player == null) return;
        boolean[] snapshot = breakthroughFlightSnapshots.remove(player.getUniqueId());
        if (!player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // Re-apply auto-flight rules first (worlds/permission/combat-lock).
        applyAutoFlight(player);
        // Then restore the previous flying state if still permitted.
        if (snapshot != null && snapshot[1] && player.getAllowFlight() && !isFlightLocked(player)) {
            player.setFlying(true);
        }
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("fly-sword.enabled", true);
        modelId = plugin.getConfig().getString("fly-sword.model", "kiembay");
        anchor = plugin.getConfig().getString("fly-sword.anchor", "HEAD");
        yOffset = plugin.getConfig().getDouble("fly-sword.y-offset", -0.05);
        scale = plugin.getConfig().getDouble("fly-sword.scale", 1.5);
        followPitch = plugin.getConfig().getBoolean("fly-sword.follow-pitch", false);
        animationEnabled = plugin.getConfig().getBoolean("fly-sword.animation.enabled", true);
        idleAnimation = plugin.getConfig().getString("fly-sword.animation.idle", "idle");
        movingAnimation = plugin.getConfig().getString("fly-sword.animation.moving", "run");
        movingVelocityThreshold = Math.max(0.0D, plugin.getConfig().getDouble("fly-sword.animation.moving-velocity-threshold", 0.015D));
        requirePermission = plugin.getConfig().getBoolean("fly-sword.require-permission", false);
        permission = plugin.getConfig().getString("fly-sword.permission", "tutiencore.flysword");
        hideWhileSpectator = plugin.getConfig().getBoolean("fly-sword.hide-while-spectator", true);
        evolutionEnabled = plugin.getConfig().getBoolean("fly-sword.evolution.enabled", true);
        autoFlightEnabled = plugin.getConfig().getBoolean("fly-sword.auto-flight.enabled", true);
        autoFlightRequirePermission = plugin.getConfig().getBoolean("fly-sword.auto-flight.require-permission", false);
        autoFlightPermission = plugin.getConfig().getString("fly-sword.auto-flight.permission", permission);
        autoFlightDisableOutsideWorld = plugin.getConfig().getBoolean("fly-sword.auto-flight.disable-outside-world", true);
        equippedSwordEnabled = plugin.getConfig().getBoolean("fly-sword.equipped.enabled", true);
        equippedSwordSlot = plugin.getConfig().getString("fly-sword.equipped.slot", "fly_sword");
        autoFlightWorlds = new HashSet<>();
        for (String world : plugin.getConfig().getStringList("fly-sword.auto-flight.worlds")) {
            if (world != null && !world.isBlank()) {
                autoFlightWorlds.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }
        combatLockEnabled = plugin.getConfig().getBoolean("fly-sword.combat-lock.enabled", true);
        combatLockDurationMillis = Math.max(0L, plugin.getConfig().getLong("fly-sword.combat-lock.duration-seconds", 20L)) * 1000L;
        combatLockDisableFlying = plugin.getConfig().getBoolean("fly-sword.combat-lock.disable-flying", true);
        combatLockDisableAllowFlight = plugin.getConfig().getBoolean("fly-sword.combat-lock.disable-allow-flight", true);
        combatLockBlockFlyCommand = plugin.getConfig().getBoolean("fly-sword.combat-lock.block-fly-command", true);
        combatLockBlockToggleFlight = plugin.getConfig().getBoolean("fly-sword.combat-lock.block-toggle-flight", true);
        combatLockEnforceFlightState = plugin.getConfig().getBoolean("fly-sword.combat-lock.enforce-flight-state", true);
        combatLockToggleMessageCooldownMillis = Math.max(0L, plugin.getConfig().getLong("fly-sword.combat-lock.toggle-message-cooldown-ticks", 20L)) * 50L;
        combatLockCommandLabels = new HashSet<>();
        List<String> labels = plugin.getConfig().getStringList("fly-sword.combat-lock.command-labels");
        if (labels.isEmpty()) {
            labels = List.of("fly", "essentials:fly", "cmi:fly");
        }
        for (String label : labels) {
            if (label != null && !label.isBlank()) {
                combatLockCommandLabels.add(label.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!combatLockEnabled) {
            flightLockedUntil.clear();
        }

        if (!enabled) {
            cleanupAll();
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyAutoFlight(player);
            }
            restartOnlineFlyingPlayers();
        }
    }

    public void sendInfo(Player player) {
        int level = getLevel(player.getUniqueId());
        String currentModel = getModelId(player);
        EvolutionTarget target = nextEvolution(level);
        List<String> lines = plugin.getConfig().getStringList("fly-sword.evolution.info");
        if (lines.isEmpty()) {
            lines = List.of(
                    "&6Kiếm Bay &8| &fCấp: &e%level%",
                    "&7Model hiện tại: &f%model%",
                    "&7Model cấp sau: &e%next_model%",
                    "&7Linh thạch: &e%vault_cost%",
                    "&7Cổ thạch: &d%playerpoints_cost%",
                    "&7Nguyên liệu: &f%materials%"
            );
        }
        for (String line : lines) {
            player.sendMessage(color(applyEvolutionPlaceholders(line, level, currentModel, target)));
        }
    }

    public String replaceEvolutionPlaceholders(Player player, String line) {
        int level = getLevel(player.getUniqueId());
        return applyEvolutionPlaceholders(line, level, getModelId(player), nextEvolution(level));
    }

    public boolean evolve(Player player) {
        if (!evolutionEnabled) {
            player.sendMessage(message("disabled", "&cTiến hoá kiếm bay đang tắt."));
            return false;
        }
        if (requirePermission && !player.hasPermission(permission)) {
            player.sendMessage(message("no-permission", "&cBạn không có quyền tiến hoá kiếm bay."));
            return false;
        }

        int level = getLevel(player.getUniqueId());
        EvolutionTarget target = nextEvolution(level);
        if (target == null) {
            player.sendMessage(message("max-level", "&cKiếm bay đã đạt cấp tối đa."));
            return false;
        }

        List<String> failures = missingRequirements(player, target);
        if (!failures.isEmpty()) {
            player.sendMessage(message("not-enough-header", "&cBạn chưa đủ điều kiện tiến hoá kiếm bay:"));
            for (String failure : failures) {
                player.sendMessage(color(plugin.getConfig().getString("fly-sword.evolution.messages.not-enough-line", "&8- &7%reason%")
                        .replace("%reason%", failure)));
            }
            return false;
        }

        if (!withdrawMoney(player, target.vaultCost())) {
            player.sendMessage(message("not-enough-money", "&cKhông thể trừ Linh thạch."));
            return false;
        }
        if (!takePlayerPoints(player, target.playerPointsCost())) {
            depositMoney(player, target.vaultCost());
            player.sendMessage(message("not-enough-playerpoints", "&cKhông thể trừ Cổ thạch."));
            return false;
        }
        if (!takeMaterials(player, target.materials())) {
            depositMoney(player, target.vaultCost());
            givePlayerPoints(player, target.playerPointsCost());
            player.sendMessage(message("material-take-failed", "&cKhông thể trừ nguyên liệu, vui lòng thử lại."));
            return false;
        }

        setLevel(player.getUniqueId(), target.level());
        player.sendMessage(color(message("success", "&aKiếm bay đã tiến hoá lên cấp &e%level%&a. Model: &b%model%")
                .replace("%level%", String.valueOf(target.level()))
                .replace("%model%", target.model())));
        if (player.isFlying()) {
            stop(player, false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.isFlying()) {
                    start(player);
                }
            });
        }
        return true;
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
        flightLockedUntil.clear();
        flightLockMessageCooldowns.clear();
        breakthroughFlightSnapshots.clear();
        cleanupAll();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (combatLockBlockToggleFlight && isFlightLocked(player)) {
            event.setCancelled(true);
            disableFlight(player);
            Bukkit.getScheduler().runTask(plugin, () -> disableFlight(player));
            sendCombatLockMessageThrottled(player, "locked-command", "&cBạn đang bị khóa bay, chờ &e{seconds}s &cđể /fly lại.");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && shouldHideWhileSpectator(player)) {
                stop(player, false);
            } else if (player.isOnline() && player.isFlying()) {
                start(player);
            } else if (!player.isOnline() || !player.getAllowFlight()) {
                stop(player, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0.0D) {
            return;
        }
        lockFlightAfterDamage(player, resolvePlayerDamager(event));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!combatLockBlockFlyCommand || !isFlightLocked(event.getPlayer())) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.length() <= 1) {
            return;
        }

        String label = message.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!combatLockCommandLabels.contains(label) && !combatLockCommandLabels.contains("*")) {
            return;
        }

        event.setCancelled(true);
        disableFlight(event.getPlayer());
        sendCombatLockMessageThrottled(event.getPlayer(), "locked-command", "&cBạn đang bị khóa bay, chờ &e{seconds}s &cđể /fly lại.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAutoFlight(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        stop(player, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyAutoFlight(player);
            if (player.isOnline() && player.isFlying() && !shouldHideWhileSpectator(player)) {
                start(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ArmorStand stand = flyingPlayers.get(player.getUniqueId());
        Location to = event.getTo();
        if (shouldHideWhileSpectator(player)) {
            stop(player, false);
            return;
        }
        if (stand != null && player.isFlying() && !stand.isDead() && to != null) {
            updateSwordPosition(player, stand, to);
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (hideWhileSpectator && event.getNewGameMode() == GameMode.SPECTATOR) {
            stop(player, false);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyAutoFlight(player);
            if (player.isOnline() && player.isFlying() && !shouldHideWhileSpectator(player)) {
                start(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAutoFlight(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        flightLockedUntil.remove(event.getEntity().getUniqueId());
        stop(event.getEntity(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flightLockedUntil.remove(event.getPlayer().getUniqueId());
        stop(event.getPlayer(), false);
    }

    private void start(Player player) {
        if (!enabled || flyingPlayers.containsKey(player.getUniqueId())) return;
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) return;
        String playerModelId = getModelId(player);
        if (playerModelId == null || playerModelId.trim().isEmpty()) return;
        if (requirePermission && !player.hasPermission(permission)) return;
        if (shouldHideWhileSpectator(player)) return;

        try {
            Location loc = swordLocation(player, player.getLocation());
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
                    com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(playerModelId);
            if (activeModel == null) {
                stand.remove();
                plugin.getLogger().warning("ModelEngine fly sword model not found: " + playerModelId);
                return;
            }
            activeModel.setScale(scale);

            com.ticxo.modelengine.api.model.ModeledEntity modeledEntity =
                    com.ticxo.modelengine.api.ModelEngineAPI.createModeledEntity(stand);
            modeledEntity.addModel(activeModel, true);
            flyingPlayers.put(player.getUniqueId(), stand);
            flySwordModels.put(player.getUniqueId(), activeModel);
            flySwordModelIds.put(player.getUniqueId(), playerModelId);
            lastFlySwordLocations.put(player.getUniqueId(), player.getLocation().clone());
            updateSwordAnimation(player, false);
            updateSwordPosition(player, stand, player.getLocation());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn fly sword model '" + playerModelId + "': " + e.getMessage());
        }
    }

    private void stop(Player player, boolean keepFlying) {
        if (player == null) return;
        ArmorStand stand = flyingPlayers.remove(player.getUniqueId());
        flySwordModels.remove(player.getUniqueId());
        flySwordAnimations.remove(player.getUniqueId());
        flySwordModelIds.remove(player.getUniqueId());
        lastFlySwordLocations.remove(player.getUniqueId());
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
                processFlightLocks();
                for (UUID uuid : new java.util.ArrayList<>(flyingPlayers.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    ArmorStand stand = flyingPlayers.get(uuid);
                    if (player == null || !player.isOnline() || !player.isFlying() || shouldHideWhileSpectator(player)
                            || stand == null || stand.isDead()) {
                        if (player != null) stop(player, false);
                        else removeCachedSword(uuid);
                        continue;
                    }
                    String desiredModel = getModelId(player);
                    if (desiredModel == null || desiredModel.isBlank()) {
                        stop(player, false);
                        continue;
                    }
                    if (!desiredModel.equals(flySwordModelIds.get(uuid))) {
                        stop(player, true);
                        if (player.isOnline() && player.isFlying() && !shouldHideWhileSpectator(player)) {
                            start(player);
                        }
                        continue;
                    }
                    updateSwordPosition(player, stand, player.getLocation());
                    updateSwordAnimation(player, isPlayerMoving(player));
                }
            }
        };
        followTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void removeCachedSword(UUID uuid) {
        flyingPlayers.remove(uuid);
        flySwordModels.remove(uuid);
        flySwordAnimations.remove(uuid);
        flySwordModelIds.remove(uuid);
        lastFlySwordLocations.remove(uuid);
    }

    private void updateSwordPosition(Player player, ArmorStand stand, Location baseLocation) {
        if (stand.getVehicle() != null) {
            stand.leaveVehicle();
        }
        Location loc = swordLocation(player, baseLocation);
        stand.teleport(loc);
        stand.setRotation(loc.getYaw(), loc.getPitch());
        stand.setVelocity(player.getVelocity());
    }

    private boolean isPlayerMoving(Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        Location current = player.getLocation();
        Location previous = lastFlySwordLocations.put(uuid, current.clone());
        if (previous == null || previous.getWorld() == null || current.getWorld() == null || !previous.getWorld().equals(current.getWorld())) {
            return false;
        }
        return previous.distanceSquared(current) > movingVelocityThreshold * movingVelocityThreshold;
    }

    private void updateSwordAnimation(Player player, boolean moving) {
        if (!animationEnabled || player == null) {
            return;
        }
        String animation = moving ? movingAnimation : idleAnimation;
        if (animation == null || animation.isBlank()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (animation.equals(flySwordAnimations.get(uuid))) {
            return;
        }
        com.ticxo.modelengine.api.model.ActiveModel model = flySwordModels.get(uuid);
        if (model == null) {
            return;
        }
        try {
            String previous = flySwordAnimations.get(uuid);
            if (previous != null && !previous.equals(animation)) {
                model.getAnimationHandler().forceStopAnimation(previous);
            }
            model.getAnimationHandler().playAnimation(animation, 0.15, 0.15, 1.0, true);
            flySwordAnimations.put(uuid, animation);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to play fly sword animation '" + animation + "': " + ex.getMessage());
        }
    }

    private Location swordLocation(Player player, Location baseLocation) {
        Location anchorLocation = useHeadAnchor()
                ? player.getEyeLocation()
                : baseLocation.clone();
        float yaw = baseLocation.getYaw();
        float pitch = followPitch ? baseLocation.getPitch() : 0.0F;
        Location loc = anchorLocation.clone().add(0, yOffset, 0);
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        return loc;
    }

    private boolean useHeadAnchor() {
        return anchor == null || !anchor.equalsIgnoreCase("FEET");
    }

    private boolean shouldHideWhileSpectator(Player player) {
        return hideWhileSpectator && player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    private void applyAutoFlight(Player player) {
        if (player == null || !player.isOnline() || !autoFlightEnabled) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        // While suspended for a breakthrough, never re-grant flight.
        if (breakthroughFlightSnapshots.containsKey(player.getUniqueId())) {
            disableFlight(player);
            return;
        }
        if (isFlightLocked(player)) {
            disableFlight(player);
            return;
        }
        boolean allowedWorld = isAutoFlightWorld(player);
        boolean allowedPermission = !autoFlightRequirePermission || player.hasPermission(autoFlightPermission);
        if (allowedWorld && allowedPermission) {
            player.setAllowFlight(true);
        } else if (autoFlightDisableOutsideWorld) {
            player.setFlying(false);
            player.setAllowFlight(false);
            stop(player, false);
        }
    }

    private boolean isAutoFlightWorld(Player player) {
        if (player == null || player.getWorld() == null) return false;
        if (autoFlightWorlds.isEmpty()) return true;
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return autoFlightWorlds.contains(world) || autoFlightWorlds.contains("*");
    }

    private void lockFlightAfterDamage(Player player, Player attacker) {
        if (!combatLockEnabled || combatLockDurationMillis <= 0L || player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previousUntil = flightLockedUntil.put(player.getUniqueId(), now + combatLockDurationMillis);
        disableFlight(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> disableFlight(player), 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> disableFlight(player), 5L);
        if (previousUntil == null || previousUntil <= now) {
            if (attacker != null) {
                sendCombatLockMessage(player, "pvp-damaged",
                        "&cBạn vừa bị &e{attacker} &ctấn công, kiếm bay bị tắt trong &e{seconds}s&c.",
                        attacker);
            } else {
                sendCombatLockMessage(player, "damaged", "&cBạn vừa nhận sát thương, không thể bay trong &e{seconds}s&c.");
            }
        }
    }

    private Player resolvePlayerDamager(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent damageByEntity)) {
            return null;
        }

        if (damageByEntity.getDamager() instanceof Player attacker) {
            return attacker.equals(event.getEntity()) ? null : attacker;
        }

        if (damageByEntity.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player attacker) {
                return attacker.equals(event.getEntity()) ? null : attacker;
            }
        }

        return null;
    }

    private boolean isFlightLocked(Player player) {
        if (!combatLockEnabled || player == null) {
            return false;
        }
        Long lockedUntil = flightLockedUntil.get(player.getUniqueId());
        if (lockedUntil == null) {
            return false;
        }
        if (lockedUntil <= System.currentTimeMillis()) {
            flightLockedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void processFlightLocks() {
        if (flightLockedUntil.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(flightLockedUntil.keySet())) {
            Long lockedUntil = flightLockedUntil.get(uuid);
            if (lockedUntil == null) {
                flightLockedUntil.remove(uuid);
                flightLockMessageCooldowns.remove(uuid);
                continue;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (lockedUntil > now) {
                if (combatLockEnforceFlightState && player != null && player.isOnline()) {
                    disableFlight(player);
                }
                continue;
            }

            flightLockedUntil.remove(uuid);
            flightLockMessageCooldowns.remove(uuid);
            if (player != null && player.isOnline()) {
                applyAutoFlight(player);
            }
        }
    }

    private void disableFlight(Player player) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (combatLockDisableFlying && player.isFlying()) {
            player.setFlying(false);
        }
        if (combatLockDisableAllowFlight && player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
        stop(player, false);
    }

    private void sendCombatLockMessage(Player player, String key, String fallback) {
        sendCombatLockMessage(player, key, fallback, null);
    }

    private void sendCombatLockMessageThrottled(Player player, String key, String fallback) {
        if (player == null) {
            return;
        }
        if (combatLockToggleMessageCooldownMillis > 0L) {
            long now = System.currentTimeMillis();
            Long nextAllowed = flightLockMessageCooldowns.get(player.getUniqueId());
            if (nextAllowed != null && nextAllowed > now) {
                return;
            }
            flightLockMessageCooldowns.put(player.getUniqueId(), now + combatLockToggleMessageCooldownMillis);
        }
        sendCombatLockMessage(player, key, fallback);
    }

    private void sendCombatLockMessage(Player player, String key, String fallback, Player attacker) {
        String message = plugin.getConfig().getString("fly-sword.combat-lock.messages." + key, fallback);
        if (message == null || message.isBlank()) {
            return;
        }
        long seconds = remainingFlightLockSeconds(player);
        String attackerName = attacker == null ? "" : attacker.getName();
        String attackerDisplayName = attacker == null ? "" : attacker.getDisplayName();
        player.sendMessage(color(message
                .replace("{seconds}", String.valueOf(seconds))
                .replace("%seconds%", String.valueOf(seconds))
                .replace("{attacker}", attackerName)
                .replace("%attacker%", attackerName)
                .replace("{attacker_display}", attackerDisplayName)
                .replace("%attacker_display%", attackerDisplayName)));
    }

    private long remainingFlightLockSeconds(Player player) {
        Long lockedUntil = player == null ? null : flightLockedUntil.get(player.getUniqueId());
        if (lockedUntil == null) {
            return 0L;
        }
        return Math.max(0L, (long) Math.ceil((lockedUntil - System.currentTimeMillis()) / 1000.0D));
    }

    private void loadData() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        store = new PerPlayerYamlStore(plugin, "fly-swords");
        // In-memory aggregate built from every per-player file. Each file keeps the legacy
        // players.<uuid>.* layout (preserved by the migrator), so getLevel/setLevel paths are unchanged.
        data = new YamlConfiguration();
        File[] files = store.getFolder().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File playerFile : files) {
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                org.bukkit.configuration.ConfigurationSection players = playerConfig.getConfigurationSection("players");
                if (players == null) {
                    continue;
                }
                for (String uuidKey : players.getKeys(false)) {
                    data.set("players." + uuidKey, players.get(uuidKey));
                }
            }
        }
    }

    /**
     * Writes ONE player's {@code players.<uuid>} subtree to {@code data/fly-swords/<uuid>.yml}.
     * Off-thread while enabled; inline on shutdown.
     */
    private void writePlayer(UUID uuid) {
        YamlConfiguration out = new YamlConfiguration();
        Object playerSection = data.get("players." + uuid);
        if (playerSection != null) {
            out.set("players." + uuid, playerSection);
        }
        final String serialized;
        try {
            serialized = out.saveToString();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not serialize fly-swords/" + uuid + ".yml: " + e.getMessage());
            return;
        }
        long seq = store.nextSeq(uuid);
        if (plugin.isEnabled()) {
            store.writeAsync(uuid, serialized, seq);
        } else {
            store.writeSync(uuid, serialized, seq);
        }
    }

    private int getLevel(UUID uuid) {
        return Math.max(1, data.getInt("players." + uuid + ".level", plugin.getConfig().getInt("fly-sword.evolution.default-level", 1)));
    }

    private void setLevel(UUID uuid, int level) {
        data.set("players." + uuid + ".level", level);
        writePlayer(uuid);
    }

    private String getModelId(Player player) {
        int level = getLevel(player.getUniqueId());
        EquipmentMenuManager.EquippedMmoItem equippedItem = getEquippedSword(player);
        String equippedType = equippedItem == null ? null : equippedItem.type();
        String equippedId = equippedItem == null ? null : equippedItem.id();
        return resolveModelId(plugin.getConfig(), modelId, level, equippedType, equippedId);
    }

    private EquipmentMenuManager.EquippedMmoItem getEquippedSword(Player player) {
        if (!equippedSwordEnabled || equipmentMenuManager == null || equippedSwordSlot == null || equippedSwordSlot.isBlank()) {
            return null;
        }
        return equipmentMenuManager.getEquippedMmoItem(player, equippedSwordSlot);
    }

    static String resolveModelId(FileConfiguration config, String defaultModel, int level, String equippedType, String equippedId) {
        String equippedModel = resolveEquippedModelId(config, equippedType, equippedId);
        if (equippedModel != null && !equippedModel.isBlank()) {
            return equippedModel;
        }

        String levelModel = config.getString("fly-sword.evolution.levels." + level + ".model", defaultModel);
        return levelModel == null || levelModel.isBlank() ? defaultModel : levelModel;
    }

    private static String resolveEquippedModelId(FileConfiguration config, String equippedType, String equippedId) {
        if (config == null || !config.getBoolean("fly-sword.equipped.enabled", true)) {
            return null;
        }
        String type = normalizeKey(equippedType);
        String id = normalizeKey(equippedId);
        if (type.isBlank() || id.isBlank()) {
            return null;
        }

        String path = "fly-sword.equipped.models." + type + "." + id;
        String model = config.getString(path + ".model");
        if (model == null || model.isBlank()) {
            model = config.getString(path);
        }
        if (model == null || model.isBlank()) {
            model = DEFAULT_EQUIPPED_MODELS.get(type + "." + id);
        }
        return model == null || model.isBlank() ? null : model;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private EvolutionTarget nextEvolution(int currentLevel) {
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("fly-sword.evolution.levels");
        if (levels == null) return null;
        int nextLevel = Integer.MAX_VALUE;
        for (String key : levels.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                if (level > currentLevel && level < nextLevel) {
                    nextLevel = level;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (nextLevel == Integer.MAX_VALUE) return null;

        String path = "fly-sword.evolution.levels." + nextLevel;
        return new EvolutionTarget(
                nextLevel,
                plugin.getConfig().getString(path + ".model", modelId),
                plugin.getConfig().getDouble(path + ".vault-cost", 0.0),
                plugin.getConfig().getInt(path + ".playerpoints-cost", 0),
                readMaterials(path + ".materials")
        );
    }

    private List<MaterialCost> readMaterials(String path) {
        List<MaterialCost> materials = new ArrayList<>();
        for (Map<?, ?> map : plugin.getConfig().getMapList(path)) {
            Object rawType = map.get("type");
            Object rawId = map.get("id");
            String type = String.valueOf(rawType == null ? "" : rawType).trim();
            String id = String.valueOf(rawId == null ? "" : rawId).trim();
            int amount = parseInt(map.get("amount"), 1);
            if (!type.isBlank() && !id.isBlank() && amount > 0) {
                materials.add(new MaterialCost(normalize(type), normalize(id), amount));
            }
        }
        return materials;
    }

    private List<String> missingRequirements(Player player, EvolutionTarget target) {
        List<String> failures = new ArrayList<>();
        if (target.vaultCost() > 0 && !hasMoney(player, target.vaultCost())) {
            failures.add(plugin.getConfig().getString("fly-sword.evolution.messages.missing-money", "Thiếu %amount% Linh thạch")
                    .replace("%amount%", formatNumber(target.vaultCost())));
        }
        if (target.playerPointsCost() > 0 && getPlayerPoints(player) < target.playerPointsCost()) {
            failures.add(plugin.getConfig().getString("fly-sword.evolution.messages.missing-playerpoints", "Thiếu %amount% Cổ thạch")
                    .replace("%amount%", String.valueOf(target.playerPointsCost())));
        }
        for (MaterialCost material : target.materials()) {
            int count = countMaterial(player, material);
            if (count < material.amount()) {
                failures.add(plugin.getConfig().getString("fly-sword.evolution.messages.missing-material", "Thiếu %amount%x %type%:%id%")
                        .replace("%amount%", String.valueOf(material.amount() - count))
                        .replace("%type%", material.type())
                        .replace("%id%", material.id()));
            }
        }
        return failures;
    }

    private boolean takeMaterials(Player player, List<MaterialCost> materials) {
        for (MaterialCost material : materials) {
            if (countMaterial(player, material) < material.amount()) {
                return false;
            }
        }
        for (MaterialCost material : materials) {
            int remaining = material.amount();
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (!matchesMaterial(item, material)) continue;
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
            player.getInventory().setContents(contents);
        }
        return true;
    }

    private int countMaterial(Player player, MaterialCost material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (matchesMaterial(item, material)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean matchesMaterial(ItemStack item, MaterialCost material) {
        if (item == null || item.getType() == Material.AIR) return false;
        try {
            NBTItem nbt = NBTItem.get(item);
            String type = firstNbt(nbt, "MMOITEMS_ITEM_TYPE", "MMOITEMS_TYPE", "type");
            String id = firstNbt(nbt, "MMOITEMS_ITEM_ID", "MMOITEMS_ID", "id");
            return material.type().equals(normalize(type)) && material.id().equals(normalize(id));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String firstNbt(NBTItem nbt, String... keys) {
        for (String key : keys) {
            String value = nbt.getString(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private boolean hasMoney(Player player, double amount) {
        Object economy = vaultEconomy();
        if (economy == null) return amount <= 0;
        try {
            Object result = economy.getClass().getMethod("has", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean withdrawMoney(Player player, double amount) {
        if (amount <= 0) return true;
        Object economy = vaultEconomy();
        if (economy == null) return false;
        try {
            Object response = economy.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            Object success = response.getClass().getMethod("transactionSuccess").invoke(response);
            return success instanceof Boolean value && value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void depositMoney(Player player, double amount) {
        if (amount <= 0) return;
        Object economy = vaultEconomy();
        if (economy == null) return;
        try {
            economy.getClass().getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Object vaultEconomy() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null) return null;
            return registration.getClass().getMethod("getProvider").invoke(registration);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private int getPlayerPoints(Player player) {
        Object api = playerPointsApi();
        if (api == null) return 0;
        try {
            Object result = api.getClass().getMethod("look", UUID.class).invoke(api, player.getUniqueId());
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private boolean takePlayerPoints(Player player, int amount) {
        if (amount <= 0) return true;
        Object api = playerPointsApi();
        if (api == null) return false;
        try {
            Object result = api.getClass().getMethod("take", UUID.class, int.class).invoke(api, player.getUniqueId(), amount);
            return !(result instanceof Boolean value) || value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void givePlayerPoints(Player player, int amount) {
        if (amount <= 0) return;
        Object api = playerPointsApi();
        if (api == null) return;
        try {
            api.getClass().getMethod("give", UUID.class, int.class).invoke(api, player.getUniqueId(), amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Object playerPointsApi() {
        org.bukkit.plugin.Plugin playerPoints = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (playerPoints == null) return null;
        try {
            return playerPoints.getClass().getMethod("getAPI").invoke(playerPoints);
        } catch (ReflectiveOperationException ignored) {
            try {
                Class<?> clazz = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
                Object instance = clazz.getMethod("getInstance").invoke(null);
                return instance.getClass().getMethod("getAPI").invoke(instance);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private String applyEvolutionPlaceholders(String line, int level, String currentModel, EvolutionTarget target) {
        String nextModel = target == null ? plugin.getConfig().getString("fly-sword.evolution.format.max-level", "Đã tối đa") : target.model();
        String vaultCost = target == null ? "0" : formatNumber(target.vaultCost());
        String pointsCost = target == null ? "0" : String.valueOf(target.playerPointsCost());
        String materials = target == null ? plugin.getConfig().getString("fly-sword.evolution.format.no-materials", "Không cần") : formatMaterials(target.materials());
        return line
                .replace("%level%", String.valueOf(level))
                .replace("%model%", currentModel == null ? "" : currentModel)
                .replace("%next_model%", nextModel == null ? "" : nextModel)
                .replace("%vault_cost%", vaultCost)
                .replace("%playerpoints_cost%", pointsCost)
                .replace("%materials%", materials);
    }

    private String formatMaterials(List<MaterialCost> materials) {
        if (materials.isEmpty()) {
            return plugin.getConfig().getString("fly-sword.evolution.format.no-materials", "Không cần");
        }
        List<String> parts = new ArrayList<>();
        for (MaterialCost material : materials) {
            parts.add(material.amount() + "x " + material.type() + ":" + material.id());
        }
        return String.join(", ", parts);
    }

    private String message(String key, String fallback) {
        return color(plugin.getConfig().getString("fly-sword.evolution.messages." + key, fallback));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void restartOnlineFlyingPlayers() {
        for (UUID uuid : new ArrayList<>(flyingPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || !player.isFlying()) {
                if (player != null) stop(player, false);
                continue;
            }
            stop(player, false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.isFlying() && !shouldHideWhileSpectator(player)) {
                    start(player);
                }
            });
        }
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

    private record MaterialCost(String type, String id, int amount) {
    }

    private record EvolutionTarget(int level, String model, double vaultCost, int playerPointsCost, List<MaterialCost> materials) {
    }
}
