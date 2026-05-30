package com.turtle.tutiencore.core.manager;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FlySwordManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, ArmorStand> flyingPlayers = new HashMap<>();
    private final File dataFile;
    private YamlConfiguration data;
    private BukkitRunnable followTask;

    private boolean enabled;
    private String modelId;
    private String anchor;
    private boolean mountToPlayer;
    private double yOffset;
    private double scale;
    private boolean followPitch;
    private boolean requirePermission;
    private String permission;
    private boolean hideWhileSpectator;
    private boolean evolutionEnabled;
    private boolean autoFlightEnabled;
    private boolean autoFlightRequirePermission;
    private boolean autoFlightDisableOutsideWorld;
    private String autoFlightPermission;
    private Set<String> autoFlightWorlds = new HashSet<>();

    public FlySwordManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "fly-swords.yml");
        loadData();
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startFollowTask();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("fly-sword.enabled", true);
        modelId = plugin.getConfig().getString("fly-sword.model", "kiembay");
        anchor = plugin.getConfig().getString("fly-sword.anchor", "HEAD");
        mountToPlayer = plugin.getConfig().getBoolean("fly-sword.mount-to-player", true);
        yOffset = plugin.getConfig().getDouble("fly-sword.y-offset", -0.05);
        scale = plugin.getConfig().getDouble("fly-sword.scale", 1.5);
        followPitch = plugin.getConfig().getBoolean("fly-sword.follow-pitch", false);
        requirePermission = plugin.getConfig().getBoolean("fly-sword.require-permission", false);
        permission = plugin.getConfig().getString("fly-sword.permission", "tutiencore.flysword");
        hideWhileSpectator = plugin.getConfig().getBoolean("fly-sword.hide-while-spectator", true);
        evolutionEnabled = plugin.getConfig().getBoolean("fly-sword.evolution.enabled", true);
        autoFlightEnabled = plugin.getConfig().getBoolean("fly-sword.auto-flight.enabled", true);
        autoFlightRequirePermission = plugin.getConfig().getBoolean("fly-sword.auto-flight.require-permission", false);
        autoFlightPermission = plugin.getConfig().getString("fly-sword.auto-flight.permission", permission);
        autoFlightDisableOutsideWorld = plugin.getConfig().getBoolean("fly-sword.auto-flight.disable-outside-world", true);
        autoFlightWorlds = new HashSet<>();
        for (String world : plugin.getConfig().getStringList("fly-sword.auto-flight.worlds")) {
            if (world != null && !world.isBlank()) {
                autoFlightWorlds.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }

        if (!enabled) {
            cleanupAll();
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyAutoFlight(player);
            }
            restartOnlineFlyingPlayers();
        }
    }

    public void sendInfo(Player player) {
        int level = getLevel(player.getUniqueId());
        String currentModel = getModelId(player);
        EvolutionTarget target = nextEvolution(level);
        List<String> lines = plugin.getConfig().getStringList("fly-sword.evolution.info");
        if (lines.isEmpty()) {
            lines = List.of(
                    "&6Kiếm Bay &8| &fCấp: &e%level%",
                    "&7Model hiện tại: &f%model%",
                    "&7Model cấp sau: &e%next_model%",
                    "&7Linh thạch: &e%vault_cost%",
                    "&7Cổ thạch: &d%playerpoints_cost%",
                    "&7Nguyên liệu: &f%materials%"
            );
        }
        for (String line : lines) {
            player.sendMessage(color(applyEvolutionPlaceholders(line, level, currentModel, target)));
        }
    }

    public String replaceEvolutionPlaceholders(Player player, String line) {
        int level = getLevel(player.getUniqueId());
        return applyEvolutionPlaceholders(line, level, getModelId(player), nextEvolution(level));
    }

    public boolean evolve(Player player) {
        if (!evolutionEnabled) {
            player.sendMessage(message("disabled", "&cTiến hoá kiếm bay đang tắt."));
            return false;
        }
        if (requirePermission && !player.hasPermission(permission)) {
            player.sendMessage(message("no-permission", "&cBạn không có quyền tiến hoá kiếm bay."));
            return false;
        }

        int level = getLevel(player.getUniqueId());
        EvolutionTarget target = nextEvolution(level);
        if (target == null) {
            player.sendMessage(message("max-level", "&cKiếm bay đã đạt cấp tối đa."));
            return false;
        }

        List<String> failures = missingRequirements(player, target);
        if (!failures.isEmpty()) {
            player.sendMessage(message("not-enough-header", "&cBạn chưa đủ điều kiện tiến hoá kiếm bay:"));
            for (String failure : failures) {
                player.sendMessage(color(plugin.getConfig().getString("fly-sword.evolution.messages.not-enough-line", "&8- &7%reason%")
                        .replace("%reason%", failure)));
            }
            return false;
        }

        if (!withdrawMoney(player, target.vaultCost())) {
            player.sendMessage(message("not-enough-money", "&cKhông thể trừ Linh thạch."));
            return false;
        }
        if (!takePlayerPoints(player, target.playerPointsCost())) {
            depositMoney(player, target.vaultCost());
            player.sendMessage(message("not-enough-playerpoints", "&cKhông thể trừ Cổ thạch."));
            return false;
        }
        if (!takeMaterials(player, target.materials())) {
            depositMoney(player, target.vaultCost());
            givePlayerPoints(player, target.playerPointsCost());
            player.sendMessage(message("material-take-failed", "&cKhông thể trừ nguyên liệu, vui lòng thử lại."));
            return false;
        }

        setLevel(player.getUniqueId(), target.level());
        player.sendMessage(color(message("success", "&aKiếm bay đã tiến hoá lên cấp &e%level%&a. Model: &b%model%")
                .replace("%level%", String.valueOf(target.level()))
                .replace("%model%", target.model())));
        if (player.isFlying()) {
            stop(player, false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.isFlying()) {
                    start(player);
                }
            });
        }
        return true;
    }

    public void cleanupAll() {
        for (UUID uuid : new java.util.ArrayList<>(flyingPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            stop(player, false);
        }
        flyingPlayers.clear();
    }

    public void stopTask() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        cleanupAll();
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && shouldHideWhileSpectator(player)) {
                stop(player, false);
            } else if (player.isOnline() && player.isFlying()) {
                start(player);
            } else if (!player.isOnline() || !player.getAllowFlight()) {
                stop(player, true);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAutoFlight(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        stop(player, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyAutoFlight(player);
            if (player.isOnline() && player.isFlying() && !shouldHideWhileSpectator(player)) {
                start(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ArmorStand stand = flyingPlayers.get(player.getUniqueId());
        Location to = event.getTo();
        if (shouldHideWhileSpectator(player)) {
            stop(player, false);
            return;
        }
        if (stand != null && player.isFlying() && !stand.isDead() && to != null) {
            updateSwordPosition(player, stand, to);
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (hideWhileSpectator && event.getNewGameMode() == GameMode.SPECTATOR) {
            stop(player, false);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyAutoFlight(player);
            if (player.isOnline() && player.isFlying() && !shouldHideWhileSpectator(player)) {
                start(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAutoFlight(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stop(event.getEntity(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }

    private void start(Player player) {
        if (!enabled || flyingPlayers.containsKey(player.getUniqueId())) return;
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) return;
        String playerModelId = getModelId(player);
        if (playerModelId == null || playerModelId.trim().isEmpty()) return;
        if (requirePermission && !player.hasPermission(permission)) return;
        if (shouldHideWhileSpectator(player)) return;

        try {
            Location loc = swordLocation(player, player.getLocation());
            ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            lockEquipment(stand);
            setDurationIfPresent(stand, "setInterpolationDuration", 0);
            setDurationIfPresent(stand, "setTeleportDuration", 0);

            com.ticxo.modelengine.api.model.ActiveModel activeModel =
                    com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(playerModelId);
            if (activeModel == null) {
                stand.remove();
                plugin.getLogger().warning("ModelEngine fly sword model not found: " + playerModelId);
                return;
            }
            activeModel.setScale(scale);

            com.ticxo.modelengine.api.model.ModeledEntity modeledEntity =
                    com.ticxo.modelengine.api.ModelEngineAPI.createModeledEntity(stand);
            modeledEntity.addModel(activeModel, true);
            flyingPlayers.put(player.getUniqueId(), stand);
            updateSwordPosition(player, stand, player.getLocation());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn fly sword model '" + playerModelId + "': " + e.getMessage());
        }
    }

    private void stop(Player player, boolean keepFlying) {
        if (player == null) return;
        ArmorStand stand = flyingPlayers.remove(player.getUniqueId());
        if (stand == null) return;

        try {
            com.ticxo.modelengine.api.model.ModeledEntity modeledEntity =
                    com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(stand);
            if (modeledEntity != null) {
                modeledEntity.destroy();
            }
        } catch (Exception ignored) {}

        stand.remove();
        if (keepFlying && player.isOnline() && player.getAllowFlight()) {
            player.setFlying(true);
        }
    }

    private void startFollowTask() {
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new java.util.ArrayList<>(flyingPlayers.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    ArmorStand stand = flyingPlayers.get(uuid);
                    if (player == null || !player.isOnline() || !player.isFlying() || shouldHideWhileSpectator(player)
                            || stand == null || stand.isDead()) {
                        if (player != null) stop(player, false);
                        else flyingPlayers.remove(uuid);
                        continue;
                    }
                    updateSwordPosition(player, stand, player.getLocation());
                }
            }
        };
        followTask.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand) || !(event.getDismounted() instanceof Player player)) {
            return;
        }
        if (flyingPlayers.get(player.getUniqueId()) != stand) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isFlying() && !stand.isDead() && !shouldHideWhileSpectator(player)) {
                updateSwordPosition(player, stand, player.getLocation());
            }
        });
    }

    private void updateSwordPosition(Player player, ArmorStand stand, Location baseLocation) {
        if (mountToPlayer && useHeadAnchor()) {
            mountSword(player, stand, baseLocation);
            return;
        }
        Location loc = swordLocation(player, baseLocation);
        stand.teleport(loc);
        stand.setRotation(loc.getYaw(), loc.getPitch());
        stand.setVelocity(player.getVelocity());
    }

    private void mountSword(Player player, ArmorStand stand, Location baseLocation) {
        if (stand.getVehicle() != player) {
            if (stand.getVehicle() != null) {
                stand.leaveVehicle();
            }
            player.addPassenger(stand);
        }
        float yaw = baseLocation.getYaw();
        float pitch = followPitch ? baseLocation.getPitch() : 0.0F;
        stand.setRotation(yaw, pitch);
        stand.setVelocity(player.getVelocity());
    }

    private Location swordLocation(Player player, Location baseLocation) {
        Location anchorLocation = useHeadAnchor()
                ? player.getEyeLocation()
                : baseLocation.clone();
        float yaw = baseLocation.getYaw();
        float pitch = followPitch ? baseLocation.getPitch() : 0.0F;
        Location loc = anchorLocation.clone().add(0, yOffset, 0);
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        return loc;
    }

    private boolean useHeadAnchor() {
        return anchor == null || !anchor.equalsIgnoreCase("FEET");
    }

    private boolean shouldHideWhileSpectator(Player player) {
        return hideWhileSpectator && player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    private void applyAutoFlight(Player player) {
        if (player == null || !player.isOnline() || !autoFlightEnabled) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        boolean allowedWorld = isAutoFlightWorld(player);
        boolean allowedPermission = !autoFlightRequirePermission || player.hasPermission(autoFlightPermission);
        if (allowedWorld && allowedPermission) {
            player.setAllowFlight(true);
        } else if (autoFlightDisableOutsideWorld) {
            player.setFlying(false);
            player.setAllowFlight(false);
            stop(player, false);
        }
    }

    private boolean isAutoFlightWorld(Player player) {
        if (player == null || player.getWorld() == null) return false;
        if (autoFlightWorlds.isEmpty()) return true;
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return autoFlightWorlds.contains(world) || autoFlightWorlds.contains("*");
    }

    private void loadData() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save fly-swords.yml: " + e.getMessage());
        }
    }

    private int getLevel(UUID uuid) {
        return Math.max(1, data.getInt("players." + uuid + ".level", plugin.getConfig().getInt("fly-sword.evolution.default-level", 1)));
    }

    private void setLevel(UUID uuid, int level) {
        data.set("players." + uuid + ".level", level);
        saveData();
    }

    private String getModelId(Player player) {
        int level = getLevel(player.getUniqueId());
        return plugin.getConfig().getString("fly-sword.evolution.levels." + level + ".model", modelId);
    }

    private EvolutionTarget nextEvolution(int currentLevel) {
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("fly-sword.evolution.levels");
        if (levels == null) return null;
        int nextLevel = Integer.MAX_VALUE;
        for (String key : levels.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                if (level > currentLevel && level < nextLevel) {
                    nextLevel = level;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (nextLevel == Integer.MAX_VALUE) return null;

        String path = "fly-sword.evolution.levels." + nextLevel;
        return new EvolutionTarget(
                nextLevel,
                plugin.getConfig().getString(path + ".model", modelId),
                plugin.getConfig().getDouble(path + ".vault-cost", 0.0),
                plugin.getConfig().getInt(path + ".playerpoints-cost", 0),
                readMaterials(path + ".materials")
        );
    }

    private List<MaterialCost> readMaterials(String path) {
        List<MaterialCost> materials = new ArrayList<>();
        for (Map<?, ?> map : plugin.getConfig().getMapList(path)) {
            Object rawType = map.get("type");
            Object rawId = map.get("id");
            String type = String.valueOf(rawType == null ? "" : rawType).trim();
            String id = String.valueOf(rawId == null ? "" : rawId).trim();
            int amount = parseInt(map.get("amount"), 1);
            if (!type.isBlank() && !id.isBlank() && amount > 0) {
                materials.add(new MaterialCost(normalize(type), normalize(id), amount));
            }
        }
        return materials;
    }

    private List<String> missingRequirements(Player player, EvolutionTarget target) {
        List<String> failures = new ArrayList<>();
        if (target.vaultCost() > 0 && !hasMoney(player, target.vaultCost())) {
            failures.add(plugin.getConfig().getString("fly-sword.evolution.messages.missing-money", "Thiếu %amount% Linh thạch")
                    .replace("%amount%", formatNumber(target.vaultCost())));
        }
        if (target.playerPointsCost() > 0 && getPlayerPoints(player) < target.playerPointsCost()) {
            failures.add(plugin.getConfig().getString("fly-sword.evolution.messages.missing-playerpoints", "Thiếu %amount% Cổ thạch")
                    .replace("%amount%", String.valueOf(target.playerPointsCost())));
        }
        for (MaterialCost material : target.materials()) {
            int count = countMaterial(player, material);
            if (count < material.amount()) {
                failures.add(plugin.getConfig().getString("fly-sword.evolution.messages.missing-material", "Thiếu %amount%x %type%:%id%")
                        .replace("%amount%", String.valueOf(material.amount() - count))
                        .replace("%type%", material.type())
                        .replace("%id%", material.id()));
            }
        }
        return failures;
    }

    private boolean takeMaterials(Player player, List<MaterialCost> materials) {
        for (MaterialCost material : materials) {
            if (countMaterial(player, material) < material.amount()) {
                return false;
            }
        }
        for (MaterialCost material : materials) {
            int remaining = material.amount();
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (!matchesMaterial(item, material)) continue;
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
            player.getInventory().setContents(contents);
        }
        return true;
    }

    private int countMaterial(Player player, MaterialCost material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (matchesMaterial(item, material)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean matchesMaterial(ItemStack item, MaterialCost material) {
        if (item == null || item.getType() == Material.AIR) return false;
        try {
            NBTItem nbt = NBTItem.get(item);
            String type = firstNbt(nbt, "MMOITEMS_ITEM_TYPE", "MMOITEMS_TYPE", "type");
            String id = firstNbt(nbt, "MMOITEMS_ITEM_ID", "MMOITEMS_ID", "id");
            return material.type().equals(normalize(type)) && material.id().equals(normalize(id));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String firstNbt(NBTItem nbt, String... keys) {
        for (String key : keys) {
            String value = nbt.getString(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private boolean hasMoney(Player player, double amount) {
        Object economy = vaultEconomy();
        if (economy == null) return amount <= 0;
        try {
            Object result = economy.getClass().getMethod("has", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean withdrawMoney(Player player, double amount) {
        if (amount <= 0) return true;
        Object economy = vaultEconomy();
        if (economy == null) return false;
        try {
            Object response = economy.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            Object success = response.getClass().getMethod("transactionSuccess").invoke(response);
            return success instanceof Boolean value && value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void depositMoney(Player player, double amount) {
        if (amount <= 0) return;
        Object economy = vaultEconomy();
        if (economy == null) return;
        try {
            economy.getClass().getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Object vaultEconomy() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null) return null;
            return registration.getClass().getMethod("getProvider").invoke(registration);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private int getPlayerPoints(Player player) {
        Object api = playerPointsApi();
        if (api == null) return 0;
        try {
            Object result = api.getClass().getMethod("look", UUID.class).invoke(api, player.getUniqueId());
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private boolean takePlayerPoints(Player player, int amount) {
        if (amount <= 0) return true;
        Object api = playerPointsApi();
        if (api == null) return false;
        try {
            Object result = api.getClass().getMethod("take", UUID.class, int.class).invoke(api, player.getUniqueId(), amount);
            return !(result instanceof Boolean value) || value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void givePlayerPoints(Player player, int amount) {
        if (amount <= 0) return;
        Object api = playerPointsApi();
        if (api == null) return;
        try {
            api.getClass().getMethod("give", UUID.class, int.class).invoke(api, player.getUniqueId(), amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Object playerPointsApi() {
        org.bukkit.plugin.Plugin playerPoints = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (playerPoints == null) return null;
        try {
            return playerPoints.getClass().getMethod("getAPI").invoke(playerPoints);
        } catch (ReflectiveOperationException ignored) {
            try {
                Class<?> clazz = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
                Object instance = clazz.getMethod("getInstance").invoke(null);
                return instance.getClass().getMethod("getAPI").invoke(instance);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private String applyEvolutionPlaceholders(String line, int level, String currentModel, EvolutionTarget target) {
        String nextModel = target == null ? plugin.getConfig().getString("fly-sword.evolution.format.max-level", "Đã tối đa") : target.model();
        String vaultCost = target == null ? "0" : formatNumber(target.vaultCost());
        String pointsCost = target == null ? "0" : String.valueOf(target.playerPointsCost());
        String materials = target == null ? plugin.getConfig().getString("fly-sword.evolution.format.no-materials", "Không cần") : formatMaterials(target.materials());
        return line
                .replace("%level%", String.valueOf(level))
                .replace("%model%", currentModel == null ? "" : currentModel)
                .replace("%next_model%", nextModel == null ? "" : nextModel)
                .replace("%vault_cost%", vaultCost)
                .replace("%playerpoints_cost%", pointsCost)
                .replace("%materials%", materials);
    }

    private String formatMaterials(List<MaterialCost> materials) {
        if (materials.isEmpty()) {
            return plugin.getConfig().getString("fly-sword.evolution.format.no-materials", "Không cần");
        }
        List<String> parts = new ArrayList<>();
        for (MaterialCost material : materials) {
            parts.add(material.amount() + "x " + material.type() + ":" + material.id());
        }
        return String.join(", ", parts);
    }

    private String message(String key, String fallback) {
        return color(plugin.getConfig().getString("fly-sword.evolution.messages." + key, fallback));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void restartOnlineFlyingPlayers() {
        for (UUID uuid : new ArrayList<>(flyingPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || !player.isFlying()) {
                if (player != null) stop(player, false);
                continue;
            }
            stop(player, false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.isFlying() && !shouldHideWhileSpectator(player)) {
                    start(player);
                }
            });
        }
    }

    private void setDurationIfPresent(ArmorStand stand, String methodName, int duration) {
        try {
            Method method = stand.getClass().getMethod(methodName, int.class);
            method.invoke(stand, duration);
        } catch (ReflectiveOperationException ignored) {
            // ArmorStand does not expose Display interpolation settings on most Bukkit APIs.
        }
    }

    private void lockEquipment(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING);
        }
    }

    private record MaterialCost(String type, String id, int amount) {
    }

    private record EvolutionTarget(int level, String model, double vaultCost, int playerPointsCost, List<MaterialCost> materials) {
    }
}
