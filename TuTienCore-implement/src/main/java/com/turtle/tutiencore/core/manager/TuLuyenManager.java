package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.infusion.InfusionManager;
import com.turtle.tutiencore.core.hook.MMOCoreActionBarSuppressor;
import com.turtle.tutiencore.core.hook.TurtleIslandHook;
import com.turtle.tutiencore.core.task.TuLuyenParticleTask;
import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.event.TuViGainEvent;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.model.CuboidZone;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TuLuyenManager implements Listener {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long TULUYEN_QUEST_TRIGGER_TICKS = 30L * 60L * TICKS_PER_SECOND;
    private static final String TULUYEN_QUEST_TRIGGER_NAME = "daily_tuluyen_30m";
    private static final String FLY_SWORD_TYPE = "FLY_SWORD";
    private static final String FLY_SWORD_BUFF_PATH = "fly-sword.tuvi-buffs";
    private static final String DEFAULT_FLY_SWORD_SLOT = "fly_sword";
    private static final FlySwordTuViBuff NO_FLY_SWORD_BUFF =
            new FlySwordTuViBuff(0.0D, 0.0D, false, 0.0D, false);
    private static final Map<String, FlySwordTuViBuff> DEFAULT_FLY_SWORD_BUFFS = Map.of(
            "FLY_SWORD.THANH_PHONG_KIEM", new FlySwordTuViBuff(50.0D, 0.0D, false, 0.0D, false),
            "FLY_SWORD.DINH_BA_KIEM", new FlySwordTuViBuff(75.0D, 0.0D, true, 25.0D, false),
            "FLY_SWORD.HA_CAM_KIEM", new FlySwordTuViBuff(100.0D, 30.0D, false, 0.0D, false),
            "FLY_SWORD.NETHER_KIEM", new FlySwordTuViBuff(150.0D, 0.0D, false, 0.0D, true)
    );

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ZoneManager zoneManager;
    private final TuLuyenParticleTask lineTask;
    private final RealmManager realmManager;
    private final InfusionManager infusionManager;
    private final EquipmentMenuManager equipmentMenuManager;
    private final TurtleIslandHook turtleIslandHook;
    private MMOCoreActionBarSuppressor actionBarSuppressor;

    private final Map<UUID, ArmorStand> tuLuyenPlayers = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Object> holograms = new HashMap<>();
    private final Map<UUID, Long> sessionTicks = new HashMap<>();
    private final Map<UUID, Long> autoFlySwordTicks = new HashMap<>();
    private final Map<UUID, Long> capWarningTimes = new HashMap<>();
    private BukkitRunnable task;

    public TuLuyenManager(JavaPlugin plugin, ConfigManager config, ZoneManager zoneManager, TuLuyenParticleTask lineTask,
                          RealmManager realmManager, InfusionManager infusionManager, EquipmentMenuManager equipmentMenuManager) {
        this.plugin = plugin;
        this.configManager = config;
        this.zoneManager = zoneManager;
        this.lineTask = lineTask;
        this.realmManager = realmManager;
        this.infusionManager = infusionManager;
        this.equipmentMenuManager = equipmentMenuManager;
        this.turtleIslandHook = new TurtleIslandHook();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    public void startTask() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ArmorStand> entry : tuLuyenPlayers.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;
                    if (shouldSuppressCinematicUi(player)) {
                        hideBossBar(player);
                        continue;
                    }

                    long tick = sessionTicks.merge(player.getUniqueId(), 1L, Long::sum);
                    if (tick % TICKS_PER_SECOND == 0L) {
                        TuTien.getApi().addTuLuyenTotalSeconds(player.getUniqueId(), 1L);
                    }

                    int effectiveInterval = getEffectiveTuLuyenInterval(player);
                    TuLuyenReward previewReward = calculateReward(player, false);
                    updateVisuals(player, previewReward, tick, effectiveInterval);

                    if (shouldTriggerTuLuyenQuestMilestone(tick, TULUYEN_QUEST_TRIGGER_TICKS)) {
                        triggerTuLuyenQuestObjective(player);
                    }

                    if (tick % effectiveInterval != 0) {
                        continue;
                    }

                    TuLuyenReward reward = calculateReward(player, true);
                    if (reward.totalPoints <= 0) {
                        if (isAtTuViCap(player) && canContinueCultivatingAtCap(player)) {
                            Bukkit.getPluginManager().callEvent(createTuLuyenGainEvent(player, 0.0, reward.externalBonusIncluded));
                            triggerTurtleIslandCultivationFire(player);
                            continue;
                        }
                        warnTuViCapReached(player);
                        continue;
                    }

                    TuViGainEvent event = createTuLuyenGainEvent(player, reward.totalPoints, reward.externalBonusIncluded);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled() || event.getAmount() <= 0) {
                        continue;
                    }

                    double finalAmount = getCappedTuViReward(player, event.getAmount());
                    if (finalAmount <= 0.0) {
                        continue;
                    }

                    // Give Points via API, capped at the next breakthrough requirement even after external event edits.
                    TuTien.getApi().addTuVi(player.getUniqueId(), finalAmount);
                    triggerTurtleIslandCultivationFire(player);
                    if (infusionManager != null) {
                        infusionManager.rollHeldTuluyenDrops(player, reward.turtleIslandEligible);
                    }
                    if (reward.lightningTriggered) {
                        player.getWorld().strikeLightningEffect(player.getLocation());
                    }
                    playIntervalResetSound(player);
                    
                    if (!configManager.getMsgReceived().isEmpty() && !shouldSuppressCinematicUi(player)) {
                        String msg = "§6✦ " + configManager.getMsgReceived()
                                .replace("%points%", String.valueOf((int) finalAmount));
                        if (reward.bonusPercent > 0) {
                            msg += " §8┃ §a+" + formatPercent(reward.bonusPercent) + "% bonus";
                        }
                        if (reward.infusionBonusPercent > 0) {
                            msg += " §8┃ §d+" + formatPercent(reward.infusionBonusPercent) + "% Lửa Thần";
                        }
                        if (reward.lightningTriggered) {
                            msg += " §8┃ §bThiên Lôi x" + formatNumber(configManager.getLightningBonusMultiplier());
                        }
                        msg += " §8┃ §7Đang tu luyện...";
                        if (actionBarSuppressor != null) {
                            actionBarSuppressor.allowNextActionBar(player);
                        }
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
                    }
                }
                processAutoFlySwordCultivation();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ArmorStand stand : tuLuyenPlayers.values()) {
            stand.remove();
        }
        tuLuyenPlayers.clear();
        sessionTicks.clear();
        autoFlySwordTicks.clear();
        capWarningTimes.clear();
        for (UUID uuid : new ArrayList<>(bossBars.keySet())) {
            clearVisuals(uuid);
        }
    }

    public boolean isTuLuyen(Player player) {
        return tuLuyenPlayers.containsKey(player.getUniqueId());
    }

    public boolean isTuLuyenHologramVisible(Player player) {
        return player != null && isTuLuyen(player) && holograms.containsKey(player.getUniqueId());
    }

    public long getSessionSeconds(UUID uuid) {
        long ticks = sessionTicks.getOrDefault(uuid, 0L);
        return Math.max(0L, ticks / TICKS_PER_SECOND);
    }

    public void setActionBarSuppressor(MMOCoreActionBarSuppressor actionBarSuppressor) {
        this.actionBarSuppressor = actionBarSuppressor;
    }

    public void toggleTuLuyen(Player player) {
        if (isTuLuyen(player)) {
            stopTuLuyen(player);
        } else {
            startTuLuyen(player);
        }
    }

    public void startTuLuyen(Player player) {
        if (isAtTuViCap(player)) {
            if (!canContinueCultivatingAtCap(player)) {
                warnTuViCapReached(player);
                return;
            }
        }

        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getLocation().add(0, 0.1, 0), EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setInvulnerable(true);
        
        // Model Engine Hook
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
            String modelId = configManager.getTuluyenModel();
            if (modelId != null && !modelId.trim().isEmpty()) {
                try {
                    com.ticxo.modelengine.api.model.ActiveModel activeModel = com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(modelId);
                    if (activeModel != null) {
                        com.ticxo.modelengine.api.model.ModeledEntity modeledEntity = com.ticxo.modelengine.api.ModelEngineAPI.createModeledEntity(stand);
                        modeledEntity.addModel(activeModel, true);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load ModelEngine model: " + modelId);
                }
            }
        }
        
        stand.addPassenger(player);

        tuLuyenPlayers.put(player.getUniqueId(), stand);
        sessionTicks.put(player.getUniqueId(), 0L);
        createVisuals(player);

        // Refresh class-based particle colors
        lineTask.refreshPlayerColors(player);

        if (!configManager.getMsgStarted().isEmpty()) {
            player.sendMessage(configManager.getMsgStarted());
        }
    }

    public void stopTuLuyen(Player player) {
        ArmorStand stand = tuLuyenPlayers.remove(player.getUniqueId());
        sessionTicks.remove(player.getUniqueId());
        autoFlySwordTicks.remove(player.getUniqueId());
        capWarningTimes.remove(player.getUniqueId());
        clearVisuals(player.getUniqueId());
        if (stand != null) {
            stand.removePassenger(player);
            
            if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
                try {
                    com.ticxo.modelengine.api.model.ModeledEntity modeledEntity = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(stand);
                    if (modeledEntity != null) {
                        modeledEntity.destroy();
                    }
                } catch (Exception e) {}
            }
            
            stand.remove();
        }

        // Clear color cache
        lineTask.clearPlayerColors(player.getUniqueId());

        if (!configManager.getMsgStopped().isEmpty()) {
            player.sendMessage(configManager.getMsgStopped());
        }
    }

    public java.util.Collection<Player> getTuLuyenPlayers() {
        java.util.List<Player> players = new java.util.ArrayList<>();
        for (UUID uuid : tuLuyenPlayers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                players.add(p);
            }
        }
        return players;
    }

    @EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isTuLuyen(player)) {
                stopTuLuyen(player);
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking() && isTuLuyen(player)) {
            stopTuLuyen(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isTuLuyen(player)) {
                stopTuLuyen(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        autoFlySwordTicks.remove(event.getPlayer().getUniqueId());
        if (isTuLuyen(event.getPlayer())) {
            stopTuLuyen(event.getPlayer());
        }
    }

    /**
     * Get the total Tu Vi bonus percentage from player permissions.
     * Permission format: tutiencore.tuvi.bonus.<percent>
     * 
     * Examples:
     *   tutiencore.tuvi.bonus.20  → +20%
     *   tutiencore.tuvi.bonus.50  → +50%
     *   tutiencore.tuvi.bonus.100 → +100% (double)
     * 
     * If tu-luyen.permission-bonus.stack is enabled, all valid values are added together.
     * Otherwise, only the highest value is used.
     * 
     * LuckPerms setup:
     *   /lp group vip permission set tutiencore.tuvi.bonus.20
     *   /lp group svip permission set tutiencore.tuvi.bonus.50
     */
    private double getTuViBonus(Player player) {
        String prefix = "tutiencore.tuvi.bonus.";
        List<String> permissions = new ArrayList<>();

        for (org.bukkit.permissions.PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            String name = perm.getPermission();
            if (perm.getValue() && name.startsWith(prefix)) {
                permissions.add(name);
            }
        }

        return resolveTuViBonus(permissions, isTuViPermissionBonusStacked());
    }

    static double resolveHighestTuViBonus(Collection<String> permissions) {
        return resolveTuViBonus(permissions, false);
    }

    static double resolveTuViBonus(Collection<String> permissions, boolean stack) {
        double totalBonus = 0.0;
        double highestBonus = 0.0;

        for (String name : permissions) {
            double value = parseTuViBonusPermission(name);
            if (stack) {
                totalBonus += value;
            } else {
                highestBonus = Math.max(highestBonus, value);
            }
        }

        return stack ? totalBonus : highestBonus;
    }

    private boolean isTuViPermissionBonusStacked() {
        return plugin.getConfig().getBoolean("tu-luyen.permission-bonus.stack", true);
    }

    static double parseTuViBonusPermission(String permission) {
        String prefix = "tutiencore.tuvi.bonus.";
        if (!permission.startsWith(prefix)) return 0.0;

        try {
            return Double.parseDouble(permission.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private TuLuyenReward calculateReward(Player player, boolean rollLightning) {
        double basePoints = configManager.rollPointsPerInterval();
        double permissionBonus = getTuViBonus(player);
        double islandBonus = getTurtleIslandCultivationBonusPercent(player);
        double equipmentBonus = getEquipmentTuViBonus(player);
        FlySwordTuViBuff flySwordBuff = getEquippedFlySwordBuff(player);
        double flySwordBonus = flySwordBuff.tuViBonusPercent();
        double bonus = permissionBonus + islandBonus + equipmentBonus + flySwordBonus;
        double environmentBonus = getEnvironmentBonus(player);
        double infusionBonus = getInfusionTuViBonus(player);
        boolean islandEligible = rollLightning && isTurtleIslandCultivationEligible(player, islandBonus);
        double totalPoints = basePoints * (1.0 + (bonus + environmentBonus + infusionBonus) / 100.0);
        LightningBonusResult lightning = rollLightning
                ? applyLightningBonus(totalPoints, configManager.isLightningBonusEnabled(),
                configManager.getLightningBonusChancePercent(), configManager.getLightningBonusMultiplier(),
                ThreadLocalRandom.current().nextDouble(100.0))
                : new LightningBonusResult(totalPoints, false);
        double completionPoints = applyFlySwordCompletionBonus(lightning.points(), flySwordBuff.completionBonusPercent());
        double cappedPoints = getCappedTuViReward(player, completionPoints);
        return new TuLuyenReward(basePoints, permissionBonus, islandBonus, bonus, environmentBonus,
                infusionBonus, equipmentBonus, flySwordBonus, flySwordBuff.completionBonusPercent(),
                cappedPoints, lightning.triggered(), islandBonus > 0.0, islandEligible);
    }

    private double getEquipmentTuViBonus(Player player) {
        if (equipmentMenuManager == null || player == null) {
            return 0.0D;
        }
        return Math.max(0.0D, equipmentMenuManager.getEquippedSystemStatBonus(player, EquipmentMenuManager.DAN_DUOC_TU_VI_BONUS_STAT));
    }

    private FlySwordTuViBuff getEquippedFlySwordBuff(Player player) {
        if (equipmentMenuManager == null || player == null || !plugin.getConfig().getBoolean(FLY_SWORD_BUFF_PATH + ".enabled", true)) {
            return NO_FLY_SWORD_BUFF;
        }

        String slot = plugin.getConfig().getString(FLY_SWORD_BUFF_PATH + ".slot",
                plugin.getConfig().getString("fly-sword.equipped.slot", DEFAULT_FLY_SWORD_SLOT));
        EquipmentMenuManager.EquippedMmoItem equippedItem = equipmentMenuManager.getEquippedMmoItem(player, slot);
        if (equippedItem == null) {
            return NO_FLY_SWORD_BUFF;
        }
        return resolveFlySwordBuff(plugin.getConfig(), equippedItem.type(), equippedItem.id());
    }

    static double resolveFlySwordTuViBonusPercent(FileConfiguration config, String type, String id) {
        return resolveFlySwordBuff(config, type, id).tuViBonusPercent();
    }

    private static FlySwordTuViBuff resolveFlySwordBuff(FileConfiguration config, String type, String id) {
        String normalizedType = normalizeMmoKey(type);
        String normalizedId = normalizeMmoKey(id);
        if (!FLY_SWORD_TYPE.equals(normalizedType) || normalizedId.isBlank()) {
            return NO_FLY_SWORD_BUFF;
        }

        String key = normalizedType + "." + normalizedId;
        FlySwordTuViBuff defaultBuff = DEFAULT_FLY_SWORD_BUFFS.getOrDefault(key, NO_FLY_SWORD_BUFF);
        if (config == null || !config.getBoolean(FLY_SWORD_BUFF_PATH + ".enabled", true)) {
            return NO_FLY_SWORD_BUFF;
        }

        String path = FLY_SWORD_BUFF_PATH + ".swords." + normalizedType + "." + normalizedId;
        double tuViBonusPercent = Math.max(0.0D,
                config.getDouble(path + ".tuvi-bonus-percent", defaultBuff.tuViBonusPercent()));
        double completionBonusPercent = Math.max(0.0D,
                config.getDouble(path + ".completion-bonus-percent", defaultBuff.completionBonusPercent()));
        boolean weatherSpeedEnabled = config.getBoolean(path + ".weather-speed.enabled", defaultBuff.weatherSpeedEnabled());
        double weatherSpeedReductionPercent = Math.max(0.0D, config.getDouble(path + ".weather-speed.interval-reduction-percent",
                config.getDouble(FLY_SWORD_BUFF_PATH + ".weather-speed.interval-reduction-percent",
                        defaultBuff.weatherSpeedReductionPercent())));
        boolean autoTuLuyenEnabled = config.getBoolean(path + ".auto-tu-luyen.enabled", defaultBuff.autoTuLuyenEnabled());

        return new FlySwordTuViBuff(tuViBonusPercent, completionBonusPercent, weatherSpeedEnabled,
                weatherSpeedReductionPercent, autoTuLuyenEnabled);
    }

    private static String normalizeMmoKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private int getEffectiveTuLuyenInterval(Player player) {
        int baseInterval = Math.max(1, configManager.getTuLuyenInterval());
        FlySwordTuViBuff buff = getEquippedFlySwordBuff(player);
        if (!buff.weatherSpeedEnabled()) {
            return baseInterval;
        }
        return resolveWeatherSpeedInterval(baseInterval, buff.weatherSpeedReductionPercent(), isFlySwordWeatherSpeedActive(player));
    }

    static int resolveWeatherSpeedInterval(int baseInterval, double reductionPercent, boolean active) {
        int safeBaseInterval = Math.max(1, baseInterval);
        if (!active || reductionPercent <= 0.0D) {
            return safeBaseInterval;
        }
        double multiplier = Math.max(0.0D, 1.0D - Math.min(100.0D, reductionPercent) / 100.0D);
        return Math.max(1, (int) Math.round(safeBaseInterval * multiplier));
    }

    private boolean isFlySwordWeatherSpeedActive(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        boolean includeThunder = plugin.getConfig().getBoolean(FLY_SWORD_BUFF_PATH + ".weather-speed.include-thunder", true);
        boolean includeRain = plugin.getConfig().getBoolean(FLY_SWORD_BUFF_PATH + ".weather-speed.include-rain", true);
        return (includeThunder && player.getWorld().isThundering()) || (includeRain && player.getWorld().hasStorm());
    }

    static double applyFlySwordCompletionBonus(double totalReward, double completionBonusPercent) {
        if (totalReward <= 0.0D || completionBonusPercent <= 0.0D) {
            return totalReward;
        }
        return totalReward * (1.0D + completionBonusPercent / 100.0D);
    }

    private double getInfusionTuViBonus(Player player) {
        if (infusionManager == null || player == null) {
            return 0.0D;
        }
        return Math.max(0.0D, infusionManager.getEquippedTuViBonusPercent(player));
    }

    private double getTurtleIslandCultivationBonusPercent(Player player) {
        return turtleIslandHook.getCultivationBonusPercent(player);
    }

    private boolean isTurtleIslandCultivationEligible(Player player, double islandBonus) {
        return islandBonus > 0.0 || turtleIslandHook.canReceiveCultivationBonus(player);
    }

    private void triggerTurtleIslandCultivationFire(Player player) {
        if (player == null) {
            return;
        }
        if (turtleIslandHook.isCultivationFireEventHookRegistered()) {
            return;
        }
        turtleIslandHook.playCultivationFire(player);
    }

    static LightningBonusResult applyLightningBonus(double points, boolean enabled, double chancePercent, double multiplier, double rollPercent) {
        if (!enabled || chancePercent <= 0.0 || multiplier <= 1.0 || rollPercent >= chancePercent) {
            return new LightningBonusResult(points, false);
        }

        return new LightningBonusResult(points * multiplier, true);
    }

    static TuViGainEvent createTuLuyenGainEvent(Player player, double amount) {
        return new TuViGainEvent(player, amount, "tuluyen");
    }

    private static TuViGainEvent createTuLuyenGainEvent(Player player, double amount, boolean externalBonusIncluded) {
        TuViGainEvent event = createTuLuyenGainEvent(player, amount);
        event.setExternalBonusIncluded(externalBonusIncluded);
        return event;
    }

    private void processAutoFlySwordCultivation() {
        if (!plugin.getConfig().getBoolean(FLY_SWORD_BUFF_PATH + ".enabled", true)) {
            autoFlySwordTicks.clear();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            processAutoFlySwordCultivation(player);
        }
    }

    private void processAutoFlySwordCultivation(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline() || isTuLuyen(player) || shouldSuppressCinematicUi(player)) {
            autoFlySwordTicks.remove(uuid);
            return;
        }

        FlySwordTuViBuff buff = getEquippedFlySwordBuff(player);
        if (!buff.autoTuLuyenEnabled()) {
            autoFlySwordTicks.remove(uuid);
            return;
        }

        long tick = autoFlySwordTicks.merge(uuid, 1L, Long::sum);
        int interval = getFlySwordAutoIntervalTicks();
        if (tick % interval != 0L) {
            return;
        }

        double reward = configManager.rollPointsPerInterval() * (1.0D + buff.tuViBonusPercent() / 100.0D);
        TuViGainEvent event = createFlySwordAutoGainEvent(player, reward);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getAmount() <= 0.0D) {
            return;
        }

        double finalAmount = getCappedTuViReward(player, event.getAmount());
        if (finalAmount <= 0.0D) {
            if (isAtTuViCap(player)) {
                warnTuViCapReached(player);
            }
            return;
        }

        TuTien.getApi().addTuVi(uuid, finalAmount);
        sendFlySwordAutoActionBar(player, finalAmount);
    }

    private int getFlySwordAutoIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt(FLY_SWORD_BUFF_PATH + ".auto-tu-luyen.interval-ticks",
                configManager.getTuLuyenInterval()));
    }

    private static TuViGainEvent createFlySwordAutoGainEvent(Player player, double amount) {
        return new TuViGainEvent(player, amount, "fly_sword_auto");
    }

    private void sendFlySwordAutoActionBar(Player player, double amount) {
        if (!plugin.getConfig().getBoolean(FLY_SWORD_BUFF_PATH + ".auto-tu-luyen.actionbar.enabled", true)) {
            return;
        }

        String format = plugin.getConfig().getString(FLY_SWORD_BUFF_PATH + ".auto-tu-luyen.actionbar.format",
                "&8[&cU Minh Huyet Kiem&8] &7Tu luyen: &a+{points} Tu Vi");
        String message = ChatColor.translateAlternateColorCodes('&',
                format.replace("{points}", formatNumber(amount)));
        if (actionBarSuppressor != null) {
            actionBarSuppressor.allowNextActionBar(player);
        }
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
    }

    static boolean shouldTriggerTuLuyenQuestMilestone(long tick, long milestoneTicks) {
        if (tick <= 0L || milestoneTicks <= 0L) {
            return false;
        }
        return tick % milestoneTicks == 0L;
    }

    private void triggerTuLuyenQuestObjective(Player player) {
        String command = "qa triggerObjective " + TULUYEN_QUEST_TRIGGER_NAME + " " + player.getName();
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!dispatched) {
            plugin.getLogger().warning("Failed to dispatch NotQuests trigger command: " + command);
        }
    }

    private double getCappedTuViReward(Player player, double requestedReward) {
        long nextRequirement = getNextTuViRequirement(player.getUniqueId());
        if (nextRequirement <= 0) return requestedReward;

        double currentTuVi = TuTien.getApi().getTuVi(player.getUniqueId());
        if (currentTuVi >= nextRequirement) return 0.0;

        return Math.min(requestedReward, nextRequirement - currentTuVi);
    }

    private boolean isAtTuViCap(Player player) {
        long nextRequirement = getNextTuViRequirement(player.getUniqueId());
        if (nextRequirement <= 0) return false;

        return TuTien.getApi().getTuVi(player.getUniqueId()) >= nextRequirement;
    }

    private boolean canContinueCultivatingAtCap(Player player) {
        return isInsidePlayerSuperiorSkyblockIsland(player);
    }

    private boolean isInsidePlayerSuperiorSkyblockIsland(Player player) {
        if (player == null || player.getLocation() == null) {
            return false;
        }

        Plugin skyblock = Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2");
        if (skyblock == null || !skyblock.isEnabled()) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
            Class<?> islandClass = Class.forName("com.bgsoftware.superiorskyblock.api.island.Island");
            Class<?> superiorPlayerClass = Class.forName("com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer");

            Object island = apiClass.getMethod("getIslandAt", Location.class).invoke(null, player.getLocation());
            if (island == null) {
                return false;
            }

            Object superiorPlayer = apiClass.getMethod("getPlayer", Player.class).invoke(null, player);
            if (superiorPlayer == null) {
                return false;
            }

            Object isMember = islandClass.getMethod("isMember", superiorPlayerClass).invoke(island, superiorPlayer);
            if (Boolean.TRUE.equals(isMember)) {
                return true;
            }

            Object owner = islandClass.getMethod("getOwner").invoke(island);
            if (owner == null) {
                return false;
            }

            Object ownerUuid = superiorPlayerClass.getMethod("getUniqueId").invoke(owner);
            return player.getUniqueId().equals(ownerUuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private long getNextTuViRequirement(UUID uuid) {
        PlayerRealm playerRealm = realmManager.getPlayerRealm(uuid);
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        if (currentRealm == null) return 0;

        if (playerRealm.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSubRealm = playerRealm.getSubRealm().next();
            return nextSubRealm != null ? currentRealm.getTuViForSubRealm(nextSubRealm) : 0;
        }

        Realm nextRealm = realmManager.getNextRealm(uuid);
        return nextRealm != null ? nextRealm.getTuViRequired() : 0;
    }

    private void warnTuViCapReached(Player player) {
        long now = System.currentTimeMillis();
        long lastWarning = capWarningTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastWarning < 10_000L) return;

        capWarningTimes.put(player.getUniqueId(), now);
        player.sendMessage("§cTu Vi đã đạt giới hạn hiện tại! §eHãy /dotpha để mở giới hạn tu luyện tiếp theo.");
    }

    private void playIntervalResetSound(Player player) {
        if (!configManager.isTuLuyenIntervalResetSoundEnabled()) {
            return;
        }

        String soundName = configManager.getTuLuyenIntervalResetSound();
        if (soundName == null || soundName.trim().isEmpty() || soundName.equalsIgnoreCase("NONE")) {
            return;
        }

        SoundCategory category = parseSoundCategory(configManager.getTuLuyenIntervalResetSoundCategory());
        float volume = clampVolume(configManager.getTuLuyenIntervalResetSoundVolume());
        float pitch = clampPitch(configManager.getTuLuyenIntervalResetSoundPitch());
        String normalizedSoundName = soundName.trim();

        try {
            Sound sound = Sound.valueOf(normalizedSoundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            player.playSound(player.getLocation(), normalizedSoundName, category, volume, pitch);
        }
    }

    private SoundCategory parseSoundCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return SoundCategory.MASTER;
        }

        try {
            return SoundCategory.valueOf(categoryName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SoundCategory.MASTER;
        }
    }

    private float clampVolume(float volume) {
        return Math.max(0.0f, volume);
    }

    private float clampPitch(float pitch) {
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    private double getEnvironmentBonus(Player player) {
        double worldBonus = plugin.getConfig().getDouble("tu-luyen.environment-bonus.worlds." + player.getWorld().getName(), 0.0);
        double regionBonus = getWorldGuardRegionBonus(player);
        double zoneBonus = getAfkZoneTuViBonus(player);
        return worldBonus + regionBonus + zoneBonus;
    }

    private double getAfkZoneTuViBonus(Player player) {
        if (zoneManager == null || player == null) {
            return 0.0D;
        }
        return resolveAfkZoneTuViBonus(zoneManager.getZoneAt(player.getLocation()));
    }

    static double resolveAfkZoneTuViBonus(CuboidZone zone) {
        if (zone == null) {
            return 0.0D;
        }
        return Math.max(0.0D, zone.getTuViBonusPercent());
    }

    private double getWorldGuardRegionBonus(Player player) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return 0.0;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tu-luyen.environment-bonus.regions");
        if (section == null) return 0.0;

        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedWorld = bukkitAdapterClass.getMethod("adapt", org.bukkit.World.class).invoke(null, player.getWorld());
            Object adaptedLocation = bukkitAdapterClass.getMethod("adapt", Location.class).invoke(null, player.getLocation());
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);
            Object applicableRegions = query.getClass().getMethod("getApplicableRegions", adaptedLocation.getClass()).invoke(query, adaptedLocation);

            double maxBonus = 0.0;
            Method getRegions = applicableRegions.getClass().getMethod("getRegions");
            Iterable<?> regions = (Iterable<?>) getRegions.invoke(applicableRegions);
            for (Object region : regions) {
                String id = String.valueOf(region.getClass().getMethod("getId").invoke(region));
                maxBonus = Math.max(maxBonus, section.getDouble(id, section.getDouble(id.toLowerCase(java.util.Locale.ROOT), 0.0)));
            }
            return maxBonus;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private void createVisuals(Player player) {
        if (plugin.getConfig().getBoolean("tu-luyen.bossbar.enabled", true)) {
            BossBar bossBar = Bukkit.createBossBar("", getBossBarColor(), getBossBarStyle());
            if (!shouldSuppressCinematicUi(player)) {
                bossBar.addPlayer(player);
            }
            bossBar.setVisible(true);
            bossBars.put(player.getUniqueId(), bossBar);
        }

        if (plugin.getConfig().getBoolean("tu-luyen.hologram.enabled", true)) {
            createFancyHologram(player);
        }

            updateVisuals(player, calculateReward(player, false), 0L, getEffectiveTuLuyenInterval(player));
    }

    private void updateVisuals(Player player, TuLuyenReward reward, long tick, int intervalTicks) {
        UUID uuid = player.getUniqueId();
        int effectiveIntervalTicks = Math.max(1, intervalTicks);
        double progress = (tick % effectiveIntervalTicks) / (double) effectiveIntervalTicks;

        BossBar bossBar = bossBars.get(uuid);
        if (bossBar != null) {
            if (shouldSuppressCinematicUi(player)) {
                bossBar.removePlayer(player);
            } else if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
            bossBar.setTitle(applyRewardPlaceholders(player, plugin.getConfig().getString("tu-luyen.bossbar.title",
                    "&bTu Vi sắp nhận: &e{base} &7+ &aBonus {bonus}% &7+ &dMôi Trường {environment}% &7+ &5Lửa Thần {infusion}% &7= &6{total}"), reward));
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }

        updateFancyHologram(player, reward);
    }

    private void createFancyHologram(Player player) {
        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            plugin.getLogger().warning("FancyHolograms is not installed; /tuluyen hologram was skipped.");
            return;
        }

        try {
            Object pluginApi = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin")
                    .getMethod("get")
                    .invoke(null);
            Object manager = pluginApi.getClass().getMethod("getHologramManager").invoke(pluginApi);
            String name = "tutiencore_tuluyen_" + player.getUniqueId().toString().replace("-", "");

            Object existing = manager.getClass().getMethod("getHologram", String.class).invoke(manager, name);
            if (existing instanceof java.util.Optional<?> optional && optional.isPresent()) {
                Object hologram = optional.get();
                removeFancyHologram(hologram);
            }

            Location location = getHologramLocation(player);
            Object data = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData")
                    .getConstructor(String.class, Location.class)
                    .newInstance(name, location);
            data.getClass().getMethod("setPersistent", boolean.class).invoke(data, false);
            data.getClass().getMethod("setVisibilityDistance", int.class)
                    .invoke(data, plugin.getConfig().getInt("tu-luyen.hologram.visibility-distance", 32));
            data.getClass().getMethod("setTextUpdateInterval", int.class)
                    .invoke(data, plugin.getConfig().getInt("tu-luyen.hologram.update-interval", 20));
            data.getClass().getMethod("setTextShadow", boolean.class)
                    .invoke(data, plugin.getConfig().getBoolean("tu-luyen.hologram.text-shadow", true));
            data.getClass().getMethod("setSeeThrough", boolean.class)
                    .invoke(data, plugin.getConfig().getBoolean("tu-luyen.hologram.see-through", false));
            applyTextDisplayStyle(data);
            prepareFancyOpenAnimation(data);

            Object hologram = manager.getClass().getMethod("create", Class.forName("de.oliver.fancyholograms.api.data.HologramData"))
                    .invoke(manager, data);
            manager.getClass().getMethod("addHologram", Class.forName("de.oliver.fancyholograms.api.hologram.Hologram"))
                    .invoke(manager, hologram);
            holograms.put(player.getUniqueId(), hologram);
            applyFancyTextDisplayStyle(hologram);
            playFancyOpenAnimation(player.getUniqueId(), hologram);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to create FancyHolograms /tuluyen hologram: " + throwable.getMessage());
        }
    }

    private void updateFancyHologram(Player player, TuLuyenReward reward) {
        Object hologram = holograms.get(player.getUniqueId());
        if (hologram == null) return;

        try {
            Object data = hologram.getClass().getMethod("getData").invoke(hologram);
            data.getClass().getMethod("setLocation", Location.class).invoke(data, getHologramLocation(player));
            data.getClass().getMethod("setText", List.class).invoke(data, getHologramText(player, reward));
            applyTextDisplayStyle(data);
            hologram.getClass().getMethod("forceUpdate").invoke(hologram);
            applyFancyTextDisplayStyle(hologram);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to update FancyHolograms /tuluyen hologram: " + throwable.getMessage());
            clearVisuals(player.getUniqueId());
        }
    }

    private void prepareFancyOpenAnimation(Object data) {
        if (!isFancyOpenAnimationEnabled()) {
            return;
        }

        setFancyInterpolationDuration(data, 0);
        setFancyScale(data, getFancyFinalScale() * getFancyOpenAnimationStartScale());
    }

    private void playFancyOpenAnimation(UUID playerId, Object hologram) {
        if (!isFancyOpenAnimationEnabled()) {
            return;
        }

        setFancyTextOpacity(hologram, getFancyOpenAnimationStartOpacity());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (holograms.get(playerId) != hologram) {
                return;
            }

            try {
                Object data = hologram.getClass().getMethod("getData").invoke(hologram);
                setFancyInterpolationDuration(data, getFancyOpenAnimationDuration());
                setFancyScale(data, getFancyFinalScale());
                hologram.getClass().getMethod("forceUpdate").invoke(hologram);
                applyFancyTextDisplayStyle(hologram);
                setFancyTextOpacity(hologram, 255);
            } catch (Throwable ignored) {
                // FancyHolograms API differs by version; skip animation instead of breaking /tuluyen.
            }
        }, getFancyOpenAnimationDelay());
    }

    private void setFancyInterpolationDuration(Object data, int duration) {
        try {
            data.getClass().getMethod("setInterpolationDuration", int.class)
                    .invoke(data, Math.max(0, Math.min(59, duration)));
        } catch (Throwable ignored) {
            // Older or custom FancyHolograms builds may not expose display interpolation.
        }
    }

    private void setFancyScale(Object data, float scale) {
        try {
            Class<?> vectorClass = Class.forName("org.joml.Vector3f");
            Object vector = vectorClass.getConstructor(float.class, float.class, float.class)
                    .newInstance(scale, scale, scale);
            Method method = data.getClass().getMethod("setScale", vectorClass);
            method.invoke(data, vector);
        } catch (Throwable ignored) {
            // Scale animation is optional and depends on FancyHolograms internals.
        }
    }

    private void setFancyTextOpacity(Object hologram, int opacity) {
        try {
            Object display = hologram.getClass().getMethod("getDisplayEntity").invoke(hologram);
            if (display == null) {
                return;
            }
            Method method = display.getClass().getMethod("setTextOpacity", byte.class);
            method.invoke(display, (byte) Math.max(0, Math.min(255, opacity)));
        } catch (Throwable ignored) {
            // Text opacity is a Bukkit TextDisplay feature; ignore if the wrapped entity is unavailable.
        }
    }

    private void applyFancyTextDisplayStyle(Object hologram) {
        try {
            Object display = hologram.getClass().getMethod("getDisplayEntity").invoke(hologram);
            if (display != null) {
                applyTextDisplayStyle(display);
            }
        } catch (Throwable ignored) {
            // FancyHolograms/Bukkit builds expose TextDisplay internals differently.
        }
    }

    static void applyTextDisplayStyle(Object display) {
        Color transparent = Color.fromARGB(0, 0, 0, 0);
        setOptionalTextDisplayStyle(display, "setShadowed", boolean.class, true);
        setOptionalTextDisplayStyle(display, "setShadow", boolean.class, true);
        setOptionalTextDisplayStyle(display, "setTextShadow", boolean.class, true);
        setOptionalTextDisplayStyle(display, "setDefaultBackground", boolean.class, false);
        setOptionalTextDisplayStyle(display, "setUseDefaultBackground", boolean.class, false);
        setOptionalTextDisplayStyle(display, "setBackgroundColor", Color.class, transparent);
        setOptionalTextDisplayStyle(display, "setBackground", Color.class, transparent);
        setOptionalTextDisplayStyle(display, "setBackground", int.class, transparent.asARGB());
    }

    private static void setOptionalTextDisplayStyle(Object display, String methodName, Class<?> parameterType, Object value) {
        try {
            display.getClass().getMethod(methodName, parameterType).invoke(display, value);
        } catch (Throwable ignored) {
        }
    }

    private boolean isFancyOpenAnimationEnabled() {
        return plugin.getConfig().getBoolean("tu-luyen.hologram.spawn-animation.enabled", true);
    }

    private float getFancyFinalScale() {
        return (float) Math.max(0.01, plugin.getConfig().getDouble("tu-luyen.hologram.scale", 1.0));
    }

    private float getFancyOpenAnimationStartScale() {
        double scale = plugin.getConfig().getDouble("tu-luyen.hologram.spawn-animation.start-scale", 0.82);
        return (float) Math.max(0.01, Math.min(2.0, scale));
    }

    private int getFancyOpenAnimationStartOpacity() {
        return Math.max(0, Math.min(255, plugin.getConfig().getInt("tu-luyen.hologram.spawn-animation.start-opacity", 40)));
    }

    private int getFancyOpenAnimationDuration() {
        return Math.max(0, Math.min(59, plugin.getConfig().getInt("tu-luyen.hologram.spawn-animation.interpolation-duration", 1)));
    }

    private long getFancyOpenAnimationDelay() {
        return Math.max(1L, plugin.getConfig().getLong("tu-luyen.hologram.spawn-animation.start-delay-ticks", 1L));
    }

    private void removeFancyHologram(Object hologram) {
        try {
            Object pluginApi = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin")
                    .getMethod("get")
                    .invoke(null);
            Object manager = pluginApi.getClass().getMethod("getHologramManager").invoke(pluginApi);
            String name = String.valueOf(hologram.getClass().getMethod("getName").invoke(hologram));
            try {
                manager.getClass().getMethod("removeHologram", String.class).invoke(manager, name);
            } catch (NoSuchMethodException ignored) {
                manager.getClass().getMethod("removeHologram", Class.forName("de.oliver.fancyholograms.api.hologram.Hologram"))
                        .invoke(manager, hologram);
            }
        } catch (Throwable ignored) {
        }
    }

    private Location getHologramLocation(Player player) {
        double yOffset = plugin.getConfig().getDouble("tu-luyen.hologram.y-offset", 2.4);
        return player.getLocation().clone().add(0.0, yOffset, 0.0);
    }

    private List<String> getHologramText(Player player, TuLuyenReward reward) {
        List<String> configuredLines = plugin.getConfig().getStringList("tu-luyen.hologram.lines");
        if (configuredLines.isEmpty()) configuredLines = getDefaultHologramLines();

        List<String> lines = new ArrayList<>();
        for (String line : configuredLines) {
            // FancyHolograms parses legacy ampersand colors itself. Passing section signs can render as broken glyphs.
            lines.add(applyRewardPlaceholders(player, line, reward, false));
        }
        lines.addAll(getTeamBonusHologramLines(player));
        return lines;
    }

    private List<String> getTeamBonusHologramLines(Player player) {
        if (!plugin.getConfig().getBoolean("tu-luyen.hologram.team-bonus.enabled", true)) {
            return List.of();
        }

        double bonusPercent = getTeamCultivationBonusPercent(player);
        if (bonusPercent <= 0.0D) {
            return List.of();
        }

        List<String> configuredLines = plugin.getConfig().getStringList("tu-luyen.hologram.team-bonus.lines");
        if (configuredLines.isEmpty()) {
            configuredLines = List.of("&aTông môn: &f+{team_bonus}%");
        }
        return applyTeamBonusPlaceholders(configuredLines, bonusPercent);
    }

    private double getTeamCultivationBonusPercent(Player player) {
        if (Bukkit.getPluginManager().getPlugin("BetterteamsAddon") == null) {
            return 0.0D;
        }

        try {
            Object api = Class.forName("com.turtle.betterteamsaddon.api.BetterteamsAddonProvider")
                    .getMethod("getApi")
                    .invoke(null);
            if (api == null) {
                return 0.0D;
            }

            Object multiplier = api.getClass()
                    .getMethod("getCultivationBonusMultiplier", UUID.class)
                    .invoke(api, player.getUniqueId());
            if (!(multiplier instanceof Number number)) {
                return 0.0D;
            }

            return Math.max(0.0D, (number.doubleValue() - 1.0D) * 100.0D);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private void clearVisuals(UUID uuid) {
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }

        Object hologram = holograms.remove(uuid);
        if (hologram != null) removeFancyHologram(hologram);
    }

    private void hideBossBar(Player player) {
        BossBar bossBar = bossBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    private boolean shouldSuppressCinematicUi(Player player) {
        return player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    private String applyRewardPlaceholders(String text, TuLuyenReward reward) {
        return applyRewardPlaceholders(null, text, reward, true);
    }

    private String applyRewardPlaceholders(Player player, String text, TuLuyenReward reward) {
        return applyRewardPlaceholders(player, text, reward, true);
    }

    private String applyRewardPlaceholders(String text, TuLuyenReward reward, boolean translateColors) {
        return applyRewardPlaceholders(null, text, reward, translateColors);
    }

    static String applyRewardPlaceholders(String text, double basePoints, double permissionBonusPercent,
                                          double islandBonusPercent, double environmentBonusPercent,
                                          double infusionBonusPercent, double totalPoints,
                                          boolean translateColors) {
        double bonusPercent = permissionBonusPercent + islandBonusPercent;
        TuLuyenReward reward = new TuLuyenReward(basePoints, permissionBonusPercent, islandBonusPercent,
                bonusPercent, environmentBonusPercent, infusionBonusPercent, 0.0D, 0.0D, 0.0D, totalPoints, false,
                islandBonusPercent > 0.0D, islandBonusPercent > 0.0D);
        String result = applyRewardPlaceholdersRaw(text, reward);
        return translateColors ? ChatColor.translateAlternateColorCodes('&', result) : result;
    }

    private String applyRewardPlaceholders(Player player, String text, TuLuyenReward reward, boolean translateColors) {
        String result = applyRewardPlaceholdersRaw(text, reward);
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
        }
        return translateColors ? ChatColor.translateAlternateColorCodes('&', result) : result;
    }

    private static String applyRewardPlaceholdersRaw(String text, TuLuyenReward reward) {
        double totalBonusPercent = reward.bonusPercent + reward.infusionBonusPercent;
        double allBonusPercent = totalBonusPercent + reward.environmentBonusPercent;
        return text
                .replace("{base}", formatNumber(reward.basePoints))
                .replace("{bonus}", formatPercent(reward.bonusPercent))
                .replace("{total_bonus}", formatPercent(totalBonusPercent))
                .replace("{cultivation_bonus}", formatPercent(totalBonusPercent))
                .replace("{all_bonus}", formatPercent(allBonusPercent))
                .replace("{permission_bonus}", formatPercent(reward.permissionBonusPercent))
                .replace("{island_bonus}", formatPercent(reward.islandBonusPercent))
                .replace("{tien_phu_bonus}", formatPercent(reward.islandBonusPercent))
                .replace("{tienphu_bonus}", formatPercent(reward.islandBonusPercent))
                .replace("{dan_duoc_bonus}", formatPercent(reward.equipmentBonusPercent))
                .replace("{danduoc_bonus}", formatPercent(reward.equipmentBonusPercent))
                .replace("{phi_kiem_bonus}", formatPercent(reward.flySwordBonusPercent))
                .replace("{phikiem_bonus}", formatPercent(reward.flySwordBonusPercent))
                .replace("{fly_sword_bonus}", formatPercent(reward.flySwordBonusPercent))
                .replace("{fly_sword_completion_bonus}", formatPercent(reward.flySwordCompletionBonusPercent))
                .replace("{environment}", formatPercent(reward.environmentBonusPercent))
                .replace("{infusion}", formatPercent(reward.infusionBonusPercent))
                .replace("{total}", formatNumber(reward.totalPoints));
    }

    private List<String> getDefaultHologramLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&eTrạng thái: &aĐang bế quan tu luyện");
        lines.add("&fCơ bản: &b{base} Tu Vi");
        lines.add("&fBonus: &a{total_bonus}%");
        lines.add("&fMôi Trường Tu Luyện: &d{environment}%");
        lines.add("&fLửa Thần: &5{infusion}%");
        lines.add("&fTổng Tu Vi Nhận Được: &6{total}");
        return lines;
    }

    private BarColor getBossBarColor() {
        try {
            return BarColor.valueOf(plugin.getConfig().getString("tu-luyen.bossbar.color", "BLUE").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarColor.BLUE;
        }
    }

    private BarStyle getBossBarStyle() {
        try {
            return BarStyle.valueOf(plugin.getConfig().getString("tu-luyen.bossbar.style", "SEGMENTED_10").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarStyle.SEGMENTED_10;
        }
    }

    private static String formatNumber(double value) {
        return String.valueOf((int) Math.round(value));
    }

    private static String formatPercent(double value) {
        return formatPercentValue(value);
    }

    static List<String> applyTeamBonusPlaceholders(List<String> configuredLines, double bonusPercent) {
        if (bonusPercent <= 0.0D) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (String line : configuredLines) {
            lines.add(line
                    .replace("{team_bonus}", formatPercentValue(bonusPercent))
                    .replace("{team_multiplier}", formatDecimalValue(1.0D + bonusPercent / 100.0D)));
        }
        return lines;
    }

    private static String formatPercentValue(double value) {
        return String.valueOf((int) Math.round(value));
    }

    private static String formatDecimalValue(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static class TuLuyenReward {
        private final double basePoints;
        private final double permissionBonusPercent;
        private final double islandBonusPercent;
        private final double bonusPercent;
        private final double environmentBonusPercent;
        private final double infusionBonusPercent;
        private final double equipmentBonusPercent;
        private final double flySwordBonusPercent;
        private final double flySwordCompletionBonusPercent;
        private final double totalPoints;
        private final boolean lightningTriggered;
        private final boolean externalBonusIncluded;
        private final boolean turtleIslandEligible;

        private TuLuyenReward(double basePoints, double permissionBonusPercent, double islandBonusPercent,
                              double bonusPercent, double environmentBonusPercent, double infusionBonusPercent,
                              double equipmentBonusPercent, double flySwordBonusPercent,
                              double flySwordCompletionBonusPercent, double totalPoints,
                              boolean lightningTriggered, boolean externalBonusIncluded, boolean turtleIslandEligible) {
            this.basePoints = basePoints;
            this.permissionBonusPercent = permissionBonusPercent;
            this.islandBonusPercent = islandBonusPercent;
            this.bonusPercent = bonusPercent;
            this.environmentBonusPercent = environmentBonusPercent;
            this.infusionBonusPercent = infusionBonusPercent;
            this.equipmentBonusPercent = equipmentBonusPercent;
            this.flySwordBonusPercent = flySwordBonusPercent;
            this.flySwordCompletionBonusPercent = flySwordCompletionBonusPercent;
            this.totalPoints = totalPoints;
            this.lightningTriggered = lightningTriggered;
            this.externalBonusIncluded = externalBonusIncluded;
            this.turtleIslandEligible = turtleIslandEligible;
        }
    }

    private record FlySwordTuViBuff(double tuViBonusPercent, double completionBonusPercent,
                                    boolean weatherSpeedEnabled, double weatherSpeedReductionPercent,
                                    boolean autoTuLuyenEnabled) {
    }

    record LightningBonusResult(double points, boolean triggered) {
    }
}
