package com.turtle.tutiencore.core.manager;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DeathTipManager implements Listener {

    private static final String CONFIG_PATH = "death-tips";
    private static final String VIEW_TAG = "tutiencore_death_tip_view";

    private final JavaPlugin plugin;
    private final Map<UUID, PendingDeathTip> pending = new HashMap<>();
    private final Map<UUID, ActiveDeathTip> active = new HashMap<>();
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

    public DeathTipManager(JavaPlugin plugin) {
        this.plugin = plugin;
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
        viewDurationTicks = Math.max(1L, config.getLong(CONFIG_PATH + ".view-duration-ticks", 80L));
        restoreGamemode = config.getBoolean(CONFIG_PATH + ".restore-gamemode", true);
        restoreToRespawnLocation = config.getBoolean(CONFIG_PATH + ".restore-to-respawn-location", true);

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

        Entity anchor = spawnAnchor(tip.deathLocation());
        if (anchor == null) {
            debug(player, "Could not spawn anchor, showing title/sound only.");
            showTip(player, tip);
            return;
        }

        GameMode restoreMode = tip.previousGameMode();
        try {
            applySpectatorTarget(player, anchor);
            debug(player, "Spectator target set to death tip anchor " + anchor.getUniqueId() + ".");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not start death tip spectator view for " + player.getName() + ": " + exception.getMessage());
            anchor.remove();
            showTip(player, tip);
            return;
        }

        showTip(player, tip);
        BukkitTask restoreTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> cleanup(player.getUniqueId(), true),
                viewDurationTicks);
        active.put(player.getUniqueId(), new ActiveDeathTip(anchor, restoreMode, respawnLocation, restoreTask));

        if (spectatorReapplyDelayTicks >= 0L) {
            startSpectatorLock(player.getUniqueId());
        }
    }

    private void applySpectatorTarget(Player player, Entity target) {
        if (teleportToAnchorBeforeSpectate && shouldTeleportToAnchor(player, target)) {
            Location targetLocation = target.getLocation();
            player.teleport(targetLocation);
            debug(player, "Teleported to death tip anchor before spectating at " + formatLocation(targetLocation) + ".");
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        if (target.getWorld() != null && !player.getWorld().equals(target.getWorld())) {
            return;
        }
        player.setSpectatorTarget(target);
    }

    private boolean shouldTeleportToAnchor(Player player, Entity target) {
        if (target.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().equals(target.getWorld())) {
            return true;
        }
        return player.getLocation().distanceSquared(target.getLocation()) > teleportToAnchorDistanceSquared;
    }

    private void startSpectatorLock(UUID uuid) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private long elapsed;

            @Override
            public void run() {
                ActiveDeathTip tip = active.get(uuid);
                if (tip == null || tip.anchor() == null || tip.anchor().isDead()) {
                    cancel();
                    return;
                }
                if (elapsed > viewDurationTicks) {
                    cancel();
                    return;
                }
                reapplySpectatorTarget(uuid);
                elapsed += spectatorReapplyIntervalTicks;
            }
        }.runTaskTimer(plugin, spectatorReapplyDelayTicks, spectatorReapplyIntervalTicks);
    }

    private void reapplySpectatorTarget(UUID uuid) {
        ActiveDeathTip tip = active.get(uuid);
        if (tip == null || tip.anchor() == null || tip.anchor().isDead()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            applySpectatorTarget(player, tip.anchor());
            debug(player, "Locked spectator target to death tip anchor " + tip.anchor().getUniqueId() + ".");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not re-apply death tip spectator view for " + player.getName() + ": " + exception.getMessage());
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
        if (tip == null) {
            return;
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
                    player.teleport(tip.respawnLocation());
                }
                if (restoreGamemode && player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(tip.restoreMode());
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not restore player after death tip view: " + exception.getMessage());
            }
        }

        if (tip.anchor() != null && !tip.anchor().isDead()) {
            tip.anchor().remove();
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

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
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

    private record ActiveDeathTip(Entity anchor, GameMode restoreMode, Location respawnLocation, BukkitTask restoreTask) {
    }
}
