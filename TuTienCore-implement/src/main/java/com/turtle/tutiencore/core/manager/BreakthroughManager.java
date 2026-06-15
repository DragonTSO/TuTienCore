package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.event.RealmBreakthroughEvent;
import com.turtle.tutiencore.api.event.RealmBreakthroughFailEvent;
import com.turtle.tutiencore.api.event.RealmBreakthroughSuccessEvent;
import com.turtle.tutiencore.api.event.SubRealmAdvanceEvent;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.mobs.entities.SpawnReason;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quản lý quá trình Đột Phá Cảnh Giới — Thiên Lôi Kiếp ⚡
 * 
 * Flow:
 * 1. /dotpha → xác nhận → title countdown 3→2→1 (thiên lôi sắp giáng)
 * 2. SAU countdown → bão sét bắt đầu, sét đánh liên tục
 * 3. Sống sót hết tia sét = thành công → tàng hình + animation "actived"
 *    Sét vẫn đánh trong suốt animation. Kết thúc animation → hiện lại.
 * 4. Chết = thất bại → tụt 1 bậc + cooldown 30 phút
 */
public class BreakthroughManager implements Listener {

    private final JavaPlugin plugin;
    private final RealmManager realmManager;

    // Players currently undergoing breakthrough
    private final Map<UUID, BreakthroughSession> activeSessions = new HashMap<>();

    // Track players who have confirmed but countdown hasn't finished yet
    private final Set<UUID> playersInCountdown = new HashSet<>();

    // Used to suspend player flight while đột phá is in progress (optional dependency).
    private FlySwordManager flySwordManager;

    public BreakthroughManager(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Inject the FlySwordManager so flight can be suspended during breakthrough.
     */
    public void setFlySwordManager(FlySwordManager flySwordManager) {
        this.flySwordManager = flySwordManager;
    }

    private void suspendFlight(Player player) {
        if (flySwordManager != null) {
            flySwordManager.suspendFlightForBreakthrough(player);
        }
    }

    private void resumeFlight(UUID uuid) {
        if (flySwordManager == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            flySwordManager.resumeFlightForBreakthrough(player);
        }
    }

    // ==========================================
    // BREAKTHROUGH SESSION
    // ==========================================

    /**
     * Represents an active breakthrough attempt
     */
    private static class BreakthroughSession {
        final UUID playerId;
        final boolean isMajor;
        final int totalBolts;
        final double baseDamagePerBolt;
        final double damagePercentPerBolt;
        final Realm targetRealm;
        final SubRealm targetSubRealm;
        int boltsRemaining;
        BukkitRunnable task;
        BukkitRunnable auraTask;
        BukkitRunnable stormTask; // Continuous ambient lightning storm
        BukkitRunnable activeAnimationTask;
        final List<ActiveMob> activeBreakthroughMobs = new ArrayList<>();
        Location startLocation;
        int currentLevitationLevel;
        volatile boolean completed; // Guard against double-execution

        BreakthroughSession(UUID playerId, boolean isMajor, int totalBolts,
                            double baseDamagePerBolt, double damagePercentPerBolt,
                            Realm targetRealm, SubRealm targetSubRealm) {
            this.playerId = playerId;
            this.isMajor = isMajor;
            this.totalBolts = totalBolts;
            this.baseDamagePerBolt = baseDamagePerBolt;
            this.damagePercentPerBolt = damagePercentPerBolt;
            this.targetRealm = targetRealm;
            this.targetSubRealm = targetSubRealm;
            this.boltsRemaining = totalBolts;
            this.currentLevitationLevel = 0;
        }
    }

    /**
     * Check if a player is currently in a breakthrough
     */
    public boolean isInBreakthrough(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    // ==========================================
    // MAJOR REALM BREAKTHROUGH (Đại Cảnh Giới)
    // ==========================================

    /**
     * Start a major realm breakthrough (Thiên Lôi Kiếp).
     * Called when player confirms in the /dotpha GUI.
     */
    public void startMajorBreakthrough(Player player) {
        UUID uuid = player.getUniqueId();

        if (isInBreakthrough(uuid)) {
            player.sendMessage("§c⚡ Bạn đang trong quá trình Thiên Lôi Kiếp!");
            return;
        }

        // Verify conditions
        List<String> failures = realmManager.checkBreakthroughConditions(uuid);
        if (!failures.isEmpty()) {
            player.sendMessage("§c§l⚠ Chưa đủ điều kiện đột phá:");
            for (String msg : failures) {
                player.sendMessage("  " + msg);
            }
            return;
        }

        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        Realm nextRealm = realmManager.getNextRealm(uuid);
        if (nextRealm == null) {
            player.sendMessage("§c§lBạn đã đạt Cảnh Giới tối đa — Hồng Mông!");
            return;
        }

        // Fire cancellable event — other plugins can prevent breakthrough
        RealmBreakthroughEvent event = new RealmBreakthroughEvent(player, currentRealm, nextRealm);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            player.sendMessage("§c⚠ Đột phá bị chặn bởi hệ thống khác!");
            return;
        }

        // Sống sót = thành công, Chết = thất bại (không pre-roll)
        int dotPhaDanRequired = realmManager.getDotPhaDanRequired(nextRealm.getId());
        if (!realmManager.takeDotPhaDan(player, dotPhaDanRequired)) {
            int have = realmManager.getDotPhaDanCount(player);
            player.sendMessage("§cThiếu Đột Phá Đan! Cần: x" + dotPhaDanRequired
                    + " | Hiện có: x" + have
                    + " §7(" + realmManager.getDotPhaDanItem() + ")");
            return;
        }

        // Take additional realm materials (nếu có cấu hình)
        if (!realmManager.takeAllMaterials(player, nextRealm.getId())) {
            player.sendMessage("§cThiếu nguyên liệu đột phá! Kiểm tra lại vật phẩm trong túi.");
            return;
        }

        double actualDmg = nextRealm.getDamagePerBolt();

        BreakthroughSession session = new BreakthroughSession(
                uuid, true,
                nextRealm.getLightningBolts(), actualDmg, nextRealm.getDamagePercentPerBolt(),
                nextRealm, null
        );

        activeSessions.put(uuid, session);

        // Disable flight during breakthrough — player can't fly away.
        suspendFlight(player);

        // Broadcast start
        String startMsg = "§e§l⚡ " + player.getName() + " §e§lđang vượt §c§lThiên Lôi Kiếp §e§lđể đột phá " 
                + nextRealm.getFormattedName() + "§e§l!";
        Bukkit.broadcastMessage(startMsg);
        playConfiguredSound(player.getWorld(), player.getLocation(),
                "breakthrough.sounds.start-initiation",
                Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER,
                1.5f, 0.8f);

        // Apply breakthrough potion effects
        applyBreakthroughEffects(player, true);

        // Start weather effects
        startWeatherEffects(player);

        // Start persistent aura particles
        startAuraTask(player, session);

        // MythicMobs owns the ModelEngine state animation so it does not fall back to idle.
        spawnActiveBreakthroughMobs(player, session);

        // NOTE: Ambient storm starts AFTER countdown (inside startLightningSequence)
        // Begin lightning sequence (countdown 3→2→1 → sét bắt đầu)
        startLightningSequence(player, session);
    }

    // ==========================================
    // SUB-REALM BREAKTHROUGH (Tiểu Cảnh Giới)
    // ==========================================

    /**
     * Start a sub-realm breakthrough (Tiểu Lôi Kiếp).
     * Called when player uses /dotpha for sub-realm advancement.
     */
    public void startSubRealmBreakthrough(Player player) {
        UUID uuid = player.getUniqueId();

        if (isInBreakthrough(uuid)) {
            player.sendMessage("§c⚡ Bạn đang trong quá trình đột phá!");
            return;
        }

        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        SubRealm currentSub = pr.getSubRealm();
        SubRealm nextSub = currentSub.next();

        if (nextSub == null) {
            player.sendMessage("§cBạn đã đạt Viên Mãn! Hãy đột phá Đại Cảnh Giới!");
            return;
        }

        List<String> failures = realmManager.checkSubRealmBreakthroughConditions(uuid, nextSub);
        if (!failures.isEmpty()) {
            player.sendMessage("§c§l⚠ Chưa đủ điều kiện đột phá:");
            for (String msg : failures) {
                player.sendMessage("  " + msg);
            }
            return;
        }

        Realm realm = realmManager.getPlayerCurrentRealm(uuid);
        int bolts = realmManager.getSubRealmBolts(currentSub);
        double dmg = realmManager.getSubRealmDmg(currentSub);

        int dotPhaDanRequired = realmManager.getSubRealmDotPhaDanRequired(currentSub);
        if (dotPhaDanRequired > 0) {
            if (!realmManager.takeDotPhaDan(player, dotPhaDanRequired)) {
                int have = realmManager.getDotPhaDanCount(player);
                player.sendMessage("§cThiếu Đột Phá Đan! Cần: x" + dotPhaDanRequired
                        + " | Hiện có: x" + have
                        + " §7(" + realmManager.getDotPhaDanItem() + ")");
                return;
            }
        }

        // Take additional sub-realm materials (nếu có cấu hình)
        if (!realmManager.takeAllSubRealmMaterials(player, realm.getId(), nextSub)) {
            player.sendMessage("§cThiếu nguyên liệu đột phá! Kiểm tra lại vật phẩm trong túi.");
            return;
        }

        BreakthroughSession session = new BreakthroughSession(
                uuid, false,
                bolts, dmg, 0.0,
                null, nextSub
        );

        activeSessions.put(uuid, session);

        // Disable flight during breakthrough — player can't fly away.
        suspendFlight(player);

        // Local broadcast only
        String startMsg = "§e⚡ " + player.getName() + " §eđang đột phá tầng nhỏ → " + nextSub.getDisplayName() + "!";
        broadcastNearby(player, startMsg, realmManager.getSubRealmBroadcastRadius());
        playConfiguredSound(player.getWorld(), player.getLocation(),
                "breakthrough.sounds.start-initiation",
                Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER,
                1.5f, 0.8f);

        // Apply lighter breakthrough effects
        applyBreakthroughEffects(player, false);

        // Start aura particles
        startAuraTask(player, session);

        // MythicMobs owns the ModelEngine state animation so it does not fall back to idle.
        spawnActiveBreakthroughMobs(player, session);

        // NOTE: Ambient storm starts AFTER countdown (inside startLightningSequence)
        // Begin lightning sequence (countdown 3→2→1 → sét bắt đầu)
        startLightningSequence(player, session);
    }

    // ==========================================
    // LIGHTNING SEQUENCE (Thiên Lôi Kiếp)
    // ==========================================

    // Lightning AOE radius (40x40 = ±20 blocks from center)
    private static final int LIGHTNING_RADIUS = 20;
    static final String ACTIVE_BREAKTHROUGH_MOBS_KEY = "breakthrough.active-mobs";
    private static final String ACTIVE_BREAKTHROUGH_ANIMATION_KEY = "breakthrough.active-animation";
    private static final int ACTIVE_BREAKTHROUGH_ANIMATION_REPLAY_TICKS = 90;
    private static final int ACTIVE_BREAKTHROUGH_MOB_SYNC_TICKS = 10;
    private static final double ACTIVE_BREAKTHROUGH_MOB_SYNC_DISTANCE_SQUARED = 0.36D;
    // Max levitation level (increases over time)
    private static final int MAX_LEVITATION_LEVEL = 6;
    // Damage scaling: at max height, damage = baseDmg * this multiplier
    private static final double MAX_HEIGHT_DMG_MULTIPLIER = 2.5;
    private static final String NEARBY_LIGHTNING_ENABLED_PATH = "breakthrough.nearby-lightning.enabled";
    private static final String NEARBY_LIGHTNING_RADIUS_PATH = "breakthrough.nearby-lightning.radius";
    private static final String NEARBY_LIGHTNING_DAMAGE_MULTIPLIER_PATH = "breakthrough.nearby-lightning.damage-multiplier";

    private void startLightningSequence(Player player, BreakthroughSession session) {
        int intervalTicks = realmManager.getLightningIntervalTicks();
        session.startLocation = player.getLocation().clone();

        // Add player to countdown set — they can't move or use commands now
        playersInCountdown.add(player.getUniqueId());

        // Phase 1: Countdown 3 → 2 → 1 (mỗi giây 1 lần, chưa có sét)
        player.sendMessage("§6§l⚡ Thiên Lôi Kiếp bắt đầu trong 3 giây... Chuẩn bị!");

        // Countdown task — runs every 20 ticks (1 second)
        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                if (!player.isOnline() || !activeSessions.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                if (countdown > 0) {
                    player.sendTitle(
                            "§c§l" + countdown,
                            "§e⚡ Thiên Lôi sắp giáng!",
                            5, 15, 5
                    );
                    playConfiguredSound(player.getWorld(), player.getLocation(),
                            "breakthrough.sounds.countdown-thunder",
                            Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER,
                            2.0f, 0.5f);
                    // Energy gathering particles during countdown
                    spawnEnergyGathering(player, 4 - countdown);
                    countdown--;
                } else {
                    // Countdown xong → BẮT ĐẦU bão sét
                    cancel();
                    // Remove from countdown set — they can now move again (but will levitate)
                    playersInCountdown.remove(player.getUniqueId());
                    player.sendTitle(
                            "§c§l⚡ THIÊN LÔI KIẾP ⚡",
                            session.isMajor ? "§eSống sót qua " + session.totalBolts + " tia sét!" 
                                           : "§eTiểu Lôi Kiếp — " + session.totalBolts + " tia sét",
                            10, 40, 10
                    );
                    // Start Levitation
                    applyBreakthroughAscent(player, session, 1);
                    // BẮT ĐẦU ambient storm SAU countdown
                    startAmbientStorm(player, session);
                    // BẮT ĐẦU lightning phase
                    startLightningPhase(player, session, intervalTicks);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Phase 2: Lightning strikes — sét đánh liên tục sau countdown.
     * Sống sót hết = thành công, chết = thất bại.
     */
    private void startLightningPhase(Player player, BreakthroughSession session, int intervalTicks) {
        session.task = new BukkitRunnable() {
            int boltsFired = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanupLevitation(player);
                    cancelSession(session);
                    cancel();
                    return;
                }

                // Lightning phase
                if (session.boltsRemaining > 0) {
                    boltsFired++;

                    // Increase levitation every few bolts
                    int levPhase = (boltsFired * MAX_LEVITATION_LEVEL) / session.totalBolts;
                    int newLevel = Math.min(levPhase + 1, MAX_LEVITATION_LEVEL);
                    if (newLevel > session.currentLevitationLevel) {
                        applyBreakthroughAscent(player, session, newLevel);
                        player.sendMessage("§c§l⚠ Thiên Lôi mạnh hơn! §e(Levitation " + newLevel + ")");
                    }

                    // Strike random lightning in AOE
                    strikeRandomLightning(player, session);
                    session.boltsRemaining--;

                    int boltNumber = session.totalBolts - session.boltsRemaining;
                    double currentDmg = calculateAppliedDamage(player, session);
                    // Show action bar progress
                    if (!shouldSuppressCinematicUi(player)) {
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                                    "§c⚡ Tia sét " + boltNumber + "/" + session.totalBolts 
                                    + " §7| §c" + String.format("%.1f", currentDmg) + " ❤ DMG"
                                    + " §7| §bLv." + session.currentLevitationLevel));
                    }

                } else {
                    // All bolts survived → SUCCESS
                    // CRITICAL: cancel FIRST to prevent re-entry if success handler throws
                    cancel();
                    cleanupBreakthroughEffects(player);
                    handleBreakthroughSuccess(player, session);
                }
            }
        };

        session.task.runTaskTimer(plugin, 0L, intervalTicks);
    }

    private boolean shouldSuppressCinematicUi(Player player) {
        return player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    private void applyBreakthroughAscent(Player player, BreakthroughSession session, int level) {
        session.currentLevitationLevel = Math.max(1, level);
        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));


        applyLevitation(player, session, session.currentLevitationLevel);
    }

    /**
     * Apply Levitation potion effect to the player.
     */
    private void applyLevitation(Player player, BreakthroughSession session, int level) {
        session.currentLevitationLevel = level;
        // Remove old levitation first
        player.removePotionEffect(PotionEffectType.LEVITATION);
        // Apply new levitation (long duration — will be removed on end)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.LEVITATION, 999999, level - 1, // amplifier is 0-indexed
                false, true, true // ambient=false, particles=true, icon=true
        ));
    }

    /**
     * Remove Levitation and safely lower the player.
     */
    private void cleanupLevitation(Player player) {
        player.removePotionEffect(PotionEffectType.LEVITATION);
        // Give slow-falling to prevent fall damage
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, 200, 0,
                false, true, true
        ));
    }

    /**
     * Apply potion effects at the start of breakthrough.
     */
    private void applyBreakthroughEffects(Player player, boolean isMajor) {
        // Glowing — let nearby players see them
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, 999999, 0, false, false, true));
        // Resistance — slight protection so they can survive
        int resLevel = isMajor ? 1 : 2;
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, 999999, resLevel, false, false, true));
    }

    /**
     * Remove all breakthrough potion effects + levitation.
     */
    private void cleanupBreakthroughEffects(Player player) {
        cleanupLevitation(player);
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        // Stop aura task
        BreakthroughSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            if (session.auraTask != null) session.auraTask.cancel();
            if (session.stormTask != null) session.stormTask.cancel();
        }
    }

    // ==========================================
    // PLAYER AURA EFFECTS (persistent during breakthrough)
    // ==========================================

    /**
     * Start a persistent aura particle task that runs every 2 ticks.
     * Creates spiral energy, ground runes, and absorption pillar around the player.
     */
    private void startAuraTask(Player player, BreakthroughSession session) {
        session.auraTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !activeSessions.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                try {
                Location loc = player.getLocation();
                World world = player.getWorld();
                double progress = session.totalBolts > 0 
                        ? 1.0 - ((double) session.boltsRemaining / session.totalBolts) 
                        : 0.0;
                tick++;

                // ── 1) DOUBLE SPIRAL — two opposing energy spirals around player ──
                double angle1 = tick * 0.3;
                double angle2 = angle1 + Math.PI; // opposite spiral
                double spiralRadius = 1.5 + progress * 0.5;
                for (int i = 0; i < 2; i++) {
                    double a = (i == 0) ? angle1 : angle2;
                    double sx = Math.cos(a) * spiralRadius;
                    double sz = Math.sin(a) * spiralRadius;
                    double sy = (tick * 0.15) % 3.0; // rises 0→3 blocks, repeats
                    Location spiralLoc = loc.clone().add(sx, sy, sz);
                    world.spawnParticle(Particle.END_ROD, spiralLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, spiralLoc, 1, 0.05, 0.05, 0.05, 0);
                }

                // ── 2) GROUND RUNE CIRCLE — every 3 ticks ──
                if (tick % 3 == 0) {
                    double runeRadius = 2.5 + progress;
                    int points = 12;
                    for (int i = 0; i < points; i++) {
                        double ra = (2 * Math.PI / points) * i + tick * 0.05;
                        double rx = Math.cos(ra) * runeRadius;
                        double rz = Math.sin(ra) * runeRadius;
                        Location runeLoc = loc.clone().add(rx, 0.1, rz);
                        world.spawnParticle(Particle.ENCHANT, runeLoc, 3, 0.1, 0, 0.1, 0.5);
                    }
                }

                // ── 3) ENERGY ABSORPTION — particles fly toward player ──
                if (tick % 5 == 0) {
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    int absorptionCount = 3 + (int) (progress * 5);
                    for (int i = 0; i < absorptionCount; i++) {
                        double ox = rand.nextDouble(-4, 5);
                        double oy = rand.nextDouble(0, 4);
                        double oz = rand.nextDouble(-4, 5);
                        Location from = loc.clone().add(ox, oy, oz);
                        // Direction vector toward player
                        double dx = (loc.getX() - from.getX()) * 0.15;
                        double dy = (loc.getY() + 1 - from.getY()) * 0.15;
                        double dz = (loc.getZ() - from.getZ()) * 0.15;
                        world.spawnParticle(Particle.ENCHANT, from, 0, dx, dy, dz, 1.0);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, from, 0, dx, dy, dz, 0.8);
                    }
                }

                // ── 4) LIGHT PILLAR — vertical beam above player, grows with progress ──
                if (tick % 4 == 0 && session.isMajor) {
                    double pillarHeight = 3 + progress * 8;
                    for (double y = 0; y < pillarHeight; y += 0.5) {
                        Location pillarLoc = loc.clone().add(0, y + 1, 0);
                        world.spawnParticle(Particle.END_ROD, pillarLoc, 1, 0.15, 0, 0.15, 0);
                    }
                }

                // ── 5) SHOCKWAVE RING — expanding ring every 15 ticks ──
                if (tick % 15 == 0) {
                    double ringRadius = 2 + progress * 3;
                    int ringPoints = 24;
                    for (int i = 0; i < ringPoints; i++) {
                        double ra = (2 * Math.PI / ringPoints) * i;
                        double rx = Math.cos(ra) * ringRadius;
                        double rz = Math.sin(ra) * ringRadius;
                        Location ringLoc = loc.clone().add(rx, 1.0, rz);
                        world.spawnParticle(Particle.CLOUD, ringLoc, 1, 0, 0, 0, 0.02);
                    }
                }
                } catch (Throwable t) {
                    // Particle API errors should not crash the task
                }
            }
        };
        session.auraTask.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Start a continuous ambient lightning storm that runs every 3 ticks.
     * Visual-only lightning bolts rain across the 40x40 area independently
     * of the main damaging bolt sequence. Creates a persistent storm effect.
     */
    private void startAmbientStorm(Player player, BreakthroughSession session) {
        session.stormTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !activeSessions.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                World world = player.getWorld();
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                double progress = 1.0 - ((double) session.boltsRemaining / session.totalBolts);

                // Scale: min bolts early → max bolts late (from config)
                int minBolts = realmManager.getStormBoltsMin();
                int maxBolts = realmManager.getStormBoltsMax();
                int stormBolts = minBolts + (int) (progress * (maxBolts - minBolts)) + rand.nextInt(0, 3);

                for (int i = 0; i < stormBolts; i++) {
                    double ox = rand.nextDouble(-LIGHTNING_RADIUS, LIGHTNING_RADIUS + 1);
                    double oz = rand.nextDouble(-LIGHTNING_RADIUS, LIGHTNING_RADIUS + 1);
                    Location strikeLoc = loc.clone().add(ox, 0, oz);
                    strikeLoc.setY(world.getHighestBlockYAt(strikeLoc) + 1);
                    world.strikeLightningEffect(strikeLoc);
                }
            }
        };
        session.stormTask.runTaskTimer(plugin, 0L, realmManager.getVisualStormInterval()); // From config.yml storm-interval
    }

    /**
     * Spawn energy gathering particles during countdown phase.
     * Intensity increases each second (phase 1→3).
     */
    private void spawnEnergyGathering(Player player, int phase) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int count = 10 + phase * 10;
        double radius = 6 - phase; // Shrinks as countdown approaches 0
        for (int i = 0; i < count; i++) {
            double ox = rand.nextDouble(-radius, radius + 1);
            double oy = rand.nextDouble(0, 5);
            double oz = rand.nextDouble(-radius, radius + 1);
            Location from = loc.clone().add(ox, oy, oz);
            double dx = (loc.getX() - from.getX()) * 0.2;
            double dy = (loc.getY() + 1 - from.getY()) * 0.2;
            double dz = (loc.getZ() - from.getZ()) * 0.2;
            world.spawnParticle(Particle.ENCHANT, from, 0, dx, dy, dz, 1.0);
            world.spawnParticle(Particle.END_ROD, from, 0, dx, dy, dz, 0.5);
        }
        // Whoosh sound
        playConfiguredSound(world, loc,
                "breakthrough.sounds.energy-charge",
                Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.MASTER,
                1.5f, 0.5f, phase * 0.3f);
    }

    /**
     * Calculate damage based on player height above start position.
     * Higher = more damage.
     */
    private double calculateDamage(Player player, BreakthroughSession session) {
        double baseY = session.startLocation.getY();
        double currentY = player.getLocation().getY();
        double heightDiff = Math.max(0, currentY - baseY);

        // Scale: 0 blocks up = 1x damage, ~30 blocks up = MAX_HEIGHT_DMG_MULTIPLIER x damage
        double heightFactor = 1.0 + (heightDiff / 30.0) * (MAX_HEIGHT_DMG_MULTIPLIER - 1.0);
        heightFactor = Math.min(heightFactor, MAX_HEIGHT_DMG_MULTIPLIER);

        if (session.damagePercentPerBolt > 0.0) {
            return Math.max(0.0, player.getMaxHealth() * (session.damagePercentPerBolt / 100.0));
        }

        return session.baseDamagePerBolt * heightFactor;
    }

    private double calculateAppliedDamage(Player player, BreakthroughSession session) {
        double damage = calculateDamage(player, session);
        return session.damagePercentPerBolt > 0.0 ? damage : damage * 2.0;
    }

    static double calculateNearbyPlayerDamage(double mainDamage, double multiplier) {
        double clampedMultiplier = Math.max(0.0, Math.min(1.0, multiplier));
        return Math.max(0.0, mainDamage) * clampedMultiplier;
    }

    /**
     * Strike a massive lightning storm across the 40x40 area around the player.
     * Creates a dramatic visual: many simultaneous bolts from sky to ground,
     * intensity scales up as breakthrough progresses.
     * Only 1 "main" bolt near the player deals actual damage.
     */
    private void strikeRandomLightning(Player player, BreakthroughSession session) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int boltsFired = session.totalBolts - session.boltsRemaining;

        // ── Intensity scales up as breakthrough progresses ──
        // Visual bolts from config (min → max as progress increases)
        double progress = (double) boltsFired / session.totalBolts; // 0.0 → 1.0
        int minBolts = realmManager.getVisualBoltsMin();
        int maxBolts = realmManager.getVisualBoltsMax();
        int halfRange = (maxBolts - minBolts) / 2;
        int totalVisualBolts = rand.nextInt(minBolts + (int)(progress * halfRange), 
                minBolts + halfRange + (int)(progress * halfRange) + 1);

        // ── 1) MAIN BOLT — near the player, deals damage ──
        double mainOffsetX = rand.nextDouble(-5, 6);
        double mainOffsetZ = rand.nextDouble(-5, 6);
        Location mainStrikeLoc = playerLoc.clone().add(mainOffsetX, 0, mainOffsetZ);
        mainStrikeLoc.setY(world.getHighestBlockYAt(mainStrikeLoc) + 1);
        world.strikeLightningEffect(mainStrikeLoc);

        // Calculate height-scaled damage and apply
        double damage = calculateAppliedDamage(player, session);
        player.damage(damage);
        strikeNearbyPlayers(player, session, damage);

        // ── 2) AMBIENT BOLTS — spread across full 40x40 area, visual only ──
        for (int i = 0; i < totalVisualBolts; i++) {
            double offsetX = rand.nextDouble(-LIGHTNING_RADIUS, LIGHTNING_RADIUS + 1);
            double offsetZ = rand.nextDouble(-LIGHTNING_RADIUS, LIGHTNING_RADIUS + 1);
            Location strikeLoc = playerLoc.clone().add(offsetX, 0, offsetZ);
            strikeLoc.setY(world.getHighestBlockYAt(strikeLoc) + 1);
            world.strikeLightningEffect(strikeLoc);
        }

        // ── 3) CLOSE BOLTS — extra bolts very near player (from config) ──
        int closeBolts = rand.nextInt(realmManager.getCloseBoltsMin(), realmManager.getCloseBoltsMax() + 1);
        for (int i = 0; i < closeBolts; i++) {
            double cx = rand.nextDouble(-3, 4);
            double cz = rand.nextDouble(-3, 4);
            Location closeLoc = playerLoc.clone().add(cx, 0, cz);
            closeLoc.setY(world.getHighestBlockYAt(closeLoc) + 1);
            world.strikeLightningEffect(closeLoc);
        }

        // ── 4) PARTICLE EFFECTS around the player ──
        Location particleLoc = playerLoc.clone().add(0, 1, 0);
        if (session.isMajor && session.targetRealm != null) {
            spawnBreakthroughParticles(player, session.targetRealm);
        } else {
            world.spawnParticle(Particle.END_ROD, particleLoc, 20, 0.5, 0.5, 0.5, 0.1);
        }

        // Extra electric sparks at strike locations for atmosphere
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, mainStrikeLoc, 15, 1.5, 0.5, 1.5, 0.05);
        world.spawnParticle(Particle.END_ROD, playerLoc.clone().add(0, 2, 0), 10, 0.3, 0.5, 0.3, 0.05);

        // ── 5) SOUND EFFECTS — layered thunder ──
        // Main thunder at player
        playConfiguredSound(world, playerLoc,
                "breakthrough.sounds.lightning-main",
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER,
                3.0f, 0.8f, rand.nextFloat() * 0.4f);
        // Distant thunder for ambient bolts
        playConfiguredSound(world, playerLoc,
                "breakthrough.sounds.lightning-impact",
                Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER,
                2.0f, 0.6f, rand.nextFloat() * 0.8f);
        // Extra rumble in late phase
        if (progress > 0.5) {
            playConfiguredSound(world, playerLoc,
                    "breakthrough.sounds.lightning-rumble",
                    Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER,
                    4.0f, 0.4f);
        }
    }

    private void strikeNearbyPlayers(Player player, BreakthroughSession session, double mainDamage) {
        if (!session.isMajor || !plugin.getConfig().getBoolean(NEARBY_LIGHTNING_ENABLED_PATH, true)) {
            return;
        }

        double radius = Math.max(0.0, plugin.getConfig().getDouble(NEARBY_LIGHTNING_RADIUS_PATH, LIGHTNING_RADIUS));
        if (radius <= 0.0) {
            return;
        }

        double nearbyDamage = calculateNearbyPlayerDamage(
                mainDamage,
                plugin.getConfig().getDouble(NEARBY_LIGHTNING_DAMAGE_MULTIPLIER_PATH, 0.25)
        );
        if (nearbyDamage <= 0.0) {
            return;
        }

        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        double radiusSquared = radius * radius;
        for (Player nearby : world.getPlayers()) {
            if (nearby.equals(player) || nearby.isDead()
                    || nearby.getGameMode() == GameMode.CREATIVE
                    || nearby.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }

            world.strikeLightningEffect(nearby.getLocation());
            nearby.damage(nearbyDamage);
        }
    }

    /**
     * Spawn special particle effects based on realm tier
     */
    private void spawnBreakthroughParticles(Player player, Realm realm) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        switch (realm.getTier()) {
            case PHAM_GIOI:
                // Yellow-green particles
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 15, 1, 1, 1, 0.05);
                world.spawnParticle(Particle.END_ROD, loc, 15, 0.5, 1, 0.5, 0.05);
                break;
            case TIEN_GIOI:
                // Blue-purple particles
                world.spawnParticle(Particle.DRAGON_BREATH, loc, 40, 1, 1.5, 1, 0.05);
                world.spawnParticle(Particle.END_ROD, loc, 25, 1, 1.5, 1, 0.1);
                world.spawnParticle(Particle.ENCHANT, loc, 30, 0.5, 0.5, 0.5, 1.0);
                break;
            case THAN_GIOI:
                // Red-gold divine particles
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 50, 1.5, 2, 1.5, 0.1);
                world.spawnParticle(Particle.END_ROD, loc, 40, 2, 2, 2, 0.15);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 1, 1.5, 1, 0.3);
                world.spawnParticle(Particle.END_ROD, loc, 25, 1, 1, 1, 0.15);
                break;
        }
    }

    // ==========================================
    // WEATHER EFFECTS
    // ==========================================

    private void startWeatherEffects(Player player) {
        // Set thunderstorm for nearby players
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(player.getLocation()) <= realmManager.getWeatherRadius()) {
                nearby.setPlayerWeather(WeatherType.DOWNFALL);
            }
        }

        // Restore weather after breakthrough ends
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isInBreakthrough(player.getUniqueId())) {
                    for (Player nearby : player.getWorld().getPlayers()) {
                        nearby.resetPlayerWeather();
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ==========================================
    // SUCCESS / FAILURE HANDLING
    // ==========================================

    private void handleBreakthroughSuccess(Player player, BreakthroughSession session) {
        // Guard against double-execution (e.g. if previous run threw exception)
        if (session.completed) return;
        session.completed = true;

        UUID uuid = player.getUniqueId();
        activeSessions.remove(uuid);
        resumeFlight(uuid);
        cleanupActiveBreakthroughMobs(session);
        if (session.isMajor) {
            // Major realm breakthrough success
            Realm oldRealm = realmManager.getPlayerCurrentRealm(uuid);
            realmManager.advanceRealm(uuid);
            Realm newRealm = realmManager.getPlayerCurrentRealm(uuid);

            // Fire success event for other plugins to react
            Bukkit.getPluginManager().callEvent(
                    new RealmBreakthroughSuccessEvent(player, oldRealm, newRealm));

            // Grand success effects
            player.sendTitle(
                    "§a§l✨ ĐỘT PHÁ THÀNH CÔNG ✨",
                    newRealm.getFormattedName(),
                    10, 60, 20
            );

            // ── DRAMATIC SUCCESS EFFECTS ──
            Location loc = player.getLocation();
            World world = player.getWorld();

            try {
                // Massive particle explosion
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 200, 2, 3, 2, 0.8);
                world.spawnParticle(Particle.END_ROD, loc, 150, 3, 4, 3, 0.5);

                // Expanding shockwave rings (animated over 1 second)
                spawnSuccessShockwave(player);

                // Light pillar burst
                for (double y = 0; y < 20; y += 0.3) {
                    Location pillar = loc.clone().add(0, y, 0);
                    world.spawnParticle(Particle.END_ROD, pillar, 3, 0.2, 0, 0.2, 0.05);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Particle effect error in breakthrough success: " + t.getMessage());
            }

            // Spawn ModelEngine model on success
            spawnSuccessModel(player, true);

            // Sounds — layered for epicness
            playConfiguredSound(world, loc,
                    "breakthrough.sounds.major-success-primary",
                    Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER,
                    3.0f, 1.0f);
            playConfiguredSound(world, loc,
                    "breakthrough.sounds.major-success-secondary",
                    Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER,
                    2.0f, 1.5f);
            playConfiguredSound(world, loc,
                    "breakthrough.sounds.major-success-explosion",
                    Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER,
                    1.5f, 0.5f);

            // Broadcast
            String successMsg = "§a§l✨ " + player.getName() + " §a§lđã vượt Kiếp Lôi, đột phá thành công " 
                    + newRealm.getFormattedName() + "§a§l!";
            Bukkit.broadcastMessage(successMsg);

            // Apply stat bonuses for new realm
            realmManager.applyStatBonus(player);

            // ── SÉT TIẾP TỤC SAU THÀNH CÔNG (trong lúc animation actived) ──
            if (realmManager.isSuccessStormContinue()) {
                int stormInterval = realmManager.getSuccessStormInterval();
                int stormBolts = realmManager.getSuccessStormBolts();
                int durationTicks = plugin.getConfig().getInt("breakthrough.model-duration", 5) * 20;

                BukkitRunnable successStorm = new BukkitRunnable() {
                    int ticksLeft = durationTicks;

                    @Override
                    public void run() {
                        if (!player.isOnline() || ticksLeft <= 0) {
                            cancel();
                            return;
                        }

                        Location sLoc = player.getLocation();
                        World sWorld = player.getWorld();
                        ThreadLocalRandom sRand = ThreadLocalRandom.current();

                        for (int i = 0; i < stormBolts; i++) {
                            double sx = sRand.nextDouble(-LIGHTNING_RADIUS, LIGHTNING_RADIUS + 1);
                            double sz = sRand.nextDouble(-LIGHTNING_RADIUS, LIGHTNING_RADIUS + 1);
                            Location sStrike = sLoc.clone().add(sx, 0, sz);
                            sStrike.setY(sWorld.getHighestBlockYAt(sStrike) + 1);
                            sWorld.strikeLightningEffect(sStrike);
                        }

                        ticksLeft -= stormInterval;
                    }
                };
                successStorm.runTaskTimer(plugin, 0L, stormInterval);
            }

        } else {
            // Sub-realm breakthrough success
            PlayerRealm pr = realmManager.getPlayerRealm(uuid);
            SubRealm oldSub = pr.getSubRealm();
            pr.setSubRealm(session.targetSubRealm);
            realmManager.savePlayerRealm(uuid);

            Realm realm = realmManager.getPlayerCurrentRealm(uuid);

            // Fire sub-realm advance event for other plugins
            Bukkit.getPluginManager().callEvent(
                    new SubRealmAdvanceEvent(player, realm, oldSub, session.targetSubRealm));

            player.sendTitle(
                    "§a✨ Đột Phá Thành Công",
                    realm.getFormattedName() + " §7— §e" + session.targetSubRealm.getDisplayName(),
                    10, 40, 10
            );

            // Lighter celebration
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 30, 1, 1, 1, 0.2);
            playConfiguredSound(player.getWorld(), loc,
                    "breakthrough.sounds.sub-success",
                    Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER,
                    2.0f, 1.5f);

            // Spawn ModelEngine model on success
            spawnSuccessModel(player, false);

            String successMsg = "§a✨ " + player.getName() + " đã đột phá → " 
                    + realm.getFormattedName() + " §7— §e" + session.targetSubRealm.getDisplayName();
            broadcastNearby(player, successMsg, realmManager.getSubRealmBroadcastRadius());

            // Re-apply stat bonuses (sub-realm doesn't change stats, but keep consistent)
            realmManager.applyStatBonus(player);
        }

        // Reset weather
        resetWeather(player);
    }

    /**
     * Handle player death during breakthrough (failure)
     */
    private void handleBreakthroughDeath(Player player) {
        UUID uuid = player.getUniqueId();
        playersInCountdown.remove(uuid);
        BreakthroughSession session = activeSessions.remove(uuid);
        if (session == null) return;

        resumeFlight(uuid);

        // Cancel lightning task
        if (session.task != null) {
            session.task.cancel();
        }
        // Cancel aura task
        if (session.auraTask != null) {
            session.auraTask.cancel();
        }
        // Cancel storm task
        if (session.stormTask != null) {
            session.stormTask.cancel();
        }
        // Remove all breakthrough effects
        cleanupBreakthroughEffects(player);
        cleanupActiveBreakthroughMobs(session);

        if (session.isMajor) {
            // Apply cooldown + demote
            Realm beforeDemote = realmManager.getPlayerCurrentRealm(uuid);
            realmManager.handleBreakthroughFailure(uuid);
            Realm afterDemote = realmManager.getPlayerCurrentRealm(uuid);

            // Fire fail event for other plugins
            Bukkit.getPluginManager().callEvent(
                    new RealmBreakthroughFailEvent(player, afterDemote, session.targetRealm));

            // Broadcast failure
            String failMsg = "§c§l💀 " + player.getName() + " §c§lkhông chống nổi Thiên Lôi, đột phá thất bại...";
            Bukkit.broadcastMessage(failMsg);

            // Player gets message on respawn
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.sendMessage("§c§l💀 Đột phá thất bại!");
                        if (realmManager.isFailDemoteEnabled() && beforeDemote != null && afterDemote != null
                                && beforeDemote.getId() != afterDemote.getId()) {
                            player.sendMessage("§c  → §4§lTỤT CẢNH GIỚI: " + beforeDemote.getFormattedName()
                                    + " §c→ " + afterDemote.getFormattedName());
                        }
                        player.sendMessage("§c  → Mất 50% Đột Phá Đan đã sử dụng");
                        player.sendMessage("§c  → Cooldown: " + (realmManager.getCooldownSeconds() / 60) + " phút");
                        player.sendMessage("§a  → Tu Vi và trang bị vẫn giữ nguyên");
                        player.sendTitle(
                                "§c§l💀 ĐỘT PHÁ THẤT BẠI",
                                "§7Cảnh giới tụt xuống " + (afterDemote != null ? afterDemote.getFormattedName() : ""),
                                10, 60, 20
                        );

                        // Update stat bonuses for demoted realm
                        realmManager.applyStatBonus(player);
                    }
                }
            }.runTaskLater(plugin, 40L); // Delay to after respawn
        } else {
            // Sub-realm failure (rare, since 100% success rate)
            String failMsg = "§c💀 " + player.getName() + " đã gục ngã trong Tiểu Lôi Kiếp...";
            broadcastNearby(player, failMsg, realmManager.getSubRealmBroadcastRadius());
        }

        // Reset weather
        resetWeather(player);
    }

    /**
     * Cancel a session (player quit, etc.)
     */
    private void cancelSession(BreakthroughSession session) {
        playersInCountdown.remove(session.playerId);
        activeSessions.remove(session.playerId);
        if (session.task != null) {
            session.task.cancel();
        }
        if (session.auraTask != null) {
            session.auraTask.cancel();
        }
        if (session.stormTask != null) {
            session.stormTask.cancel();
        }
        if (session.activeAnimationTask != null) {
            session.activeAnimationTask.cancel();
        }
        cleanupActiveBreakthroughMobs(session);
        resumeFlight(session.playerId);
        // Remove all effects for the player
        Player p = Bukkit.getPlayer(session.playerId);
        if (p != null && p.isOnline()) {
            cleanupBreakthroughEffects(p);
            resetWeather(p);
        }
        // Apply cooldown for major breakthrough
        if (session.isMajor) {
            realmManager.handleBreakthroughFailure(session.playerId);
        }
    }

    private void resetWeather(Player player) {
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(player.getLocation()) <= realmManager.getWeatherRadius()) {
                nearby.resetPlayerWeather();
            }
        }
    }

    private void broadcastNearby(Player player, String message, int radius) {
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(player.getLocation()) <= radius) {
                nearby.sendMessage(message);
            }
        }
    }

    // ==========================================
    // MODELENGINE SUCCESS MODEL
    // ==========================================

    /**
     * Spawn model đột phá: ArmorStand + Model + Player cưỡi lên.
     * Player tàng hình trong lúc animation chạy, hiện lại khi animation kết thúc.
     */
    private void spawnSuccessModel(Player player, boolean isMajor) {
        if (!plugin.getConfig().getBoolean("breakthrough.enabled", true)) return;
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) return;

        List<String> modelIds = getSuccessModelIds(plugin.getConfig(), isMajor);
        if (modelIds.isEmpty()) return;

        int duration = plugin.getConfig().getInt("breakthrough.model-duration", 5);
        List<ArmorStand> stands = new ArrayList<>();

        try {
            Location loc = player.getLocation();
            List<com.ticxo.modelengine.api.model.ActiveModel> activeModels = new ArrayList<>();

            for (String modelId : modelIds) {
                // Spawn one invisible ArmorStand per model so ModelEngine can animate them together.
                ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setSmall(true);
                stand.setBasePlate(false);
                stand.setInvulnerable(true);

                com.ticxo.modelengine.api.model.ActiveModel activeModel =
                        com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(modelId);
                if (activeModel == null) {
                    stand.remove();
                    plugin.getLogger().warning("ModelEngine model not found: " + modelId);
                    continue;
                }

                com.ticxo.modelengine.api.model.ModeledEntity modeledEntity =
                        com.ticxo.modelengine.api.ModelEngineAPI.createModeledEntity(stand);
                modeledEntity.addModel(activeModel, true);
                stands.add(stand);
                activeModels.add(activeModel);
            }

            if (stands.isEmpty()) return;

            // Player chỉ cưỡi model đầu tiên; các model còn lại chạy đồng vị trí.
            stands.getFirst().addPassenger(player);

            // ── TÀN HÌNH PLAYER trong lúc animation ──
            // Add invisibility potion effect
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, duration * 20 + 20, 0,
                    false, false, false // No particles, no icon
            ));
            // Hide player from all other players
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hidePlayer(plugin, player);
                }
            }

            plugin.getLogger().info("Breakthrough models " + modelIds + " — player " + player.getName() + " mounted (invisible)");

            // Delay 2 ticks rồi chạy animation "actived" cho tất cả model.
            final List<com.ticxo.modelengine.api.model.ActiveModel> finalModels = List.copyOf(activeModels);
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (com.ticxo.modelengine.api.model.ActiveModel model : finalModels) {
                        try {
                            model.getAnimationHandler().playAnimation("actived", 0.25, 0.25, 1.0, true);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to play 'actived' animation: " + e.getMessage());
                        }
                    }
                    plugin.getLogger().info("Playing 'actived' animation on breakthrough models " + modelIds);
                }
            }.runTaskLater(plugin, 2L);

            // Sau duration giây: dismount, xóa toàn bộ model, HIỆN LẠI player
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (ArmorStand stand : stands) {
                        try {
                            stand.removePassenger(player);
                            com.ticxo.modelengine.api.model.ModeledEntity me =
                                    com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(stand);
                            if (me != null) {
                                me.destroy();
                            }
                        } catch (Exception ignored) {
                        }
                        stand.remove();
                    }

                    // ── HIỆN LẠI PLAYER ──
                    if (player.isOnline()) {
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (!online.equals(player)) {
                                online.showPlayer(plugin, player);
                            }
                        }
                        // Re-appear effects
                        try {
                            Location pLoc = player.getLocation();
                            player.getWorld().spawnParticle(Particle.END_ROD, pLoc.clone().add(0, 1, 0), 50, 1, 2, 1, 0.3);
                            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pLoc, 30, 1, 1, 1, 0.5);
                            playConfiguredSound(player.getWorld(), pLoc,
                                    "breakthrough.sounds.model-return",
                                    Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER,
                                    2.0f, 1.2f);
                        } catch (Throwable t) {
                            // Particle API errors should not prevent player from becoming visible
                        }
                        plugin.getLogger().info("Player " + player.getName() + " is now visible again");
                    }
                }
            }.runTaskLater(plugin, duration * 20L);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn breakthrough models: " + modelIds + " — " + e.getMessage());
            for (ArmorStand stand : stands) {
                try {
                    com.ticxo.modelengine.api.model.ModeledEntity me =
                            com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(stand);
                    if (me != null) {
                        me.destroy();
                    }
                } catch (Exception ignored) {
                }
                stand.remove();
            }
            // Ensure player is visible if model spawn fails
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.showPlayer(plugin, player);
                }
            }
        }
    }

    static List<String> getSuccessModelIds(ConfigurationSection config, boolean isMajor) {
        String listKey = isMajor ? "breakthrough.major-success-models" : "breakthrough.sub-success-models";
        List<String> models = new ArrayList<>();

        if (config.isList(listKey)) {
            for (String modelId : config.getStringList(listKey)) {
                String trimmed = modelId.trim();
                if (!trimmed.isEmpty()) {
                    models.add(trimmed);
                }
            }
            return models;
        }

        String legacyKey = isMajor ? "breakthrough.major-success-model" : "breakthrough.sub-success-model";
        String legacyModel = config.getString(legacyKey, "");
        if (legacyModel != null && !legacyModel.trim().isEmpty()) {
            models.add(legacyModel.trim());
        }

        return models;
    }

    private void spawnActiveBreakthroughMobs(Player player, BreakthroughSession session) {
        if (!plugin.getConfig().getBoolean("breakthrough.enabled", true)) return;
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return;

        List<String> mobIds = getConfiguredModelIds(ACTIVE_BREAKTHROUGH_MOBS_KEY);
        if (mobIds.isEmpty()) return;

        Location loc = player.getLocation();
        for (String mobId : mobIds) {
            try {
                Optional<MythicMob> mythicMob = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
                if (mythicMob.isEmpty()) {
                    plugin.getLogger().warning("Configured breakthrough MythicMob not found: " + mobId);
                    continue;
                }

                ActiveMob activeMob = mythicMob.get().spawn(BukkitAdapter.adapt(loc), 1.0, SpawnReason.SUMMON);
                if (activeMob != null) {
                    session.activeBreakthroughMobs.add(activeMob);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to spawn breakthrough MythicMob '" + mobId + "': " + t.getMessage());
            }
        }

        if (!session.activeBreakthroughMobs.isEmpty()) {
            startActiveBreakthroughAnimationTask(player, session);
        }
    }

    private void startActiveBreakthroughAnimationTask(Player player, BreakthroughSession session) {
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) return;

        session.activeAnimationTask = new BukkitRunnable() {
            int ticks;
            int ticksSinceMobSync;
            boolean playedInitialAnimation;

            @Override
            public void run() {
                if (!player.isOnline() || !activeSessions.containsKey(session.playerId)) {
                    cancel();
                    return;
                }

                ticks += 2;
                if (!playedInitialAnimation) {
                    playedInitialAnimation = true;
                    replayActiveBreakthroughAnimation(session);
                }
                ticksSinceMobSync += 2;
                if (ticksSinceMobSync >= ACTIVE_BREAKTHROUGH_MOB_SYNC_TICKS) {
                    ticksSinceMobSync = 0;
                    syncActiveBreakthroughMobs(session, player.getLocation());
                }
                if (ticks >= ACTIVE_BREAKTHROUGH_ANIMATION_REPLAY_TICKS) {
                    ticks = 0;
                    replayActiveBreakthroughAnimation(session);
                }
            }
        };
        session.activeAnimationTask.runTaskTimer(plugin, 2L, 2L);
    }

    private void syncActiveBreakthroughMobs(BreakthroughSession session, Location loc) {
        if (loc == null) {
            return;
        }

        for (ActiveMob activeMob : session.activeBreakthroughMobs) {
            try {
                if (activeMob == null || activeMob.isDead()) continue;

                org.bukkit.entity.Entity entity = BukkitAdapter.adapt(activeMob.getEntity());
                if (entity != null && entity.isValid() && shouldSyncBreakthroughMob(entity, loc)) {
                    entity.teleport(loc);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to move breakthrough MythicMob with player: " + t.getMessage());
            }
        }
    }

    private boolean shouldSyncBreakthroughMob(org.bukkit.entity.Entity entity, Location loc) {
        Location entityLocation = entity.getLocation();
        if (entityLocation.getWorld() == null || loc.getWorld() == null || !entityLocation.getWorld().equals(loc.getWorld())) {
            return true;
        }
        return entityLocation.distanceSquared(loc) > ACTIVE_BREAKTHROUGH_MOB_SYNC_DISTANCE_SQUARED;
    }

    private void replayActiveBreakthroughAnimation(BreakthroughSession session) {
        String animation = plugin.getConfig().getString(ACTIVE_BREAKTHROUGH_ANIMATION_KEY, "spawn");
        if (animation == null || animation.trim().isEmpty() || animation.equalsIgnoreCase("none")) {
            return;
        }
        final String animationName = animation.trim();

        for (ActiveMob activeMob : session.activeBreakthroughMobs) {
            try {
                if (activeMob == null || activeMob.isDead()) continue;

                org.bukkit.entity.Entity entity = BukkitAdapter.adapt(activeMob.getEntity());
                ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);
                if (modeledEntity == null || modeledEntity.isDestroyed()) continue;

                for (ActiveModel activeModel : modeledEntity.getModels().values()) {
                    activeModel.getAnimationHandler().playAnimation(animationName, 0.1, 0.1, 1.0, true);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to replay breakthrough ModelEngine animation: " + t.getMessage());
            }
        }
    }

    private void cleanupActiveBreakthroughMobs(BreakthroughSession session) {
        if (session.activeAnimationTask != null) {
            session.activeAnimationTask.cancel();
            session.activeAnimationTask = null;
        }

        for (ActiveMob activeMob : session.activeBreakthroughMobs) {
            try {
                if (activeMob != null && !activeMob.isDead()) {
                    activeMob.remove();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to remove breakthrough MythicMob: " + t.getMessage());
            }
        }
        session.activeBreakthroughMobs.clear();
    }

    private List<String> getConfiguredModelIds(String key) {
        List<String> models = new ArrayList<>();
        if (!plugin.getConfig().isList(key)) return models;

        for (String modelId : plugin.getConfig().getStringList(key)) {
            String trimmed = modelId.trim();
            if (!trimmed.isEmpty()) {
                models.add(trimmed);
            }
        }

        return models;
    }

    /**
     * Spawn expanding shockwave rings on breakthrough success.
     * 5 rings expanding outward over 1 second.
     */
    private void spawnSuccessShockwave(Player player) {
        Location center = player.getLocation().clone().add(0, 1, 0);
        World world = player.getWorld();

        for (int wave = 0; wave < 5; wave++) {
            final int waveIndex = wave;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    double radius = 2 + waveIndex * 3; // 2, 5, 8, 11, 14 blocks
                    int points = 24 + waveIndex * 8; // More points for larger rings
                    for (int i = 0; i < points; i++) {
                        double angle = (2 * Math.PI / points) * i;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        Location ringLoc = center.clone().add(x, 0, z);
                        world.spawnParticle(Particle.CLOUD, ringLoc, 1, 0, 0, 0, 0.02);
                        world.spawnParticle(Particle.END_ROD, ringLoc, 1, 0, 0.2, 0, 0.05);
                    }
                    // Sound for each wave
                    playConfiguredSound(world, center,
                            "breakthrough.sounds.success-shockwave",
                            Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.MASTER,
                            1.5f, 0.5f, waveIndex * 0.2f);
                }
            }.runTaskLater(plugin, wave * 4L); // 4 ticks apart (0.2 sec)
        }
    }

    private void playConfiguredSound(World world, Location location, String path,
                                     Sound fallbackSound, SoundCategory fallbackCategory,
                                     float fallbackVolume, float fallbackPitch) {
        playConfiguredSound(world, location, path, fallbackSound, fallbackCategory,
                fallbackVolume, fallbackPitch, 0.0f);
    }

    private void playConfiguredSound(World world, Location location, String path,
                                     Sound fallbackSound, SoundCategory fallbackCategory,
                                     float fallbackVolume, float fallbackPitch,
                                     float pitchOffset) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            world.playSound(location, fallbackSound, fallbackCategory,
                    clampVolume(fallbackVolume), clampPitch(fallbackPitch + pitchOffset));
            return;
        }

        String soundName = section.getString("sound", fallbackSound.name());
        if (soundName == null || soundName.trim().isEmpty() || soundName.equalsIgnoreCase("NONE")) {
            return;
        }

        Sound sound = parseSound(soundName, fallbackSound);
        SoundCategory category = parseCategory(section.getString("category"), fallbackCategory);
        float volume = (float) section.getDouble("volume", fallbackVolume);
        float pitch = (float) section.getDouble("pitch", fallbackPitch);

        playConfiguredSoundByName(world, location, soundName, sound, category,
                clampVolume(volume), clampPitch(pitch + pitchOffset));
    }

    private Sound parseSound(String soundName, Sound fallbackSound) {
        try {
            return Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallbackSound;
        }
    }

    private void playConfiguredSoundByName(World world, Location location, String configuredSoundName,
                                           Sound fallbackSound, SoundCategory category,
                                           float volume, float pitch) {
        String normalizedSoundName = configuredSoundName == null ? "" : configuredSoundName.trim();
        if (normalizedSoundName.isEmpty() || normalizedSoundName.equalsIgnoreCase("NONE")) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(normalizedSoundName.toUpperCase(Locale.ROOT));
            world.playSound(location, sound, category, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            world.playSound(location, normalizedSoundName, category, volume, pitch);
        }
    }

    private SoundCategory parseCategory(String categoryName, SoundCategory fallbackCategory) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return fallbackCategory;
        }

        try {
            return SoundCategory.valueOf(categoryName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallbackCategory;
        }
    }

    private float clampVolume(float volume) {
        return Math.max(0.0f, volume);
    }

    private float clampPitch(float pitch) {
        return Math.max(0.0f, Math.min(2.0f, pitch));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        // During countdown phase: block horizontal movement completely
        if (playersInCountdown.contains(uuid)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            
            // Allow vertical movement (for any existing velocity/levitation)
            // but block horizontal (X/Z) movement
            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                // Cancel horizontal movement
                event.setCancelled(true);
            }
            return;
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Block all commands during breakthrough (countdown + lightning phase)
        if (playersInCountdown.contains(uuid) || isInBreakthrough(uuid)) {
            event.setCancelled(true);
            player.sendMessage("§c⚡ Không thể sử dụng lệnh trong lúc đột phá!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        playersInCountdown.remove(player.getUniqueId());
        if (isInBreakthrough(player.getUniqueId())) {
            handleBreakthroughDeath(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playersInCountdown.remove(uuid);
        BreakthroughSession session = activeSessions.get(uuid);
        if (session != null) {
            cancelSession(session);
            plugin.getLogger().info(event.getPlayer().getName() + " quit during breakthrough — session cancelled.");
        }
    }

    /**
     * Cleanup all active sessions (called on plugin disable)
     */
    public void cleanup() {
        for (BreakthroughSession session : activeSessions.values()) {
            if (session.task != null) {
                session.task.cancel();
            }
            if (session.auraTask != null) {
                session.auraTask.cancel();
            }
            if (session.stormTask != null) {
                session.stormTask.cancel();
            }
            if (session.activeAnimationTask != null) {
                session.activeAnimationTask.cancel();
            }
            cleanupActiveBreakthroughMobs(session);
            resumeFlight(session.playerId);
        }
        activeSessions.clear();
    }
}
