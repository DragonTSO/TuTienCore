package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.core.manager.RealmManager;

import io.lumine.mythic.lib.api.item.NBTItem;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MMOItemsRealmRequirementHook implements Listener {

    private final JavaPlugin plugin;
    private final RealmManager realmManager;
    private final MMOItemsRealmRequirementStat stat;
    private boolean initialized;

    public MMOItemsRealmRequirementHook(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
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
        return canUse(stat, realmManager, player, item, message);
    }

    public static boolean canUse(RealmManager realmManager, Player player, ItemStack item, boolean message) {
        return canUse(new MMOItemsRealmRequirementStat(realmManager), realmManager, player, item, message);
    }

    private static boolean canUse(MMOItemsRealmRequirementStat stat, RealmManager realmManager, Player player, ItemStack item, boolean message) {
        if (item == null || item.getType().isAir()) {
            return true;
        }

        return stat.canUse(player, NBTItem.get(item), message) && canUseUnparsedCanUseLore(realmManager, player, item, message);
    }

    private static boolean canUseUnparsedCanUseLore(RealmManager realmManager, Player player, ItemStack item, boolean message) {
        if (realmManager == null || player == null || item == null || item.getType().isAir()) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return true;

        int requiredRealm = 0;
        for (String line : meta.getLore()) {
            requiredRealm = Math.max(requiredRealm, canUseLoreRequirement(line, "canh gioi"));
        }
        if (requiredRealm <= 0) return true;

        PlayerRealm playerRealm = realmManager.getPlayerRealm(player.getUniqueId());
        int currentRealm = playerRealm == null ? -1 : playerRealm.getRealmId();
        if (currentRealm >= requiredRealm) return true;

        if (message) {
            player.sendMessage(ChatColor.RED + "Canh gioi cua ban chua du de su dung vat pham nay. Can: " + requiredRealm);
        }
        return false;
    }

    public static int canUseLoreRequirement(String line, String label) {
        if (line == null || label == null || label.isBlank()) return 0;
        String colored = ChatColor.translateAlternateColorCodes('&', line);
        String plain = normalizeLoreLine(ChatColor.stripColor(colored));
        if (!plain.contains("{can-use}") && !plain.contains("#can-use#")) return 0;
        String normalizedLabel = normalizeLoreLine(label);
        int labelIndex = plain.indexOf(normalizedLabel);
        if (labelIndex < 0) return 0;
        Matcher matcher = Pattern.compile("\\d+").matcher(plain.substring(labelIndex + normalizedLabel.length()));
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    private static String normalizeLoreLine(String line) {
        String value = line == null ? "" : line;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
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
