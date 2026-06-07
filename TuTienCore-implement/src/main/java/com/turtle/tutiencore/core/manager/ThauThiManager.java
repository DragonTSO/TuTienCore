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
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
import org.bukkit.util.Vector;

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
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DisplayState> displays = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long tickCounter;

    public ThauThiManager(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        start();
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

        if (!plugin.getConfig().getBoolean("thauthi.enabled", true)) {
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
            player.sendMessage(colorize(message("off", "&cĐã tắt Thấu Thị.")));
        } else {
            enabledPlayers.add(uuid);
            player.sendMessage(colorize(message("on", "&aĐã bật Thấu Thị.")));
        }
        return true;
    }

    private void tick() {
        if (enabledPlayers.isEmpty()) {
            return;
        }

        tickCounter++;
        int updateInterval = Math.max(1, plugin.getConfig().getInt("thauthi.update-interval-ticks", 2));
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

            if (plugin.getConfig().getBoolean("thauthi.require-permission-each-tick", true)
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
        double distance = Math.max(1.0D, plugin.getConfig().getDouble("thauthi.max-distance", 18.0D));
        double raySize = Math.max(0.0D, plugin.getConfig().getDouble("thauthi.ray-size", 0.35D));
        boolean ignoreSpectator = plugin.getConfig().getBoolean("thauthi.ignore-spectator", true);

        RayTraceResult result = world.rayTraceEntities(eye, direction, distance, raySize, entity -> {
            if (!(entity instanceof Player target) || target == viewer || target.isDead() || !viewer.canSee(target)) {
                return false;
            }
            return !ignoreSpectator || target.getGameMode() != GameMode.SPECTATOR;
        });

        if (result == null || !(result.getHitEntity() instanceof Player target)) {
            return null;
        }

        if (plugin.getConfig().getBoolean("thauthi.require-line-of-sight", true) && !viewer.hasLineOfSight(target)) {
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
            state = new DisplayState(spawnDisplay(viewer, location, text));
            displays.put(viewerId, state);
        }

        state.cancelRemoval();
        state.removing = false;
        state.targetUuid = target.getUniqueId();

        TextDisplay display = state.display;
        display.setText(text);
        display.setTeleportDuration(clampDuration(plugin.getConfig().getInt("thauthi.teleport-duration", 3)));
        display.setInterpolationDuration(clampDuration(plugin.getConfig().getInt("thauthi.interpolation-duration", 3)));
        setOptional(display, "setInterpolationDelay", int.class,
                Math.max(0, plugin.getConfig().getInt("thauthi.interpolation-delay", 0)));
        display.teleport(location);
        display.setTextOpacity(parseOpacity(plugin.getConfig().getInt("thauthi.opacity", 230)));
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
            hologram.setShadowed(plugin.getConfig().getBoolean("thauthi.shadow", true));
            hologram.setSeeThrough(plugin.getConfig().getBoolean("thauthi.see-through", false));
            hologram.setLineWidth(Math.max(1, plugin.getConfig().getInt("thauthi.line-width", 190)));
            hologram.setTeleportDuration(clampDuration(plugin.getConfig().getInt("thauthi.teleport-duration", 3)));
            hologram.setInterpolationDuration(clampDuration(plugin.getConfig().getInt("thauthi.interpolation-duration", 3)));
            setOptional(hologram, "setViewRange", float.class,
                    (float) Math.max(1.0D, plugin.getConfig().getDouble("thauthi.view-range", 24.0D)));
            setOptional(hologram, "setDefaultBackground", boolean.class,
                    plugin.getConfig().getBoolean("thauthi.default-background", false));
            setOptional(hologram, "setBackgroundColor", Color.class, backgroundColor());
        });
        viewer.showEntity(plugin, display);
        return display;
    }

    private Location targetLocation(Player viewer, Player target) {
        double yOffset = plugin.getConfig().getDouble("thauthi.y-offset", 0.75D);
        double xOffset = plugin.getConfig().getDouble("thauthi.x-offset", -0.95D);
        double zOffset = plugin.getConfig().getDouble("thauthi.z-offset", 0.0D);
        Location location = target.getLocation().add(0.0D, target.getHeight() + yOffset, 0.0D);

        if (!plugin.getConfig().getBoolean("thauthi.offset-relative-to-viewer", true)) {
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

    private void removeDisplay(UUID viewerId, boolean fade) {
        DisplayState state = displays.get(viewerId);
        if (state == null || state.display == null) {
            return;
        }

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
        display.setInterpolationDuration(clampDuration(plugin.getConfig().getInt("thauthi.fade-out-interpolation-duration", 4)));
        display.setTextOpacity(parseOpacity(plugin.getConfig().getInt("thauthi.fade-out-opacity", 0)));
        long delay = Math.max(1L, plugin.getConfig().getLong("thauthi.fade-out-ticks", 8L));
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
        List<String> lines = plugin.getConfig().getStringList("thauthi.lines");
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
            return plugin.getConfig().getString("thauthi.level-unavailable", "N/A");
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

        return plugin.getConfig().getString("thauthi.level-unavailable", "N/A");
    }

    private String placeholderLevel(Player target) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return plugin.getConfig().getString("thauthi.level-unavailable", "N/A");
        }
        String parsed = PlaceholderAPI.setPlaceholders(target, "%tutienlevel_level%");
        return parsed == null || parsed.isBlank() || parsed.startsWith("%")
                ? plugin.getConfig().getString("thauthi.level-unavailable", "N/A")
                : parsed;
    }

    private String permission() {
        return plugin.getConfig().getString("thauthi.permission", "tutiencore.thauthi");
    }

    private String message(String key, String fallback) {
        return plugin.getConfig().getString("thauthi.messages." + key, fallback);
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
                clampColor(plugin.getConfig().getInt("thauthi.background-color.a", 0)),
                clampColor(plugin.getConfig().getInt("thauthi.background-color.r", 0)),
                clampColor(plugin.getConfig().getInt("thauthi.background-color.g", 0)),
                clampColor(plugin.getConfig().getInt("thauthi.background-color.b", 0)));
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
        private boolean removing;

        private DisplayState(TextDisplay display) {
            this.display = display;
        }

        private void cancelRemoval() {
            if (removeTask != null) {
                removeTask.cancel();
                removeTask = null;
            }
        }
    }
}
