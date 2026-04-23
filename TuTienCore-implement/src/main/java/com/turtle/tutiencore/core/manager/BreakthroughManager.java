package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.event.RealmBreakthroughEvent;
import com.turtle.tutiencore.api.event.RealmBreakthroughFailEvent;
import com.turtle.tutiencore.api.event.RealmBreakthroughSuccessEvent;
import com.turtle.tutiencore.api.event.SubRealmAdvanceEvent;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quản lý quá trình Đột Phá Cảnh Giới — Thiên Lôi Kiếp ⚡
 * 
 * Khi người chơi kích hoạt đột phá:
 * 1. Roll tỉ lệ thành công (ẩn)
 * 2. Trời tối, sấm rền
 * 3. Sét giáng lần lượt (2-3 giây/tia)
 * 4. Sống sót = thành công, Chết = thất bại
 */
public class BreakthroughManager implements Listener {

    private final JavaPlugin plugin;
    private final RealmManager realmManager;

    // Players currently undergoing breakthrough
    private final Map<UUID, BreakthroughSession> activeSessions = new HashMap<>();

    public BreakthroughManager(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ==========================================
    // BREAKTHROUGH SESSION
    // ==========================================

    /**
     * Represents an active breakthrough attempt
     */
    private static class BreakthroughSession {
        final UUID playerId;
        final boolean isMajor; // true = Đại Cảnh Giới, false = Tiểu Cảnh Giới
        final int totalBolts;
        final double damagePerBolt;
        final boolean rollSuccess;
        final double failMultiplier;
        final Realm targetRealm; // null for sub-realm
        final SubRealm targetSubRealm; // null for major realm
        int boltsRemaining;
        BukkitRunnable task;

        BreakthroughSession(UUID playerId, boolean isMajor, int totalBolts,
                            double damagePerBolt, boolean rollSuccess, double failMultiplier,
                            Realm targetRealm, SubRealm targetSubRealm) {
            this.playerId = playerId;
            this.isMajor = isMajor;
            this.totalBolts = totalBolts;
            this.damagePerBolt = damagePerBolt;
            this.rollSuccess = rollSuccess;
            this.failMultiplier = failMultiplier;
            this.targetRealm = targetRealm;
            this.targetSubRealm = targetSubRealm;
            this.boltsRemaining = totalBolts;
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

        // Roll success
        double successRate = nextRealm.getSuccessRate();
        boolean rollSuccess = ThreadLocalRandom.current().nextDouble(100.0) < successRate;

        double actualDmg = rollSuccess ? nextRealm.getDamagePerBolt() 
                                       : nextRealm.getDamagePerBolt() * realmManager.getFailDamageMultiplier();

        BreakthroughSession session = new BreakthroughSession(
                uuid, true,
                nextRealm.getLightningBolts(), actualDmg,
                rollSuccess, realmManager.getFailDamageMultiplier(),
                nextRealm, null
        );

        activeSessions.put(uuid, session);

        // Broadcast start
        String startMsg = "§e§l⚡ " + player.getName() + " §e§lđang vượt §c§lThiên Lôi Kiếp §e§lđể đột phá " 
                + nextRealm.getFormattedName() + "§e§l!";
        Bukkit.broadcastMessage(startMsg);

        // Start weather effects
        startWeatherEffects(player);

        // Begin lightning sequence
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

        // Check Tu Vi
        Realm realm = realmManager.getPlayerCurrentRealm(uuid);
        long required = realm.getTuViForSubRealm(nextSub);
        double tuVi = com.turtle.tutiencore.api.TuTien.getApi().getTuVi(uuid);
        if (tuVi < required) {
            player.sendMessage("§cTu Vi chưa đủ! Cần: §e" + RealmManager.formatNumber(required) 
                    + " §c| Hiện tại: §e" + RealmManager.formatNumber((long) tuVi));
            return;
        }

        int bolts = realmManager.getSubRealmBolts(currentSub);
        double dmg = realmManager.getSubRealmDmg(currentSub);

        BreakthroughSession session = new BreakthroughSession(
                uuid, false,
                bolts, dmg,
                true, 1.0, // Sub-realm always 100% success
                null, nextSub
        );

        activeSessions.put(uuid, session);

        // Local broadcast only
        String startMsg = "§e⚡ " + player.getName() + " §eđang đột phá tầng nhỏ → " + nextSub.getDisplayName() + "!";
        broadcastNearby(player, startMsg, realmManager.getSubRealmBroadcastRadius());

        // Lighter effects for sub-realm
        startLightningSequence(player, session);
    }

    // ==========================================
    // LIGHTNING SEQUENCE
    // ==========================================

    private void startLightningSequence(Player player, BreakthroughSession session) {
        int intervalTicks = realmManager.getLightningIntervalTicks();

        // Countdown before first bolt
        player.sendMessage("§6§l⚡ Thiên Lôi Kiếp bắt đầu trong 3 giây... Chuẩn bị!");

        session.task = new BukkitRunnable() {
            int countdown = 3;
            boolean started = false;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelSession(session);
                    cancel();
                    return;
                }

                // Countdown phase
                if (!started) {
                    if (countdown > 0) {
                        player.sendTitle(
                                "§c§l" + countdown,
                                "§e⚡ Thiên Lôi sắp giáng!",
                                5, 15, 5
                        );
                        // Thunder sound during countdown
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 
                                SoundCategory.WEATHER, 2.0f, 0.5f);
                        countdown--;
                        return;
                    } else {
                        started = true;
                        player.sendTitle(
                                "§c§l⚡ THIÊN LÔI KIẾP ⚡",
                                session.isMajor ? "§eSống sót qua " + session.totalBolts + " tia sét!" 
                                               : "§eTiểu Lôi Kiếp — " + session.totalBolts + " tia sét",
                                10, 40, 10
                        );
                        return;
                    }
                }

                // Lightning phase
                if (session.boltsRemaining > 0) {
                    strikeLightning(player, session);
                    session.boltsRemaining--;

                    int boltNumber = session.totalBolts - session.boltsRemaining;
                    // Show action bar progress
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                                    "§c⚡ Tia sét " + boltNumber + "/" + session.totalBolts 
                                    + " §7| §c" + String.format("%.1f", session.damagePerBolt) + " ❤ DMG"));

                } else {
                    // All bolts survived → SUCCESS
                    handleBreakthroughSuccess(player, session);
                    cancel();
                }
            }
        };

        // Run countdown every second (20 ticks), then lightning at interval
        session.task.runTaskTimer(plugin, 20L, intervalTicks);
    }

    /**
     * Strike lightning on the player with damage
     */
    private void strikeLightning(Player player, BreakthroughSession session) {
        Location loc = player.getLocation();

        // Visual lightning (no natural damage)
        player.getWorld().strikeLightningEffect(loc);

        // Custom damage (hearts → actual HP: 1 heart = 2 HP)
        double damage = session.damagePerBolt * 2.0; // Convert hearts to HP

        // Apply damage
        player.damage(damage);

        // Particle effects based on realm tier
        if (session.isMajor && session.targetRealm != null) {
            spawnBreakthroughParticles(player, session.targetRealm);
        } else {
            // Lighter particles for sub-realm
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        }

        // Sound effects
        player.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 3.0f, 1.0f);
        player.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 2.0f, 1.0f);
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
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 30, 1, 1, 1, 0.1);
                world.spawnParticle(Particle.END_ROD, loc, 15, 0.5, 1, 0.5, 0.05);
                break;
            case TIEN_GIOI:
                // Blue-purple particles
                world.spawnParticle(Particle.DRAGON_BREATH, loc, 40, 1, 1.5, 1, 0.05);
                world.spawnParticle(Particle.END_ROD, loc, 25, 1, 1.5, 1, 0.1);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 30, 0.5, 0.5, 0.5, 0.2);
                break;
            case THAN_GIOI:
                // Red-gold divine particles
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 50, 1.5, 2, 1.5, 0.1);
                world.spawnParticle(Particle.END_ROD, loc, 40, 2, 2, 2, 0.15);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 1, 1.5, 1, 0.3);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 40, 1, 1, 1, 0.2);
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
        UUID uuid = player.getUniqueId();
        activeSessions.remove(uuid);

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

            // Particles celebration
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 100, 1, 2, 1, 0.5);
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 80, 2, 3, 2, 0.3);

            // Spawn ModelEngine model on success
            spawnSuccessModel(player, true);

            // Sound
            player.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 3.0f, 1.0f);

            // Broadcast
            String successMsg = "§a§l✨ " + player.getName() + " §a§lđã vượt Kiếp Lôi, đột phá thành công " 
                    + newRealm.getFormattedName() + "§a§l!";
            Bukkit.broadcastMessage(successMsg);

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
            player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 2.0f, 1.5f);

            // Spawn ModelEngine model on success
            spawnSuccessModel(player, false);

            String successMsg = "§a✨ " + player.getName() + " đã đột phá → " 
                    + realm.getFormattedName() + " §7— §e" + session.targetSubRealm.getDisplayName();
            broadcastNearby(player, successMsg, realmManager.getSubRealmBroadcastRadius());
        }

        // Reset weather
        resetWeather(player);
    }

    /**
     * Handle player death during breakthrough (failure)
     */
    private void handleBreakthroughDeath(Player player) {
        UUID uuid = player.getUniqueId();
        BreakthroughSession session = activeSessions.remove(uuid);
        if (session == null) return;

        // Cancel lightning task
        if (session.task != null) {
            session.task.cancel();
        }

        if (session.isMajor) {
            // Apply cooldown
            realmManager.handleBreakthroughFailure(uuid);

            // Fire fail event for other plugins
            Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
            Bukkit.getPluginManager().callEvent(
                    new RealmBreakthroughFailEvent(player, currentRealm, session.targetRealm));

            // Broadcast failure
            String failMsg = "§c§l💀 " + player.getName() + " §c§lkhông chống nổi Thiên Lôi, đột phá thất bại...";
            Bukkit.broadcastMessage(failMsg);

            // Player gets message on respawn
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.sendMessage("§c§l💀 Đột phá thất bại!");
                        player.sendMessage("§c  → Mất 50% Đột Phá Đan đã sử dụng");
                        player.sendMessage("§c  → Cooldown: " + (realmManager.getCooldownSeconds() / 60) + " phút");
                        player.sendMessage("§a  → Tu Vi và trang bị vẫn giữ nguyên");
                        player.sendTitle(
                                "§c§l💀 ĐỘT PHÁ THẤT BẠI",
                                "§7Hãy chuẩn bị kỹ hơn và thử lại...",
                                10, 60, 20
                        );
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
        activeSessions.remove(session.playerId);
        if (session.task != null) {
            session.task.cancel();
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
     * Giống cách TuLuyenManager — player ride ArmorStand nên animation hiển thị trên người chơi.
     * Sau duration giây sẽ tự dismount + xóa.
     */
    private void spawnSuccessModel(Player player, boolean isMajor) {
        if (!plugin.getConfig().getBoolean("breakthrough.enabled", true)) return;
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) return;

        String configKey = isMajor ? "breakthrough.major-success-model" : "breakthrough.sub-success-model";
        String modelId = plugin.getConfig().getString(configKey, "");
        if (modelId == null || modelId.trim().isEmpty()) return;

        int duration = plugin.getConfig().getInt("breakthrough.model-duration", 5);

        try {
            // Spawn invisible ArmorStand tại vị trí player
            Location loc = player.getLocation();
            ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setInvulnerable(true);

            // Tạo và gắn ModelEngine model lên ArmorStand
            com.ticxo.modelengine.api.model.ActiveModel activeModel =
                    com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(modelId);
            if (activeModel == null) {
                stand.remove();
                plugin.getLogger().warning("ModelEngine model not found: " + modelId);
                return;
            }

            com.ticxo.modelengine.api.model.ModeledEntity modeledEntity =
                    com.ticxo.modelengine.api.ModelEngineAPI.createModeledEntity(stand);
            modeledEntity.addModel(activeModel, true);

            // Player cưỡi lên ArmorStand → animation sẽ hiển thị trên người chơi
            stand.addPassenger(player);

            plugin.getLogger().info("Breakthrough model '" + modelId + "' — player " + player.getName() + " mounted");

            // Delay 2 ticks rồi chạy animation "actived"
            final com.ticxo.modelengine.api.model.ActiveModel finalModel = activeModel;
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        finalModel.getAnimationHandler().playAnimation("actived", 0.25, 0.25, 1.0, true);
                        plugin.getLogger().info("Playing 'actived' animation on model '" + modelId + "'");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to play 'actived' animation: " + e.getMessage());
                    }
                }
            }.runTaskLater(plugin, 2L);

            // Sau duration giây: dismount player, xóa model + ArmorStand
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // Dismount player
                        stand.removePassenger(player);
                        // Destroy model
                        com.ticxo.modelengine.api.model.ModeledEntity me =
                                com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(stand);
                        if (me != null) {
                            me.destroy();
                        }
                    } catch (Exception ignored) {}
                    // Xóa ArmorStand
                    stand.remove();
                }
            }.runTaskLater(plugin, duration * 20L);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn breakthrough model: " + modelId + " — " + e.getMessage());
        }
    }

    // ==========================================
    // EVENT HANDLERS
    // ==========================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isInBreakthrough(player.getUniqueId())) {
            handleBreakthroughDeath(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
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
        }
        activeSessions.clear();
    }
}
