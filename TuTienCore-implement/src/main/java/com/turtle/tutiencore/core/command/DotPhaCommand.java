package com.turtle.tutiencore.core.command;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.manager.BreakthroughManager;
import com.turtle.tutiencore.core.manager.RealmManager;
import com.turtle.tutiencore.core.gui.RealmListGUI;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Lệnh /dotpha — Mở menu Đột Phá Cảnh Giới
 * 
 * GUI hiển thị:
 * - Thông tin cảnh giới hiện tại
 * - Điều kiện đột phá (check ✅/❌)
 * - Nút xác nhận bắt đầu Thiên Lôi Kiếp
 */
public class DotPhaCommand implements CommandExecutor, Listener {

    private final JavaPlugin plugin;
    private final RealmManager realmManager;
    private final BreakthroughManager breakthroughManager;
    private final RealmListGUI realmListGUI;

    private FileConfiguration guiConfig;
    private String guiTitle;
    private String confirmGuiTitle;

    // Track which players have the GUI open
    private final Set<UUID> openGuis = new HashSet<>();
    private final Set<UUID> confirmGuis = new HashSet<>();

    private static class BreakthroughButtonData {
        final String configPath;
        final Material fallbackMaterial;
        final String fallbackName;
        final List<String> fallbackLore;
        final Map<String, String> placeholders;
        final boolean ready;

        BreakthroughButtonData(String configPath, Material fallbackMaterial, String fallbackName,
                               List<String> fallbackLore, Map<String, String> placeholders,
                               boolean ready) {
            this.configPath = configPath;
            this.fallbackMaterial = fallbackMaterial;
            this.fallbackName = fallbackName;
            this.fallbackLore = fallbackLore;
            this.placeholders = placeholders;
            this.ready = ready;
        }
    }

    public DotPhaCommand(JavaPlugin plugin, RealmManager realmManager, BreakthroughManager breakthroughManager, RealmListGUI realmListGUI) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        this.breakthroughManager = breakthroughManager;
        this.realmListGUI = realmListGUI;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "gui/dotpha.yml");
        if (!file.exists()) {
            plugin.saveResource("gui/dotpha.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(file);
        guiTitle = color(guiConfig.getString("main-menu.title", "&5&l⚡ Đột Phá Cảnh Giới ⚡"));
        confirmGuiTitle = color(guiConfig.getString("confirm-menu.title", "&c&l⚡ XÁC NHẬN ĐỘT PHÁ ⚡"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được cho người chơi!");
            return true;
        }

        Player player = (Player) sender;

        // Check if already in breakthrough
        if (breakthroughManager.isInBreakthrough(player.getUniqueId())) {
            player.sendMessage("§c⚡ Bạn đang trong quá trình Thiên Lôi Kiếp! Không thể mở menu.");
            return true;
        }

        openBreakthroughMenu(player);
        return true;
    }

    // ==========================================
    // MAIN BREAKTHROUGH MENU
    // ==========================================

    private void openBreakthroughMenu(Player player) {
        UUID uuid = player.getUniqueId();
        int size = guiConfig.getInt("main-menu.size", 54);
        Inventory gui = Bukkit.createInventory(null, size, guiTitle);

        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        Realm nextRealm = realmManager.getNextRealm(uuid);
        double tuVi = TuTien.getApi().getTuVi(uuid);

        // ==========================================
        // Row 1: Decorative border (glass panes)
        // ==========================================
        fillConfiguredSlots(gui, "main-menu.items.border", size);

        // ==========================================
        // Slot 13: Current Realm Info (center top area)
        // ==========================================
        Map<String, String> currentPlaceholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
        gui.setItem(getSlot("main-menu.items.current-realm", 13), createConfiguredItem(player,
                "main-menu.items.current-realm", Material.NETHER_STAR,
                currentRealm.getFormattedName() + " §7— §e" + pr.getSubRealm().getDisplayName(),
                Collections.emptyList(), currentPlaceholders));

        // ==========================================
        // Slot 22: Info panel (Realm List)
        // ==========================================
        gui.setItem(getSlot("main-menu.items.realm-list", 22), createConfiguredItem(player,
                "main-menu.items.realm-list", Material.BOOK, "§b§l📖 Danh Sách Cảnh Giới", Collections.emptyList(),
                createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi)));

        // Unified breakthrough button: one item handles both sub-realm and major breakthroughs.
        int legacySubSlot = getSlot("main-menu.items.sub-realm-breakthrough", 20);
        int legacyMajorSlot = getSlot("main-menu.items.major-breakthrough", 24);
        clearSlot(gui, legacySubSlot, size);
        clearSlot(gui, legacyMajorSlot, size);
        BreakthroughButtonData breakthroughButton = createBreakthroughButton(player, pr, currentRealm, nextRealm, tuVi);
        gui.setItem(getSlot("main-menu.items.breakthrough", legacyMajorSlot), createConfiguredItem(player,
                breakthroughButton.configPath, breakthroughButton.fallbackMaterial,
                breakthroughButton.fallbackName, breakthroughButton.fallbackLore,
                breakthroughButton.placeholders, breakthroughButton.ready));

        // ==========================================
        // Slot 31: Tips/Strategy
        // ==========================================
        gui.setItem(getSlot("main-menu.items.tips", 31), createConfiguredItem(player,
                "main-menu.items.tips", Material.WRITABLE_BOOK, "§e§l💡 Chiến Thuật", Collections.emptyList(),
                createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi)));

        // ==========================================
        // Slot 49: Close button
        // ==========================================
        gui.setItem(getSlot("main-menu.items.close-button", 49), createConfiguredItem(player,
                "main-menu.items.close-button", Material.BARRIER, "§c§lĐóng Menu",
                Collections.emptyList(), createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi)));

        openGuis.add(uuid);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    private BreakthroughButtonData createBreakthroughButton(Player player, PlayerRealm pr, Realm currentRealm,
                                                            Realm nextRealm, double tuVi) {
        UUID uuid = player.getUniqueId();

        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            if (nextSub != null) {
                long tuViRequired = currentRealm.getTuViForSubRealm(nextSub);
                long thucLuc = realmManager.getThucLuc(uuid);
                String thucLucDisplay = realmManager.getThucLucDisplay(uuid);
                long thucLucRequired = currentRealm.getThucLucForSubRealm(nextSub);
                double money = realmManager.getMoney(uuid);
                double moneyRequired = currentRealm.getMoneyForSubRealm(nextSub);
                int bolts = realmManager.getSubRealmBolts(pr.getSubRealm());
                double damage = realmManager.getSubRealmDmg(pr.getSubRealm());
                boolean tuViOk = tuVi >= tuViRequired;
                boolean thucLucOk = thucLuc >= thucLucRequired;
                boolean moneyOk = money >= moneyRequired;
                boolean ready = realmManager.checkSubRealmBreakthroughConditions(uuid, nextSub).isEmpty();

                Map<String, String> placeholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
                placeholders.put("{breakthrough_type}", "Tầng nhỏ");
                placeholders.put("{breakthrough_target}", pr.getSubRealm().getDisplayName() + " §7→ §a" + nextSub.getDisplayName());
                placeholders.put("{next_sub_realm}", nextSub.getDisplayName());
                placeholders.put("{next_target}", nextSub.getDisplayName());
                placeholders.put("{tuvi_required}", RealmManager.formatNumber(tuViRequired));
                placeholders.put("{thuc_luc}", thucLucDisplay);
                placeholders.put("{thuc_luc_required}", RealmManager.formatNumber(thucLucRequired));
                placeholders.put("{money}", RealmManager.formatMoney(money));
                placeholders.put("{money_required}", RealmManager.formatMoney(moneyRequired));
                placeholders.put("{lightning_name}", "Tiểu Lôi Kiếp");
                placeholders.put("{lightning_bolts}", String.valueOf(bolts));
                placeholders.put("{damage_per_bolt}", formatDecimal(damage) + " ❤");
                placeholders.put("{total_damage}", formatDecimal(bolts * damage) + " ❤");
                placeholders.put("{success_rate}", "100%");
                placeholders.put("{dot_pha_dan}", "Không cần");
                placeholders.put("{cooldown}", "Không");
                placeholders.put("{punishment}", "Không tụt cảnh giới");
                putStatusPlaceholders(placeholders, tuViOk, thucLucOk, moneyOk, true, ready);

                List<String> fallbackLore = Collections.emptyList();

                return new BreakthroughButtonData("main-menu.items.breakthrough",
                        ready ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                        "§d§lĐột Phá", fallbackLore, placeholders, ready);
            }
        }

        if (nextRealm != null) {
            List<String> failures = realmManager.checkBreakthroughConditions(uuid);
            boolean ready = failures.isEmpty();
            long thucLuc = realmManager.getThucLuc(uuid);
            String thucLucDisplay = realmManager.getThucLucDisplay(uuid);
            long thucLucRequired = nextRealm.getThucLucRequired();
            double money = realmManager.getMoney(uuid);
            double moneyRequired = nextRealm.getMoneyRequired();
            boolean tuViOk = tuVi >= nextRealm.getTuViRequired();
            boolean thucLucOk = thucLuc >= thucLucRequired;
            boolean moneyOk = money >= moneyRequired;
            boolean cooldownOk = !pr.isOnCooldown();

            Map<String, String> placeholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
            placeholders.put("{breakthrough_type}", "Cảnh giới");
            placeholders.put("{breakthrough_target}", currentRealm.getFormattedName() + " §7→ " + nextRealm.getFormattedName());
            placeholders.put("{next_target}", nextRealm.getFormattedName());
            placeholders.put("{next_realm}", nextRealm.getFormattedName());
            placeholders.put("{tuvi_required}", RealmManager.formatNumber(nextRealm.getTuViRequired()));
            placeholders.put("{thuc_luc}", thucLucDisplay);
            placeholders.put("{thuc_luc_required}", RealmManager.formatNumber(thucLucRequired));
            placeholders.put("{money}", RealmManager.formatMoney(money));
            placeholders.put("{money_required}", RealmManager.formatMoney(moneyRequired));
            placeholders.put("{lightning_name}", "Thiên Lôi Kiếp");
            placeholders.put("{lightning_bolts}", String.valueOf(nextRealm.getLightningBolts()));
            placeholders.put("{damage_per_bolt}", nextRealm.getDamagePerBoltDisplay());
            placeholders.put("{total_damage}", nextRealm.getTotalDamageSuccessDisplay());
            placeholders.put("{success_rate}", formatDecimal(nextRealm.getSuccessRate()) + "%");
            placeholders.put("{dot_pha_dan}", "x" + realmManager.getDotPhaDanRequired(nextRealm.getId()));
            placeholders.put("{cooldown}", cooldownOk ? "Không" : formatTime(pr.getRemainingCooldownSeconds()));
            placeholders.put("{punishment}", "Thất bại có thể tụt cảnh giới");
            putStatusPlaceholders(placeholders, tuViOk, thucLucOk, moneyOk, cooldownOk, ready);

            List<String> fallbackLore = Collections.emptyList();

            return new BreakthroughButtonData("main-menu.items.breakthrough",
                    ready ? Material.END_CRYSTAL : Material.BARRIER,
                    "§d§lĐột Phá", fallbackLore, placeholders, ready);
        }

        Map<String, String> placeholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
        placeholders.put("{breakthrough_type}", "Cực đỉnh");
        placeholders.put("{breakthrough_target}", "Đã đạt tối đa");
        placeholders.put("{next_target}", "Không có");
        putStatusPlaceholders(placeholders, true, true, true, true, false);
        List<String> fallbackLore = Collections.emptyList();
        return new BreakthroughButtonData("main-menu.items.max-realm", Material.DRAGON_EGG,
                "§4§l✦ Cực Đỉnh ✦", fallbackLore, placeholders, false);
    }

    private void putStatusPlaceholders(Map<String, String> placeholders, boolean tuViOk, boolean thucLucOk,
                                       boolean moneyOk, boolean cooldownOk, boolean ready) {
        placeholders.put("{status_tuvi}", status(tuViOk));
        placeholders.put("{status_thuc_luc}", status(thucLucOk));
        placeholders.put("{status_money}", status(moneyOk));
        placeholders.put("{status_cooldown}", status(cooldownOk));
        placeholders.put("{status_ready}", status(ready));
        placeholders.put("{ready_text}", ready ? "Có thể đột phá" : "Chưa đủ điều kiện");
        placeholders.put("{click_text}", ready ? "Click để mở xác nhận." : "Cần hoàn tất điều kiện phía trên.");
    }

    private String status(boolean ok) {
        return ok ? "§a✔" : "§c✘";
    }

    private String formatDecimal(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainSeconds = seconds % 60;
        if (minutes <= 0) {
            return remainSeconds + " giây";
        }
        return minutes + " phút " + remainSeconds + " giây";
    }

    private void clearSlot(Inventory gui, int slot, int size) {
        if (slot >= 0 && slot < size) {
            gui.setItem(slot, null);
        }
    }

    // ==========================================
    // CONFIRMATION GUI
    // ==========================================

    private void openConfirmMenu(Player player, boolean isMajor) {
        UUID uuid = player.getUniqueId();
        int size = guiConfig.getInt("confirm-menu.size", 27);
        Inventory gui = Bukkit.createInventory(null, size, confirmGuiTitle);

        // Fill background
        Material background = getMaterial("confirm-menu.background.material", Material.BLACK_STAINED_GLASS_PANE);
        ItemStack bgPane = createItem(background, "§0", Collections.emptyList());
        for (int i = 0; i < size; i++) gui.setItem(i, bgPane);

        // Center info
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);

        double tuVi = TuTien.getApi().getTuVi(uuid);
        Realm nextRealm = realmManager.getNextRealm(uuid);
        Map<String, String> confirmPlaceholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
        confirmPlaceholders.putAll(createBreakthroughButton(player, pr, currentRealm, nextRealm, tuVi).placeholders);
        if (!isMajor && pr.getSubRealm().next() != null) {
            confirmPlaceholders.put("{next_sub_realm}", pr.getSubRealm().next().getDisplayName());
        }
        gui.setItem(getSlot("confirm-menu.items.info", 13), createConfiguredItem(player,
                "confirm-menu.items.info", Material.LIGHTNING_ROD, "§e§l⚡ Thông Tin Đột Phá", Collections.emptyList(),
                confirmPlaceholders));

        // Confirm button (slot 11)
        gui.setItem(getSlot("confirm-menu.items.confirm", 11), createConfiguredItem(player,
                "confirm-menu.items.confirm", Material.LIME_CONCRETE, "§a§l✔ XÁC NHẬN", Collections.emptyList(),
                confirmPlaceholders));

        // Cancel button (slot 15)
        gui.setItem(getSlot("confirm-menu.items.cancel", 15), createConfiguredItem(player,
                "confirm-menu.items.cancel", Material.RED_CONCRETE, "§c§l✗ HỦY BỎ", Collections.emptyList(),
                confirmPlaceholders));

        openGuis.remove(uuid);
        confirmGuis.add(uuid);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 0.5f);
    }

    // ==========================================
    // CLICK EVENTS
    // ==========================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        String title = event.getView().getTitle();

        // Main breakthrough menu
        if (title.equals(guiTitle) && openGuis.contains(uuid)) {
            event.setCancelled(true);

            int slot = event.getRawSlot();

            // Unified breakthrough button: sub-realm first, major realm at Viên Mãn.
            if (slot == getSlot("main-menu.items.breakthrough",
                    getSlot("main-menu.items.major-breakthrough", 24))) {
                handleBreakthroughClick(player);
                return;
            }

            // Slot 22: Open Realm List GUI
            if (slot == getSlot("main-menu.items.realm-list", 22)) {
                openGuis.remove(uuid);
                realmListGUI.open(player);
            }

            // Slot 49: Close
            if (slot == getSlot("main-menu.items.close-button", 49)) {
                player.closeInventory();
            }
        }

        // Confirmation menu
        if (title.equals(confirmGuiTitle) && confirmGuis.contains(uuid)) {
            event.setCancelled(true);

            int slot = event.getRawSlot();

            // Slot 11: Confirm
            if (slot == getSlot("confirm-menu.items.confirm", 11)) {
                player.closeInventory();
                confirmGuis.remove(uuid);

                // Determine if major or sub-realm
                PlayerRealm pr = realmManager.getPlayerRealm(uuid);
                if (pr.getSubRealm() == SubRealm.VIEN_MAN) {
                    // Major breakthrough
                    breakthroughManager.startMajorBreakthrough(player);
                } else {
                    // Sub-realm breakthrough
                    breakthroughManager.startSubRealmBreakthrough(player);
                }
            }

            // Slot 15: Cancel
            if (slot == getSlot("confirm-menu.items.cancel", 15)) {
                player.closeInventory();
                player.sendMessage("§7Đã hủy đột phá.");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private void handleBreakthroughClick(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);

        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            if (nextSub == null) {
                player.sendMessage("§cKhông tìm thấy tầng nhỏ tiếp theo.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                return;
            }

            List<String> failures = realmManager.checkSubRealmBreakthroughConditions(uuid, nextSub);
            if (failures.isEmpty()) {
                openConfirmMenu(player, false);
            } else {
                sendConditionFailures(player, failures);
            }
            return;
        }

        Realm nextRealm = realmManager.getNextRealm(uuid);
        if (nextRealm == null) {
            player.sendMessage("§aBạn đã đạt cảnh giới tối đa.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }

        List<String> failures = realmManager.checkBreakthroughConditions(uuid);
        if (failures.isEmpty()) {
            openConfirmMenu(player, true);
        } else {
            sendConditionFailures(player, failures);
        }
    }

    private void sendConditionFailures(Player player, List<String> failures) {
        player.sendMessage("§c§l⚠ Chưa đủ điều kiện:");
        for (String msg : failures) {
            player.sendMessage("  " + msg);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            UUID uuid = event.getPlayer().getUniqueId();
            String title = event.getView().getTitle();
            // Only remove from the set matching the closed GUI
            if (title.equals(guiTitle)) {
                openGuis.remove(uuid);
            } else if (title.equals(confirmGuiTitle)) {
                confirmGuis.remove(uuid);
            }
        }
    }

    // ==========================================
    // UTILITY
    // ==========================================

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, 
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createConfiguredItem(Player player, String path, Material defaultMaterial, String defaultName,
                                           List<String> defaultLore, Map<String, String> placeholders) {
        return createConfiguredItem(player, path, defaultMaterial, defaultName, defaultLore, placeholders, true);
    }

    private ItemStack createConfiguredItem(Player player, String path, Material defaultMaterial, String defaultName,
                                           List<String> defaultLore, Map<String, String> placeholders, boolean ready) {
        ConfigurationSection section = guiConfig.getConfigurationSection(path);
        if (section == null) {
            return createItem(defaultMaterial, defaultName, defaultLore);
        }

        Material material = getConfiguredMaterial(section, defaultMaterial, ready);
        String name = replacePlaceholders(player, section.getString("name", defaultName), placeholders);
        List<String> loreTemplate = section.getStringList("lore");
        List<String> lore = loreTemplate.isEmpty() ? defaultLore : loreTemplate;
        List<String> parsedLore = new ArrayList<>();
        for (String line : lore) {
            parsedLore.add(replacePlaceholders(player, line, placeholders));
        }
        return createItem(material, name, parsedLore);
    }

    private Material getConfiguredMaterial(ConfigurationSection section, Material fallback, boolean ready) {
        String key = ready ? "material-ready" : "material-locked";
        String materialName = section.getString(key, section.getString("material"));
        if (materialName == null) {
            return fallback;
        }
        try {
            return Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void fillConfiguredSlots(Inventory gui, String path, int size) {
        ConfigurationSection section = guiConfig.getConfigurationSection(path);
        if (section == null) {
            ItemStack borderPane = createItem(Material.PURPLE_STAINED_GLASS_PANE, "§5", Collections.emptyList());
            for (int i = 0; i < 9 && i < size; i++) gui.setItem(i, borderPane);
            for (int i = Math.max(0, size - 9); i < size; i++) gui.setItem(i, borderPane);
            for (int i = 9; i < size - 9; i += 9) gui.setItem(i, borderPane);
            for (int i = 17; i < size; i += 9) gui.setItem(i, borderPane);
            return;
        }
        ItemStack item = createConfiguredItem(null, path, Material.PURPLE_STAINED_GLASS_PANE, "§5", Collections.emptyList(), Collections.emptyMap());
        for (int slot : parseSlots(section.getString("slots", ""), size)) {
            gui.setItem(slot, item);
        }
    }

    private List<Integer> parseSlots(String raw, int size) {
        List<Integer> slots = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                if (trimmed.contains("-")) {
                    String[] bounds = trimmed.split("-", 2);
                    int start = Integer.parseInt(bounds[0].trim());
                    int end = Integer.parseInt(bounds[1].trim());
                    for (int slot = start; slot <= end; slot++) {
                        if (slot >= 0 && slot < size) slots.add(slot);
                    }
                } else {
                    int slot = Integer.parseInt(trimmed);
                    if (slot >= 0 && slot < size) slots.add(slot);
                }
            } catch (NumberFormatException ignored) {}
        }
        return slots;
    }

    private Map<String, String> createBasePlaceholders(Player player, PlayerRealm pr, Realm currentRealm, Realm nextRealm, double tuVi) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", player.getName());
        placeholders.put("{realm}", currentRealm.getFormattedName());
        placeholders.put("{realm_name}", currentRealm.getName());
        placeholders.put("{realm_display}", currentRealm.getDisplayNameTranslated());
        placeholders.put("{realm_tier}", currentRealm.getTier().getDisplayName());
        placeholders.put("{sub_realm}", pr.getSubRealm().getDisplayName());
        placeholders.put("{next_realm}", nextRealm != null ? nextRealm.getFormattedName() : "Không có");
        placeholders.put("{next_sub_realm}", pr.getSubRealm().next() != null ? pr.getSubRealm().next().getDisplayName() : "Không có");
        placeholders.put("{tuvi}", RealmManager.formatNumber((long) tuVi));
        placeholders.put("{cooldown}", String.valueOf(pr.getRemainingCooldownSeconds()));
        if (pr.getSubRealm() != SubRealm.VIEN_MAN && pr.getSubRealm().next() != null) {
            SubRealm nextSub = pr.getSubRealm().next();
            long nextTuVi = currentRealm.getTuViForSubRealm(nextSub);
            boolean ready = realmManager.checkSubRealmBreakthroughConditions(player.getUniqueId(), nextSub).isEmpty();
            placeholders.put("{next_tuvi_required}", RealmManager.formatNumber(nextTuVi));
            placeholders.put("{breakthrough_target}", pr.getSubRealm().getDisplayName() + " §7→ §a" + nextSub.getDisplayName());
            placeholders.put("{breakthrough_type}", "Tầng nhỏ");
            placeholders.put("{status_ready}", status(ready));
            placeholders.put("{ready_text}", ready ? "Có thể đột phá tầng nhỏ" : "Chưa đủ điều kiện");
        } else if (nextRealm != null) {
            boolean ready = realmManager.checkBreakthroughConditions(player.getUniqueId()).isEmpty();
            placeholders.put("{next_tuvi_required}", RealmManager.formatNumber(nextRealm.getTuViRequired()));
            placeholders.put("{breakthrough_target}", currentRealm.getFormattedName() + " §7→ " + nextRealm.getFormattedName());
            placeholders.put("{breakthrough_type}", "Cảnh giới");
            placeholders.put("{status_ready}", status(ready));
            placeholders.put("{ready_text}", ready ? "Có thể đột phá cảnh giới" : "Chưa đủ điều kiện");
        } else {
            placeholders.put("{next_tuvi_required}", "Tối đa");
            placeholders.put("{breakthrough_target}", "Đã đạt cực đỉnh");
            placeholders.put("{breakthrough_type}", "Cực đỉnh");
            placeholders.put("{status_ready}", status(false));
            placeholders.put("{ready_text}", "Đã đạt cảnh giới tối đa");
        }
        return placeholders;
    }

    private String replacePlaceholders(Player player, String text, Map<String, String> placeholders) {
        if (text == null) return "";
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        if (player != null) {
            text = text.replace("%player%", player.getName()).replace("%player_name%", player.getName());
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
        }
        return color(text);
    }

    private int getSlot(String path, int fallback) {
        return guiConfig.getInt(path + ".slot", fallback);
    }

    private Material getMaterial(String path, Material fallback) {
        String value = guiConfig.getString(path);
        if (value == null) return fallback;
        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

}
