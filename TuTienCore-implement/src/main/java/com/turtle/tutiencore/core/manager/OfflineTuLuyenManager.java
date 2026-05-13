package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.core.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OfflineTuLuyenManager implements Listener {

    private static final String PERMISSION = "tutiencore.tuluyen.vip";
    private static final int GUI_SIZE = 27;
    private static final int CLAIM_SLOT = 11;
    private static final int CLAIM_X2_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Set<UUID> openGuis = new HashSet<>();

    private File file;
    private FileConfiguration data;

    public OfflineTuLuyenManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        setup();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save offline-tuluyen.yml: " + e.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.hasPermission(PERMISSION)) {
            data.set(path(uuid, "last-offline-start"), System.currentTimeMillis());
        } else {
            data.set(path(uuid, "last-offline-start"), null);
        }
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long startedAt = data.getLong(path(uuid, "last-offline-start"), 0L);
        data.set(path(uuid, "last-offline-start"), null);

        if (startedAt > 0L && player.hasPermission(PERMISSION)) {
            long offlineSeconds = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
            long intervals = offlineSeconds / configManager.getOfflineIntervalSeconds();
            double earned = 0;
            for (long i = 0; i < intervals; i++) {
                earned += configManager.rollPointsPerInterval();
            }
            if (earned > 0) {
                double pending = data.getDouble(path(uuid, "pending-tuvi"), 0.0);
                data.set(path(uuid, "pending-tuvi"), pending + earned);
            }
        }
        save();

        if (getPendingTuVi(uuid) > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && getPendingTuVi(uuid) > 0) {
                    open(player);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!openGuis.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == CLAIM_SLOT) {
            claim(player, false);
            return;
        }
        if (slot == CLAIM_X2_SLOT) {
            claim(player, true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "offline-tuluyen.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create offline-tuluyen.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    private void open(Player player) {
        double pending = getPendingTuVi(player.getUniqueId());
        if (pending <= 0) {
            return;
        }

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, color("&0✦ Tu Luyện Offline ✦"));
        fill(gui);
        gui.setItem(CLAIM_SLOT, item(Material.EXPERIENCE_BOTTLE, "&a&lNhận Tu Vi", List.of(
                "&8ᴛʜôɴɢ ᴛɪɴ",
                "&7Tu Vi đã tích lũy: &e" + format(pending),
                "&7Hình thức: &aNhận miễn phí",
                "",
                "&aChuột trái để nhận ngay.")));
        gui.setItem(CLAIM_X2_SLOT, item(Material.EMERALD, "&b&lNhận x2 Tu Vi", List.of(
                "&8ɴâɴɢ ᴄấᴘ ᴘʜầɴ ᴛʜưởɴɢ",
                "&7Tu Vi gốc: &e" + format(pending),
                "&7Sau nhân đôi: &b" + format(pending * 2),
                "&7Chi phí: &e" + configManager.getOfflineClaimX2Cost() + " PlayerPoints",
                "",
                "&bChuột trái để nhận x2.")));
        gui.setItem(CLOSE_SLOT, item(Material.BARRIER, "&cĐể Sau", List.of(
                "&7Tu Vi offline vẫn được giữ lại.",
                "&7Bạn có thể claim ở lần vào sau.")));

        openGuis.add(player.getUniqueId());
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    private void claim(Player player, boolean x2) {
        UUID uuid = player.getUniqueId();
        double pending = getPendingTuVi(uuid);
        if (pending <= 0) {
            player.sendMessage(color("&cBạn không có Tu Vi offline để nhận."));
            player.closeInventory();
            return;
        }

        int cost = configManager.getOfflineClaimX2Cost();
        if (x2) {
            if (!takePlayerPoints(player, cost)) {
                player.sendMessage(color("&cKhông đủ &e" + cost + " PlayerPoints &cđể nhận x2. &7Tu Vi offline vẫn được giữ lại."));
                return;
            }
        }

        double reward = x2 ? pending * 2 : pending;
        TuTien.getApi().addTuVi(uuid, reward);
        data.set(path(uuid, "pending-tuvi"), null);
        save();

        player.sendMessage(color("&aĐã nhận &e" + format(reward) + " Tu Vi &atừ tu luyện offline" + (x2 ? " &b(x2)&a." : ".")));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.2f);
        player.closeInventory();
    }

    private boolean takePlayerPoints(Player player, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
            return false;
        }

        try {
            Class<?> playerPointsClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object playerPoints = playerPointsClass.getMethod("getInstance").invoke(null);
            Object api = playerPointsClass.getMethod("getAPI").invoke(playerPoints);
            Method look = api.getClass().getMethod("look", UUID.class);
            int balance = (int) look.invoke(api, player.getUniqueId());
            if (balance < cost) {
                return false;
            }
            Method take = api.getClass().getMethod("take", UUID.class, int.class);
            return (boolean) take.invoke(api, player.getUniqueId(), cost);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not use PlayerPoints API for offline Tu Luyen x2 claim: " + e.getMessage());
            return false;
        }
    }

    private double getPendingTuVi(UUID uuid) {
        return data.getDouble(path(uuid, "pending-tuvi"), 0.0);
    }

    private String path(UUID uuid, String child) {
        return uuid + "." + child;
    }

    private void fill(Inventory gui) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(color(line));
        }
        meta.setLore(coloredLore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String format(double amount) {
        return RealmManager.formatNumber((long) amount);
    }
}
