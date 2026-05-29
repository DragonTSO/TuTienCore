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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
    private static final int GUI_SIZE = 27;
    private static final int CLAIM_SLOT = 11;
    private static final int CLAIM_X2_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Set<UUID> openGuis = new HashSet<>();
    private final Set<UUID> pendingResourcePackOpen = new HashSet<>();

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
        UUID uuid = event.getPlayer().getUniqueId();
        pendingResourcePackOpen.remove(uuid);
        data.set(path(uuid, "last-offline-start"), System.currentTimeMillis());
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long startedAt = data.getLong(path(uuid, "last-offline-start"), 0L);
        data.set(path(uuid, "last-offline-start"), null);

        if (startedAt > 0L) {
            long realOfflineSeconds = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
            long offlineSeconds = Math.min(realOfflineSeconds, getMaxOfflineSeconds());
            long intervals = offlineSeconds / configManager.getOfflineIntervalSeconds();
            double multiplier = getOfflineMultiplier(player);
            double earned = 0.0;

            for (long i = 0; i < intervals; i++) {
                earned += configManager.rollPointsPerInterval();
            }
            earned *= multiplier;

            if (earned > 0) {
                double pending = data.getDouble(path(uuid, "pending-tuvi"), 0.0);
                data.set(path(uuid, "pending-tuvi"), pending + earned);
                data.set(path(uuid, "last-earned-seconds"), offlineSeconds);
                data.set(path(uuid, "last-real-offline-seconds"), realOfflineSeconds);
                data.set(path(uuid, "last-earned-multiplier"), multiplier);
            }
        }
        save();

        if (getPendingTuVi(uuid) > 0) {
            queueOfflineGuiOpen(player);
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!pendingResourcePackOpen.contains(uuid)) {
            return;
        }

        String status = event.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED") || status.equals("DOWNLOADED")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openPendingGui(event.getPlayer()),
                    configManager.getOfflineOpenDelayTicks());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof OfflineGuiHolder)) {
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
        if (event.getInventory().getHolder() instanceof OfflineGuiHolder) {
            openGuis.remove(event.getPlayer().getUniqueId());
        }
    }

    private void queueOfflineGuiOpen(Player player) {
        UUID uuid = player.getUniqueId();
        long lastOfflineSeconds = data.getLong(path(uuid, "last-real-offline-seconds"), 0L);
        if (lastOfflineSeconds < configManager.getOfflineOpenMinSeconds()) {
            return;
        }

        if (!configManager.isOfflineOpenAfterResourcePack()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openPendingGui(player),
                    configManager.getOfflineOpenDelayTicks());
            return;
        }

        pendingResourcePackOpen.add(uuid);
        long fallbackTicks = configManager.getOfflineOpenFallbackDelayTicks();
        if (fallbackTicks > 0L) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openPendingGui(player), fallbackTicks);
        }
    }

    private void openPendingGui(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!pendingResourcePackOpen.remove(uuid) && configManager.isOfflineOpenAfterResourcePack()) {
            return;
        }
        if (getPendingTuVi(uuid) > 0) {
            open(player);
        }
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
        UUID uuid = player.getUniqueId();
        double pending = getPendingTuVi(uuid);
        if (pending <= 0) {
            return;
        }
        if (isViewingOtherMenu(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 20L);
            return;
        }

        long earnedSeconds = data.getLong(path(uuid, "last-earned-seconds"), 0L);
        long realSeconds = data.getLong(path(uuid, "last-real-offline-seconds"), earnedSeconds);
        double multiplier = data.getDouble(path(uuid, "last-earned-multiplier"), getOfflineMultiplier(player));

        OfflineGuiHolder holder = new OfflineGuiHolder();
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, color("&0✦ Tu Luyện Offline ✦"));
        holder.setInventory(gui);
        fill(gui);
        gui.setItem(CLAIM_SLOT, item(Material.EXPERIENCE_BOTTLE, "&a&lNhận Tu Vi", List.of(
                "&8thông tin",
                "&7Tu Vi đã tích lũy: &e" + format(pending),
                "&7Thời gian tính: &b" + formatDuration(earnedSeconds) + getCappedSuffix(realSeconds, earnedSeconds),
                "&7Hiệu suất offline: &a" + formatMultiplier(multiplier),
                "&7Hình thức: &aNhận miễn phí",
                "",
                "&aChuột trái để nhận ngay.")));
        gui.setItem(CLAIM_X2_SLOT, item(Material.EMERALD, "&b&lNhận x2 Tu Vi", List.of(
                "&8nâng cấp phần thưởng",
                "&7Tu Vi gốc: &e" + format(pending),
                "&7Thời gian tính: &b" + formatDuration(earnedSeconds) + getCappedSuffix(realSeconds, earnedSeconds),
                "&7Hiệu suất offline: &a" + formatMultiplier(multiplier),
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

    private boolean isViewingOtherMenu(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof OfflineGuiHolder) {
            return false;
        }
        return top.getType() != InventoryType.CRAFTING;
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
        if (x2 && !takePlayerPoints(player, cost)) {
            player.sendMessage(color("&cKhông đủ &e" + cost + " PlayerPoints &cđể nhận x2. &7Tu Vi offline vẫn được giữ lại."));
            return;
        }

        double reward = x2 ? pending * 2 : pending;
        TuTien.getApi().addTuVi(uuid, reward);
        data.set(path(uuid, "pending-tuvi"), null);
        data.set(path(uuid, "last-earned-seconds"), null);
        data.set(path(uuid, "last-real-offline-seconds"), null);
        data.set(path(uuid, "last-earned-multiplier"), null);
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

    private double getOfflineMultiplier(Player player) {
        return player.hasPermission(offlinePermission())
                ? configManager.getOfflinePermissionMultiplier()
                : configManager.getOfflineDefaultMultiplier();
    }

    private String offlinePermission() {
        String permission = configManager.getOfflinePermission();
        return (permission == null || permission.isBlank()) ? "tutiencore.tuluyen.vip" : permission;
    }

    private long getMaxOfflineSeconds() {
        int maxHours = configManager.getOfflineMaxHours();
        return maxHours <= 0 ? Long.MAX_VALUE : maxHours * 3600L;
    }

    private String getCappedSuffix(long realSeconds, long earnedSeconds) {
        return realSeconds > earnedSeconds ? " &8(đã đạt giới hạn)" : "";
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
        if (meta == null) {
            return item;
        }
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
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String format(double amount) {
        return RealmManager.formatNumber((long) amount);
    }

    private String formatMultiplier(double multiplier) {
        int percent = (int) Math.round(multiplier * 100.0);
        return percent + "%";
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0 giây";
        }
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) {
            return String.format("%d giờ %02d phút", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("%d phút %02d giây", minutes, secs);
        }
        return secs + " giây";
    }

    private static final class OfflineGuiHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
