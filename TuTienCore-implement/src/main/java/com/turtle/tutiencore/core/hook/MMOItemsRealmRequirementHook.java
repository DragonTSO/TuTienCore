package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.core.manager.RealmManager;

import io.lumine.mythic.lib.api.item.NBTItem;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class MMOItemsRealmRequirementHook implements Listener {

    private final JavaPlugin plugin;
    private final MMOItemsRealmRequirementStat stat;
    private boolean initialized;

    public MMOItemsRealmRequirementHook(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.stat = new MMOItemsRealmRequirementStat(realmManager);
    }

    public void register() {
        initialize();
        Bukkit.getScheduler().runTask(plugin, this::initialize);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 100L);
    }

    @EventHandler
    public void onMMOItemsReload(MMOItemsReloadEvent event) {
        registerStat();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMMOItemsEquip(net.Indyuce.mmoitems.api.event.item.ItemEquipEvent event) {
        if (!canUse(event.getPlayer(), event.getItem(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!canUse(player, event.getItem(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (!canUse(event.getPlayer(), newItem, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!canUse(player, event.getMainHandItem(), true) || !canUse(player, event.getOffHandItem(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !shouldCheckMainHandForDamage(true)) {
            return;
        }

        if (!canUse(player, player.getInventory().getItemInMainHand(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> removeUnusableEquipment(player));
    }

    private void initialize() {
        if (initialized || !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }

        try {
            registerStat();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            initialized = true;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not register MMOItems stat "
                    + MMOItemsRealmRequirementStat.STAT_ID + ": " + exception.getMessage());
        }
    }

    private void registerStat() {
        if (MMOItemsStatRegistry.registerOrReplace(stat)) {
            plugin.getLogger().info("Registered MMOItems stat " + MMOItemsRealmRequirementStat.STAT_ID);
        }
    }

    private boolean canUse(Player player, ItemStack item, boolean message) {
        if (item == null || item.getType().isAir()) {
            return true;
        }

        return stat.canUse(player, NBTItem.get(item), message);
    }

    static boolean shouldCheckMainHandForDamage(boolean directPlayerDamage) {
        return directPlayerDamage;
    }

    private void removeUnusableEquipment(Player player) {
        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> overflow = new HashMap<>();

        removeUnusableEquipmentItem(player, inventory, inventory.getHelmet(), EquipmentSlotKind.HELMET, overflow);
        removeUnusableEquipmentItem(player, inventory, inventory.getChestplate(), EquipmentSlotKind.CHESTPLATE, overflow);
        removeUnusableEquipmentItem(player, inventory, inventory.getLeggings(), EquipmentSlotKind.LEGGINGS, overflow);
        removeUnusableEquipmentItem(player, inventory, inventory.getBoots(), EquipmentSlotKind.BOOTS, overflow);
        removeUnusableEquipmentItem(player, inventory, inventory.getItemInOffHand(), EquipmentSlotKind.OFF_HAND, overflow);

        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void removeUnusableEquipmentItem(Player player, PlayerInventory inventory, ItemStack item, EquipmentSlotKind slot, Map<Integer, ItemStack> overflow) {
        if (canUse(player, item, true)) {
            return;
        }

        slot.clear(inventory);
        overflow.putAll(inventory.addItem(item));
    }

    private enum EquipmentSlotKind {
        HELMET {
            @Override
            void clear(PlayerInventory inventory) {
                inventory.setHelmet(null);
            }
        },
        CHESTPLATE {
            @Override
            void clear(PlayerInventory inventory) {
                inventory.setChestplate(null);
            }
        },
        LEGGINGS {
            @Override
            void clear(PlayerInventory inventory) {
                inventory.setLeggings(null);
            }
        },
        BOOTS {
            @Override
            void clear(PlayerInventory inventory) {
                inventory.setBoots(null);
            }
        },
        OFF_HAND {
            @Override
            void clear(PlayerInventory inventory) {
                inventory.setItemInOffHand(null);
            }
        };

        abstract void clear(PlayerInventory inventory);
    }
}
