package com.turtle.tutiencore.core.hook;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.inventory.EquippedItem;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class MMOItemsMaxHealthPercentHook implements Listener {

    private static final String TARGET_STAT = "MAX_HEALTH";
    private static final String MODIFIER_KEY = "tutiencore.max_health_percent.equipment";

    private final JavaPlugin plugin;
    private final MMOItemsMaxHealthPercentStat stat;
    private boolean initialized;

    public MMOItemsMaxHealthPercentHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stat = new MMOItemsMaxHealthPercentStat();
    }

    public void register() {
        initialize();
        Bukkit.getScheduler().runTask(plugin, this::initialize);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 100L);
    }

    public void removeAllOnlineModifiers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeModifier(player);
        }
    }

    @EventHandler
    public void onMMOItemsReload(MMOItemsReloadEvent event) {
        registerStat();
        updateAllOnlinePlayers();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMMOItemsEquip(net.Indyuce.mmoitems.api.event.item.ItemEquipEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMMOItemsInventoryEquip(net.Indyuce.mmoitems.api.event.inventory.ItemEquipEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMMOItemsInventoryUnequip(net.Indyuce.mmoitems.api.event.inventory.ItemUnequipEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateLater(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        updateLater(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeModifier(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            updateLater(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            updateLater(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        updateLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            updateLater(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        updateLater(event.getPlayer());
    }

    private void initialize() {
        if (initialized || !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }

        try {
            registerStat();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getScheduler().runTask(plugin, this::updateAllOnlinePlayers);
            initialized = true;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not register MMOItems stat "
                    + MMOItemsMaxHealthPercentStat.STAT_ID + ": " + exception.getMessage());
        }
    }

    private void registerStat() {
        if (MMOItems.plugin.getStats().get(MMOItemsMaxHealthPercentStat.STAT_ID) != null) {
            return;
        }

        MMOItems.plugin.getStats().register(stat);
        plugin.getLogger().info("Registered MMOItems stat " + MMOItemsMaxHealthPercentStat.STAT_ID);
    }

    private void updateAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    private void updateLater(Player player) {
        updateLater(player, 1L);
    }

    private void updateLater(Player player, long delay) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                updatePlayer(player);
            }
        }, delay);
    }

    private void updatePlayer(Player player) {
        double percent = getEquipmentPercent(player);
        applyModifier(player, percent);
    }

    private double getEquipmentPercent(Player player) {
        Double mmoItemsPercent = getMMOItemsEquippedPercent(player);
        if (mmoItemsPercent != null) {
            return mmoItemsPercent;
        }

        return getVanillaEquipmentPercent(player);
    }

    private Double getMMOItemsEquippedPercent(Player player) {
        try {
            PlayerData playerData = PlayerData.get(player);
            if (playerData == null || playerData.getInventory().getEquipped().isEmpty()) {
                return null;
            }

            double percent = 0;
            for (EquippedItem equippedItem : playerData.getInventory().getEquipped()) {
                if (!equippedItem.isPlacementLegal() || !equippedItem.isUsable(playerData.getRPG())) {
                    continue;
                }

                percent += stat.readPercent(equippedItem.getItem());
            }
            return percent;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private double getVanillaEquipmentPercent(Player player) {
        PlayerInventory inventory = player.getInventory();
        double percent = 0;

        percent += getItemPercent(inventory.getItemInMainHand());
        percent += getItemPercent(inventory.getItemInOffHand());
        percent += getItemPercent(inventory.getHelmet());
        percent += getItemPercent(inventory.getChestplate());
        percent += getItemPercent(inventory.getLeggings());
        percent += getItemPercent(inventory.getBoots());

        return percent;
    }

    private double getItemPercent(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }

        try {
            return stat.readPercent(NBTItem.get(item));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void applyModifier(Player player, double percent) {
        StatInstance instance = getTargetInstance(player);
        if (instance == null) {
            return;
        }

        instance.remove(MODIFIER_KEY);
        if (percent <= 0) {
            return;
        }

        StatModifier modifier = new StatModifier(
                MODIFIER_KEY,
                TARGET_STAT,
                percent / 100.0,
                io.lumine.mythic.lib.player.modifier.ModifierType.RELATIVE
        );
        instance.addModifier(modifier);
    }

    private void removeModifier(Player player) {
        StatInstance instance = getTargetInstance(player);
        if (instance != null) {
            instance.remove(MODIFIER_KEY);
        }
    }

    private StatInstance getTargetInstance(Player player) {
        try {
            MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());
            if (mmoData == null) {
                return null;
            }

            StatMap statMap = mmoData.getStatMap();
            if (statMap == null) {
                return null;
            }

            return statMap.getInstance(TARGET_STAT);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
