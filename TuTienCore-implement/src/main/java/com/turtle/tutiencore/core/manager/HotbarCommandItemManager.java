package com.turtle.tutiencore.core.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HotbarCommandItemManager implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<String, HotbarItemRule> rules = new LinkedHashMap<>();
    private final Map<UUID, Long> lastTriggerMillis = new HashMap<>();

    private boolean enabled;
    private boolean replaceExisting;
    private boolean triggerOnSelect;
    private boolean cancelSelect;
    private long restoreDelayTicks;
    private long cooldownTicks;

    public HotbarCommandItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "hotbar_command_item");
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("hotbar-command-items.enabled", true);
        replaceExisting = plugin.getConfig().getBoolean("hotbar-command-items.replace-existing", true);
        triggerOnSelect = plugin.getConfig().getBoolean("hotbar-command-items.trigger.on-select", true);
        cancelSelect = plugin.getConfig().getBoolean("hotbar-command-items.trigger.cancel-select", true);
        restoreDelayTicks = Math.max(1L, plugin.getConfig().getLong("hotbar-command-items.restore-delay-ticks", 2L));
        cooldownTicks = Math.max(0L, plugin.getConfig().getLong("hotbar-command-items.trigger.cooldown-ticks", 10L));
        loadRules();

        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleEnsure(player, restoreDelayTicks);
        }
    }

    private void loadRules() {
        rules.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("hotbar-command-items.items");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = "hotbar-command-items.items." + id;
            if (!plugin.getConfig().getBoolean(path + ".enabled", true)) continue;

            int keySlot = plugin.getConfig().getInt(path + ".slot", -1);
            int inventorySlot = keySlot - 1;
            if (inventorySlot < 0 || inventorySlot > 8) {
                plugin.getLogger().warning("Invalid hotbar-command-items slot for " + id + ": " + keySlot + ". Use 1-9.");
                continue;
            }

            Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "NETHER_STAR"));
            if (material == null || material.isAir()) {
                material = Material.NETHER_STAR;
            }

            rules.put(id, new HotbarItemRule(
                    id,
                    keySlot,
                    inventorySlot,
                    material,
                    plugin.getConfig().getString(path + ".name", "&bHotbar Command"),
                    plugin.getConfig().getStringList(path + ".lore"),
                    plugin.getConfig().getInt(path + ".custom-model-data", 0),
                    plugin.getConfig().getBoolean(path + ".hide-attributes", true),
                    plugin.getConfig().getString(path + ".command", ""),
                    plugin.getConfig().getBoolean(path + ".as-console", false),
                    plugin.getConfig().getString(path + ".permission", "")
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleEnsure(event.getPlayer(), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleEnsure(event.getPlayer(), restoreDelayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleEnsure(event.getPlayer(), restoreDelayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleEnsure(player, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!enabled || !triggerOnSelect) return;

        Player player = event.getPlayer();
        ItemStack selected = player.getInventory().getItem(event.getNewSlot());
        HotbarItemRule rule = ruleFromItem(selected);
        if (rule == null) return;

        if (cancelSelect) {
            event.setCancelled(true);
        }
        scheduleEnsure(player, 1L);
        trigger(player, rule);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isManagedItem(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        scheduleEnsure(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isManagedItem(event.getMainHandItem()) || isManagedItem(event.getOffHandItem())) {
            event.setCancelled(true);
            scheduleEnsure(event.getPlayer(), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!enabled) return;

        boolean locked = false;
        if (isManagedItem(event.getCurrentItem()) || isManagedItem(event.getCursor())) {
            locked = true;
        }
        if (event.getClick() == ClickType.NUMBER_KEY && isProtectedSlot(event.getHotbarButton())) {
            locked = true;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            locked = isManagedItem(mainHand) || isManagedItem(offHand);
        }
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && isProtectedSlot(event.getSlot())) {
            locked = true;
        }

        if (locked) {
            event.setCancelled(true);
            scheduleEnsure(player, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!enabled) return;
        if (isManagedItem(event.getOldCursor())) {
            event.setCancelled(true);
            scheduleEnsure(player, 1L);
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) continue;
            int converted = event.getView().convertSlot(rawSlot);
            if (isProtectedSlot(converted)) {
                event.setCancelled(true);
                scheduleEnsure(player, 1L);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled) return;
        event.getDrops().removeIf(this::isManagedItem);
    }

    public void ensureItems(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;

        PlayerInventory inventory = player.getInventory();
        removeManagedDuplicates(inventory);

        for (HotbarItemRule rule : rules.values()) {
            ItemStack current = inventory.getItem(rule.inventorySlot());
            if (isRuleItem(current, rule.id())) {
                inventory.setItem(rule.inventorySlot(), createItem(rule, player));
                continue;
            }
            if (current != null && !current.getType().isAir()) {
                if (!replaceExisting) continue;
                giveOrDrop(player, current);
            }
            inventory.setItem(rule.inventorySlot(), createItem(rule, player));
        }

        if (cancelSelect && isProtectedSlot(inventory.getHeldItemSlot())) {
            int fallbackSlot = firstUnlockedHotbarSlot(inventory.getHeldItemSlot());
            inventory.setHeldItemSlot(fallbackSlot);
        }
    }

    private void removeManagedDuplicates(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            String id = managedId(item);
            if (id == null) continue;
            HotbarItemRule rule = rules.get(id);
            if (rule == null) {
                inventory.setItem(slot, null);
                continue;
            }
            if (slot != rule.inventorySlot()) {
                inventory.setItem(slot, null);
            }
        }
    }

    private ItemStack createItem(HotbarItemRule rule, Player player) {
        ItemStack item = new ItemStack(rule.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(rule.name().replace("%player%", player.getName()).replace("%slot%", String.valueOf(rule.keySlot()))));
            List<String> lore = new ArrayList<>();
            for (String line : rule.lore()) {
                lore.add(color(line
                        .replace("%player%", player.getName())
                        .replace("%slot%", String.valueOf(rule.keySlot()))
                        .replace("%command%", "/" + stripSlash(rule.command()))));
            }
            meta.setLore(lore);
            if (rule.customModelData() > 0) {
                meta.setCustomModelData(rule.customModelData());
            }
            if (rule.hideAttributes()) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, rule.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void trigger(Player player, HotbarItemRule rule) {
        if (rule.command() == null || rule.command().isBlank()) return;
        long now = System.currentTimeMillis();
        Long last = lastTriggerMillis.get(player.getUniqueId());
        long cooldownMillis = cooldownTicks * 50L;
        if (last != null && cooldownMillis > 0 && now - last < cooldownMillis) {
            return;
        }
        lastTriggerMillis.put(player.getUniqueId(), now);

        String permission = rule.permission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(color(plugin.getConfig().getString("hotbar-command-items.messages.no-permission", "&cBạn không có quyền dùng nút này.")));
            return;
        }

        String command = stripSlash(rule.command())
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (rule.asConsole()) {
                CommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, command);
            } else {
                player.performCommand(command);
            }
        });
    }

    private void scheduleEnsure(Player player, long delay) {
        if (!enabled || player == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureItems(player), Math.max(1L, delay));
    }

    private boolean isProtectedSlot(int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot > 8) return false;
        for (HotbarItemRule rule : rules.values()) {
            if (rule.inventorySlot() == inventorySlot) return true;
        }
        return false;
    }

    private int firstUnlockedHotbarSlot(int fallback) {
        for (int slot = 0; slot <= 8; slot++) {
            if (!isProtectedSlot(slot)) return slot;
        }
        return Math.max(0, Math.min(8, fallback));
    }

    private boolean isManagedItem(ItemStack item) {
        return ruleFromItem(item) != null;
    }

    private boolean isRuleItem(ItemStack item, String id) {
        String itemId = managedId(item);
        return itemId != null && itemId.equals(id);
    }

    private HotbarItemRule ruleFromItem(ItemStack item) {
        String id = managedId(item);
        return id == null ? null : rules.get(id);
    }

    private String managedId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String id = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) return null;
        return id;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private String stripSlash(String command) {
        if (command == null) return "";
        String result = command.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private record HotbarItemRule(
            String id,
            int keySlot,
            int inventorySlot,
            Material material,
            String name,
            List<String> lore,
            int customModelData,
            boolean hideAttributes,
            String command,
            boolean asConsole,
            String permission) {
    }
}
