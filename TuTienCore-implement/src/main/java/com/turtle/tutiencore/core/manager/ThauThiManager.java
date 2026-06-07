package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThauThiManager implements CommandExecutor, Listener {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,###");

    private final JavaPlugin plugin;
    private final RealmManager realmManager;
    private File configFile;
    private FileConfiguration settings;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DisplayState> displays = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long tickCounter;

    public ThauThiManager(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        start();
    }

    public void reload() {
        configFile = new File(plugin.getDataFolder(), "thauthi.yml");
        if (!configFile.exists()) {
            plugin.saveResource("thauthi.yml", false);
        }
        settings = YamlConfiguration.loadConfiguration(configFile);
    }

    private void start() {
        stopTaskOnly();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        stopTaskOnly();
        enabledPlayers.clear();
        removeAllDisplays();
    }

    private void stopTaskOnly() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(colorize(message("only-player", "&cChỉ player mới dùng được lệnh này.")));
            return true;
        }

        if (!settings.getBoolean("thauthi.enabled", true)) {
            player.sendMessage(colorize(message("disabled", "&cThấu thị đang tắt.")));
            return true;
        }

        String permission = permission();
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(colorize(message("no-permission", "&cBạn không có quyền dùng Thấu Thị.")));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (enabledPlayers.remove(uuid)) {
            removeDisplay(uuid, false);
            playClientEffect(player, "toggle-off");
            player.sendMessage(colorize(message("off", "&cĐã tắt Thấu Thị.")));
        } else {
            enabledPlayers.add(uuid);
            playClientEffect(player, "toggle-on");
            player.sendMessage(colorize(message("on", "&aĐã bật Thấu Thị.")));
        }
        return true;
    }

    private void tick() {
        if (enabledPlayers.isEmpty()) {
            return;
        }

        tickCounter++;
        int updateInterval = Math.max(1, settings.getInt("thauthi.update-interval-ticks", 2));
        if (tickCounter % updateInterval != 0) {
            return;
        }

        for (UUID uuid : new HashSet<>(enabledPlayers)) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null || !viewer.isOnline()) {
                enabledPlayers.remove(uuid);
                removeDisplay(uuid, false);
                continue;
            }

            if (settings.getBoolean("thauthi.require-permission-each-tick", true)
                    && !permission().isBlank() && !viewer.hasPermission(permission())) {
                enabledPlayers.remove(uuid);
                removeDisplay(uuid, false);
                continue;
            }

            Player target = findTarget(viewer);
            if (target == null) {
                removeDisplay(uuid, true);
                continue;
            }

            showOrUpdate(viewer, target);
        }
    }

    private Player findTarget(Player viewer) {
        World world = viewer.getWorld();
        Location eye = viewer.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double distance = Math.max(1.0D, settings.getDouble("thauthi.max-distance", 18.0D));
        double raySize = Math.max(0.0D, settings.getDouble("thauthi.ray-size", 0.35D));
        boolean ignoreSpectator = settings.getBoolean("thauthi.ignore-spectator", true);

        RayTraceResult result = world.rayTraceEntities(eye, direction, distance, raySize, entity -> {
            if (!(entity instanceof Player target) || target == viewer || target.isDead() || !viewer.canSee(target)) {
                return false;
            }
            return !ignoreSpectator || target.getGameMode() != GameMode.SPECTATOR;
        });

        if (result == null || !(result.getHitEntity() instanceof Player target)) {
            return null;
        }

        if (settings.getBoolean("thauthi.require-line-of-sight", true) && !viewer.hasLineOfSight(target)) {
            return null;
        }

        RayTraceResult blockHit = world.rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true);
        if (blockHit != null && blockHit.getHitPosition() != null) {
            double blockDistance = blockHit.getHitPosition().distance(eye.toVector());
            double targetDistance = target.getEyeLocation().toVector().distance(eye.toVector());
            if (blockDistance + 0.15D < targetDistance) {
                return null;
            }
        }

        return target;
    }

    private void showOrUpdate(Player viewer, Player target) {
        UUID viewerId = viewer.getUniqueId();
        DisplayState state = displays.get(viewerId);
        Location location = targetLocation(viewer, target);
        String text = buildText(viewer, target);

        if (state == null || state.display == null || !state.display.isValid()
                || state.display.getWorld() != target.getWorld()) {
            removeDisplay(viewerId, false);
            state = new DisplayState(spawnDisplay(viewer, introStartLocation(location), text));
            displays.put(viewerId, state);
        }

        state.cancelRemoval();
        state.removing = false;
        UUID targetUuid = target.getUniqueId();
        boolean newTarget = state.targetUuid == null || !state.targetUuid.equals(targetUuid);
        if (newTarget) {
            state.targetUuid = targetUuid;
            playClientEffect(viewer, "target-found");
        }

        TextDisplay display = state.display;
        display.setText(text);
        display.setTeleportDuration(clampDuration(settings.getInt("thauthi.teleport-duration", 3)));
        display.setInterpolationDuration(clampDuration(settings.getInt("thauthi.interpolation-duration", 3)));
        setOptional(display, "setInterpolationDelay", int.class,
                Math.max(0, settings.getInt("thauthi.interpolation-delay", 0)));
        byte opacity = parseOpacity(settings.getInt("thauthi.opacity", 230));
        if (newTarget) {
            animateIn(state, location, opacity);
        } else if (!state.animatingIn) {
            display.teleport(location);
            display.setTextOpacity(opacity);
        }
        applyScale(display, viewer, target);
    }

    private TextDisplay spawnDisplay(Player viewer, Location location, String text) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, hologram -> {
            hologram.setVisibleByDefault(false);
            hologram.setPersistent(false);
            hologram.setGravity(false);
            hologram.setInvulnerable(true);
            hologram.setBillboard(Display.Billboard.CENTER);
            hologram.setText(text);
            hologram.setTextOpacity(parseOpacity(0));
            hologram.setShadowed(settings.getBoolean("thauthi.shadow", true));
            hologram.setSeeThrough(settings.getBoolean("thauthi.see-through", false));
            hologram.setLineWidth(Math.max(1, settings.getInt("thauthi.line-width", 190)));
            hologram.setTeleportDuration(clampDuration(settings.getInt("thauthi.teleport-duration", 3)));
            hologram.setInterpolationDuration(clampDuration(settings.getInt("thauthi.interpolation-duration", 3)));
            setOptional(hologram, "setViewRange", float.class,
                    (float) Math.max(1.0D, settings.getDouble("thauthi.view-range", 24.0D)));
            setOptional(hologram, "setDefaultBackground", boolean.class,
                    settings.getBoolean("thauthi.default-background", false));
            setOptional(hologram, "setBackgroundColor", Color.class, backgroundColor());
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    private Location targetLocation(Player viewer, Player target) {
        double yOffset = settings.getDouble("thauthi.y-offset", 0.75D);
        double xOffset = settings.getDouble("thauthi.x-offset", -0.95D);
        double zOffset = settings.getDouble("thauthi.z-offset", 0.0D);
        Location location = target.getLocation().add(0.0D, target.getHeight() + yOffset, 0.0D);

        if (!settings.getBoolean("thauthi.offset-relative-to-viewer", true)) {
            return location.add(xOffset, 0.0D, zOffset);
        }

        Vector forward = target.getLocation().toVector().subtract(viewer.getLocation().toVector());
        forward.setY(0.0D);
        if (forward.lengthSquared() < 0.0001D) {
            forward = viewer.getLocation().getDirection();
            forward.setY(0.0D);
        }
        if (forward.lengthSquared() < 0.0001D) {
            return location.add(xOffset, 0.0D, zOffset);
        }

        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();
        return location.add(right.multiply(xOffset)).add(forward.multiply(zOffset));
    }

    private void applyScale(TextDisplay display, Player viewer, Player target) {
        double scale = settings.getDouble("thauthi.scale.default", 1.0D);
        if (settings.getBoolean("thauthi.scale.distance-based", true)) {
            double minDistance = Math.max(0.0D, settings.getDouble("thauthi.scale.min-distance", 1.5D));
            double maxDistance = Math.max(minDistance + 0.01D, settings.getDouble("thauthi.scale.max-distance", 10.0D));
            double minScale = Math.max(0.01D, settings.getDouble("thauthi.scale.min", 0.62D));
            double maxScale = Math.max(minScale, settings.getDouble("thauthi.scale.max", 1.0D));
            double distance = viewer.getLocation().distance(target.getLocation());
            double progress = Math.max(0.0D, Math.min(1.0D, (distance - minDistance) / (maxDistance - minDistance)));
            progress = progress * progress * (3.0D - 2.0D * progress);
            scale = minScale + ((maxScale - minScale) * progress);
        }

        float value = (float) Math.max(0.01D, scale);
        display.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(value, value, value),
                new AxisAngle4f()));
    }

    private Location introStartLocation(Location finalLocation) {
        if (!settings.getBoolean("thauthi.spawn-animation.enabled", true)) {
            return finalLocation;
        }
        double fromYOffset = settings.getDouble("thauthi.spawn-animation.from-y-offset", -0.45D);
        return finalLocation.clone().add(0.0D, fromYOffset, 0.0D);
    }

    private void animateIn(DisplayState state, Location finalLocation, byte targetOpacity) {
        TextDisplay display = state.display;
        state.cancelFadeIn();

        if (!settings.getBoolean("thauthi.spawn-animation.enabled", true)) {
            state.animatingIn = false;
            display.teleport(finalLocation);
            display.setTextOpacity(targetOpacity);
            return;
        }

        int fadeTicks = Math.max(1, settings.getInt("thauthi.spawn-animation.fade-in-ticks", 10));
        int riseDuration = clampDuration(settings.getInt("thauthi.spawn-animation.rise-duration", 8));
        int interpolationDuration = clampDuration(settings.getInt("thauthi.spawn-animation.interpolation-duration", fadeTicks));
        long delay = Math.max(0L, settings.getLong("thauthi.spawn-animation.start-delay-ticks", 1L));
        Location startLocation = introStartLocation(finalLocation);

        state.animatingIn = true;
        display.setTeleportDuration(0);
        display.setInterpolationDuration(interpolationDuration);
        display.setTextOpacity(parseOpacity(0));
        display.teleport(startLocation);

        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick;

            @Override
            public void run() {
                if (!display.isValid()) {
                    cancel();
                    return;
                }

                if (tick == 0) {
                    display.setTeleportDuration(riseDuration);
                    display.setInterpolationDuration(interpolationDuration);
                    display.teleport(finalLocation);
                }

                double progress = Math.max(0.0D, Math.min(1.0D, (double) tick / fadeTicks));
                progress = progress * progress * (3.0D - 2.0D * progress);
                display.setTextOpacity(parseOpacity((int) Math.round((targetOpacity & 0xFF) * progress)));

                if (tick >= fadeTicks) {
                    display.setTextOpacity(targetOpacity);
                    state.animatingIn = false;
                    cancel();
                    return;
                }
                tick++;
            }

            private void cancel() {
                state.animatingIn = false;
                if (taskRef[0] != null) {
                    taskRef[0].cancel();
                    if (state.fadeInTask == taskRef[0]) {
                        state.fadeInTask = null;
                    }
                }
            }
        }, delay, 1L);
        state.fadeInTask = taskRef[0];
    }

    private void removeDisplay(UUID viewerId, boolean fade) {
        DisplayState state = displays.get(viewerId);
        if (state == null || state.display == null) {
            return;
        }
        state.cancelFadeIn();
        Player viewer = Bukkit.getPlayer(viewerId);

        TextDisplay display = state.display;
        if (!fade || !display.isValid()) {
            state.cancelRemoval();
            if (display.isValid()) {
                display.remove();
            }
            displays.remove(viewerId);
            return;
        }

        if (state.removing) {
            return;
        }

        state.removing = true;
        if (viewer != null && viewer.isOnline()) {
            playClientEffect(viewer, "target-lost");
        }
        state.targetUuid = null;
        int fallDuration = clampDuration(settings.getInt("thauthi.fade-out-teleport-duration",
                settings.getInt("thauthi.fade-out-interpolation-duration", 4)));
        int interpolationDuration = clampDuration(settings.getInt("thauthi.fade-out-interpolation-duration", 4));
        double yOffset = settings.getDouble("thauthi.fade-out-y-offset", -0.45D);
        Location endLocation = display.getLocation().clone().add(0.0D, yOffset, 0.0D);

        display.setTeleportDuration(fallDuration);
        display.setInterpolationDuration(interpolationDuration);
        display.teleport(endLocation);
        display.setTextOpacity(parseOpacity(settings.getInt("thauthi.fade-out-opacity", 0)));
        long delay = Math.max(1L, Math.max(
                settings.getLong("thauthi.fade-out-ticks", 8L),
                Math.max(fallDuration, interpolationDuration)));
        state.removeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DisplayState current = displays.get(viewerId);
            if (current == state) {
                if (display.isValid()) {
                    display.remove();
                }
                displays.remove(viewerId);
            }
        }, delay);
    }

    private void playClientEffect(Player player, String key) {
        if (player == null || !player.isOnline()
                || !settings.getBoolean("thauthi.effects.enabled", true)) {
            return;
        }

        String basePath = "thauthi.effects." + key;
        playClientSound(player, basePath + ".sound");
        spawnClientParticle(player, basePath + ".particle");
    }

    private void playClientSound(Player player, String path) {
        if (!settings.getBoolean(path + ".enabled", false)) {
            return;
        }

        String name = settings.getString(path + ".name", "");
        if (name == null || name.isBlank()) {
            return;
        }

        SoundCategory category = soundCategory(settings.getString(path + ".category", "MASTER"));
        float volume = (float) settings.getDouble(path + ".volume", 1.0D);
        float pitch = (float) settings.getDouble(path + ".pitch", 1.0D);
        player.playSound(player.getLocation(), name, category, volume, pitch);
    }

    private void spawnClientParticle(Player player, String path) {
        if (!settings.getBoolean(path + ".enabled", false)) {
            return;
        }

        Particle particle = particle(settings.getString(path + ".type", "ENCHANT"));
        if (particle == null) {
            return;
        }

        String shape = settings.getString(path + ".shape", "BURST");
        if ("EXPANDING_RING".equalsIgnoreCase(shape) || "RING".equalsIgnoreCase(shape)) {
            spawnExpandingRing(player, particle, path);
            return;
        }

        Location location = effectLocation(player, path);
        int count = Math.max(0, settings.getInt(path + ".count", 12));
        double offsetX = Math.max(0.0D, settings.getDouble(path + ".x-offset", 0.25D));
        double offsetY = Math.max(0.0D, settings.getDouble(path + ".y-offset", 0.25D));
        double offsetZ = Math.max(0.0D, settings.getDouble(path + ".z-offset", 0.25D));
        double speed = Math.max(0.0D, settings.getDouble(path + ".speed", 0.02D));
        try {
            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
        } catch (IllegalArgumentException ignored) {
            // Some particles require extra data; ignore invalid config instead of spamming console.
        }
    }

    private void spawnExpandingRing(Player player, Particle particle, String path) {
        int ticks = Math.max(1, settings.getInt(path + ".ring.ticks", 5));
        long interval = Math.max(1L, settings.getLong(path + ".ring.interval-ticks", 1L));
        int points = Math.max(6, settings.getInt(path + ".ring.points", 28));
        int countPerPoint = Math.max(1, settings.getInt(path + ".ring.point-count", 1));
        double pointOffset = Math.max(0.0D, settings.getDouble(path + ".ring.point-offset", 0.0D));
        double speed = Math.max(0.0D, settings.getDouble(path + ".ring.speed",
                settings.getDouble(path + ".speed", 0.0D)));
        double radiusStart = Math.max(0.0D, settings.getDouble(path + ".ring.radius-start", 0.12D));
        double radiusEnd = Math.max(radiusStart, settings.getDouble(path + ".ring.radius-end", 1.15D));
        double forwardOffset = settings.getDouble(path + ".ring.forward-offset", 0.85D);
        boolean smooth = settings.getBoolean(path + ".ring.smooth", true);

        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                double progress = ticks <= 1 ? 1.0D : Math.max(0.0D, Math.min(1.0D, (double) tick / (ticks - 1)));
                if (smooth) {
                    progress = progress * progress * (3.0D - 2.0D * progress);
                }
                double radius = radiusStart + ((radiusEnd - radiusStart) * progress);

                Location eye = player.getEyeLocation();
                Vector forward = eye.getDirection().normalize();
                Vector right = new Vector(0.0D, 1.0D, 0.0D).crossProduct(forward);
                if (right.lengthSquared() < 0.0001D) {
                    right = new Vector(1.0D, 0.0D, 0.0D);
                } else {
                    right.normalize();
                }
                Vector up = forward.clone().crossProduct(right);
                if (up.lengthSquared() < 0.0001D) {
                    up = new Vector(0.0D, 1.0D, 0.0D);
                } else {
                    up.normalize();
                }

                Location center = effectLocation(player, path).add(forward.multiply(forwardOffset));
                for (int point = 0; point < points; point++) {
                    double angle = (Math.PI * 2.0D * point) / points;
                    Vector offset = right.clone().multiply(Math.cos(angle) * radius)
                            .add(up.clone().multiply(Math.sin(angle) * radius));
                    try {
                        player.spawnParticle(particle, center.clone().add(offset), countPerPoint,
                                pointOffset, pointOffset, pointOffset, speed);
                    } catch (IllegalArgumentException ignored) {
                        cancel();
                        return;
                    }
                }

                if (tick >= ticks - 1) {
                    cancel();
                    return;
                }
                tick++;
            }

            private void cancel() {
                if (taskRef[0] != null) {
                    taskRef[0].cancel();
                }
            }
        }, 0L, interval);
    }

    private Location effectLocation(Player player, String path) {
        String anchor = settings.getString(path + ".anchor", "EYE");
        Location location = "FEET".equalsIgnoreCase(anchor) ? player.getLocation() : player.getEyeLocation();
        return location.add(
                settings.getDouble(path + ".location-x-offset", 0.0D),
                settings.getDouble(path + ".location-y-offset", 0.0D),
                settings.getDouble(path + ".location-z-offset", 0.0D));
    }

    private void removeAllDisplays() {
        for (UUID uuid : new HashSet<>(displays.keySet())) {
            removeDisplay(uuid, false);
        }
        displays.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        enabledPlayers.remove(uuid);
        removeDisplay(uuid, false);
    }

    private String buildText(Player viewer, Player target) {
        List<String> lines = settings.getStringList("thauthi.lines");
        if (lines.isEmpty()) {
            lines = List.of(
                    "&6&lThấu Thị &8» &f{target}",
                    "&7Cảnh giới: &e{realm_full}",
                    "&7Level: &a{level} &8| &7Tu Vi: &b{tuvi_compact}");
        }

        List<String> rendered = new ArrayList<>();
        for (String line : lines) {
            rendered.add(colorize(applyPlaceholders(viewer, target, line)));
        }
        return String.join("\n", rendered);
    }

    private String applyPlaceholders(Player viewer, Player target, String line) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            line = PlaceholderAPI.setPlaceholders(target, line);
        }

        UUID targetId = target.getUniqueId();
        double tuVi = TuTien.getApi().getTuVi(targetId);
        PlayerRealm playerRealm = realmManager.getPlayerRealm(targetId);
        Realm realm = realmManager.getPlayerCurrentRealm(targetId);
        long nextTuVi = getNextTuViRequired(targetId, playerRealm, realm);
        double health = Math.max(0.0D, target.getHealth());
        double maxHealth = Math.max(0.0D, target.getMaxHealth());
        String level = getTuTienLevel(target);

        return line
                .replace("{viewer}", viewer.getName())
                .replace("{player}", target.getName())
                .replace("{target}", target.getName())
                .replace("{display_name}", target.getDisplayName())
                .replace("{health}", formatDecimal(health))
                .replace("{health_int}", String.valueOf(Math.round(health)))
                .replace("{max_health}", formatDecimal(maxHealth))
                .replace("{max_health_int}", String.valueOf(Math.round(maxHealth)))
                .replace("{tuvi}", String.valueOf(tuVi))
                .replace("{tuvi_int}", String.valueOf((long) tuVi))
                .replace("{tuvi_formatted}", INTEGER_FORMAT.format((long) tuVi))
                .replace("{tuvi_compact}", RealmManager.formatNumber((long) tuVi))
                .replace("{next_tuvi}", String.valueOf(nextTuVi))
                .replace("{next_tuvi_int}", String.valueOf(nextTuVi))
                .replace("{next_tuvi_formatted}", INTEGER_FORMAT.format(nextTuVi))
                .replace("{next_tuvi_compact}", RealmManager.formatNumber(nextTuVi))
                .replace("{realm}", realmManager.getPlayerRealmName(targetId))
                .replace("{realm_full}", realmManager.getPlayerDisplayName(targetId))
                .replace("{sub_realm}", realmManager.getPlayerSubRealmName(targetId))
                .replace("{realm_tier}", realm != null ? realm.getTier().getDisplayName() : "Pham Gioi")
                .replace("{level}", level)
                .replace("{world}", target.getWorld().getName());
    }

    private long getNextTuViRequired(UUID uuid, PlayerRealm playerRealm, Realm currentRealm) {
        if (playerRealm == null || currentRealm == null) {
            return 0L;
        }

        SubRealm currentSubRealm = playerRealm.getSubRealm();
        if (currentSubRealm != SubRealm.VIEN_MAN) {
            SubRealm nextSubRealm = currentSubRealm.next();
            return nextSubRealm != null ? currentRealm.getTuViForSubRealm(nextSubRealm) : 0L;
        }

        Realm nextRealm = realmManager.getNextRealm(uuid);
        return nextRealm != null ? nextRealm.getTuViRequired() : 0L;
    }

    private String getTuTienLevel(Player target) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TuTienLevel")) {
            return settings.getString("thauthi.level-unavailable", "N/A");
        }

        try {
            Class<?> apiClass = Class.forName("com.turtle.tutienlevel.api.TuTienLevelAPI");
            @SuppressWarnings({ "rawtypes", "unchecked" })
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) apiClass);
            if (registration == null || registration.getProvider() == null) {
                return placeholderLevel(target);
            }
            Method method = apiClass.getMethod("getLevel", Player.class);
            Object value = method.invoke(registration.getProvider(), target);
            if (value instanceof Number number) {
                return String.valueOf(number.intValue());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return placeholderLevel(target);
        }

        return settings.getString("thauthi.level-unavailable", "N/A");
    }

    private String placeholderLevel(Player target) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return settings.getString("thauthi.level-unavailable", "N/A");
        }
        String parsed = PlaceholderAPI.setPlaceholders(target, "%tutienlevel_level%");
        return parsed == null || parsed.isBlank() || parsed.startsWith("%")
                ? settings.getString("thauthi.level-unavailable", "N/A")
                : parsed;
    }

    private String permission() {
        return settings.getString("thauthi.permission", "tutiencore.thauthi");
    }

    private String message(String key, String fallback) {
        return settings.getString("thauthi.messages." + key, fallback);
    }

    private static String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static int clampDuration(int value) {
        return Math.max(0, Math.min(59, value));
    }

    private static byte parseOpacity(int value) {
        return (byte) Math.max(0, Math.min(255, value));
    }

    private Color backgroundColor() {
        return Color.fromARGB(
                clampColor(settings.getInt("thauthi.background-color.a", 0)),
                clampColor(settings.getInt("thauthi.background-color.r", 0)),
                clampColor(settings.getInt("thauthi.background-color.g", 0)),
                clampColor(settings.getInt("thauthi.background-color.b", 0)));
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void setOptional(Object target, String methodName, Class<?> parameterType, Object value) {
        try {
            target.getClass().getMethod(methodName, parameterType).invoke(target, value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static SoundCategory soundCategory(String name) {
        if (name != null) {
            try {
                return SoundCategory.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return SoundCategory.MASTER;
    }

    private static Particle particle(String name) {
        if (name != null) {
            try {
                return Particle.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', translateHexColors(text == null ? "" : text));
    }

    private static String translateHexColors(String text) {
        return translateHexPattern(translateHexPattern(text, MINI_HEX_PATTERN), HEX_PATTERN);
    }

    private static String translateHexPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char character : hex.toCharArray()) {
                replacement.append('&').append(character);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static final class DisplayState {
        private final TextDisplay display;
        private UUID targetUuid;
        private BukkitTask removeTask;
        private BukkitTask fadeInTask;
        private boolean removing;
        private boolean animatingIn;

        private DisplayState(TextDisplay display) {
            this.display = display;
        }

        private void cancelRemoval() {
            if (removeTask != null) {
                removeTask.cancel();
                removeTask = null;
            }
        }

        private void cancelFadeIn() {
            if (fadeInTask != null) {
                fadeInTask.cancel();
                fadeInTask = null;
            }
            animatingIn = false;
        }
    }
}
