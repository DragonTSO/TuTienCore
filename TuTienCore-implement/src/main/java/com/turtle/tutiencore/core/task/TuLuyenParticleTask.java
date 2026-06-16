package com.turtle.tutiencore.core.task;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.manager.RealmManager;
import com.turtle.tutiencore.core.manager.TuLuyenManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.data.ParticleData;
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleType;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Tu Luyện Particle Effect System
 * 
 * Creates cultivation meditation visual effects with realm-based colors.
 * Colors are determined by the player's current Cảnh Giới.
 */
public class TuLuyenParticleTask {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private TuLuyenManager tuLuyenManager;
    private RealmManager realmManager;
    private final Random random = new Random();
    private BukkitRunnable auraTask;

    // Animation state
    private double globalTick = 0;

    // Viewers (as PacketEvents Users) within range of the player currently being processed by the
    // aura task. Particles are streamed as clientbound packets instead of World.spawnParticle, which
    // (a) is the requested packet-based approach and (b) is safe to call from the async aura task,
    // unlike Bukkit's World.spawnParticle. The aura task is single-threaded, so a single field is fine.
    private List<User> currentViewers = java.util.Collections.emptyList();

    // Default cyan color (fallback when no realm color)
    private static final int[][] DEFAULT_COLORS = {{0, 220, 255}, {100, 255, 230}};

    // Cache player realm colors (refreshed when they start tu luyen)
    private final Map<UUID, int[][]> playerColorCache = new HashMap<>();

    public TuLuyenParticleTask(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void setTuLuyenManager(TuLuyenManager tuLuyenManager) {
        this.tuLuyenManager = tuLuyenManager;
    }

    public void setRealmManager(RealmManager realmManager) {
        this.realmManager = realmManager;
    }

    /**
     * Get the color palette for a player based on their current realm.
     * Returns [primary RGB, secondary RGB].
     */
    public int[][] getPlayerColors(Player player) {
        UUID uuid = player.getUniqueId();
        int[][] cached = playerColorCache.get(uuid);
        if (cached != null) return cached;

        // Fallback: if not cached (shouldn't happen if refreshPlayerColors was called)
        // Return default because the aura task runs async.
        plugin.getLogger().warning("[RealmColor] Cache miss for " + player.getName() + " - using default colors (async context)");
        return DEFAULT_COLORS;
    }

    /**
     * Resolve colors from the player's current realm.
     * Called once when player starts tu luyen, cached afterward.
     */
    private int[][] resolveRealmColors(Player player) {
        if (realmManager == null) {
            plugin.getLogger().warning("[RealmColor] RealmManager not ready - default colors for " + player.getName());
            return DEFAULT_COLORS;
        }

        try {
            Object data = resolveMMOCorePlayerData(player);
            if (data != null) {
                Object profess = readProfess(data);
                if (profess != null) {
                    Map<String, int[][]> classColors = configManager.getClassColors();
                    
                    String rawId = readProfessValue(profess, "getId");
                    String className = readProfessValue(profess, "getName");
                    
                    // Possible keys to try in order
                    String[] keysToTry = {
                        rawId.toUpperCase(),                                      // Exact ID (KIEMTON)
                        rawId.toUpperCase().replace("-", "_"),                    // ID with underscores (KIEM_TON)
                        className.toUpperCase().replace(" ", "_"),                // Name with underscores (KIEM_TIEN)
                        className.toUpperCase().replace(" ", ""),                 // Name compressed (KIEMTIEN)
                        rawId.toUpperCase().replace("_", "")                      // ID compressed (KIEMTON)
                    };

                    plugin.getLogger().info("[ClassColor] Resolving for " + player.getName() 
                            + " | MMOCore ID: '" + rawId + "', Name: '" + className + "'");
                    
                    for (String key : keysToTry) {
                        int[][] mapped = classColors.get(key);
                        if (mapped != null) {
                            plugin.getLogger().info("[ClassColor] Matched key: '" + key + "'");
                            return mapped;
                        }
                    }
                    
                    plugin.getLogger().warning("[ClassColor] NO MATCH in config for player " + player.getName() 
                            + ". Tried: " + java.util.Arrays.toString(keysToTry) 
                            + ". Config keys: " + classColors.keySet());
                } else {
                    plugin.getLogger().info("[ClassColor] " + player.getName() + " has no MMOCore class.");
                }
            }

            com.turtle.tutiencore.api.realm.Realm realm = realmManager.getPlayerCurrentRealm(player.getUniqueId());
            if (realm == null) {
                plugin.getLogger().warning("[RealmColor] No realm found for " + player.getName() + " - using default colors");
                return DEFAULT_COLORS;
            }

            int[] primary = toRgb(realm.getColor());
            int[] secondary = brighten(primary);
            plugin.getLogger().info("[RealmColor] Resolved " + player.getName()
                    + " realm=" + realm.getId() + " color=" + realm.getColor());
            return new int[][]{primary, secondary};
        } catch (Throwable t) {
            plugin.getLogger().warning("[RealmColor] Error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        return DEFAULT_COLORS;
    }

    private Object resolveMMOCorePlayerData(Player player) {
        try {
            Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            Method method = playerDataClass.getMethod("get", Player.class);
            return method.invoke(null, player);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Object readProfess(Object data) {
        try {
            Method method = data.getClass().getMethod("getProfess");
            return method.invoke(data);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String readProfessValue(Object profess, String methodName) {
        try {
            Method method = profess.getClass().getMethod(methodName);
            Object value = method.invoke(profess);
            return value != null ? String.valueOf(value) : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private int[] toRgb(String colorCode) {
        ChatColor chatColor = ChatColor.getByChar(extractLastColorChar(colorCode));
        if (chatColor == null) return DEFAULT_COLORS[0];

        switch (chatColor) {
            case BLACK: return new int[]{0, 0, 0};
            case DARK_BLUE: return new int[]{0, 0, 170};
            case DARK_GREEN: return new int[]{0, 170, 0};
            case DARK_AQUA: return new int[]{0, 170, 170};
            case DARK_RED: return new int[]{170, 0, 0};
            case DARK_PURPLE: return new int[]{170, 0, 170};
            case GOLD: return new int[]{255, 170, 0};
            case GRAY: return new int[]{170, 170, 170};
            case DARK_GRAY: return new int[]{85, 85, 85};
            case BLUE: return new int[]{85, 85, 255};
            case GREEN: return new int[]{85, 255, 85};
            case AQUA: return new int[]{85, 255, 255};
            case RED: return new int[]{255, 85, 85};
            case LIGHT_PURPLE: return new int[]{255, 85, 255};
            case YELLOW: return new int[]{255, 255, 85};
            case WHITE: return new int[]{255, 255, 255};
            default: return DEFAULT_COLORS[0];
        }
    }

    private char extractLastColorChar(String colorCode) {
        if (colorCode == null || colorCode.isEmpty()) return 'b';

        for (int i = colorCode.length() - 2; i >= 0; i--) {
            char marker = colorCode.charAt(i);
            if ((marker == '&' || marker == ChatColor.COLOR_CHAR) && i + 1 < colorCode.length()) {
                return colorCode.charAt(i + 1);
            }
        }
        return colorCode.length() == 1 ? colorCode.charAt(0) : 'b';
    }

    private int[] brighten(int[] rgb) {
        return new int[]{
            Math.min(255, rgb[0] + 70),
            Math.min(255, rgb[1] + 70),
            Math.min(255, rgb[2] + 70)
        };
    }

    /**
     * Refresh color cache for a player (call when they start tu luyen).
     * MUST be called from the MAIN THREAD - resolves realm data immediately.
     */
    public void refreshPlayerColors(Player player) {
        int[][] colors = resolveRealmColors(player);
        playerColorCache.put(player.getUniqueId(), colors);
        plugin.getLogger().info("[RealmColor] Cached colors for " + player.getName()
                + ": primary=[" + colors[0][0] + "," + colors[0][1] + "," + colors[0][2] + "]"
                + " secondary=[" + colors[1][0] + "," + colors[1][1] + "," + colors[1][2] + "]");
    }

    /**
     * Clear color cache for a player (call when they stop tu luyen)
     */
    public void clearPlayerColors(UUID uuid) {
        playerColorCache.remove(uuid);
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
        auraTask.runTaskTimerAsynchronously(plugin, 0L, 2L); // Every 2 ticks (was 1, reduced for performance)
    }

    /**
     * Main effect orchestrator - spawns all cultivation visual effects.
     * Skips entirely when no viewer is nearby so we never build particle geometry no one can see.
     */
    private void spawnCultivationEffect(Player player) {
        Location base = player.getLocation();
        World world = player.getWorld();

        // Resolve viewers once per player per tick. Skip all geometry when nobody can see it.
        currentViewers = resolveNearbyViewers(world, base);
        if (currentViewers.isEmpty()) {
            return;
        }

        int[][] colors = getPlayerColors(player);

        // === LAYER 1: Double Helix Energy Spiral (every 2 ticks now) ===
        if (configManager.isCultHelixEnabled()) {
            spawnDoubleHelix(world, base, colors);
        }

        // === LAYER 2: Energy Burst Rays ===
        if (configManager.isCultRaysEnabled() && (int) globalTick % configManager.getCultRayInterval() == 0) {
            spawnEnergyBurstRays(world, base, colors);
        }

        // === LAYER 3: Lightning/Electric Arcs (every 3 ticks — more frequent for visibility) ===
        if (configManager.isCultLightningEnabled() && (int) globalTick % 3 == 0) {
            spawnLightningArcs(world, base, colors);
        }

        // === LAYER 4: Inward Absorption Particles (every 4 ticks — reduced from 2) ===
        if (configManager.isCultAbsorptionEnabled() && (int) globalTick % 2 == 0) {
            spawnAbsorptionParticles(world, base, colors);
        }

        // === LAYER 5: Ground Runic Circle (every 6 ticks — reduced from 4) ===
        if (configManager.isCultGroundCircleEnabled() && (int) globalTick % 3 == 0) {
            spawnGroundCircle(world, base, colors);
        }

        // === LAYER 6: Vertical Energy Pillar (every 10 ticks — reduced from 6) ===
        if (configManager.isCultPillarEnabled() && (int) globalTick % 5 == 0) {
            spawnEnergyPillar(world, base, colors);
        }

        // === LAYER 7: Ambient Floating Particles (every 6 ticks — reduced from 3) ===
        if (configManager.isCultAmbientEnabled() && (int) globalTick % 3 == 0) {
            spawnAmbientFloatingParticles(world, base, colors);
        }
    }

    /**
     * LAYER 1: Double Helix — Two intertwined spiral arms
     */
    private void spawnDoubleHelix(World world, Location base, int[][] colors) {
        double speed = globalTick * 0.15;
        double heightRange = 2.5;

        for (int arm = 0; arm < 2; arm++) {
            double armOffset = arm * Math.PI;

            // Reduced from 8 to 5 points per arm
            for (int i = 0; i < 5; i++) {
                double t = speed + (i * 0.35) + armOffset;
                double radius = 1.0 + Math.sin(globalTick * 0.05 + i * 0.3) * 0.3;
                double x = radius * Math.cos(t);
                double z = radius * Math.sin(t);
                double y = (((globalTick * 0.08 + i * 0.15) % heightRange));

                Location loc = base.clone().add(x, y, z);

                // Primary color
                spawnColoredDust(world, loc, colors[0][0], colors[0][1], colors[0][2], 1.2f);

                // Secondary glow trail (every other point)
                if (i % 2 == 0) {
                    spawnColoredDust(world, loc, colors[1][0], colors[1][1], colors[1][2], 0.8f);
                }
            }
        }
    }

    /**
     * LAYER 2: Energy Inward Rays — Tia năng lượng hấp thụ từ ngoài vào tâm
     */
    private void spawnEnergyBurstRays(World world, Location base, int[][] colors) {
        Location chest = base.clone().add(0, configManager.getCultRayTargetYOffset(), 0);

        int minCount = configManager.getCultRayCountMin();
        int maxCount = configManager.getCultRayCountMax();
        int rayCount = minCount + random.nextInt(maxCount - minCount + 1);

        double minDistance = configManager.getCultRayDistanceMin();
        double maxDistance = configManager.getCultRayDistanceMax();
        double minY = configManager.getCultRayYMin();
        double maxY = configManager.getCultRayYMax();

        double circleRadius = configManager.getCultRayCircleRadius();
        int circlePoints = configManager.getCultRayCirclePoints();
        float circleSize = configManager.getCultRayCircleSize();
        double circleRotation = globalTick * configManager.getCultRayCircleRotationSpeed();

        int rayPoints = configManager.getCultRayPoints();
        float rayStartSize = configManager.getCultRayStartSize();
        float rayEndSize = configManager.getCultRayEndSize();
        double spiralRadius = configManager.getCultRaySpiralRadius();
        boolean endRodTrail = configManager.isCultRayEndRodTrail();

        for (int i = 0; i < rayCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            double y = minY + random.nextDouble() * (maxY - minY);
            Location gateCenter = base.clone().add(
                    Math.cos(angle) * distance,
                    y,
                    Math.sin(angle) * distance
            );

            Vector inward = chest.toVector().subtract(gateCenter.toVector());
            if (inward.lengthSquared() < 0.0001D) {
                continue;
            }
            inward.normalize();

            Vector up = new Vector(0, 1, 0);
            if (Math.abs(inward.dot(up)) > 0.96D) {
                up = new Vector(1, 0, 0);
            }
            Vector right = inward.clone().crossProduct(up).normalize();
            Vector ringUp = right.clone().crossProduct(inward).normalize();

            spawnSpiritGateCircle(world, gateCenter, right, ringUp, circleRadius, circlePoints,
                    circleRotation + i, circleSize, colors);
            spawnGateRay(world, gateCenter, chest, right, ringUp, inward, rayPoints,
                    rayStartSize, rayEndSize, spiralRadius, endRodTrail, colors);
        }
    }

    private void spawnSpiritGateCircle(World world, Location center, Vector right, Vector up,
                                       double radius, int points, double rotation,
                                       float size, int[][] colors) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i / points) + rotation;
            Vector offset = right.clone().multiply(Math.cos(angle) * radius)
                    .add(up.clone().multiply(Math.sin(angle) * radius));
            Location loc = center.clone().add(offset);

            int[] color = i % 2 == 0 ? colors[1] : colors[0];
            spawnColoredDust(world, loc, color[0], color[1], color[2], size);
        }
    }

    private void spawnGateRay(World world, Location from, Location to, Vector right, Vector up, Vector inward,
                              int points, float startSize, float endSize, double spiralRadius,
                              boolean endRodTrail, int[][] colors) {
        for (int i = 0; i <= points; i++) {
            double progress = i / (double) points;
            Location loc = new Location(world,
                    from.getX() + (to.getX() - from.getX()) * progress,
                    from.getY() + (to.getY() - from.getY()) * progress,
                    from.getZ() + (to.getZ() - from.getZ()) * progress
            );

            if (spiralRadius > 0.0D) {
                double spiralAngle = globalTick * 0.45D + i * 0.9D;
                double fadeOut = 1.0D - progress;
                Vector spiral = right.clone().multiply(Math.cos(spiralAngle) * spiralRadius * fadeOut)
                        .add(up.clone().multiply(Math.sin(spiralAngle) * spiralRadius * fadeOut));
                loc.add(spiral);
            }

            float glow = (float) (0.35D + progress * 0.65D);
            int r = clampColor(colors[0][0] * glow + colors[1][0] * (1.0D - glow) * 0.35D);
            int g = clampColor(colors[0][1] * glow + colors[1][1] * (1.0D - glow) * 0.35D);
            int b = clampColor(colors[0][2] * glow + colors[1][2] * (1.0D - glow) * 0.35D);
            float size = startSize + (endSize - startSize) * (float) progress;
            spawnColoredDust(world, loc, r, g, b, size);

            if (endRodTrail && i % 3 == 0) {
                spawnDirectionalParticle(ParticleTypes.END_ROD, loc,
                        inward.getX(), inward.getY(), inward.getZ(), (float) (0.08D + progress * 0.08D), 0);
            }
        }
    }

    /**
     * LAYER 3: Lightning/Electric Arcs — sét điện quanh người
     */
    private void spawnLightningArcs(World world, Location base, int[][] colors) {
        int arcCount = 2 + random.nextInt(3); // 2-4 arcs for more visibility

        for (int arc = 0; arc < arcCount; arc++) {
            double startAngle = random.nextDouble() * Math.PI * 2;
            double startY = 0.3 + random.nextDouble() * 2.0;
            double startRadius = 0.8 + random.nextDouble() * 1.0;

            Location start = base.clone().add(
                    startRadius * Math.cos(startAngle), startY,
                    startRadius * Math.sin(startAngle));

            double endAngle = startAngle + (random.nextDouble() - 0.5) * Math.PI;
            double endY = startY + (random.nextDouble() - 0.5) * 1.8;
            double endRadius = startRadius + (random.nextDouble() - 0.5) * 0.8;

            Location end = base.clone().add(
                    endRadius * Math.cos(endAngle), Math.max(0.1, endY),
                    endRadius * Math.sin(endAngle));

            int segments = 6 + random.nextInt(4); // 6-9 segments for longer arcs
            Location prev = start.clone();

            for (int i = 1; i <= segments; i++) {
                double progress = i / (double) segments;
                double jitterX = (random.nextDouble() - 0.5) * 0.4;
                double jitterY = (random.nextDouble() - 0.5) * 0.4;
                double jitterZ = (random.nextDouble() - 0.5) * 0.4;

                Location point = new Location(world,
                        start.getX() + (end.getX() - start.getX()) * progress + jitterX,
                        start.getY() + (end.getY() - start.getY()) * progress + jitterY,
                        start.getZ() + (end.getZ() - start.getZ()) * progress + jitterZ);

                double dist = prev.distance(point);
                int linePoints = Math.max(3, (int) (dist * 6)); // Denser line
                for (int j = 0; j < linePoints; j++) {
                    double lt = j / (double) linePoints;
                    Location lineLoc = new Location(world,
                            prev.getX() + (point.getX() - prev.getX()) * lt,
                            prev.getY() + (point.getY() - prev.getY()) * lt,
                            prev.getZ() + (point.getZ() - prev.getZ()) * lt);

                    // Core: pure primary color, big dust for bold arcs
                    spawnColoredDust(world, lineLoc, colors[0][0], colors[0][1], colors[0][2], 1.0f);
                    // Glow: white-tinted overlay for brightness
                    if (j % 2 == 0) {
                        int r = Math.min(255, colors[0][0] + 120);
                        int g = Math.min(255, colors[0][1] + 120);
                        int b = Math.min(255, colors[0][2] + 120);
                        spawnColoredDust(world, lineLoc, r, g, b, 0.6f);
                    }
                }
                prev = point;
            }
        }
    }

    /**
     * LAYER 4: Absorption Particles — Linh khí bị hấp thụ vào người
     */
    private void spawnAbsorptionParticles(World world, Location base, int[][] colors) {
        Location chest = base.clone().add(0, 1.0, 0);

        for (int i = 0; i < 3; i++) { // Reduced from 4 to 3
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 2.0 + random.nextDouble() * 3.0;
            double y = random.nextDouble() * 3.0;

            Location outerLoc = base.clone().add(
                    radius * Math.cos(angle), y, radius * Math.sin(angle));

            Vector dir = chest.toVector().subtract(outerLoc.toVector()).normalize();

            spawnDirectionalParticle(ParticleTypes.END_ROD, outerLoc,
                    (float) dir.getX(), (float) dir.getY(), (float) dir.getZ(), 0.12f, 0);

            spawnColoredDust(world, outerLoc, colors[1][0], colors[1][1], colors[1][2], 1.0f);
        }
    }

    /**
     * LAYER 5: Ground Runic Circle — Vòng tròn pháp trận dưới chân
     */
    private void spawnGroundCircle(World world, Location base, int[][] colors) {
        double radius = 1.8;
        double rotation = globalTick * 0.08;
        int points = 16; // Reduced from 24

        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i / points) + rotation;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location loc = base.clone().add(x, 0.05, z);

            if (i % 2 == 0) {
                spawnColoredDust(world, loc, colors[0][0], colors[0][1], colors[0][2], 0.8f);
            } else {
                spawnColoredDust(world, loc, colors[1][0], colors[1][1], colors[1][2], 0.6f);
            }
        }

        // Inner circle — reduced from 12 to 8 points
        double innerRadius = 0.9;
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI * i / 8) - rotation * 1.5;
            double x = innerRadius * Math.cos(angle);
            double z = innerRadius * Math.sin(angle);
            Location loc = base.clone().add(x, 0.05, z);

            // Lighter tint of primary
            int r = Math.min(255, colors[0][0] + 80);
            int g = Math.min(255, colors[0][1] + 80);
            int b = Math.min(255, colors[0][2] + 80);
            spawnColoredDust(world, loc, r, g, b, 0.5f);
        }
    }

    /**
     * LAYER 6: Vertical Energy Pillar — Cột năng lượng chiếu lên trời
     */
    private void spawnEnergyPillar(World world, Location base, int[][] colors) {
        double pillarHeight = 3.0 + Math.sin(globalTick * 0.03) * 1.0; // Slightly shorter
        int points = (int) (pillarHeight * 2); // Reduced density from *3 to *2

        for (int i = 0; i < points; i++) {
            double y = (i / (double) points) * pillarHeight;
            double wobble = Math.sin(globalTick * 0.1 + y * 2) * 0.1;
            Location loc = base.clone().add(wobble, y, wobble);

            float intensity = 1.0f - (float) (y / pillarHeight) * 0.8f;
            int r = (int) (colors[0][0] * intensity);
            int g = (int) (colors[0][1] * intensity);
            int b = (int) (colors[0][2] * intensity);

            spawnColoredDust(world, loc, r, g, b, intensity * 0.7f);
        }
    }

    /**
     * LAYER 7: Ambient Floating Particles — Hạt năng lượng bay lơ lửng
     */
    private void spawnAmbientFloatingParticles(World world, Location base, int[][] colors) {
        for (int i = 0; i < 2; i++) { // Reduced from 3 to 2
            double x = (random.nextDouble() - 0.5) * 4;
            double y = random.nextDouble() * 3;
            double z = (random.nextDouble() - 0.5) * 4;

            Location loc = base.clone().add(x, y, z);

            spawnDirectionalParticle(ParticleTypes.END_ROD, loc, 0f, 0.02f, 0f, 0.01f, 0);

            // Enchantment particles
            spawnDirectionalParticle(ParticleTypes.ENCHANT, base.clone().add(0, 1, 0),
                    1.5f, 1.0f, 1.5f, 0.5f, 2);
        }
    }

    /**
     * Collect the PacketEvents {@link User}s within the configured view distance of the effect
     * center. Particles are streamed only to these viewers, so we never build geometry nobody sees
     * and never touch a Bukkit world object from the async aura thread.
     */
    private List<User> resolveNearbyViewers(World world, Location center) {
        double maxDistanceSquared = configManager.getCultViewDistanceSquared();
        List<User> viewers = new ArrayList<>();
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(center) > maxDistanceSquared) {
                continue;
            }
            User user = resolveUser(viewer);
            if (user != null) {
                viewers.add(user);
            }
        }
        return viewers;
    }

    private User resolveUser(Player player) {
        try {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            return (user != null && user.getChannel() != null) ? user : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Utility: stream a colored dust particle to the current viewers as a clientbound packet.
     */
    private void spawnColoredDust(World world, Location loc, int r, int g, int b, float size) {
        ParticleDustData dust = new ParticleDustData(size,
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b)));
        emit(new Particle<>(ParticleTypes.DUST, dust), loc, 0f, 0f, 0f, 0f, 1);
    }

    /**
     * Streams a non-dust particle (END_ROD, ENCHANT, ...) to the current viewers. {@code dx/dy/dz}
     * are the packet offset/direction and {@code maxSpeed} the particle speed, matching the old
     * {@code World.spawnParticle(type, loc, count, dx, dy, dz, speed)} call shape.
     */
    private void spawnDirectionalParticle(ParticleType<ParticleData> type, Location loc,
                                          double dx, double dy, double dz, float maxSpeed, int count) {
        emit(new Particle<>(type), loc, (float) dx, (float) dy, (float) dz, maxSpeed, count);
    }

    /**
     * Streams a single particle packet to every nearby viewer resolved for the player currently
     * being processed. Safe to call from the async aura task since it only builds and sends packets.
     */
    private void emit(Particle<?> particle, Location loc, float offsetX, float offsetY, float offsetZ,
                      float maxSpeed, int count) {
        List<User> viewers = currentViewers;
        if (viewers.isEmpty()) {
            return;
        }
        Vector3d position = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        Vector3f offset = new Vector3f(offsetX, offsetY, offsetZ);
        for (User user : viewers) {
            try {
                user.sendPacket(new WrapperPlayServerParticle(particle, true, position, offset, maxSpeed, count));
            } catch (RuntimeException ignored) {
                // A single viewer's channel may have closed mid-tick; keep streaming to the rest.
            }
        }
    }

    private int clampColor(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    public void stopAuraTask() {
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
    }
}
