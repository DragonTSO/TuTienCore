package com.turtle.tutiencore.core.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class DeathTipManager implements Listener {

    private static final String CONFIG_PATH = "death-tips";
    private static final String VIEW_TAG = "tutiencore_death_tip_view";
    private static final AtomicInteger FAKE_ENTITY_IDS = new AtomicInteger(2_000_000_000);

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, PendingDeathTip> pending = new HashMap<>();
    private final Map<UUID, ActiveDeathTip> active = new HashMap<>();
    private final Map<UUID, BukkitTask> cinematicTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> cinematicTextTasks = new HashMap<>();
    private final Map<UUID, ClientsideCinematicText> cinematicTextDisplays = new HashMap<>();
    private final Set<UUID> internalGamemodeChanges = new HashSet<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private boolean mobOnly;
    private boolean debug;
    private boolean requireImmediateRespawn;
    private long respawnDelayTicks;
    private long fallbackDelayTicks;
    private long spectatorReapplyDelayTicks;
    private long spectatorReapplyIntervalTicks;
    private boolean teleportToAnchorBeforeSpectate;
    private double teleportToAnchorDistanceSquared;
    private boolean forceSpectatorEnabled;
    private boolean forceSpectatorCancelGamemodeChange;
    private boolean forceSpectatorCancelTeleport;
    private boolean forceSpectatorUseAnchorTarget;
    private boolean forceSpectatorLockPosition;
    private boolean cinematicEnabled;
    private CinematicMode cinematicMode;
    private double cinematicRadius;
    private double cinematicHeight;
    private double cinematicLookAtYOffset;
    private boolean cinematicRotateAround;
    private double cinematicStartAngleDegrees;
    private double cinematicDegreesPerSecond;
    private float cinematicStartPitch;
    private double cinematicPitchStepPerTick;
    private double cinematicPitchStepSeconds;
    private double cinematicHeadJerkPitch;
    private boolean cinematicPitchUseRealtime;
    private long cinematicStepTicks;
    private int cinematicTeleportDuration;
    private int cinematicInterpolationDuration;
    private List<Long> cinematicTargetRetryTicks;
    private boolean deathHeadEnabled;
    private double deathHeadYOffset;
    private float deathHeadScale;
    private int deathHeadLight;
    private String deathHeadBillboard;
    private String deathHeadTransform;
    private long viewDurationTicks;
    private boolean restoreGamemode;
    private boolean restoreToRespawnLocation;
    private double anchorYOffset;
    private boolean titleEnabled;
    private String titleText;
    private String subtitleText;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;
    private boolean soundEnabled;
    private String soundName;
    private SoundCategory soundCategory;
    private float soundVolume;
    private float soundPitch;
    private boolean messageEnabled;
    private List<String> messageLines;
    private List<String> tips;
    private boolean cinematicTextEnabled;
    private CinematicTextBedrockMode cinematicTextBedrockMode;
    private String cinematicTextTitle;
    private String cinematicTextSubtitle;
    private boolean cinematicTextWaitForPitch;
    private long cinematicTextFadeDelayTicks;
    private long cinematicTextSubtitleIntroTicks;
    private long cinematicTextDurationTicks;
    private long cinematicTextUpdateIntervalTicks;
    private boolean cinematicTextFollowPlayerCamera;
    private double cinematicTextDistance;
    private double cinematicTextYOffset;
    private double cinematicTextRiseDistance;
    private double cinematicTextSubtitleStartYOffset;
    private double cinematicTextSubtitleEndYOffset;
    private float cinematicTextStartScale;
    private float cinematicTextSubtitleScale;
    private int cinematicTextStartOpacity;
    private int cinematicTextSubtitleStartOpacity;
    private int cinematicTextEndOpacity;
    private int cinematicTextTeleportDuration;
    private int cinematicTextInterpolationDuration;
    private int cinematicTextLineWidth;
    private float cinematicTextViewRange;
    private boolean cinematicTextShadow;
    private boolean cinematicTextSeeThrough;
    private int cinematicTextBackgroundAlpha;

    public DeathTipManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        loadConfigFile();

        enabled = config.getBoolean(CONFIG_PATH + ".enabled", true);
        mobOnly = config.getBoolean(CONFIG_PATH + ".mob-only", false);
        debug = config.getBoolean(CONFIG_PATH + ".debug", false);
        requireImmediateRespawn = config.getBoolean(CONFIG_PATH + ".require-immediate-respawn", true);
        respawnDelayTicks = Math.max(0L, config.getLong(CONFIG_PATH + ".respawn-delay-ticks", 2L));
        fallbackDelayTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".fallback-delay-ticks", 6L));
        spectatorReapplyDelayTicks = Math.max(-1L, config.getLong(CONFIG_PATH + ".spectator-reapply-delay-ticks", 2L));
        spectatorReapplyIntervalTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".spectator-reapply-interval-ticks", 2L));
        teleportToAnchorBeforeSpectate = config.getBoolean(CONFIG_PATH + ".teleport-to-anchor-before-spectate", true);
        double teleportDistance = Math.max(0.0, config.getDouble(CONFIG_PATH + ".teleport-to-anchor-distance", 8.0));
        teleportToAnchorDistanceSquared = teleportDistance * teleportDistance;
        forceSpectatorEnabled = config.getBoolean(CONFIG_PATH + ".force-spectator.enabled", true);
        forceSpectatorCancelGamemodeChange = config.getBoolean(CONFIG_PATH + ".force-spectator.cancel-gamemode-change", true);
        forceSpectatorCancelTeleport = config.getBoolean(CONFIG_PATH + ".force-spectator.cancel-teleport", true);
        forceSpectatorUseAnchorTarget = config.getBoolean(CONFIG_PATH + ".force-spectator.use-anchor-target", false);
        forceSpectatorLockPosition = config.getBoolean(CONFIG_PATH + ".force-spectator.lock-position", true);
        cinematicEnabled = config.getBoolean(CONFIG_PATH + ".cinematic.enabled", false);
        cinematicMode = parseCinematicMode(config.getString(CONFIG_PATH + ".cinematic.mode", "DISPLAY"));
        cinematicRadius = Math.max(0.5, config.getDouble(CONFIG_PATH + ".cinematic.radius", 4.5));
        cinematicHeight = config.getDouble(CONFIG_PATH + ".cinematic.height", 2.0);
        cinematicLookAtYOffset = config.getDouble(CONFIG_PATH + ".cinematic.look-at-y-offset", 1.0);
        cinematicRotateAround = config.getBoolean(CONFIG_PATH + ".cinematic.rotate-around", false);
        cinematicStartAngleDegrees = config.getDouble(CONFIG_PATH + ".cinematic.start-angle-degrees", 180.0);
        cinematicDegreesPerSecond = config.getDouble(CONFIG_PATH + ".cinematic.degrees-per-second", 55.0);
        cinematicStartPitch = (float) config.getDouble(CONFIG_PATH + ".cinematic.start-pitch", 100.0);
        cinematicPitchStepPerTick = Math.max(0.0, config.getDouble(CONFIG_PATH + ".cinematic.pitch-step-per-tick", 0.1));
        cinematicPitchStepSeconds = Math.max(0.001D, config.getDouble(CONFIG_PATH + ".cinematic.pitch-step-seconds", 0.01D));
        cinematicHeadJerkPitch = Math.max(0.0D, config.getDouble(CONFIG_PATH + ".cinematic.head-jerk-pitch", 0.0D));
        cinematicPitchUseRealtime = config.getBoolean(CONFIG_PATH + ".cinematic.pitch-use-realtime", true);
        cinematicStepTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".cinematic.step-ticks", 2L));
        cinematicTeleportDuration = Math.max(0, Math.min(59, config.getInt(CONFIG_PATH + ".cinematic.teleport-duration", 2)));
        cinematicInterpolationDuration = Math.max(0, config.getInt(CONFIG_PATH + ".cinematic.interpolation-duration", 2));
        cinematicTargetRetryTicks = config.getLongList(CONFIG_PATH + ".cinematic.target-retry-ticks");
        if (cinematicTargetRetryTicks.isEmpty()) {
            cinematicTargetRetryTicks = List.of(1L, 3L, 6L, 10L);
        }
        deathHeadEnabled = config.getBoolean(CONFIG_PATH + ".death-head.enabled", true);
        deathHeadYOffset = config.getDouble(CONFIG_PATH + ".death-head.y-offset", 0.25);
        deathHeadScale = (float) Math.max(0.1, config.getDouble(CONFIG_PATH + ".death-head.scale", 0.85));
        deathHeadLight = Math.max(0, Math.min(15, config.getInt(CONFIG_PATH + ".death-head.light", 15)));
        deathHeadBillboard = config.getString(CONFIG_PATH + ".death-head.billboard", "FIXED");
        deathHeadTransform = config.getString(CONFIG_PATH + ".death-head.transform", "HEAD");
        viewDurationTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".view-duration-ticks", 80L));
        restoreGamemode = config.getBoolean(CONFIG_PATH + ".restore-gamemode", true);
        restoreToRespawnLocation = config.getBoolean(CONFIG_PATH + ".restore-to-respawn-location", true);

        cinematicTextEnabled = config.getBoolean(CONFIG_PATH + ".cinematic-text.enabled", true);
        cinematicTextBedrockMode = parseCinematicTextBedrockMode(config.getString(CONFIG_PATH + ".cinematic-text.bedrock-mode", "TITLE"));
        cinematicTextTitle = config.getString(CONFIG_PATH + ".cinematic-text.title", "&c&lTrang bị chưa đủ mạnh");
        cinematicTextSubtitle = config.getString(CONFIG_PATH + ".cinematic-text.subtitle", "&7%tip%");
        cinematicTextWaitForPitch = config.getBoolean(CONFIG_PATH + ".cinematic-text.wait-for-pitch", true);
        cinematicTextFadeDelayTicks = Math.max(0L, config.getLong(CONFIG_PATH + ".cinematic-text.fade-delay-ticks", 10L));
        cinematicTextSubtitleIntroTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".cinematic-text.subtitle-intro-ticks", 16L));
        cinematicTextDurationTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".cinematic-text.duration-ticks", viewDurationTicks));
        cinematicTextUpdateIntervalTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".cinematic-text.update-interval-ticks", 1L));
        cinematicTextFollowPlayerCamera = config.getBoolean(CONFIG_PATH + ".cinematic-text.follow-player-camera", true);
        cinematicTextDistance = Math.max(0.1, config.getDouble(CONFIG_PATH + ".cinematic-text.distance", 2.4));
        cinematicTextYOffset = config.getDouble(CONFIG_PATH + ".cinematic-text.y-offset", -0.05);
        cinematicTextRiseDistance = config.getDouble(CONFIG_PATH + ".cinematic-text.rise-distance", 0.55);
        cinematicTextSubtitleStartYOffset = config.getDouble(CONFIG_PATH + ".cinematic-text.subtitle-start-y-offset", -0.55D);
        cinematicTextSubtitleEndYOffset = config.getDouble(CONFIG_PATH + ".cinematic-text.subtitle-end-y-offset", -0.24D);
        cinematicTextStartScale = (float) Math.max(0.05, config.getDouble(CONFIG_PATH + ".cinematic-text.start-scale", 0.9));
        cinematicTextSubtitleScale = (float) Math.max(0.05, config.getDouble(CONFIG_PATH + ".cinematic-text.subtitle-scale",
                config.getDouble(CONFIG_PATH + ".cinematic-text.subtitle-start-scale", cinematicTextStartScale)));
        cinematicTextStartOpacity = clamp(config.getInt(CONFIG_PATH + ".cinematic-text.start-opacity", 230), 0, 255);
        cinematicTextSubtitleStartOpacity = clamp(config.getInt(CONFIG_PATH + ".cinematic-text.subtitle-start-opacity", 0), 0, 255);
        cinematicTextEndOpacity = clamp(config.getInt(CONFIG_PATH + ".cinematic-text.end-opacity", 0), 0, 255);
        cinematicTextTeleportDuration = clamp(config.getInt(CONFIG_PATH + ".cinematic-text.teleport-duration", 1), 0, 59);
        cinematicTextInterpolationDuration = Math.max(0, config.getInt(CONFIG_PATH + ".cinematic-text.interpolation-duration", 1));
        cinematicTextLineWidth = Math.max(1, config.getInt(CONFIG_PATH + ".cinematic-text.line-width", 260));
        cinematicTextViewRange = (float) Math.max(0.1, config.getDouble(CONFIG_PATH + ".cinematic-text.view-range", 8.0));
        cinematicTextShadow = config.getBoolean(CONFIG_PATH + ".cinematic-text.shadow", true);
        cinematicTextSeeThrough = config.getBoolean(CONFIG_PATH + ".cinematic-text.see-through", true);
        cinematicTextBackgroundAlpha = clamp(config.getInt(CONFIG_PATH + ".cinematic-text.background-alpha", 0), 0, 255);

        anchorYOffset = config.getDouble(CONFIG_PATH + ".anchor.y-offset", 0.0);

        titleEnabled = config.getBoolean(CONFIG_PATH + ".title.enabled", true);
        titleText = config.getString(CONFIG_PATH + ".title.title", "&c&lTrang Bị Yếu");
        subtitleText = config.getString(CONFIG_PATH + ".title.subtitle", "&7%tip%");
        titleFadeIn = Math.max(0, config.getInt(CONFIG_PATH + ".title.fade-in", 5));
        titleStay = Math.max(1, config.getInt(CONFIG_PATH + ".title.stay", 70));
        titleFadeOut = Math.max(0, config.getInt(CONFIG_PATH + ".title.fade-out", 15));

        soundEnabled = config.getBoolean(CONFIG_PATH + ".sound.enabled", true);
        soundName = config.getString(CONFIG_PATH + ".sound.name", "ENTITY_WITHER_SPAWN");
        soundCategory = parseSoundCategory(config.getString(CONFIG_PATH + ".sound.category", "MASTER"));
        soundVolume = (float) Math.max(0.0, config.getDouble(CONFIG_PATH + ".sound.volume", 0.55));
        soundPitch = (float) Math.max(0.0, Math.min(2.0, config.getDouble(CONFIG_PATH + ".sound.pitch", 0.75)));

        messageEnabled = config.getBoolean(CONFIG_PATH + ".message.enabled", false);
        messageLines = config.getStringList(CONFIG_PATH + ".message.lines");
        tips = config.getStringList(CONFIG_PATH + ".tips");
        if (tips.isEmpty()) {
            tips = List.of("&7Trang bị của bạn còn yếu, hãy luyện khí hoặc nâng phẩm trước khi quay lại.");
        }
    }

    private void loadConfigFile() {
        configFile = new File(plugin.getDataFolder(), "region-respawn.yml");
        if (!configFile.exists()) {
            plugin.saveResource("region-respawn.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void stop() {
        pending.clear();
        for (UUID uuid : List.copyOf(active.keySet())) {
            cleanup(uuid, false);
        }
        active.clear();
        for (UUID uuid : List.copyOf(cinematicTextDisplays.keySet())) {
            clearCinematicText(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getEntity();
        LivingEntity mob = getMobKiller(player);
        if (mob == null && mobOnly) {
            debug(player, "Skipped: death was not caused by a mob.");
            return;
        }

        World world = player.getWorld();
        if (requireImmediateRespawn && !Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_IMMEDIATE_RESPAWN))) {
            debug(player, "Skipped: gamerule doImmediateRespawn is not true in world " + world.getName() + ".");
            return;
        }

        Location deathLocation = player.getLocation().clone();
        deathLocation.setY(deathLocation.getY() + anchorYOffset);
        String sourceName = mob == null ? getDeathSourceName(player) : mob.getName();
        pending.put(player.getUniqueId(), new PendingDeathTip(
                deathLocation,
                player.getGameMode(),
                sourceName,
                randomTip()
        ));
        debug(player, "Queued death tip at " + formatLocation(deathLocation) + " source=" + sourceName + ".");
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> startFallbackDeathView(player.getUniqueId()),
                fallbackDelayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        PendingDeathTip tip = pending.remove(event.getPlayer().getUniqueId());
        if (tip == null) {
            debug(event.getPlayer(), "Respawn ignored: no pending death tip.");
            return;
        }

        Player player = event.getPlayer();
        Location respawnLocation = event.getRespawnLocation().clone();
        debug(player, "Respawn event matched pending death tip. Starting view after " + respawnDelayTicks + " tick(s).");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> startDeathView(player, tip, respawnLocation), respawnDelayTicks);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        cleanup(uuid, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (!forceSpectatorEnabled || !forceSpectatorCancelGamemodeChange) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (internalGamemodeChanges.contains(uuid) || !active.containsKey(uuid)) {
            return;
        }

        if (event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            debug(player, "Cancelled gamemode change to " + event.getNewGameMode() + " while death tip spectator is locked.");
            scheduleSpectatorForce(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!forceSpectatorEnabled || !forceSpectatorCancelTeleport) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ActiveDeathTip tip = active.get(uuid);
        if (tip == null || internalTeleports.contains(uuid)) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && isAnchorSpectateTeleport(tip, event.getTo())) {
            debug(player, "Allowed spectator teleport to death tip anchor.");
            return;
        }

        event.setCancelled(true);
        Location to = event.getTo();
        debug(player, "Cancelled teleport while death tip spectator is locked. Cause="
                + event.getCause()
                + ", to=" + (to == null ? "unknown" : formatLocation(to))
                + ".");
        scheduleSpectatorForce(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!forceSpectatorEnabled || !forceSpectatorLockPosition) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ActiveDeathTip tip = active.get(uuid);
        if (tip == null || internalTeleports.contains(uuid)) {
            return;
        }

        Location to = event.getTo();
        if (to == null || isSameCameraPosition(to, tip.cameraLocation())) {
            return;
        }

        event.setTo(tip.cameraLocation());
        scheduleSpectatorForce(uuid);
    }

    private void startFallbackDeathView(UUID uuid) {
        PendingDeathTip tip = pending.get(uuid);
        if (tip == null) {
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            pending.remove(uuid);
            return;
        }

        if (player.isDead()) {
            debug(player, "Fallback waiting: player is still dead, keeping pending tip for respawn event.");
            return;
        }

        pending.remove(uuid);
        debug(player, "Fallback started death tip view because respawn event did not consume pending tip.");
        startDeathView(player, tip, player.getLocation().clone());
    }

    private void startDeathView(Player player, PendingDeathTip tip, Location respawnLocation) {
        if (!player.isOnline()) {
            return;
        }

        cleanup(player.getUniqueId(), false);

        Location focusLocation = createFocusLocation(tip.deathLocation());
        Location cameraLocation = cinematicEnabled
                ? computeCinematicCameraLocation(focusLocation, tip.deathLocation().getYaw(), 0.0D, 0.0D)
                : tip.deathLocation().clone();
        Entity anchor = createCameraAnchor(cameraLocation);
        if (shouldUseCameraAnchor() && anchor == null) {
            debug(player, "Could not spawn anchor, showing title/sound only.");
            showTip(player, tip);
            return;
        }
        if (anchor != null) {
            debug(player, "Spawned death tip anchor " + anchor.getType() + " " + anchor.getUniqueId()
                    + " at " + formatLocation(anchor.getLocation()) + ".");
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> debugAnchorState(player.getUniqueId(), anchor), 1L);
        } else {
            debug(player, "Using fixed spectator camera at " + formatLocation(cameraLocation) + ".");
        }
        Entity deathHead = deathHeadEnabled ? spawnDeathHead(player, tip.deathLocation()) : null;
        if (deathHead != null) {
            debug(player, "Spawned death head " + deathHead.getUniqueId()
                    + " at " + formatLocation(deathHead.getLocation()) + ".");
        }

        GameMode restoreMode = tip.previousGameMode();
        try {
            applySpectatorView(player, anchor, cameraLocation);
            if (anchor != null) {
                debug(player, "Spectator target set to death tip anchor " + anchor.getUniqueId() + ".");
            } else {
                debug(player, "Fixed spectator camera applied.");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not start death tip spectator view for " + player.getName() + ": " + exception.getMessage());
            if (anchor != null) {
                anchor.remove();
            }
            if (deathHead != null) {
                deathHead.remove();
            }
            showTip(player, tip);
            return;
        }

        showTip(player, tip);
        long pitchMotionTicks = cinematicEnabled ? computePitchMotionTicks(computeCinematicTargetPitch(focusLocation, tip.deathLocation().getYaw())) : 0L;
        long textStartDelayTicks = cinematicTextEnabled && cinematicTextWaitForPitch ? pitchMotionTicks : 0L;
        long totalViewTicks = Math.max(viewDurationTicks,
                textStartDelayTicks + (cinematicTextEnabled ? cinematicTextTotalTicks() : 0L));
        BukkitTask restoreTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> cleanup(player.getUniqueId(), true),
                totalViewTicks);
        active.put(player.getUniqueId(), new ActiveDeathTip(
                anchor,
                restoreMode,
                respawnLocation,
                restoreTask,
                cameraLocation,
                focusLocation,
                tip.deathLocation().getYaw(),
                tip.deathLocation().getPitch(),
                deathHead,
                textStartDelayTicks,
                totalViewTicks
        ));
        startCinematicText(player, tip);
        if (cinematicEnabled) {
            startCinematicCamera(player.getUniqueId());
        }
        if (anchor != null) {
            startSpectatorTargetWarmup(player.getUniqueId());
        }

        if (spectatorReapplyDelayTicks >= 0L) {
            startSpectatorLock(player.getUniqueId());
        }
    }

    private void applySpectatorView(Player player, Entity target, Location cameraLocation) {
        if (player.getGameMode() != GameMode.SPECTATOR) {
            setGameModeInternally(player, GameMode.SPECTATOR);
        }
        if (target == null) {
            if (player.getSpectatorTarget() != null) {
                setSpectatorTargetInternally(player, null);
            }
            teleportInternally(player, cameraLocation);
            return;
        }
        if (teleportToAnchorBeforeSpectate && shouldTeleportToAnchor(player, target)) {
            Location targetLocation = target.getLocation();
            teleportInternally(player, targetLocation);
            debug(player, "Teleported to death tip anchor before spectating at " + formatLocation(targetLocation) + ".");
        }
        if (target.getWorld() != null && !player.getWorld().equals(target.getWorld())) {
            return;
        }
        setSpectatorTargetInternally(player, target);
    }

    private boolean shouldTeleportToAnchor(Player player, Entity target) {
        if (target == null) {
            return false;
        }
        if (target.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().equals(target.getWorld())) {
            return true;
        }
        return player.getLocation().distanceSquared(target.getLocation()) > teleportToAnchorDistanceSquared;
    }

    private Entity createCameraAnchor(Location cameraLocation) {
        if (cinematicEnabled && cinematicMode == CinematicMode.DISPLAY) {
            return spawnDisplayCamera(cameraLocation);
        }
        if (forceSpectatorUseAnchorTarget) {
            return spawnAnchor(cameraLocation);
        }
        return null;
    }

    private boolean shouldUseCameraAnchor() {
        return (cinematicEnabled && cinematicMode == CinematicMode.DISPLAY) || forceSpectatorUseAnchorTarget;
    }

    private Location createFocusLocation(Location deathLocation) {
        Location focus = deathLocation.clone();
        focus.setY(focus.getY() + cinematicLookAtYOffset);
        return focus;
    }

    private Location computeCinematicCameraLocation(Location focusLocation, float deathYaw,
                                                    double elapsedSeconds, double pitchSteps) {
        double angleDegrees = deathYaw + cinematicStartAngleDegrees;
        if (cinematicRotateAround) {
            angleDegrees += cinematicDegreesPerSecond * elapsedSeconds;
        }
        double angle = Math.toRadians(angleDegrees);
        double x = -Math.sin(angle) * cinematicRadius;
        double z = Math.cos(angle) * cinematicRadius;
        Location camera = new Location(
                focusLocation.getWorld(),
                focusLocation.getX() + x,
                focusLocation.getY() + cinematicHeight,
                focusLocation.getZ() + z
        );
        faceLocation(camera, focusLocation);
        float targetPitch = camera.getPitch();
        camera.setPitch(computeCinematicPitch(targetPitch, pitchSteps));
        return camera;
    }

    private float computeCinematicTargetPitch(Location focusLocation, float deathYaw) {
        Location camera = computeCinematicCameraLocation(focusLocation, deathYaw, 0.0D, Double.MAX_VALUE);
        return camera.getPitch();
    }

    private float computeCinematicPitch(float deathPitch, double pitchSteps) {
        if (cinematicPitchStepPerTick <= 0.0D) {
            return deathPitch;
        }

        double baseSteps = computeBasePitchSteps(deathPitch);
        if (baseSteps <= 0.0D) {
            return deathPitch;
        }

        double steps = Math.max(0.0D, pitchSteps);
        if (steps <= baseSteps) {
            return (float) Math.max(deathPitch, cinematicStartPitch - (cinematicPitchStepPerTick * steps));
        }

        double jerkSteps = computeHeadJerkSteps();
        if (jerkSteps <= 0.0D) {
            return deathPitch;
        }

        double settleSteps = steps - baseSteps;
        double jerkPitch = Math.min(cinematicHeadJerkPitch, cinematicPitchStepPerTick * settleSteps);
        if (settleSteps <= jerkSteps) {
            return (float) (deathPitch - jerkPitch);
        }

        double returnPitch = Math.min(cinematicHeadJerkPitch, cinematicPitchStepPerTick * (settleSteps - jerkSteps));
        return (float) Math.min(deathPitch, (deathPitch - cinematicHeadJerkPitch) + returnPitch);
    }

    private long computePitchMotionTicks(float deathPitch) {
        if (cinematicPitchStepPerTick <= 0.0D) {
            return 0L;
        }
        double steps = computeBasePitchSteps(deathPitch);
        if (steps <= 0.0D) {
            return 0L;
        }
        steps += computeHeadJerkSteps() * 2.0D;
        if (cinematicPitchUseRealtime) {
            return (long) Math.ceil(steps * cinematicPitchStepSeconds * 20.0D);
        }
        return (long) Math.ceil(steps);
    }

    private double computeBasePitchSteps(float deathPitch) {
        if (cinematicPitchStepPerTick <= 0.0D || cinematicStartPitch < deathPitch) {
            return 0.0D;
        }
        return (cinematicStartPitch - deathPitch) / cinematicPitchStepPerTick;
    }

    private double computeHeadJerkSteps() {
        if (cinematicPitchStepPerTick <= 0.0D || cinematicHeadJerkPitch <= 0.0D) {
            return 0.0D;
        }
        return cinematicHeadJerkPitch / cinematicPitchStepPerTick;
    }

    private void faceLocation(Location cameraLocation, Location focusLocation) {
        Vector direction = focusLocation.toVector().subtract(cameraLocation.toVector());
        if (direction.lengthSquared() > 0.0001D) {
            cameraLocation.setDirection(direction);
        }
    }

    private boolean isSameCameraPosition(Location current, Location cameraLocation) {
        if (current == null || cameraLocation == null || current.getWorld() == null || cameraLocation.getWorld() == null) {
            return false;
        }
        if (!current.getWorld().equals(cameraLocation.getWorld())) {
            return false;
        }
        return current.distanceSquared(cameraLocation) < 0.0001
                && Math.abs(current.getYaw() - cameraLocation.getYaw()) < 0.5F
                && Math.abs(current.getPitch() - cameraLocation.getPitch()) < 0.5F;
    }

    private boolean isAnchorSpectateTeleport(ActiveDeathTip tip, Location to) {
        if (tip.anchor() == null || tip.anchor().isDead() || to == null || to.getWorld() == null) {
            return false;
        }
        Location anchorLocation = tip.anchor().getLocation();
        return to.getWorld().equals(anchorLocation.getWorld())
                && to.distanceSquared(anchorLocation) <= Math.max(1.0, teleportToAnchorDistanceSquared);
    }

    private void startSpectatorLock(UUID uuid) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private long elapsed;

            @Override
            public void run() {
                ActiveDeathTip tip = active.get(uuid);
                if (tip == null) {
                    cancel();
                    return;
                }
                if (tip.anchor() != null && tip.anchor().isDead()) {
                    cancel();
                    return;
                }
                if (elapsed > tip.totalViewTicks()) {
                    cancel();
                    return;
                }
                reapplySpectatorTarget(uuid);
                elapsed += spectatorReapplyIntervalTicks;
            }
        }.runTaskTimer(plugin, spectatorReapplyDelayTicks, spectatorReapplyIntervalTicks);
    }

    private void startCinematicCamera(UUID uuid) {
        BukkitTask oldTask = cinematicTasks.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            private long elapsedTicks;
            private final long startNanos = System.nanoTime();

            @Override
            public void run() {
                ActiveDeathTip tip = active.get(uuid);
                if (tip == null) {
                    cancel();
                    return;
                }
                if (elapsedTicks > tip.totalViewTicks()) {
                    cancel();
                    return;
                }

                double elapsedSeconds = getCinematicElapsedSeconds(startNanos, elapsedTicks);
                double pitchSteps = getCinematicPitchSteps(startNanos, elapsedTicks);
                Location nextCameraLocation = computeCinematicCameraLocation(
                        tip.focusLocation(),
                        tip.deathYaw(),
                        elapsedSeconds,
                        pitchSteps
                );
                copyLocation(tip.cameraLocation(), nextCameraLocation);

                if (tip.anchor() != null) {
                    moveCameraTarget(tip.anchor(), nextCameraLocation);
                }

                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline() && tip.anchor() == null) {
                    teleportInternally(player, nextCameraLocation);
                }

                elapsedTicks += cinematicStepTicks;
            }
        }.runTaskTimer(plugin, 0L, cinematicStepTicks);
        cinematicTasks.put(uuid, task);
    }

    private double getCinematicElapsedSeconds(long startNanos, long elapsedTicks) {
        if (!cinematicPitchUseRealtime) {
            return Math.max(0L, elapsedTicks) / 20.0D;
        }
        long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
        return Math.max(0.0D, elapsedNanos / 1_000_000_000.0D);
    }

    private double getCinematicPitchSteps(long startNanos, long elapsedTicks) {
        if (!cinematicPitchUseRealtime) {
            return Math.max(0L, elapsedTicks);
        }
        return getCinematicElapsedSeconds(startNanos, elapsedTicks) / cinematicPitchStepSeconds;
    }

    private void moveCameraTarget(Entity target, Location cameraLocation) {
        applyDisplayCameraDurations(target);
        target.setRotation(cameraLocation.getYaw(), cameraLocation.getPitch());
        target.teleport(cameraLocation);
        target.setRotation(cameraLocation.getYaw(), cameraLocation.getPitch());
    }

    private void startCinematicText(Player player, PendingDeathTip pendingTip) {
        if (!cinematicTextEnabled || player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        ActiveDeathTip activeTip = active.get(uuid);
        if (activeTip == null) {
            return;
        }

        clearCinematicText(uuid);
        if (activeTip.textStartDelayTicks() > 0L) {
            BukkitTask startTask = cinematicPitchUseRealtime
                    ? startCinematicTextRealtimeDelay(uuid, pendingTip, activeTip.textStartDelayTicks())
                    : plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        cinematicTextTasks.remove(uuid);
                        Player currentPlayer = plugin.getServer().getPlayer(uuid);
                        if (currentPlayer != null && currentPlayer.isOnline() && active.containsKey(uuid)) {
                            startCinematicTextNow(currentPlayer, pendingTip);
                        }
                    }, activeTip.textStartDelayTicks());
            cinematicTextTasks.put(uuid, startTask);
            return;
        }
        startCinematicTextNow(player, pendingTip);
    }

    private BukkitTask startCinematicTextRealtimeDelay(UUID uuid, PendingDeathTip pendingTip, long delayTicks) {
        return new org.bukkit.scheduler.BukkitRunnable() {
            private final long startNanos = System.nanoTime();

            @Override
            public void run() {
                long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
                long elapsedStandardTicks = Math.max(0L, Math.round(elapsedNanos / 50_000_000.0D));
                if (elapsedStandardTicks < delayTicks) {
                    return;
                }
                cinematicTextTasks.remove(uuid);
                Player currentPlayer = plugin.getServer().getPlayer(uuid);
                if (currentPlayer != null && currentPlayer.isOnline() && active.containsKey(uuid)) {
                    startCinematicTextNow(currentPlayer, pendingTip);
                }
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void startCinematicTextNow(Player player, PendingDeathTip pendingTip) {
        UUID uuid = player.getUniqueId();
        ActiveDeathTip activeTip = active.get(uuid);
        if (activeTip == null) {
            return;
        }

        Location initialLocation = computeCinematicTextLocation(player, activeTip.cameraLocation(), cinematicTextYOffset);
        World world = initialLocation.getWorld();
        if (world == null) {
            return;
        }

        if (isBedrockPlayer(player)) {
            switch (cinematicTextBedrockMode) {
                case OFF -> {
                    debug(player, "Skipping cinematic text for Bedrock player.");
                    return;
                }
                case TITLE -> {
                    showBedrockCinematicTitle(player, pendingTip);
                    return;
                }
                case CLIENTSIDE -> debug(player, "Using ProtocolLib clientside cinematic text for Bedrock player.");
            }
        }

        ClientsideCinematicText textDisplay = spawnClientsideCinematicText(
                player,
                initialLocation,
                computeCinematicTextLocation(player, activeTip.cameraLocation(), cinematicTextYOffset + cinematicTextSubtitleStartYOffset)
        );
        if (textDisplay == null) {
            debug(player, "Could not spawn clientside cinematic text packets.");
            return;
        }

        cinematicTextDisplays.put(uuid, textDisplay);
        if (!updateCinematicText(player, pendingTip, textDisplay, activeTip.cameraLocation(), 0L)) {
            clearCinematicText(uuid);
            return;
        }

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            private long elapsedTicks;
            private final long totalTicks = cinematicTextTotalTicks();

            @Override
            public void run() {
                ActiveDeathTip currentTip = active.get(uuid);
                Player currentPlayer = plugin.getServer().getPlayer(uuid);
                if (currentTip == null || currentPlayer == null || !currentPlayer.isOnline()
                        || !textDisplay.isActive() || elapsedTicks >= totalTicks) {
                    clearCinematicText(uuid);
                    cancel();
                    return;
                }

                elapsedTicks += cinematicTextUpdateIntervalTicks;
                if (!updateCinematicText(currentPlayer, pendingTip, textDisplay, currentTip.cameraLocation(), elapsedTicks)) {
                    clearCinematicText(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, cinematicTextUpdateIntervalTicks, cinematicTextUpdateIntervalTicks);
        cinematicTextTasks.put(uuid, task);
    }

    private long cinematicTextTotalTicks() {
        return cinematicTextSubtitleIntroTicks + cinematicTextFadeDelayTicks + cinematicTextDurationTicks;
    }

    private double cinematicTextFadeProgress(long elapsedTicks) {
        long fadeTicks = Math.max(0L, elapsedTicks - cinematicTextSubtitleIntroTicks - cinematicTextFadeDelayTicks);
        return Math.min(1.0D, fadeTicks / (double) cinematicTextDurationTicks);
    }

    private boolean updateCinematicText(Player player, PendingDeathTip pendingTip, ClientsideCinematicText textDisplay,
                                        Location cameraLocation, long elapsedTicks) {
        double introProgress = cinematicTextSubtitleIntroProgress(elapsedTicks);
        double fadeProgress = cinematicTextFadeProgress(elapsedTicks);

        Location titleLocation = computeCinematicTextLocation(
                player,
                cameraLocation,
                cinematicTextYOffset + (cinematicTextRiseDistance * fadeProgress)
        );

        double subtitleOffset = cinematicTextSubtitleStartYOffset
                + ((cinematicTextSubtitleEndYOffset - cinematicTextSubtitleStartYOffset) * introProgress)
                + (cinematicTextRiseDistance * fadeProgress);
        double subtitleYOffset = cinematicTextYOffset + subtitleOffset;
        Location subtitleLocation = computeCinematicTextLocation(player, cameraLocation, subtitleYOffset);

        return textDisplay.update(
                player,
                titleLocation,
                cinematicTextTitleLine(player, pendingTip),
                cinematicTextOpacity(fadeProgress),
                cinematicTextScale(),
                subtitleLocation,
                cinematicTextSubtitleLine(player, pendingTip),
                cinematicTextSubtitleOpacity(introProgress, fadeProgress),
                cinematicTextSubtitleScale()
        );
    }

    private String cinematicTextTitleLine(Player player, PendingDeathTip pendingTip) {
        String formattedTip = replacePlaceholders(pendingTip.tip(), player, pendingTip);
        return color(replacePlaceholders(cinematicTextTitle, player, pendingTip).replace("%tip%", formattedTip));
    }

    private String cinematicTextSubtitleLine(Player player, PendingDeathTip pendingTip) {
        String formattedTip = replacePlaceholders(pendingTip.tip(), player, pendingTip);
        return color(replacePlaceholders(cinematicTextSubtitle, player, pendingTip).replace("%tip%", formattedTip));
    }

    private void showBedrockCinematicTitle(Player player, PendingDeathTip pendingTip) {
        int stay = Math.max(1, safeTicksToInt(cinematicTextSubtitleIntroTicks + cinematicTextFadeDelayTicks + cinematicTextDurationTicks));
        player.sendTitle(
                cinematicTextTitleLine(player, pendingTip),
                cinematicTextSubtitleLine(player, pendingTip),
                titleFadeIn,
                stay,
                titleFadeOut
        );
        debug(player, "Using title fallback for Bedrock cinematic text.");
    }

    private Location computeCinematicTextLocation(Player player, Location cameraLocation, double yOffset) {
        Location camera = currentTextCameraLocation(player, cameraLocation);
        if (camera == null || camera.getWorld() == null) {
            return cameraLocation;
        }

        Vector forward = camera.getDirection();
        if (forward.lengthSquared() < 0.0001D) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();

        Vector up = screenUp(forward);
        Location textLocation = camera.clone()
                .add(forward.multiply(cinematicTextDistance))
                .add(up.multiply(yOffset));
        textLocation.setYaw(camera.getYaw());
        textLocation.setPitch(camera.getPitch());
        return textLocation;
    }

    private Location currentTextCameraLocation(Player player, Location fallbackCameraLocation) {
        if (cinematicTextFollowPlayerCamera && player != null && player.isOnline()) {
            Location eyeLocation = player.getEyeLocation();
            if (eyeLocation != null && eyeLocation.getWorld() != null) {
                return eyeLocation.clone();
            }
        }
        return fallbackCameraLocation == null ? null : fallbackCameraLocation.clone();
    }

    private Vector screenUp(Vector forward) {
        Vector worldUp = new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(worldUp);
        if (right.lengthSquared() < 0.0001D) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();

        Vector up = right.crossProduct(forward.clone());
        if (up.lengthSquared() < 0.0001D) {
            return worldUp;
        }
        return up.normalize();
    }

    private double cinematicTextSubtitleIntroProgress(long elapsedTicks) {
        return Math.min(1.0D, Math.max(0L, elapsedTicks) / (double) cinematicTextSubtitleIntroTicks);
    }

    private Vector3f cinematicTextScale() {
        return new Vector3f(cinematicTextStartScale, cinematicTextStartScale, cinematicTextStartScale);
    }

    private Vector3f cinematicTextSubtitleScale() {
        return new Vector3f(cinematicTextSubtitleScale, cinematicTextSubtitleScale, cinematicTextSubtitleScale);
    }

    private byte cinematicTextOpacity(double progress) {
        int opacity = clamp((int) Math.round(cinematicTextStartOpacity
                + ((cinematicTextEndOpacity - cinematicTextStartOpacity) * progress)), 0, 255);
        return (byte) opacity;
    }

    private byte cinematicTextSubtitleOpacity(double introProgress, double fadeProgress) {
        int introOpacity = clamp((int) Math.round(cinematicTextSubtitleStartOpacity
                + ((cinematicTextStartOpacity - cinematicTextSubtitleStartOpacity) * introProgress)), 0, 255);
        if (fadeProgress <= 0.0D) {
            return (byte) introOpacity;
        }
        int fadeOpacity = clamp((int) Math.round(cinematicTextStartOpacity
                + ((cinematicTextEndOpacity - cinematicTextStartOpacity) * fadeProgress)), 0, 255);
        return (byte) fadeOpacity;
    }

    private void startSpectatorTargetWarmup(UUID uuid) {
        for (long retryTick : cinematicTargetRetryTicks) {
            plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> refreshSpectatorTarget(uuid),
                    Math.max(1L, retryTick)
            );
        }
    }

    private void refreshSpectatorTarget(UUID uuid) {
        ActiveDeathTip tip = active.get(uuid);
        if (tip == null || tip.anchor() == null || tip.anchor().isDead()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (player.getGameMode() != GameMode.SPECTATOR) {
            setGameModeInternally(player, GameMode.SPECTATOR);
        }

        // Some clients ignore the first target packet right after respawn.
        // Clearing for one tick then setting the target again mimics the manual shift refresh.
        setSpectatorTargetInternally(player, null);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ActiveDeathTip currentTip = active.get(uuid);
            if (currentTip == null || currentTip.anchor() == null || currentTip.anchor().isDead()) {
                return;
            }
            Player currentPlayer = plugin.getServer().getPlayer(uuid);
            if (currentPlayer == null || !currentPlayer.isOnline()) {
                return;
            }
            if (currentPlayer.getGameMode() != GameMode.SPECTATOR) {
                setGameModeInternally(currentPlayer, GameMode.SPECTATOR);
            }
            setSpectatorTargetInternally(currentPlayer, currentTip.anchor());
            debug(currentPlayer, "Refreshed cinematic spectator target " + currentTip.anchor().getUniqueId() + ".");
        });
    }

    private void copyLocation(Location target, Location source) {
        target.setWorld(source.getWorld());
        target.setX(source.getX());
        target.setY(source.getY());
        target.setZ(source.getZ());
        target.setYaw(source.getYaw());
        target.setPitch(source.getPitch());
    }

    private void reapplySpectatorTarget(UUID uuid) {
        ActiveDeathTip tip = active.get(uuid);
        if (tip == null) {
            return;
        }
        if (tip.anchor() != null && tip.anchor().isDead()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            boolean wasCorrect = isSpectatorViewCorrect(player, tip);
            applySpectatorView(player, tip.anchor(), tip.cameraLocation());
            if (!wasCorrect) {
                if (tip.anchor() != null) {
                    debug(player, "Locked spectator target to death tip anchor " + tip.anchor().getUniqueId() + ".");
                } else {
                    debug(player, "Locked fixed spectator camera at " + formatLocation(tip.cameraLocation()) + ".");
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not re-apply death tip spectator view for " + player.getName() + ": " + exception.getMessage());
        }
    }

    private boolean isSpectatorViewCorrect(Player player, ActiveDeathTip tip) {
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return false;
        }
        if (tip.anchor() != null) {
            return player.getSpectatorTarget() == tip.anchor();
        }
        return isSameCameraPosition(player.getLocation(), tip.cameraLocation());
    }

    private void scheduleSpectatorForce(UUID uuid) {
        plugin.getServer().getScheduler().runTask(plugin, () -> reapplySpectatorTarget(uuid));
    }

    private void setGameModeInternally(Player player, GameMode gameMode) {
        UUID uuid = player.getUniqueId();
        internalGamemodeChanges.add(uuid);
        try {
            player.setGameMode(gameMode);
        } finally {
            internalGamemodeChanges.remove(uuid);
        }
    }

    private void teleportInternally(Player player, Location location) {
        UUID uuid = player.getUniqueId();
        internalTeleports.add(uuid);
        try {
            player.teleport(location);
        } finally {
            internalTeleports.remove(uuid);
        }
    }

    private void setSpectatorTargetInternally(Player player, Entity target) {
        UUID uuid = player.getUniqueId();
        internalTeleports.add(uuid);
        try {
            player.setSpectatorTarget(target);
        } finally {
            internalTeleports.remove(uuid);
        }
    }

    private void debugAnchorState(UUID playerUuid, Entity anchor) {
        if (!debug) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        debug(player, "Anchor check: valid=" + anchor.isValid()
                + ", dead=" + anchor.isDead()
                + ", world=" + (anchor.getWorld() == null ? "unknown" : anchor.getWorld().getName())
                + ", location=" + formatLocation(anchor.getLocation())
                + ", spectatorTarget=" + (player.getSpectatorTarget() == null ? "none" : player.getSpectatorTarget().getUniqueId())
                + ".");
    }

    private Entity spawnDisplayCamera(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        return world.spawn(location, TextDisplay.class, display -> {
            display.setText("");
            display.setBillboard(Display.Billboard.FIXED);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.addScoreboardTag(VIEW_TAG);
            applyDisplayCameraDurations(display);
        });
    }

    private Entity spawnDeathHead(Player player, Location deathLocation) {
        World world = deathLocation.getWorld();
        if (world == null) {
            return null;
        }

        Location headLocation = deathLocation.clone();
        headLocation.setY(headLocation.getY() + deathHeadYOffset);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            head.setItemMeta(skullMeta);
        }

        return world.spawn(headLocation, ItemDisplay.class, display -> {
            display.setItemStack(head);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setBrightness(new Display.Brightness(deathHeadLight, deathHeadLight));
            display.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(deathHeadScale, deathHeadScale, deathHeadScale),
                    new Quaternionf()
            ));
            display.setBillboard(parseBillboard(deathHeadBillboard));
            display.setItemDisplayTransform(parseItemDisplayTransform(deathHeadTransform));
            display.addScoreboardTag(VIEW_TAG);
        });
    }

    private void applyDisplayCameraDurations(Entity entity) {
        if (entity instanceof Display display) {
            display.setTeleportDuration(cinematicTeleportDuration);
            display.setInterpolationDuration(cinematicInterpolationDuration);
        }
    }

    private Entity spawnAnchor(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        return world.spawn(location, Villager.class, villager -> {
            villager.setInvisible(true);
            villager.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
            villager.setAI(false);
            villager.setGravity(false);
            villager.setSilent(true);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setRemoveWhenFarAway(false);
            villager.addScoreboardTag(VIEW_TAG);
            villager.setCustomNameVisible(false);
            villager.setPersistent(false);
        });
    }

    private void showTip(Player player, PendingDeathTip tip) {
        String formattedTip = replacePlaceholders(tip.tip(), player, tip);

        if (titleEnabled) {
            player.sendTitle(
                    color(replacePlaceholders(titleText, player, tip).replace("%tip%", formattedTip)),
                    color(replacePlaceholders(subtitleText, player, tip).replace("%tip%", formattedTip)),
                    titleFadeIn,
                    titleStay,
                    titleFadeOut
            );
        }

        if (soundEnabled && soundName != null && !soundName.isBlank()) {
            playSound(player);
        }

        if (messageEnabled) {
            for (String line : messageLines) {
                player.sendMessage(color(replacePlaceholders(line, player, tip).replace("%tip%", formattedTip)));
            }
        }
    }

    private void cleanup(UUID uuid, boolean restorePlayer) {
        ActiveDeathTip tip = active.remove(uuid);
        clearCinematicText(uuid);
        if (tip == null) {
            return;
        }

        BukkitTask cinematicTask = cinematicTasks.remove(uuid);
        if (cinematicTask != null) {
            cinematicTask.cancel();
        }

        if (tip.restoreTask() != null) {
            tip.restoreTask().cancel();
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (restorePlayer && player != null && player.isOnline()) {
            try {
                if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() == tip.anchor()) {
                    player.setSpectatorTarget(null);
                }
                if (restoreToRespawnLocation) {
                    teleportInternally(player, tip.respawnLocation());
                }
                if (restoreGamemode && player.getGameMode() == GameMode.SPECTATOR) {
                    setGameModeInternally(player, tip.restoreMode());
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not restore player after death tip view: " + exception.getMessage());
            }
        }

        if (tip.anchor() != null && !tip.anchor().isDead()) {
            tip.anchor().remove();
        }
        if (tip.deathHead() != null && !tip.deathHead().isDead()) {
            tip.deathHead().remove();
        }
    }

    private void clearCinematicText(UUID uuid) {
        BukkitTask textTask = cinematicTextTasks.remove(uuid);
        if (textTask != null) {
            textTask.cancel();
        }

        ClientsideCinematicText displays = cinematicTextDisplays.remove(uuid);
        if (displays != null) {
            displays.destroy();
        }
    }

    private LivingEntity getMobKiller(Player player) {
        EntityDamageEvent cause = player.getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent damageByEntity)) {
            return null;
        }

        Entity damager = damageByEntity.getDamager();
        if (damager instanceof Player) {
            return null;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player) {
                return null;
            }
            if (shooter instanceof LivingEntity livingShooter) {
                return livingShooter;
            }
            return null;
        }
        if (damager instanceof LivingEntity livingDamager) {
            return livingDamager;
        }
        return null;
    }

    private String getDeathSourceName(Player player) {
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause == null || cause.getCause() == null) {
            return "Unknown";
        }
        return cause.getCause().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String randomTip() {
        return tips.get(ThreadLocalRandom.current().nextInt(tips.size()));
    }

    private String replacePlaceholders(String text, Player player, PendingDeathTip tip) {
        if (text == null) {
            return "";
        }
        return text
                .replace("%player%", player.getName())
                .replace("%mob%", tip.mobName())
                .replace("%world%", tip.deathLocation().getWorld() == null ? "" : tip.deathLocation().getWorld().getName());
    }

    private void playSound(Player player) {
        String normalizedSoundName = soundName.trim();
        try {
            Sound sound = Sound.valueOf(normalizedSoundName.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
            player.playSound(player.getLocation(), sound, soundCategory, soundVolume, soundPitch);
        } catch (IllegalArgumentException ignored) {
            player.playSound(player.getLocation(), normalizedSoundName, soundCategory, soundVolume, soundPitch);
        }
    }

    private SoundCategory parseSoundCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return SoundCategory.MASTER;
        }
        try {
            return SoundCategory.valueOf(categoryName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SoundCategory.MASTER;
        }
    }

    private CinematicMode parseCinematicMode(String modeName) {
        if (modeName == null || modeName.isBlank()) {
            return CinematicMode.DISPLAY;
        }
        try {
            return CinematicMode.valueOf(modeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CinematicMode.DISPLAY;
        }
    }

    private CinematicTextBedrockMode parseCinematicTextBedrockMode(String modeName) {
        if (modeName == null || modeName.isBlank()) {
            return CinematicTextBedrockMode.TITLE;
        }
        try {
            return CinematicTextBedrockMode.valueOf(modeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CinematicTextBedrockMode.TITLE;
        }
    }

    private Display.Billboard parseBillboard(String billboardName) {
        if (billboardName == null || billboardName.isBlank()) {
            return Display.Billboard.FIXED;
        }
        try {
            return Display.Billboard.valueOf(billboardName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Display.Billboard.FIXED;
        }
    }

    private ItemDisplay.ItemDisplayTransform parseItemDisplayTransform(String transformName) {
        if (transformName == null || transformName.isBlank()) {
            return ItemDisplay.ItemDisplayTransform.HEAD;
        }
        try {
            return ItemDisplay.ItemDisplayTransform.valueOf(transformName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ItemDisplay.ItemDisplayTransform.HEAD;
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int safeTicksToInt(long ticks) {
        if (ticks > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(0L, ticks);
    }

    private boolean isBedrockPlayer(Player player) {
        return isGeyserBedrockPlayer(player) || isFloodgatePlayer(player);
    }

    private boolean isGeyserBedrockPlayer(Player player) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = apiClass.getMethod("api").invoke(null);
            Object result = apiClass.getMethod("isBedrockPlayer", UUID.class).invoke(api, player.getUniqueId());
            return result instanceof Boolean bedrock && bedrock;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private boolean isFloodgatePlayer(Player player) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
            return result instanceof Boolean bedrock && bedrock;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private ClientsideCinematicText spawnClientsideCinematicText(Player player, Location titleLocation, Location subtitleLocation) {
        ClientsideCinematicText text = new ClientsideCinematicText(
                player.getUniqueId(),
                new ClientsideTextDisplay(nextFakeEntityId(), UUID.randomUUID()),
                new ClientsideTextDisplay(nextFakeEntityId(), UUID.randomUUID())
        );
        if (!text.spawn(player, titleLocation, subtitleLocation)) {
            text.destroy();
            return null;
        }
        return text;
    }

    private int nextFakeEntityId() {
        return FAKE_ENTITY_IDS.updateAndGet(current -> current >= Integer.MAX_VALUE - 4 ? 2_000_000_000 : current + 1);
    }

    private boolean sendPacket(Player player, PacketContainer packet) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        try {
            protocolManager.sendServerPacket(player, packet);
            return true;
        } catch (Exception exception) {
            if (debug) {
                plugin.getLogger().warning("[DeathTips] Could not send clientside cinematic packet to "
                        + player.getName() + ": " + exception.getMessage());
            }
            return false;
        }
    }

    private <T> boolean writeIfPresent(StructureModifier<T> modifier, int index, T value) {
        if (modifier == null || modifier.size() <= index) {
            return false;
        }
        modifier.write(index, value);
        return true;
    }

    private byte angleToProtocolByte(float angle) {
        return (byte) Math.floor(angle * 256.0F / 360.0F);
    }

    private int cinematicTextBackgroundColor() {
        return (cinematicTextBackgroundAlpha << 24);
    }

    private byte cinematicTextStyleFlags() {
        byte flags = 0;
        if (cinematicTextShadow) {
            flags |= 0x01;
        }
        if (cinematicTextSeeThrough) {
            flags |= 0x02;
        }
        return flags;
    }

    private final class ClientsideCinematicText {
        private final UUID viewerUuid;
        private final ClientsideTextDisplay title;
        private final ClientsideTextDisplay subtitle;
        private boolean active;

        private ClientsideCinematicText(UUID viewerUuid, ClientsideTextDisplay title, ClientsideTextDisplay subtitle) {
            this.viewerUuid = viewerUuid;
            this.title = title;
            this.subtitle = subtitle;
        }

        private boolean spawn(Player viewer, Location titleLocation, Location subtitleLocation) {
            active = title.spawn(viewer, titleLocation) && subtitle.spawn(viewer, subtitleLocation);
            return active;
        }

        private boolean update(Player viewer,
                               Location titleLocation,
                               String titleText,
                               byte titleOpacity,
                               Vector3f titleScale,
                               Location subtitleLocation,
                               String subtitleText,
                               byte subtitleOpacity,
                               Vector3f subtitleScale) {
            if (!active || !viewer.getUniqueId().equals(viewerUuid)) {
                return false;
            }
            boolean titleUpdated = title.update(viewer, titleLocation, titleText, titleOpacity, titleScale);
            boolean subtitleUpdated = subtitle.update(viewer, subtitleLocation, subtitleText, subtitleOpacity, subtitleScale);
            active = titleUpdated && subtitleUpdated;
            return active;
        }

        private boolean isActive() {
            return active;
        }

        private void destroy() {
            active = false;
            Player viewer = plugin.getServer().getPlayer(viewerUuid);
            if (viewer == null || !viewer.isOnline()) {
                return;
            }

            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            if (!writeIfPresent(packet.getIntLists(), 0, List.of(title.entityId(), subtitle.entityId()))) {
                writeIfPresent(packet.getIntegerArrays(), 0, new int[]{title.entityId(), subtitle.entityId()});
            }
            sendPacket(viewer, packet);
        }
    }

    private final class ClientsideTextDisplay {
        private final int entityId;
        private final UUID entityUuid;

        private ClientsideTextDisplay(int entityId, UUID entityUuid) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
        }

        private int entityId() {
            return entityId;
        }

        private boolean spawn(Player viewer, Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }

            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            writeIfPresent(packet.getIntegers(), 0, entityId);
            writeIfPresent(packet.getUUIDs(), 0, entityUuid);
            writeIfPresent(packet.getEntityTypeModifier(), 0, org.bukkit.entity.EntityType.TEXT_DISPLAY);
            writeIfPresent(packet.getDoubles(), 0, location.getX());
            writeIfPresent(packet.getDoubles(), 1, location.getY());
            writeIfPresent(packet.getDoubles(), 2, location.getZ());
            writeIfPresent(packet.getBytes(), 0, angleToProtocolByte(location.getPitch()));
            writeIfPresent(packet.getBytes(), 1, angleToProtocolByte(location.getYaw()));
            writeIfPresent(packet.getBytes(), 2, angleToProtocolByte(location.getYaw()));
            writeIfPresent(packet.getIntegers(), 1, 0);

            if (!sendPacket(viewer, packet)) {
                return false;
            }
            return update(viewer, location, "", (byte) 0, new Vector3f(cinematicTextStartScale));
        }

        private boolean update(Player viewer, Location location, String text, byte opacity, Vector3f scale) {
            return teleport(viewer, location) && metadata(viewer, text, opacity, scale);
        }

        private boolean teleport(Player viewer, Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }

            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            writeIfPresent(packet.getIntegers(), 0, entityId);
            writeIfPresent(packet.getDoubles(), 0, location.getX());
            writeIfPresent(packet.getDoubles(), 1, location.getY());
            writeIfPresent(packet.getDoubles(), 2, location.getZ());
            writeIfPresent(packet.getBytes(), 0, angleToProtocolByte(location.getYaw()));
            writeIfPresent(packet.getBytes(), 1, angleToProtocolByte(location.getPitch()));
            writeIfPresent(packet.getBooleans(), 0, false);
            return sendPacket(viewer, packet);
        }

        private boolean metadata(Player viewer, String text, byte opacity, Vector3f scale) {
            WrappedDataWatcher watcher = new WrappedDataWatcher();
            watcher.setBoolean(5, true, true);
            watcher.setInteger(8, 0, true);
            watcher.setInteger(9, cinematicTextInterpolationDuration, true);
            watcher.setInteger(10, cinematicTextTeleportDuration, true);
            setDisplayScale(watcher, scale);
            watcher.setByte(15, (byte) 3, true);
            watcher.setFloat(17, cinematicTextViewRange, true);
            watcher.setFloat(20, 1.0F, true);
            watcher.setFloat(21, 0.5F, true);
            watcher.setChatComponent(23, WrappedChatComponent.fromLegacyText(text == null ? "" : text), true);
            watcher.setInteger(24, cinematicTextLineWidth, true);
            watcher.setInteger(25, cinematicTextBackgroundColor(), true);
            watcher.setByte(26, opacity, true);
            watcher.setByte(27, cinematicTextStyleFlags(), true);

            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            writeIfPresent(packet.getIntegers(), 0, entityId);
            if (!writeIfPresent(packet.getDataValueCollectionModifier(), 0, watcher.toDataValueCollection())) {
                writeIfPresent(packet.getWatchableCollectionModifier(), 0, watcher.getWatchableObjects());
            }
            return sendPacket(viewer, packet);
        }

        private void setDisplayScale(WrappedDataWatcher watcher, Vector3f scale) {
            try {
                watcher.setObject(
                        12,
                        WrappedDataWatcher.Registry.get(Vector3f.class),
                        new Vector3f(scale.x, scale.y, scale.z),
                        true
                );
            } catch (IllegalArgumentException exception) {
                if (debug) {
                    plugin.getLogger().warning("[DeathTips] ProtocolLib cannot serialize TextDisplay scale as JOML Vector3f: "
                            + exception.getMessage());
                }
            }
        }
    }

    private void debug(Player player, String message) {
        if (!debug) {
            return;
        }
        plugin.getLogger().info("[DeathTips] " + player.getName() + ": " + message);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName()
                + " " + location.getX()
                + " " + location.getY()
                + " " + location.getZ()
                + " yaw=" + location.getYaw()
                + " pitch=" + location.getPitch();
    }

    private record PendingDeathTip(Location deathLocation, GameMode previousGameMode, String mobName, String tip) {
    }

    private enum CinematicMode {
        DISPLAY,
        PLAYER
    }

    private enum CinematicTextBedrockMode {
        TITLE,
        CLIENTSIDE,
        OFF
    }

    private record ActiveDeathTip(Entity anchor, GameMode restoreMode, Location respawnLocation, BukkitTask restoreTask,
                                  Location cameraLocation, Location focusLocation, float deathYaw, float deathPitch,
                                  Entity deathHead, long textStartDelayTicks, long totalViewTicks) {
    }
}
