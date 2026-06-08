package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.core.model.CuboidZone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ZoneManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, CuboidZone> zones = new HashMap<>();
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    private final Material wandMaterial = Material.BLAZE_ROD;
    private final NamespacedKey zoneWandKey;
    private File file;
    private FileConfiguration config;

    public ZoneManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.zoneWandKey = new NamespacedKey(plugin, "zone_wand");
        loadFile();
        loadZones();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadFile() {
        file = new File(plugin.getDataFolder(), "zones.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("Cannot create zones.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void loadZones() {
        config = YamlConfiguration.loadConfiguration(file);
        zones.clear();
        if (!config.contains("zones")) {
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("zones");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            Map<String, Object> map = new HashMap<>();
            map.put("pos1", section.getLocation(key + ".pos1"));
            map.put("pos2", section.getLocation(key + ".pos2"));
            if (section.contains(key + ".center")) {
                map.put("center", section.getLocation(key + ".center"));
            }
            map.put("tuvi-bonus-percent", section.getDouble(key + ".tuvi-bonus-percent", 0.0D));
            zones.put(key, CuboidZone.deserialize(key, map));
        }
    }

    public void saveZones() {
        config.set("zones", null);
        for (CuboidZone zone : zones.values()) {
            config.set("zones." + zone.getId() + ".pos1", zone.getPos1());
            config.set("zones." + zone.getId() + ".pos2", zone.getPos2());
            if (zone.getCenter() != null) {
                config.set("zones." + zone.getId() + ".center", zone.getCenter());
            }
            config.set("zones." + zone.getId() + ".tuvi-bonus-percent", zone.getTuViBonusPercent());
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Cannot save zones.yml");
        }
    }

    public void createZone(String id, Location pos1, Location pos2) {
        CuboidZone zone = new CuboidZone(id, pos1, pos2);
        zones.put(id, zone);
        saveZones();
    }

    public void deleteZone(String id) {
        zones.remove(id);
        saveZones();
    }

    public CuboidZone getZone(String id) {
        return zones.get(id);
    }

    public CuboidZone getZoneAt(Location location) {
        for (CuboidZone zone : zones.values()) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    public Collection<CuboidZone> getAllZones() {
        return zones.values();
    }

    public Collection<String> getZoneIds() {
        return zones.keySet();
    }

    public Material getWandMaterial() {
        return wandMaterial;
    }

    public Location getPos1(Player player) {
        return pos1Map.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2Map.get(player.getUniqueId());
    }

    public ItemStack createWandItem() {
        ItemStack wand = new ItemStack(wandMaterial);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&bTuTien Zone Wand"));
            meta.setLore(List.of(
                    color("&7Chuột trái: &fđặt Pos1"),
                    color("&7Chuột phải: &fđặt Pos2"),
                    color("&8/ttc create <name>")
            ));
            meta.getPersistentDataContainer().set(zoneWandKey, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public void openEditGui(Player player, String zoneId) {
        CuboidZone zone = zones.get(zoneId);
        if (zone == null) {
            player.sendMessage(color("&cKhông tìm thấy zone &f" + zoneId + "&c."));
            return;
        }

        ZoneEditHolder holder = new ZoneEditHolder(zoneId);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&8Sửa AFK Zone: &f" + zoneId));
        holder.setInventory(inventory);
        renderEditGui(inventory, zone);
        player.openInventory(inventory);
    }

    private void renderEditGui(Inventory inventory, CuboidZone zone) {
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(4, named(Material.END_CRYSTAL, "&d" + zone.getId(), List.of(
                "&7Bonus TuVi hiện tại:",
                "&f+" + formatBonus(zone.getTuViBonusPercent()) + "%"
        )));
        inventory.setItem(10, named(Material.REDSTONE, "&c-10%", List.of("&7Giảm bonus TuVi 10%")));
        inventory.setItem(11, named(Material.REDSTONE_TORCH, "&c-5%", List.of("&7Giảm bonus TuVi 5%")));
        inventory.setItem(12, named(Material.RED_DYE, "&c-1%", List.of("&7Giảm bonus TuVi 1%")));
        inventory.setItem(14, named(Material.LIME_DYE, "&a+1%", List.of("&7Tăng bonus TuVi 1%")));
        inventory.setItem(15, named(Material.EMERALD, "&a+5%", List.of("&7Tăng bonus TuVi 5%")));
        inventory.setItem(16, named(Material.EMERALD_BLOCK, "&a+10%", List.of("&7Tăng bonus TuVi 10%")));
        inventory.setItem(22, named(Material.WRITABLE_BOOK, "&aLưu và đóng", List.of(
                "&7Đã tự lưu mỗi lần bấm.",
                "&fClick để đóng menu."
        )));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ZoneEditHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }

        CuboidZone zone = zones.get(holder.zoneId());
        if (zone == null) {
            player.closeInventory();
            player.sendMessage(color("&cZone này không còn tồn tại."));
            return;
        }

        double delta = switch (event.getRawSlot()) {
            case 10 -> -10.0D;
            case 11 -> -5.0D;
            case 12 -> -1.0D;
            case 14 -> 1.0D;
            case 15 -> 5.0D;
            case 16 -> 10.0D;
            default -> 0.0D;
        };

        if (event.getRawSlot() == 22) {
            player.closeInventory();
            player.sendMessage(color("&aĐã lưu AFK Zone &f" + zone.getId() + " &avới bonus &f+"
                    + formatBonus(zone.getTuViBonusPercent()) + "%&a."));
            return;
        }

        if (delta == 0.0D) {
            return;
        }

        zone.setTuViBonusPercent(zone.getTuViBonusPercent() + delta);
        saveZones();
        renderEditGui(event.getInventory(), zone);
        player.sendMessage(color("&aBonus TuVi của &f" + zone.getId() + " &a= &f+"
                + formatBonus(zone.getTuViBonusPercent()) + "%&a."));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ZoneEditHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!player.hasPermission("tutiencore.admin")) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != wandMaterial || !isZoneWand(event.getItem())) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location location = event.getClickedBlock().getLocation();
            pos1Map.put(player.getUniqueId(), location);
            player.sendMessage(color("&aĐã đặt Pos1 tại &f" + location.getBlockX() + ", "
                    + location.getBlockY() + ", " + location.getBlockZ()));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location location = event.getClickedBlock().getLocation();
            pos2Map.put(player.getUniqueId(), location);
            player.sendMessage(color("&aĐã đặt Pos2 tại &f" + location.getBlockX() + ", "
                    + location.getBlockY() + ", " + location.getBlockZ()));
        }
    }

    private boolean isZoneWand(ItemStack item) {
        if (item == null || item.getType() != wandMaterial || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(zoneWandKey, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) {
            return true;
        }
        return meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).contains("TuTien Zone Wand");
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(color(line));
            }
            meta.setLore(coloredLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String formatBonus(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static final class ZoneEditHolder implements InventoryHolder {
        private final String zoneId;
        private Inventory inventory;

        private ZoneEditHolder(String zoneId) {
            this.zoneId = zoneId;
        }

        private String zoneId() {
            return zoneId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
