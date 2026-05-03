package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.task.TuLuyenParticleTask;
import com.turtle.tutiencore.api.TuTien;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TuLuyenManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TuLuyenParticleTask lineTask;

    private final Map<UUID, ArmorStand> tuLuyenPlayers = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Object> holograms = new HashMap<>();
    private final Map<UUID, Long> sessionTicks = new HashMap<>();
    private BukkitRunnable task;

    public TuLuyenManager(JavaPlugin plugin, ConfigManager config, ZoneManager zoneManager, TuLuyenParticleTask lineTask) {
        this.plugin = plugin;
        this.configManager = config;
        this.lineTask = lineTask;
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

                    long tick = sessionTicks.merge(player.getUniqueId(), 1L, Long::sum);
                    TuLuyenReward reward = calculateReward(player);
                    updateVisuals(player, reward, tick);

                    if (tick % Math.max(1, configManager.getTuLuyenInterval()) != 0) {
                        continue;
                    }

                    // Give Points via API
                    TuTien.getApi().addTuVi(player.getUniqueId(), reward.totalPoints);
                    
                    if (!configManager.getMsgReceived().isEmpty()) {
                        String msg = configManager.getMsgReceived()
                                .replace("%points%", String.valueOf((int) reward.totalPoints));
                        if (reward.permissionBonusPercent > 0) {
                            msg += " §a(+" + formatPercent(reward.permissionBonusPercent) + "% bonus)";
                        }
                        player.sendMessage(msg);
                    }
                }
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
        for (UUID uuid : new ArrayList<>(bossBars.keySet())) {
            clearVisuals(uuid);
        }
    }

    public boolean isTuLuyen(Player player) {
        return tuLuyenPlayers.containsKey(player.getUniqueId());
    }

    public void toggleTuLuyen(Player player) {
        if (isTuLuyen(player)) {
            stopTuLuyen(player);
        } else {
            startTuLuyen(player);
        }
    }

    public void startTuLuyen(Player player) {
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
        if (isTuLuyen(event.getPlayer())) {
            stopTuLuyen(event.getPlayer());
        }
    }

    /**
     * Get the highest Tu Vi bonus percentage from player permissions.
     * Permission format: tutiencore.tuvi.bonus.<percent>
     * 
     * Examples:
     *   tutiencore.tuvi.bonus.20  → +20%
     *   tutiencore.tuvi.bonus.50  → +50%
     *   tutiencore.tuvi.bonus.100 → +100% (double)
     * 
     * If player has multiple bonus perms, the highest value is used.
     * 
     * LuckPerms setup:
     *   /lp group vip permission set tutiencore.tuvi.bonus.20
     *   /lp group svip permission set tutiencore.tuvi.bonus.50
     */
    private double getTuViBonus(Player player) {
        double maxBonus = 0;
        String prefix = "tutiencore.tuvi.bonus.";

        for (org.bukkit.permissions.PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            String name = perm.getPermission();
            if (perm.getValue() && name.startsWith(prefix)) {
                try {
                    double val = Double.parseDouble(name.substring(prefix.length()));
                    if (val > maxBonus) {
                        maxBonus = val;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return maxBonus;
    }

    private TuLuyenReward calculateReward(Player player) {
        double basePoints = configManager.getPointsPerInterval();
        double permissionBonus = getTuViBonus(player);
        double environmentBonus = getEnvironmentBonus(player);
        double totalPoints = basePoints * (1.0 + (permissionBonus + environmentBonus) / 100.0);
        return new TuLuyenReward(basePoints, permissionBonus, environmentBonus, totalPoints);
    }

    private double getEnvironmentBonus(Player player) {
        double worldBonus = plugin.getConfig().getDouble("tu-luyen.environment-bonus.worlds." + player.getWorld().getName(), 0.0);
        double regionBonus = getWorldGuardRegionBonus(player);
        return worldBonus + regionBonus;
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
            bossBar.addPlayer(player);
            bossBar.setVisible(true);
            bossBars.put(player.getUniqueId(), bossBar);
        }

        if (plugin.getConfig().getBoolean("tu-luyen.hologram.enabled", true)) {
            createFancyHologram(player);
        }

        updateVisuals(player, calculateReward(player), 0L);
    }

    private void updateVisuals(Player player, TuLuyenReward reward, long tick) {
        UUID uuid = player.getUniqueId();
        double progress = (tick % Math.max(1, configManager.getTuLuyenInterval())) / (double) Math.max(1, configManager.getTuLuyenInterval());

        BossBar bossBar = bossBars.get(uuid);
        if (bossBar != null) {
            bossBar.setTitle(applyRewardPlaceholders(player, plugin.getConfig().getString("tu-luyen.bossbar.title",
                    "&bTu Vi sắp nhận: &e{base} &7+ &aBonus {bonus}% &7+ &dMôi Trường {environment}% &7= &6{total}"), reward));
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

            Object hologram = manager.getClass().getMethod("create", Class.forName("de.oliver.fancyholograms.api.data.HologramData"))
                    .invoke(manager, data);
            manager.getClass().getMethod("addHologram", Class.forName("de.oliver.fancyholograms.api.hologram.Hologram"))
                    .invoke(manager, hologram);
            holograms.put(player.getUniqueId(), hologram);
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
            hologram.getClass().getMethod("forceUpdate").invoke(hologram);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to update FancyHolograms /tuluyen hologram: " + throwable.getMessage());
            clearVisuals(player.getUniqueId());
        }
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
        return lines;
    }

    private void clearVisuals(UUID uuid) {
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }

        Object hologram = holograms.remove(uuid);
        if (hologram != null) removeFancyHologram(hologram);
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

    private String applyRewardPlaceholders(Player player, String text, TuLuyenReward reward, boolean translateColors) {
        String result = text
                .replace("{base}", formatNumber(reward.basePoints))
                .replace("{bonus}", formatPercent(reward.permissionBonusPercent))
                .replace("{environment}", formatPercent(reward.environmentBonusPercent))
                .replace("{total}", formatNumber(reward.totalPoints));
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
        }
        return translateColors ? ChatColor.translateAlternateColorCodes('&', result) : result;
    }

    private List<String> getDefaultHologramLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&eTrạng thái: &aĐang bế quan tu luyện");
        lines.add("&fCơ bản: &b{base} Tu Vi");
        lines.add("&fBonus: &a{bonus}%");
        lines.add("&fMôi Trường Tu Luyện: &d{environment}%");
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

    private String formatNumber(double value) {
        return String.valueOf((int) Math.round(value));
    }

    private String formatPercent(double value) {
        return String.valueOf((int) Math.round(value));
    }

    private static class TuLuyenReward {
        private final double basePoints;
        private final double permissionBonusPercent;
        private final double environmentBonusPercent;
        private final double totalPoints;

        private TuLuyenReward(double basePoints, double permissionBonusPercent, double environmentBonusPercent, double totalPoints) {
            this.basePoints = basePoints;
            this.permissionBonusPercent = permissionBonusPercent;
            this.environmentBonusPercent = environmentBonusPercent;
            this.totalPoints = totalPoints;
        }
    }
}
