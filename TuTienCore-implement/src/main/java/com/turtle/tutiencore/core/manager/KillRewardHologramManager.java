package com.turtle.tutiencore.core.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kill-reward "+money" popup, rendered as a <b>packet-based</b> TEXT_DISPLAY instead of a real
 * Bukkit entity.
 *
 * <p>The previous implementation called {@code World.spawn(TextDisplay)} per kill. Spark showed
 * the cost was almost entirely Minecraft's own entity machinery — {@code CraftRegionAccessor.spawn}
 * → {@code ServerLevel.addEntity} → chunk-system entity tracking — not the plugin code. On a mob
 * farm those add up.
 *
 * <p>Here we never create a server-side entity. We allocate a fake entity id, send a SPAWN_ENTITY +
 * ENTITY_METADATA packet to the nearby players only, animate it (rise + fade) by streaming
 * ENTITY_TELEPORT + ENTITY_METADATA packets, and DESTROY_ENTITIES at the end. Nothing touches the
 * world entity list, chunk tracker, or the main-thread tick beyond building packets — and the
 * animation loop runs async since packet sends are thread-safe.
 */
public final class KillRewardHologramManager {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,###");

    // Fake entity ids for client-only displays. Started high so they never collide with the
    // server's own incrementally-assigned (low, positive) entity ids within a session.
    private static final AtomicInteger ENTITY_ID = new AtomicInteger(Integer.MAX_VALUE / 2);

    // ─── TEXT_DISPLAY metadata indices (MC 1.21.x) ──────────────────────────────
    private static final int IDX_BILLBOARD = 15;
    private static final int IDX_TEXT = 23;
    private static final int IDX_LINE_WIDTH = 24;
    private static final int IDX_BACKGROUND = 25;
    private static final int IDX_TEXT_OPACITY = 26;
    private static final int IDX_DISPLAY_FLAGS = 27;

    private static final byte BILLBOARD_CENTER = 3;
    private static final byte FLAG_SHADOW = 0x01;
    private static final byte FLAG_SEE_THROUGH = 0x02;

    private final JavaPlugin plugin;

    // Active popups (entityId -> viewers) so a plugin disable can destroy any still-animating ones.
    private final Map<Integer, List<User>> activePopups = new ConcurrentHashMap<>();

    public KillRewardHologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void showMoney(Location location, Player player, long baseMoney, long finalMoney, long bonusMoney, String mobId) {
        if (location == null || location.getWorld() == null || player == null || finalMoney <= 0L) {
            return;
        }
        if (!plugin.getConfig().getBoolean("kill-reward-hologram.enabled", true)
                || !plugin.getConfig().getBoolean("kill-reward-hologram.money.enabled", true)) {
            return;
        }

        String text = plugin.getConfig().getString("kill-reward-hologram.money.text", "&6+%money% Linh Thach");
        if (text == null || text.isBlank()) {
            return;
        }

        double bonusPercent = baseMoney <= 0L ? 0.0D : (double) bonusMoney * 100.0D / (double) baseMoney;
        String rendered = applyMoneyPlaceholders(text, player, baseMoney, finalMoney, bonusMoney, bonusPercent, mobId);
        spawn(location, rendered);
    }

    public void removeAll() {
        for (Map.Entry<Integer, List<User>> entry : activePopups.entrySet()) {
            destroy(entry.getValue(), entry.getKey());
        }
        activePopups.clear();
    }

    private void spawn(Location location, String text) {
        int durationTicks = Math.max(1, plugin.getConfig().getInt("kill-reward-hologram.duration-ticks", 35));
        double yOffset = plugin.getConfig().getDouble("kill-reward-hologram.y-offset", 1.35D);
        double rise = plugin.getConfig().getDouble("kill-reward-hologram.rise", 0.8D);
        double xOffset = plugin.getConfig().getDouble("kill-reward-hologram.x-offset", 0.0D);
        double zOffset = plugin.getConfig().getDouble("kill-reward-hologram.z-offset", 0.0D);
        double randomRadius = Math.max(0.0D, plugin.getConfig().getDouble("kill-reward-hologram.random-xz-radius", 0.25D));
        byte startOpacity = parseOpacity(plugin.getConfig().getInt("kill-reward-hologram.start-opacity", 255));
        byte endOpacity = parseOpacity(plugin.getConfig().getInt("kill-reward-hologram.end-opacity", 0));
        boolean shadow = plugin.getConfig().getBoolean("kill-reward-hologram.shadow", true);
        boolean seeThrough = plugin.getConfig().getBoolean("kill-reward-hologram.see-through", true);
        int lineWidth = Math.max(1, plugin.getConfig().getInt("kill-reward-hologram.line-width", 160));
        double viewDistance = Math.max(1.0D, plugin.getConfig().getDouble("kill-reward-hologram.view-distance", 48.0D));

        double randomX = randomRadius <= 0.0D ? 0.0D : (Math.random() - 0.5D) * randomRadius * 2.0D;
        double randomZ = randomRadius <= 0.0D ? 0.0D : (Math.random() - 0.5D) * randomRadius * 2.0D;
        final double startX = location.getX() + xOffset + randomX;
        final double startY = location.getY() + yOffset;
        final double startZ = location.getZ() + zOffset;

        // Resolve viewers on the main thread (reads Bukkit player locations); after this the popup
        // only sends packets, which is thread-safe.
        List<User> viewers = resolveNearbyViewers(location.getWorld(), startX, startY, startZ, viewDistance * viewDistance);
        if (viewers.isEmpty()) {
            return;
        }

        final int entityId = ENTITY_ID.getAndIncrement();
        final String json = legacyToJson(colorize(text));

        // Spawn + initial metadata.
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(UUID.randomUUID()), EntityTypes.TEXT_DISPLAY,
                new Vector3d(startX, startY, startZ), 0f, 0f, 0f, 0, Optional.empty());
        WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata(
                entityId, buildMetadata(json, startOpacity, lineWidth, shadow, seeThrough));
        for (User user : viewers) {
            send(user, spawn);
            send(user, metadata);
        }

        activePopups.put(entityId, viewers);

        // Animation interval. The popup only rises + fades, so updating every other tick (default)
        // is visually indistinguishable but halves the per-step packet bursts when many kill popups
        // overlap (mob farms). Progress advances by `interval` ticks so the popup still finishes
        // after the same `durationTicks` wall-clock duration.
        int interval = Math.max(1, plugin.getConfig().getInt("kill-reward-hologram.animation-interval-ticks", 2));
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick += interval;
                double progress = Math.min(1.0D, (double) tick / durationTicks);
                double y = startY + rise * progress;
                byte opacity = interpolateOpacity(startOpacity, endOpacity, progress);

                WrapperPlayServerEntityTeleport teleport = new WrapperPlayServerEntityTeleport(
                        entityId, new Vector3d(startX, y, startZ), 0f, 0f, false);
                WrapperPlayServerEntityMetadata fade = new WrapperPlayServerEntityMetadata(
                        entityId, List.of(new EntityData<>(IDX_TEXT_OPACITY, EntityDataTypes.BYTE, opacity)));
                for (User user : viewers) {
                    send(user, teleport);
                    send(user, fade);
                }

                if (tick >= durationTicks) {
                    destroy(viewers, entityId);
                    activePopups.remove(entityId);
                    cancel();
                }
            }
        }.runTaskTimerAsynchronously(plugin, interval, interval);
    }

    /**
     * Collect the PacketEvents {@link User}s within {@code maxDistanceSquared} of the popup origin.
     * Packets are streamed only to these viewers, matching the render-distance visibility the real
     * entity used to have without ever creating an entity.
     */
    private List<User> resolveNearbyViewers(World world, double x, double y, double z, double maxDistanceSquared) {
        List<User> viewers = new ArrayList<>();
        for (Player viewer : world.getPlayers()) {
            Location loc = viewer.getLocation();
            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            try {
                User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
                if (user != null && user.getChannel() != null) {
                    viewers.add(user);
                }
            } catch (RuntimeException ignored) {
                // PacketEvents not ready for this viewer; skip them.
            }
        }
        return viewers;
    }

    private List<EntityData<?>> buildMetadata(String json, byte opacity, int lineWidth, boolean shadow, boolean seeThrough) {
        byte flags = 0;
        if (shadow) flags |= FLAG_SHADOW;
        if (seeThrough) flags |= FLAG_SEE_THROUGH;

        List<EntityData<?>> data = new ArrayList<>(6);
        data.add(new EntityData<>(IDX_BILLBOARD, EntityDataTypes.BYTE, BILLBOARD_CENTER));
        data.add(new EntityData<>(IDX_TEXT, EntityDataTypes.COMPONENT, json));
        data.add(new EntityData<>(IDX_LINE_WIDTH, EntityDataTypes.INT, lineWidth));
        data.add(new EntityData<>(IDX_BACKGROUND, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(IDX_TEXT_OPACITY, EntityDataTypes.BYTE, opacity));
        data.add(new EntityData<>(IDX_DISPLAY_FLAGS, EntityDataTypes.BYTE, flags));
        return data;
    }

    private void destroy(List<User> viewers, int entityId) {
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(entityId);
        for (User user : viewers) {
            send(user, destroy);
        }
    }

    private void send(User user, Object packet) {
        try {
            user.sendPacket((com.github.retrooper.packetevents.wrapper.PacketWrapper<?>) packet);
        } catch (RuntimeException ignored) {
            // A single viewer's channel may have closed mid-animation; keep streaming to the rest.
        }
    }

    private String applyMoneyPlaceholders(String text, Player player, long baseMoney, long finalMoney,
                                          long bonusMoney, double bonusPercent, String mobId) {
        String mob = mobId == null ? "" : mobId;
        return text
                .replace("%player%", player.getName())
                .replace("{player}", player.getName())
                .replace("%mob%", mob)
                .replace("{mob}", mob)
                .replace("%money%", String.valueOf(finalMoney))
                .replace("{money}", String.valueOf(finalMoney))
                .replace("%money_formatted%", formatInteger(finalMoney))
                .replace("{money_formatted}", formatInteger(finalMoney))
                .replace("%base_money%", String.valueOf(baseMoney))
                .replace("{base_money}", String.valueOf(baseMoney))
                .replace("%base_money_formatted%", formatInteger(baseMoney))
                .replace("{base_money_formatted}", formatInteger(baseMoney))
                .replace("%bonus_money%", String.valueOf(bonusMoney))
                .replace("{bonus_money}", String.valueOf(bonusMoney))
                .replace("%bonus_money_formatted%", formatInteger(bonusMoney))
                .replace("{bonus_money_formatted}", formatInteger(bonusMoney))
                .replace("%bonus_percent%", formatDecimal(bonusPercent))
                .replace("{bonus_percent}", formatDecimal(bonusPercent));
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

    /**
     * Wraps legacy section-coded text into a minimal JSON text component. The client renders the
     * embedded section codes, so this preserves the existing color/format output without needing an
     * Adventure serializer on the classpath. Only JSON string escaping is applied.
     */
    private static String legacyToJson(String legacy) {
        StringBuilder out = new StringBuilder("{\"text\":\"");
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append("\"}").toString();
    }

    private static byte parseOpacity(int value) {
        return (byte) Math.max(0, Math.min(255, value));
    }

    private static byte interpolateOpacity(byte start, byte end, double progress) {
        int startValue = Byte.toUnsignedInt(start);
        int endValue = Byte.toUnsignedInt(end);
        int value = (int) Math.round(startValue + ((endValue - startValue) * progress));
        return parseOpacity(value);
    }

    private static String formatInteger(long value) {
        return INTEGER_FORMAT.format(value);
    }

    private static String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

}
