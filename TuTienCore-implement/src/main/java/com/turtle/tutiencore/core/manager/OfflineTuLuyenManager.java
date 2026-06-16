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
    private static final String GUI_PATH = "gui";
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
    private FileConfiguration guiConfig;

    public OfflineTuLuyenManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        setup();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void save() {
        // Snapshot on the calling (main) thread, then write to disk off-thread so player
        // join/quit never blocks the server tick on file I/O.
        final String snapshot = data.saveToString();
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeSnapshot(snapshot));
        } else {
            // Plugin disabling — must write synchronously since the scheduler is shutting down.
            writeSnapshot(snapshot);
        }
    }

    private synchronized void writeSnapshot(String snapshot) {
        try {
            java.nio.file.Files.writeString(file.toPath(), snapshot,
                    java.nio.charset.StandardCharsets.UTF_8);
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
        if (isGuiItemEnabled("close", true) && slot == itemSlot("close", CLOSE_SLOT)) {
            player.closeInventory();
            return;
        }
        if (isGuiItemEnabled("claim", true) && slot == itemSlot("claim", CLAIM_SLOT)) {
            claim(player, false);
            return;
        }
        if (isGuiItemEnabled("claim-x2", true) && slot == itemSlot("claim-x2", CLAIM_X2_SLOT)) {
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
        loadGuiConfig();
    }

    private void loadGuiConfig() {
        File guiFile = new File(plugin.getDataFolder(), "gui/offline-tuluyen.yml");
        if (!guiFile.exists()) {
            if (guiFile.getParentFile() != null && !guiFile.getParentFile().exists()) {
                guiFile.getParentFile().mkdirs();
            }
            plugin.saveResource("gui/offline-tuluyen.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
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
        OfflineGuiContext context = new OfflineGuiContext(player, pending, earnedSeconds, realSeconds, multiplier);

        OfflineGuiHolder holder = new OfflineGuiHolder();
        Inventory gui = Bukkit.createInventory(holder, guiSize(), color(formatPlaceholders(
                guiConfig.getString(GUI_PATH + ".title", "&0✦ Tu Luyện Offline ✦"), context)));
        holder.setInventory(gui);
        fill(gui, context);
        setGuiItem(gui, "claim", CLAIM_SLOT, Material.EXPERIENCE_BOTTLE, "&a&lNhận Tu Vi", List.of(
                "&8thông tin",
                "&7Tu Vi đã tích lũy: &e%tuvi%",
                "&7Thời gian tính: &b%earned_time%%capped_suffix%",
                "&7Hiệu suất offline: &a%multiplier%",
                "&7Hình thức: &aNhận miễn phí",
                "",
                "&aChuột trái để nhận ngay."), context);
        setGuiItem(gui, "claim-x2", CLAIM_X2_SLOT, Material.EMERALD, "&b&lNhận x2 Tu Vi", List.of(
                "&8nâng cấp phần thưởng",
                "&7Tu Vi gốc: &e%tuvi%",
                "&7Thời gian tính: &b%earned_time%%capped_suffix%",
                "&7Hiệu suất offline: &a%multiplier%",
                "&7Sau nhân đôi: &b%tuvi_x2%",
                "&7Chi phí: &e%x2_cost% PlayerPoints",
                "",
                "&bChuột trái để nhận x2."), context);
        setGuiItem(gui, "close", CLOSE_SLOT, Material.BARRIER, "&cĐể Sau", List.of(
                "&7Tu Vi offline vẫn được giữ lại.",
                "&7Bạn có thể claim ở lần vào sau."), context);

        openGuis.add(player.getUniqueId());
        player.openInventory(gui);
        playConfiguredSound(player, "open-sound", Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);
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
        OfflineGuiContext context = new OfflineGuiContext(
                player,
                pending,
                data.getLong(path(uuid, "last-earned-seconds"), 0L),
                data.getLong(path(uuid, "last-real-offline-seconds"), 0L),
                data.getDouble(path(uuid, "last-earned-multiplier"), getOfflineMultiplier(player))
        );
        if (pending <= 0) {
            player.sendMessage(message("no-pending", "&cBạn không có Tu Vi offline để nhận.", context));
            player.closeInventory();
            return;
        }

        if (x2 && !canUseClaimX2(player)) {
            player.sendMessage(message("x2-no-permission",
                    "&cBạn cần thuê gói nhận x2 theo tháng để dùng nút này. &7Permission: &e%x2_permission%", context));
            return;
        }

        int cost = configManager.getOfflineClaimX2Cost();
        if (x2 && !takePlayerPoints(player, cost)) {
            player.sendMessage(message("not-enough-points",
                    "&cKhông đủ &e%x2_cost% PlayerPoints &cđể nhận x2. &7Tu Vi offline vẫn được giữ lại.", context));
            return;
        }

        double reward = x2 ? pending * 2 : pending;
        TuTien.getApi().addTuVi(uuid, reward);
        data.set(path(uuid, "pending-tuvi"), null);
        data.set(path(uuid, "last-earned-seconds"), null);
        data.set(path(uuid, "last-real-offline-seconds"), null);
        data.set(path(uuid, "last-earned-multiplier"), null);
        save();

        player.sendMessage(message(x2 ? "claim-x2-success" : "claim-success",
                x2 ? "&aĐã nhận &e%reward% Tu Vi &atừ tu luyện offline &b(x2)&a."
                        : "&aĐã nhận &e%reward% Tu Vi &atừ tu luyện offline.",
                context.withReward(reward)));
        playConfiguredSound(player, "claim-sound", Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.2f);
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

    private boolean canUseClaimX2(Player player) {
        if (!configManager.isOfflineClaimX2PermissionRequired()) {
            return true;
        }
        String permission = offlineClaimX2Permission();
        return permission.isBlank() || player.hasPermission(permission);
    }

    private String offlineClaimX2Permission() {
        String permission = configManager.getOfflineClaimX2Permission();
        return permission == null ? "" : permission;
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

    private int guiSize() {
        int size = guiConfig.getInt(GUI_PATH + ".size", GUI_SIZE);
        size = Math.max(9, Math.min(54, size));
        return ((size + 8) / 9) * 9;
    }

    private boolean isGuiItemEnabled(String id, boolean fallback) {
        return guiConfig.getBoolean(GUI_PATH + ".items." + id + ".enabled", fallback);
    }

    private int itemSlot(String id, int fallback) {
        return guiConfig.getInt(GUI_PATH + ".items." + id + ".slot", fallback);
    }

    private void fill(Inventory gui, OfflineGuiContext context) {
        ItemStack pane = configItem(GUI_PATH + ".filler", Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), context);
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }

    private void setGuiItem(Inventory gui, String id, int fallbackSlot, Material fallbackMaterial,
                            String fallbackName, List<String> fallbackLore, OfflineGuiContext context) {
        if (!isGuiItemEnabled(id, true)) {
            return;
        }
        int slot = itemSlot(id, fallbackSlot);
        if (slot < 0 || slot >= gui.getSize()) {
            return;
        }
        gui.setItem(slot, configItem(GUI_PATH + ".items." + id, fallbackMaterial, fallbackName, fallbackLore, context));
    }

    private ItemStack configItem(String path, Material fallbackMaterial, String fallbackName,
                                 List<String> fallbackLore, OfflineGuiContext context) {
        Material material = material(guiConfig.getString(path + ".material"), fallbackMaterial);
        String name = guiConfig.getString(path + ".name", fallbackName);
        List<String> lore = guiConfig.getStringList(path + ".lore");
        if (lore.isEmpty()) {
            lore = fallbackLore;
        }
        return item(material, formatPlaceholders(name, context), formatPlaceholders(lore, context));
    }

    private Material material(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material material = Material.getMaterial(value.trim().toUpperCase());
        return material == null ? fallback : material;
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

    private String message(String path, String fallback, OfflineGuiContext context) {
        return color(formatPlaceholders(guiConfig.getString(GUI_PATH + ".messages." + path, fallback), context));
    }

    private void playConfiguredSound(Player player, String path, Sound fallbackSound, SoundCategory fallbackCategory,
                                     float fallbackVolume, float fallbackPitch) {
        String base = GUI_PATH + "." + path;
        if (!guiConfig.getBoolean(base + ".enabled", true)) {
            return;
        }
        Sound sound = parseSound(guiConfig.getString(base + ".sound", fallbackSound.name()), fallbackSound);
        SoundCategory category = parseSoundCategory(guiConfig.getString(base + ".category", fallbackCategory.name()), fallbackCategory);
        float volume = (float) guiConfig.getDouble(base + ".volume", fallbackVolume);
        float pitch = (float) guiConfig.getDouble(base + ".pitch", fallbackPitch);
        player.playSound(player.getLocation(), sound, category, volume, pitch);
    }

    private Sound parseSound(String value, Sound fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(value.trim().toUpperCase().replace('.', '_').replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private SoundCategory parseSoundCategory(String value, SoundCategory fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return SoundCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private List<String> formatPlaceholders(List<String> lines, OfflineGuiContext context) {
        List<String> formatted = new ArrayList<>();
        for (String line : lines) {
            formatted.add(formatPlaceholders(line, context));
        }
        return formatted;
    }

    private String formatPlaceholders(String text, OfflineGuiContext context) {
        if (text == null) {
            return "";
        }
        if (context == null) {
            return text;
        }
        return text
                .replace("%player%", context.player.getName())
                .replace("%player_name%", context.player.getName())
                .replace("%tuvi%", format(context.pending))
                .replace("%tuvi_x2%", format(context.pending * 2))
                .replace("%reward%", format(context.reward))
                .replace("%earned_seconds%", String.valueOf(context.earnedSeconds))
                .replace("%real_seconds%", String.valueOf(context.realSeconds))
                .replace("%earned_time%", formatDuration(context.earnedSeconds))
                .replace("%real_time%", formatDuration(context.realSeconds))
                .replace("%capped_suffix%", getCappedSuffix(context.realSeconds, context.earnedSeconds))
                .replace("%multiplier%", formatMultiplier(context.multiplier))
                .replace("%multiplier_percent%", formatMultiplier(context.multiplier))
                .replace("%x2_cost%", String.valueOf(configManager.getOfflineClaimX2Cost()))
                .replace("%x2_permission%", offlineClaimX2Permission())
                .replace("%x2_permission_status%", canUseClaimX2(context.player) ? "&aĐã thuê" : "&cChưa thuê");
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

    private static final class OfflineGuiContext {
        private final Player player;
        private final double pending;
        private final long earnedSeconds;
        private final long realSeconds;
        private final double multiplier;
        private final double reward;

        private OfflineGuiContext(Player player, double pending, long earnedSeconds, long realSeconds, double multiplier) {
            this(player, pending, earnedSeconds, realSeconds <= 0L ? earnedSeconds : realSeconds, multiplier, pending);
        }

        private OfflineGuiContext(Player player, double pending, long earnedSeconds, long realSeconds,
                                  double multiplier, double reward) {
            this.player = player;
            this.pending = pending;
            this.earnedSeconds = earnedSeconds;
            this.realSeconds = realSeconds;
            this.multiplier = multiplier;
            this.reward = reward;
        }

        private OfflineGuiContext withReward(double reward) {
            return new OfflineGuiContext(player, pending, earnedSeconds, realSeconds, multiplier, reward);
        }
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
