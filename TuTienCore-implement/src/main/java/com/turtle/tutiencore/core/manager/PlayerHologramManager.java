package com.turtle.tutiencore.core.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.config.ConfigManager;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerHologramManager implements Listener {

    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_900_000_000);
    private static final String HIDDEN_NAME_TEAM = "ttc_hide_names";
    private static final Quaternion4f IDENTITY_ROTATION = new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f);

    private final JavaPlugin plugin;
    @SuppressWarnings("unused")
    private final ConfigManager configManager;
    private final RealmManager realmManager;
    private final Map<UUID, PacketHologram> holograms = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, TeamState>> viewerTeams = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> fallbackHiddenNames = new ConcurrentHashMap<>();
    private final Set<UUID> fallbackTeamCreated = ConcurrentHashMap.newKeySet();
    private final PacketListenerAbstract packetListener;

    private BukkitTask task;
    private boolean packetListenerRegistered;
    private TuLuyenManager tuLuyenManager;

    public PlayerHologramManager(JavaPlugin plugin, ConfigManager configManager, RealmManager realmManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.realmManager = realmManager;
        this.packetListener = new HologramPacketListener();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPacketListener();
        reload();
    }

    public void setTuLuyenManager(TuLuyenManager tuLuyenManager) {
        this.tuLuyenManager = tuLuyenManager;
    }

    public void reload() {
        stopTask();
        removeAllHolograms();
        clearFallbackNameTeams();
        viewerTeams.clear();

        if (!isEnabled() || !isPacketEventsReady()) {
            return;
        }

        syncFallbackNameTeams();
        tick();

        long interval = Math.max(1L, plugin.getConfig().getLong("player-hologram.update-interval", 5L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        stopTask();
        removeAllHolograms();
        clearFallbackNameTeams();
        unregisterPacketListener();
    }

    private void tick() {
        if (!isEnabled() || !isPacketEventsReady()) {
            reload();
            return;
        }

        Set<UUID> onlineOwners = new HashSet<>();
        for (Player owner : Bukkit.getOnlinePlayers()) {
            onlineOwners.add(owner.getUniqueId());
            if (owner.hasMetadata("NPC") || shouldHideForTuLuyen(owner)) {
                removeHologram(owner.getUniqueId());
                continue;
            }

            PacketHologram hologram = holograms.computeIfAbsent(owner.getUniqueId(), uuid -> new PacketHologram());
            String text = buildText(owner);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (shouldSee(viewer, owner)) {
                    hologram.showOrUpdate(viewer, owner, text);
                } else {
                    hologram.hide(viewer);
                }
            }
        }

        for (UUID ownerId : new HashSet<>(holograms.keySet())) {
            if (!onlineOwners.contains(ownerId)) {
                removeHologram(ownerId);
            }
        }

        syncFallbackNameTeams();
    }

    private boolean shouldHideForTuLuyen(Player player) {
        if (!plugin.getConfig().getBoolean("player-hologram.hide-while-tuluyen-hologram", true)) {
            return false;
        }
        return tuLuyenManager != null && tuLuyenManager.isTuLuyenHologramVisible(player);
    }

    private boolean shouldSee(Player viewer, Player owner) {
        if (!viewer.isOnline() || !owner.isOnline()) {
            return false;
        }
        if (viewer.getWorld() != owner.getWorld()) {
            return false;
        }
        if (!showSelf() && viewer.getUniqueId().equals(owner.getUniqueId())) {
            return false;
        }
        if (!viewer.canSee(owner)) {
            return false;
        }

        User user = getUser(viewer);
        if (user == null || user.getChannel() == null) {
            return false;
        }
        if (user.getClientVersion().isOlderThan(ClientVersion.V_1_19_4)) {
            return false;
        }

        double viewRange = Math.max(1.0, plugin.getConfig().getDouble("player-hologram.view-range", 32.0));
        return viewer.getLocation().distanceSquared(owner.getLocation()) <= viewRange * viewRange;
    }

    private Location getSpawnLocation(Player player) {
        double yOffset = plugin.getConfig().getDouble("player-hologram.y-offset", 2.55);
        Location location = player.getLocation().clone().add(0.0, yOffset, 0.0);
        location.setPitch(0.0f);
        return location;
    }

    private void removeHologram(UUID ownerId) {
        PacketHologram hologram = holograms.remove(ownerId);
        if (hologram != null) {
            hologram.remove();
        }
    }

    private void removeAllHolograms() {
        for (UUID ownerId : new HashSet<>(holograms.keySet())) {
            removeHologram(ownerId);
        }
    }

    private void removeViewer(UUID viewerId) {
        for (PacketHologram hologram : holograms.values()) {
            hologram.forgetViewer(viewerId);
        }
    }

    private void registerPacketListener() {
        if (packetListenerRegistered || !isPacketEventsReady()) {
            return;
        }

        try {
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);
            packetListenerRegistered = true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not register PacketEvents nametag listener: " + ex.getMessage());
        }
    }

    private void unregisterPacketListener() {
        if (!packetListenerRegistered || !isPacketEventsReady()) {
            return;
        }

        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        } catch (RuntimeException ignored) {
            // PacketEvents may already be shutting down.
        } finally {
            packetListenerRegistered = false;
        }
    }

    private User getUser(Player player) {
        try {
            return PacketEvents.getAPI().getPlayerManager().getUser(player);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isPacketEventsReady() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("packetevents") && PacketEvents.getAPI() != null;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | RuntimeException ex) {
            return false;
        }
    }

    private List<EntityData<?>> createMetadata(String text) {
        return createMetadata(text, getConfiguredScale(), (byte) -1, 0);
    }

    private List<EntityData<?>> createMetadata(String text, float scale, byte textOpacity, int interpolationDuration) {
        List<EntityData<?>> metadata = new ArrayList<>();
        int teleportDuration = Math.max(0, Math.min(59, plugin.getConfig().getInt("player-hologram.teleport-duration", 2)));
        int lineWidth = Math.max(1, plugin.getConfig().getInt("player-hologram.line-width", 200));
        float viewRange = (float) Math.max(1.0, plugin.getConfig().getDouble("player-hologram.view-range", 32.0));
        float yTranslation = getPassengerTranslationYOffset();

        metadata.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
        metadata.add(new EntityData<>(8, EntityDataTypes.INT, 0));
        metadata.add(new EntityData<>(9, EntityDataTypes.INT, Math.max(0, Math.min(59, interpolationDuration))));
        metadata.add(new EntityData<>(10, EntityDataTypes.INT, teleportDuration));
        metadata.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0.0f, yTranslation, 0.0f)));
        metadata.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
        metadata.add(new EntityData<>(13, EntityDataTypes.QUATERNION, IDENTITY_ROTATION));
        metadata.add(new EntityData<>(14, EntityDataTypes.QUATERNION, IDENTITY_ROTATION));
        metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3));
        metadata.add(new EntityData<>(17, EntityDataTypes.FLOAT, viewRange));
        metadata.add(new EntityData<>(20, EntityDataTypes.FLOAT, 1.0f));
        metadata.add(new EntityData<>(21, EntityDataTypes.FLOAT, 0.5f));
        metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, LegacyComponentSerializer.legacySection().deserialize(text)));
        metadata.add(new EntityData<>(24, EntityDataTypes.INT, lineWidth));
        metadata.add(new EntityData<>(25, EntityDataTypes.INT, getBackgroundColor()));
        metadata.add(new EntityData<>(26, EntityDataTypes.BYTE, textOpacity));
        metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, getStyleFlags()));
        return metadata;
    }

    private List<EntityData<?>> createOpenAnimationStartMetadata(String text) {
        float scale = getConfiguredScale() * getOpenAnimationStartScale();
        return createMetadata(text, scale, getOpenAnimationStartOpacity(), 0);
    }

    private List<EntityData<?>> createOpenAnimationTargetMetadata(String text) {
        return createMetadata(text, getConfiguredScale(), (byte) -1, getOpenAnimationDuration());
    }

    private float getConfiguredScale() {
        return (float) Math.max(0.01, plugin.getConfig().getDouble("player-hologram.scale", 1.0));
    }

    private boolean isOpenAnimationEnabled() {
        return plugin.getConfig().getBoolean("player-hologram.spawn-animation.enabled", true);
    }

    private float getOpenAnimationStartScale() {
        double scale = plugin.getConfig().getDouble("player-hologram.spawn-animation.start-scale", 0.82);
        return (float) Math.max(0.01, Math.min(2.0, scale));
    }

    private byte getOpenAnimationStartOpacity() {
        return (byte) clampColor(plugin.getConfig().getInt("player-hologram.spawn-animation.start-opacity", 40));
    }

    private int getOpenAnimationDuration() {
        return Math.max(0, Math.min(59, plugin.getConfig().getInt("player-hologram.spawn-animation.interpolation-duration", 1)));
    }

    private long getOpenAnimationDelay() {
        return Math.max(1L, plugin.getConfig().getLong("player-hologram.spawn-animation.start-delay-ticks", 1L));
    }

    private float getPassengerTranslationYOffset() {
        double yOffset = plugin.getConfig().getDouble("player-hologram.y-offset", 2.55);
        double passengerBaseOffset = plugin.getConfig().getDouble("player-hologram.passenger-base-offset", 1.35);
        return (float) Math.max(-5.0, Math.min(5.0, yOffset - passengerBaseOffset));
    }

    private byte getStyleFlags() {
        byte flags = 0;
        if (plugin.getConfig().getBoolean("player-hologram.text-shadow", true)) {
            flags |= 0x01;
        }
        if (plugin.getConfig().getBoolean("player-hologram.see-through", false)) {
            flags |= 0x02;
        }
        if (plugin.getConfig().getBoolean("player-hologram.default-background", false)) {
            flags |= 0x04;
        }
        return flags;
    }

    private int getBackgroundColor() {
        int alpha = clampColor(plugin.getConfig().getInt("player-hologram.background-color.a", 0));
        int red = clampColor(plugin.getConfig().getInt("player-hologram.background-color.r", 0));
        int green = clampColor(plugin.getConfig().getInt("player-hologram.background-color.g", 0));
        int blue = clampColor(plugin.getConfig().getInt("player-hologram.background-color.b", 0));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String buildText(Player player) {
        List<String> configuredLines = plugin.getConfig().getStringList("player-hologram.lines");
        if (configuredLines.isEmpty()) {
            configuredLines = getDefaultLines();
        }

        List<String> rendered = new ArrayList<>();
        for (String line : configuredLines) {
            String parsed = applyPlaceholders(player, line);
            rendered.add(ChatColor.translateAlternateColorCodes('&', parsed));
        }
        return String.join("\n", rendered);
    }

    private String applyPlaceholders(Player player, String line) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            line = PlaceholderAPI.setPlaceholders(player, line);
        }

        UUID uuid = player.getUniqueId();
        double tuVi = TuTien.getApi().getTuVi(uuid);
        PlayerRealm playerRealm = realmManager.getPlayerRealm(uuid);
        Realm realm = realmManager.getPlayerCurrentRealm(uuid);
        long nextTuVi = getNextTuViRequired(uuid, playerRealm, realm);
        double health = Math.max(0.0, player.getHealth());
        double maxHealth = Math.max(0.0, player.getMaxHealth());

        line = line.replace("{player}", player.getName());
        line = line.replace("{display_name}", player.getDisplayName());
        line = line.replace("{health}", formatDecimal(health));
        line = line.replace("{health_int}", String.valueOf(Math.round(health)));
        line = line.replace("{max_health}", formatDecimal(maxHealth));
        line = line.replace("{max_health_int}", String.valueOf(Math.round(maxHealth)));
        line = line.replace("{tuvi}", String.valueOf(tuVi));
        line = line.replace("{tuvi_int}", String.valueOf((long) tuVi));
        line = line.replace("{tuvi_formatted}", String.format("%,.0f", tuVi));
        line = line.replace("{tuvi_compact}", formatCompact(tuVi));
        line = line.replace("{next_tuvi}", String.valueOf(nextTuVi));
        line = line.replace("{next_tuvi_int}", String.valueOf(nextTuVi));
        line = line.replace("{next_tuvi_formatted}", String.format("%,d", nextTuVi));
        line = line.replace("{next_tuvi_compact}", formatCompact(nextTuVi));
        line = line.replace("{realm}", realmManager.getPlayerRealmName(uuid));
        line = line.replace("{realm_full}", realmManager.getPlayerDisplayName(uuid));
        line = line.replace("{sub_realm}", realmManager.getPlayerSubRealmName(uuid));
        line = line.replace("{realm_tier}", realm != null ? realm.getTier().getDisplayName() : "Pham Gioi");
        line = line.replace("{world}", player.getWorld().getName());
        return line;
    }

    private List<String> getDefaultLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&8[&6*&8] &f{player}");
        lines.add("&7{realm_full}");
        lines.add("&fTu Vi: &a{tuvi_compact}");
        return lines;
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

    private String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String formatCompact(double number) {
        if (number < 1000) {
            return String.valueOf((long) number);
        }
        int exp = (int) (Math.log(number) / Math.log(1000));
        char suffix = "kMGTPE".charAt(Math.min(exp - 1, 5));
        return String.format("%.1f%c", number / Math.pow(1000, exp), suffix);
    }

    private void syncFallbackNameTeams() {
        if (!hideVanillaName() || !isPacketEventsReady()) {
            clearFallbackNameTeams();
            return;
        }

        Set<String> onlineNames = getOnlinePlayerNames();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            User user = getUser(viewer);
            if (user == null || user.getChannel() == null) {
                continue;
            }

            Set<String> covered = getNamesCoveredByRealTeams(viewer.getUniqueId());
            Set<String> needed = new HashSet<>(onlineNames);
            needed.removeAll(covered);
            syncFallbackNameTeam(user, viewer.getUniqueId(), needed);
        }
    }

    private void syncFallbackNameTeam(User user, UUID viewerId, Set<String> needed) {
        Set<String> current = fallbackHiddenNames.computeIfAbsent(viewerId, uuid -> ConcurrentHashMap.newKeySet());
        boolean created = fallbackTeamCreated.contains(viewerId);
        if (current.equals(needed) && (needed.isEmpty() || created)) {
            return;
        }

        if (!created && !needed.isEmpty()) {
            user.sendPacket(new WrapperPlayServerTeams(HIDDEN_NAME_TEAM, WrapperPlayServerTeams.TeamMode.CREATE, createHiddenTeamInfo(), needed));
            fallbackTeamCreated.add(viewerId);
        } else {
            Set<String> added = new HashSet<>(needed);
            added.removeAll(current);

            // On 1.21.11 the client can crash if it receives REMOVE_ENTITIES for a
            // scoreboard member that another team packet already moved away. Real
            // team packets still hide those names, so only add/update the fallback.
            if (!added.isEmpty()) {
                user.sendPacket(new WrapperPlayServerTeams(HIDDEN_NAME_TEAM, WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, (WrapperPlayServerTeams.ScoreBoardTeamInfo) null, added));
            }
            if (!needed.isEmpty()) {
                user.sendPacket(new WrapperPlayServerTeams(HIDDEN_NAME_TEAM, WrapperPlayServerTeams.TeamMode.UPDATE, createHiddenTeamInfo(), needed));
            }
        }

        current.clear();
        current.addAll(needed);
    }

    private void clearFallbackNameTeams() {
        if (!isPacketEventsReady()) {
            fallbackHiddenNames.clear();
            return;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            User user = getUser(viewer);
            if (user != null && user.getChannel() != null && fallbackTeamCreated.contains(viewer.getUniqueId())) {
                user.sendPacket(new WrapperPlayServerTeams(HIDDEN_NAME_TEAM, WrapperPlayServerTeams.TeamMode.REMOVE, (WrapperPlayServerTeams.ScoreBoardTeamInfo) null, Collections.emptyList()));
            }
        }
        fallbackHiddenNames.clear();
        fallbackTeamCreated.clear();
    }

    private void removeFallbackMembers(UUID viewerId, Collection<String> members) {
        Set<String> fallback = fallbackHiddenNames.get(viewerId);
        if (fallback != null) {
            fallback.removeAll(members);
        }
    }

    private Set<String> getNamesCoveredByRealTeams(UUID viewerId) {
        Map<String, TeamState> teams = viewerTeams.get(viewerId);
        if (teams == null || teams.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> covered = new HashSet<>();
        Set<String> onlineNames = getOnlinePlayerNames();
        for (TeamState state : teams.values()) {
            for (String member : state.members) {
                if (onlineNames.contains(member)) {
                    covered.add(member);
                }
            }
        }
        return covered;
    }

    private Set<String> getOnlinePlayerNames() {
        Set<String> names = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private WrapperPlayServerTeams.ScoreBoardTeamInfo createHiddenTeamInfo() {
        return new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.ALWAYS,
                NamedTextColor.WHITE,
                WrapperPlayServerTeams.OptionData.NONE
        );
    }

    private boolean containsOnlinePlayer(Collection<String> names) {
        for (String name : names) {
            if (Bukkit.getPlayerExact(name) != null) {
                return true;
            }
        }
        return false;
    }

    private Player getPlayerByEntityId(int entityId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getEntityId() == entityId) {
                return player;
            }
        }
        return null;
    }

    private int[] getPassengerIds(Player owner) {
        List<Entity> passengers = owner.getPassengers();
        int[] ids = new int[passengers.size()];
        for (int i = 0; i < passengers.size(); i++) {
            ids[i] = passengers.get(i).getEntityId();
        }
        return ids;
    }

    private int[] appendPassenger(int[] passengers, int passengerId) {
        int[] source = passengers != null ? passengers : new int[0];
        for (int id : source) {
            if (id == passengerId) {
                return source;
            }
        }

        int[] updated = Arrays.copyOf(source, source.length + 1);
        updated[source.length] = passengerId;
        return updated;
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("player-hologram.enabled", true);
    }

    private boolean hideVanillaName() {
        return plugin.getConfig().getBoolean("player-hologram.hide-vanilla-name", true);
    }

    private boolean showSelf() {
        return plugin.getConfig().getBoolean("player-hologram.show-self", false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isEnabled()) {
                tick();
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        removeHologram(playerId);
        removeViewer(playerId);
        viewerTeams.remove(playerId);
        fallbackHiddenNames.remove(playerId);
        fallbackTeamCreated.remove(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, this::syncFallbackNameTeams, 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PacketHologram hologram = holograms.get(event.getPlayer().getUniqueId());
            if (hologram != null) {
                hologram.hideFromAll();
            }
            if (isEnabled()) {
                tick();
            }
        }, 2L);
    }

    private final class PacketHologram {

        private final int entityId = NEXT_ENTITY_ID.incrementAndGet();
        private final UUID entityUuid = UUID.randomUUID();
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        private final Map<UUID, String> lastTextByViewer = new ConcurrentHashMap<>();

        private void showOrUpdate(Player viewer, Player owner, String text) {
            User user = getUser(viewer);
            if (user == null || user.getChannel() == null) {
                viewers.remove(viewer.getUniqueId());
                lastTextByViewer.remove(viewer.getUniqueId());
                return;
            }

            UUID viewerId = viewer.getUniqueId();
            boolean firstSpawn = viewers.add(viewerId);
            String previousText = lastTextByViewer.get(viewerId);
            Location location = getSpawnLocation(owner);
            if (firstSpawn) {
                user.sendPacket(new WrapperPlayServerSpawnEntity(
                        entityId,
                        Optional.of(entityUuid),
                        EntityTypes.TEXT_DISPLAY,
                        toVector(location),
                        0.0f,
                        location.getYaw(),
                        location.getYaw(),
                        0,
                        Optional.of(Vector3d.zero())
                ));
                if (isOpenAnimationEnabled()) {
                    user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, createOpenAnimationStartMetadata(text)));
                    scheduleOpenAnimation(viewerId, owner.getUniqueId());
                } else {
                    user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, createMetadata(text)));
                }
            } else if (!text.equals(previousText)) {
                user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, createMetadata(text)));
            }

            attachToOwner(user, owner);
            lastTextByViewer.put(viewerId, text);
        }

        private void scheduleOpenAnimation(UUID viewerId, UUID ownerId) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!viewers.contains(viewerId)) {
                    return;
                }

                Player viewer = Bukkit.getPlayer(viewerId);
                Player owner = Bukkit.getPlayer(ownerId);
                if (viewer == null || owner == null || !shouldSee(viewer, owner)) {
                    return;
                }

                User user = getUser(viewer);
                String text = lastTextByViewer.get(viewerId);
                if (user == null || user.getChannel() == null || text == null) {
                    return;
                }

                user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, createOpenAnimationTargetMetadata(text)));
                attachToOwner(user, owner);
            }, getOpenAnimationDelay());
        }

        private void attachToOwner(User user, Player owner) {
            int[] passengers = appendPassenger(getPassengerIds(owner), entityId);
            user.sendPacket(new WrapperPlayServerSetPassengers(owner.getEntityId(), passengers));
        }

        private boolean isShownTo(UUID viewerId) {
            return viewers.contains(viewerId);
        }

        private void forgetViewer(UUID viewerId) {
            viewers.remove(viewerId);
            lastTextByViewer.remove(viewerId);
        }

        private void hide(Player viewer) {
            UUID viewerId = viewer.getUniqueId();
            if (!viewers.remove(viewerId)) {
                return;
            }
            lastTextByViewer.remove(viewerId);

            User user = getUser(viewer);
            if (user != null && user.getChannel() != null) {
                user.sendPacket(new WrapperPlayServerDestroyEntities(entityId));
            }
        }

        private void hideFromAll() {
            for (UUID viewerId : new HashSet<>(viewers)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    hide(viewer);
                } else {
                    viewers.remove(viewerId);
                    lastTextByViewer.remove(viewerId);
                }
            }
        }

        private void remove() {
            hideFromAll();
        }

        private Vector3d toVector(Location location) {
            return new Vector3d(location.getX(), location.getY(), location.getZ());
        }
    }

    private final class HologramPacketListener extends PacketListenerAbstract {

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
                handleTeamsPacket(event);
                return;
            }

            if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
                handleSetPassengersPacket(event);
            }
        }

        private void handleSetPassengersPacket(PacketSendEvent event) {
            if (!isEnabled()) {
                return;
            }

            UUID viewerId = event.getUser().getUUID();
            if (viewerId == null) {
                return;
            }

            WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
            Player owner = getPlayerByEntityId(packet.getEntityId());
            if (owner == null) {
                return;
            }

            PacketHologram hologram = holograms.get(owner.getUniqueId());
            if (hologram == null || !hologram.isShownTo(viewerId)) {
                return;
            }

            int[] passengers = packet.getPassengers();
            int[] updated = appendPassenger(passengers, hologram.entityId);
            if (updated != passengers) {
                packet.setPassengers(updated);
                event.markForReEncode(true);
            }
        }

        private void handleTeamsPacket(PacketSendEvent event) {
            if (!hideVanillaName()) {
                return;
            }

            WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
            if (HIDDEN_NAME_TEAM.equals(packet.getTeamName())) {
                return;
            }

            UUID viewerId = event.getUser().getUUID();
            Map<String, TeamState> teams = viewerTeams.computeIfAbsent(viewerId, uuid -> new ConcurrentHashMap<>());
            String teamName = packet.getTeamName();

            switch (packet.getTeamMode()) {
                case CREATE -> handleCreateTeamPacket(event, packet, teams, teamName);
                case UPDATE -> handleUpdateTeamPacket(event, packet, teams, teamName);
                case ADD_ENTITIES -> handleAddEntitiesPacket(event, packet, teams, teamName);
                case REMOVE_ENTITIES -> handleRemoveEntitiesPacket(packet, teams, teamName);
                case REMOVE -> teams.remove(teamName);
            }
        }

        private void handleCreateTeamPacket(PacketSendEvent event, WrapperPlayServerTeams packet, Map<String, TeamState> teams, String teamName) {
            TeamState state = teams.computeIfAbsent(teamName, ignored -> new TeamState());
            state.members.clear();
            state.members.addAll(packet.getPlayers());
            removeFallbackMembers(event.getUser().getUUID(), state.members);
            packet.getTeamInfo().ifPresent(info -> {
                state.info = info;
                if (containsOnlinePlayer(state.members)) {
                    info.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    event.markForReEncode(true);
                }
            });
        }

        private void handleUpdateTeamPacket(PacketSendEvent event, WrapperPlayServerTeams packet, Map<String, TeamState> teams, String teamName) {
            TeamState state = teams.computeIfAbsent(teamName, ignored -> new TeamState());
            packet.getTeamInfo().ifPresent(info -> {
                state.info = info;
                if (containsOnlinePlayer(state.members)) {
                    info.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    event.markForReEncode(true);
                }
            });
        }

        private void handleAddEntitiesPacket(PacketSendEvent event, WrapperPlayServerTeams packet, Map<String, TeamState> teams, String teamName) {
            TeamState state = teams.computeIfAbsent(teamName, ignored -> new TeamState());
            state.members.addAll(packet.getPlayers());
            removeFallbackMembers(event.getUser().getUUID(), packet.getPlayers());

            if (containsOnlinePlayer(packet.getPlayers()) && state.info != null) {
                state.info.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                event.getUser().sendPacket(new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.UPDATE, state.info, state.members));
            }
        }

        private void handleRemoveEntitiesPacket(WrapperPlayServerTeams packet, Map<String, TeamState> teams, String teamName) {
            TeamState state = teams.get(teamName);
            if (state != null) {
                state.members.removeAll(packet.getPlayers());
            }
        }
    }

    private static final class TeamState {
        private final Set<String> members = ConcurrentHashMap.newKeySet();
        private volatile WrapperPlayServerTeams.ScoreBoardTeamInfo info;
    }
}
