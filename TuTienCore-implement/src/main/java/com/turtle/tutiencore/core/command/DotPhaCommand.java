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
        List<String> currentRealmLore = new ArrayList<>();
        currentRealmLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
        currentRealmLore.add("§7Đại Giới: " + currentRealm.getTier().getColor() + currentRealm.getTier().getDisplayName());
        currentRealmLore.add("§7Cảnh Giới: " + currentRealm.getFormattedName());
        currentRealmLore.add("§7Tầng Nhỏ: §e" + pr.getSubRealm().getDisplayName());
        currentRealmLore.add("");
        currentRealmLore.add("§7Tu Vi: §b" + RealmManager.formatNumber((long) tuVi));
        currentRealmLore.add("§8━━━━━━━━━━━━━━━━━━━━━");

        // Progress bar for Tu Vi within current realm
        if (currentRealm.getVienManTuVi() > 0) {
            long startTuVi = currentRealm.getSoKyTuVi();
            long endTuVi = currentRealm.getVienManTuVi();
            double progress = Math.min(1.0, (tuVi - startTuVi) / (double)(endTuVi - startTuVi));
            currentRealmLore.add(createProgressBar(progress) + " §7" + String.format("%.1f%%", progress * 100));
        }

        Map<String, String> currentPlaceholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
        gui.setItem(getSlot("main-menu.items.current-realm", 13), createConfiguredItem(player,
                "main-menu.items.current-realm", Material.NETHER_STAR,
                currentRealm.getFormattedName() + " §7— §e" + pr.getSubRealm().getDisplayName(),
                currentRealmLore, currentPlaceholders));

        // ==========================================
        // Slot 20: Sub-Realm Breakthrough (if not Viên Mãn)
        // ==========================================
        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            if (nextSub != null) {
                long subRequired = currentRealm.getTuViForSubRealm(nextSub);
                List<String> conditions = realmManager.checkSubRealmBreakthroughConditions(uuid, nextSub);
                boolean canSubBreak = conditions.isEmpty();

                List<String> subLore = new ArrayList<>();
                subLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
                subLore.add("§7Đột phá: §e" + pr.getSubRealm().getDisplayName() + " §7→ §a" + nextSub.getDisplayName());
                subLore.add("");
                subLore.add(canSubBreak ? "§a✅ Tu Vi đủ!" : "§c❌ Tu Vi chưa đủ!");
                subLore.add("§7Cần: §e" + RealmManager.formatNumber(subRequired));
                subLore.add("§7Hiện tại: §b" + RealmManager.formatNumber((long) tuVi));

                long subThucLucRequired = currentRealm.getThucLucForSubRealm(nextSub);
                long thucLuc = realmManager.getThucLuc(uuid);
                String thucLucDisplay = realmManager.getThucLucDisplay(uuid);
                boolean thucLucOk = thucLuc >= subThucLucRequired;
                subLore.add((thucLucOk ? "§a✅ " : "§c❌ ") + "Thực Lực: §b" + RealmManager.formatNumber(thucLuc)
                        + " §7/ §e" + RealmManager.formatNumber(subThucLucRequired));

                double subMoneyRequired = currentRealm.getMoneyForSubRealm(nextSub);
                double money = realmManager.getMoney(uuid);
                boolean moneyOk = money >= subMoneyRequired;
                subLore.add((moneyOk ? "§a✅ " : "§c❌ ") + "Tiền: §b" + RealmManager.formatMoney(money)
                        + " §7/ §e" + RealmManager.formatMoney(subMoneyRequired));
                subLore.add("");

                int subBolts = realmManager.getSubRealmBolts(pr.getSubRealm());
                double subDmg = realmManager.getSubRealmDmg(pr.getSubRealm());
                subLore.add("§e⚡ Tiểu Lôi Kiếp:");
                subLore.add("§7  Số tia sét: §c" + subBolts);
                subLore.add("§7  DMG/tia: §c" + subDmg + " ❤");
                subLore.add("§7  Tỉ lệ: §a100%");
                subLore.add("§8━━━━━━━━━━━━━━━━━━━━━");

                if (canSubBreak) {
                    subLore.add("§a§l▶ Click để đột phá tầng nhỏ!");
                } else {
                    subLore.add("§c§l✗ Chưa đủ điều kiện");
                }

                Map<String, String> subPlaceholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
                subPlaceholders.put("{next_sub_realm}", nextSub.getDisplayName());
                subPlaceholders.put("{tuvi_required}", RealmManager.formatNumber(subRequired));
                subPlaceholders.put("{thuc_luc}", thucLucDisplay);
                subPlaceholders.put("{thuc_luc_required}", RealmManager.formatNumber(subThucLucRequired));
                subPlaceholders.put("{money}", RealmManager.formatMoney(money));
                subPlaceholders.put("{money_required}", RealmManager.formatMoney(subMoneyRequired));
                subPlaceholders.put("{lightning_bolts}", String.valueOf(subBolts));
                subPlaceholders.put("{damage_per_bolt}", String.valueOf(subDmg));
                subPlaceholders.put("{total_damage}", String.format("%.0f", subBolts * subDmg));
                subPlaceholders.put("{status_tuvi}", canSubBreak ? "§a✅" : "§c❌");
                subPlaceholders.put("{status_thuc_luc}", thucLucOk ? "§a✅" : "§c❌");
                subPlaceholders.put("{status_money}", moneyOk ? "§a✅" : "§c❌");
                subPlaceholders.put("{status_cooldown}", !pr.isOnCooldown() ? "§a✅" : "§c❌");

                gui.setItem(getSlot("main-menu.items.sub-realm-breakthrough", 20), createConfiguredItem(player,
                        "main-menu.items.sub-realm-breakthrough", canSubBreak ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                        "§e⚡ Đột Phá Tầng Nhỏ", subLore, subPlaceholders, canSubBreak));
            }
        }

        // ==========================================
        // Slot 22: Info panel (Realm List)
        // ==========================================
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
        infoLore.add("§7Hệ thống 19 Cảnh Giới:");
        infoLore.add("");

        // Show first few realms with current highlighted
        for (int i = 1; i <= Math.min(19, 19); i++) {
            Realm r = realmManager.getRealm(i);
            if (r == null) continue;
            String marker = (i == pr.getRealmId()) ? "§a§l➤ " : "§7  ";
            String checkMark = (i < pr.getRealmId()) ? "§a✔ " : (i == pr.getRealmId() ? "§e★ " : "§8○ ");
            infoLore.add(marker + checkMark + r.getFormattedName() + " §8(Lv" + i + ")");
        }
        infoLore.add("§8━━━━━━━━━━━━━━━━━━━━━");

        gui.setItem(getSlot("main-menu.items.realm-list", 22), createConfiguredItem(player,
                "main-menu.items.realm-list", Material.BOOK, "§b§l📖 Danh Sách Cảnh Giới", infoLore,
                createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi)));

        // ==========================================
        // Slot 24: Major Realm Breakthrough
        // ==========================================
        if (nextRealm != null && pr.getSubRealm() == SubRealm.VIEN_MAN) {
            List<String> conditions = realmManager.checkBreakthroughConditions(uuid);
            boolean canBreak = conditions.isEmpty();

            List<String> majorLore = new ArrayList<>();
            majorLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            majorLore.add("§7Đột phá → " + nextRealm.getFormattedName());
            majorLore.add("");

            // Show conditions
            majorLore.add("§e§l⚙ Điều Kiện:");
            boolean tuViOk = tuVi >= nextRealm.getTuViRequired();
            majorLore.add((tuViOk ? "§a  ✅ " : "§c  ❌ ") + "Tu Vi: " + RealmManager.formatNumber((long) tuVi) 
                    + " / " + RealmManager.formatNumber(nextRealm.getTuViRequired()));

            long thucLuc = realmManager.getThucLuc(uuid);
            String thucLucDisplay = realmManager.getThucLucDisplay(uuid);
            long thucLucRequired = nextRealm.getThucLucRequired();
            boolean thucLucOk = thucLuc >= thucLucRequired;
            majorLore.add((thucLucOk ? "§a  ✅ " : "§c  ❌ ") + "Thực Lực: " + RealmManager.formatNumber(thucLuc)
                    + " / " + RealmManager.formatNumber(thucLucRequired));

            double money = realmManager.getMoney(uuid);
            double moneyRequired = nextRealm.getMoneyRequired();
            boolean moneyOk = money >= moneyRequired;
            majorLore.add((moneyOk ? "§a  ✅ " : "§c  ❌ ") + "Tiền: " + RealmManager.formatMoney(money)
                    + " / " + RealmManager.formatMoney(moneyRequired));

            boolean subOk = pr.getSubRealm() == SubRealm.VIEN_MAN;
            majorLore.add((subOk ? "§a  ✅ " : "§c  ❌ ") + "Tầng: " + pr.getSubRealm().getDisplayName() + " (cần Viên Mãn)");

            boolean cdOk = !pr.isOnCooldown();
            if (!cdOk) {
                long remaining = pr.getRemainingCooldownSeconds();
                majorLore.add("§c  ❌ Cooldown: còn " + (remaining / 60) + " phút " + (remaining % 60) + "s");
            } else {
                majorLore.add("§a  ✅ Không cooldown");
            }

            majorLore.add("");

            // Thiên Lôi Kiếp info
            majorLore.add("§c§l⚡ Thiên Lôi Kiếp:");
            majorLore.add("§7  Số tia sét: §c" + nextRealm.getLightningBolts());
            majorLore.add("§7  DMG/tia: §c" + nextRealm.getDamagePerBolt() + " ❤");
            majorLore.add("§7  Tổng DMG: §c" + String.format("%.0f", nextRealm.getTotalDamageSuccess()) + " ❤");
            majorLore.add("§e  Sống sót qua sét = §a§lThành Công");
            majorLore.add("§c  Chết bởi sét = §4§lThất Bại");
            majorLore.add("");

            // Đột Phá Đan requirement
            int danRequired = realmManager.getDotPhaDanRequired(nextRealm.getId());
            majorLore.add("§6  Đột Phá Đan: §e" + danRequired + " cái");
            majorLore.add("§8━━━━━━━━━━━━━━━━━━━━━");

            if (canBreak) {
                majorLore.add("§a§l▶ Click để mở xác nhận đột phá!");
            } else {
                majorLore.add("§c§l✗ Chưa đủ điều kiện");
            }

            Map<String, String> majorPlaceholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
            majorPlaceholders.put("{next_realm}", nextRealm.getFormattedName());
            majorPlaceholders.put("{tuvi_required}", RealmManager.formatNumber(nextRealm.getTuViRequired()));
            majorPlaceholders.put("{thuc_luc}", thucLucDisplay);
            majorPlaceholders.put("{thuc_luc_required}", RealmManager.formatNumber(thucLucRequired));
            majorPlaceholders.put("{money}", RealmManager.formatMoney(money));
            majorPlaceholders.put("{money_required}", RealmManager.formatMoney(moneyRequired));
            majorPlaceholders.put("{lightning_bolts}", String.valueOf(nextRealm.getLightningBolts()));
            majorPlaceholders.put("{damage_per_bolt}", String.valueOf(nextRealm.getDamagePerBolt()));
            majorPlaceholders.put("{total_damage}", String.format("%.0f", nextRealm.getTotalDamageSuccess()));
            majorPlaceholders.put("{status_tuvi}", tuViOk ? "§a✅" : "§c❌");
            majorPlaceholders.put("{status_thuc_luc}", thucLucOk ? "§a✅" : "§c❌");
            majorPlaceholders.put("{status_money}", moneyOk ? "§a✅" : "§c❌");
            majorPlaceholders.put("{status_cooldown}", cdOk ? "§a✅" : "§c❌");

            gui.setItem(getSlot("main-menu.items.major-breakthrough", 24), createConfiguredItem(player,
                    "main-menu.items.major-breakthrough", canBreak ? Material.END_CRYSTAL : Material.BARRIER,
                    "§c§l⚡ Đột Phá Đại Cảnh Giới", majorLore, majorPlaceholders, canBreak));
        } else if (nextRealm != null) {
            // Not at Viên Mãn yet
            List<String> waitLore = new ArrayList<>();
            waitLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            waitLore.add("§7Mục tiêu: " + nextRealm.getFormattedName());
            waitLore.add("");
            waitLore.add("§c❌ Cần đạt §eViên Mãn §ctrước!");
            waitLore.add("§7Hiện tại: §e" + pr.getSubRealm().getDisplayName());
            waitLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            waitLore.add("§7Hãy đột phá tầng nhỏ trước.");

            gui.setItem(getSlot("main-menu.items.major-breakthrough", 24), createConfiguredItem(player,
                    "main-menu.items.major-breakthrough", Material.BARRIER,
                    "§8⚡ Đột Phá Đại Cảnh Giới §c(Chưa mở)", waitLore,
                    createMajorPlaceholders(player, pr, currentRealm, nextRealm, tuVi), false));
        } else {
            // Max realm
            List<String> maxLore = new ArrayList<>();
            maxLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            maxLore.add("§a§lBạn đã đạt cực đỉnh!");
            maxLore.add("§6Hồng Mông — Đạo tối thượng");
            maxLore.add("§8━━━━━━━━━━━━━━━━━━━━━");

            gui.setItem(getSlot("main-menu.items.major-breakthrough", 24), createConfiguredItem(player,
                    "main-menu.items.max-realm", Material.DRAGON_EGG, "§4§l✦ Cực Đỉnh ✦", maxLore,
                    createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi)));
        }

        // ==========================================
        // Slot 31: Tips/Strategy
        // ==========================================
        List<String> tipsLore = new ArrayList<>();
        tipsLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
        tipsLore.add("§e💡 Mẹo vượt Thiên Lôi Kiếp:");
        tipsLore.add("");
        tipsLore.add("§7🧪 Uống §aHồi Phục Đan §7trước khi bắt đầu");
        tipsLore.add("§7🛡️ Mặc §bgiáp DEF cao §7để giảm DMG");
        tipsLore.add("§7💚 Mang §aPet Y Tu §7để tự heal");
        tipsLore.add("§7🔮 Dùng §dHộ Thể Phù §7để chặn sét");
        tipsLore.add("§7💎 VIP có §6Thiên Lôi Hộ Phù §7giảm 20%");
        tipsLore.add("");
        tipsLore.add("§c⚠ Đây là thử thách solo!");
        tipsLore.add("§c  Không thể nhờ người khác heal!");
        tipsLore.add("§8━━━━━━━━━━━━━━━━━━━━━");

        gui.setItem(getSlot("main-menu.items.tips", 31), createConfiguredItem(player,
                "main-menu.items.tips", Material.WRITABLE_BOOK, "§e§l💡 Chiến Thuật", tipsLore,
                createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi)));

        // ==========================================
        // Slot 49: Close button
        // ==========================================
        gui.setItem(getSlot("main-menu.items.close-button", 49), createConfiguredItem(player,
                "main-menu.items.close-button", Material.BARRIER, "§c§lĐóng Menu",
                Collections.singletonList("§7Click để đóng"), createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi)));

        openGuis.add(uuid);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);
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

        List<String> confirmLore = new ArrayList<>();
        if (isMajor) {
            Realm nextRealm = realmManager.getNextRealm(uuid);
            confirmLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            confirmLore.add("§7Đột phá: " + currentRealm.getFormattedName() + " §7→ " + nextRealm.getFormattedName());
            confirmLore.add("");
            confirmLore.add("§c⚠ CẢNH BÁO:");
            confirmLore.add("§c  Tia sét sẽ giáng xuống bạn!");
            confirmLore.add("§c  Sống sót = Thành công");
            confirmLore.add("§c  Chết = Thất bại:");
            confirmLore.add("§4    → Tụt 1 bậc cảnh giới");
            confirmLore.add("§4    → Cooldown 30 phút");
            confirmLore.add("§c    → Mất 50% Đột Phá Đan đã dùng");
            confirmLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
        } else {
            SubRealm nextSub = pr.getSubRealm().next();
            confirmLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            confirmLore.add("§7Đột phá tầng nhỏ:");
            confirmLore.add("§e  " + pr.getSubRealm().getDisplayName() + " §7→ §a" + nextSub.getDisplayName());
            confirmLore.add("");
            confirmLore.add("§a  Tỉ lệ: 100% thành công");
            confirmLore.add("§7  Sét nhẹ, an toàn!");
            confirmLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
        }

        double tuVi = TuTien.getApi().getTuVi(uuid);
        Realm nextRealm = realmManager.getNextRealm(uuid);
        Map<String, String> confirmPlaceholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
        if (!isMajor && pr.getSubRealm().next() != null) {
            confirmPlaceholders.put("{next_sub_realm}", pr.getSubRealm().next().getDisplayName());
        }
        gui.setItem(getSlot("confirm-menu.items.info", 13), createConfiguredItem(player,
                "confirm-menu.items.info", Material.LIGHTNING_ROD, "§e§l⚡ Thông Tin Đột Phá", confirmLore,
                confirmPlaceholders));

        // Confirm button (slot 11)
        List<String> yesLore = new ArrayList<>();
        yesLore.add("§a§lBắt đầu Thiên Lôi Kiếp!");
        yesLore.add("§7Click để xác nhận");
        gui.setItem(getSlot("confirm-menu.items.confirm", 11), createConfiguredItem(player,
                "confirm-menu.items.confirm", Material.LIME_CONCRETE, "§a§l✔ XÁC NHẬN", yesLore,
                confirmPlaceholders));

        // Cancel button (slot 15)
        List<String> noLore = new ArrayList<>();
        noLore.add("§cHủy bỏ đột phá");
        noLore.add("§7Click để quay lại");
        gui.setItem(getSlot("confirm-menu.items.cancel", 15), createConfiguredItem(player,
                "confirm-menu.items.cancel", Material.RED_CONCRETE, "§c§l✗ HỦY BỎ", noLore,
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

            // Slot 20: Sub-realm breakthrough
            if (slot == getSlot("main-menu.items.sub-realm-breakthrough", 20)) {
                PlayerRealm pr = realmManager.getPlayerRealm(uuid);
                if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
                    SubRealm nextSub = pr.getSubRealm().next();
                    if (nextSub != null) {
                        List<String> failures = realmManager.checkSubRealmBreakthroughConditions(uuid, nextSub);
                        if (failures.isEmpty()) {
                            openConfirmMenu(player, false);
                        } else {
                            player.sendMessage("§c§l⚠ Chưa đủ điều kiện:");
                            for (String msg : failures) {
                                player.sendMessage("  " + msg);
                            }
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                        }
                    }
                }
            }

            // Slot 24: Major realm breakthrough
            if (slot == getSlot("main-menu.items.major-breakthrough", 24)) {
                PlayerRealm pr = realmManager.getPlayerRealm(uuid);
                Realm nextRealm = realmManager.getNextRealm(uuid);
                if (nextRealm != null && pr.getSubRealm() == SubRealm.VIEN_MAN) {
                    List<String> failures = realmManager.checkBreakthroughConditions(uuid);
                    if (failures.isEmpty()) {
                        openConfirmMenu(player, true);
                    } else {
                        player.sendMessage("§c§l⚠ Chưa đủ điều kiện:");
                        for (String msg : failures) {
                            player.sendMessage("  " + msg);
                        }
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                    }
                }
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
        return placeholders;
    }

    private Map<String, String> createMajorPlaceholders(Player player, PlayerRealm pr, Realm currentRealm, Realm nextRealm, double tuVi) {
        Map<String, String> placeholders = createBasePlaceholders(player, pr, currentRealm, nextRealm, tuVi);
        if (nextRealm == null) {
            return placeholders;
        }

        long thucLuc = realmManager.getThucLuc(player.getUniqueId());
        long thucLucRequired = nextRealm.getThucLucRequired();
        double money = realmManager.getMoney(player.getUniqueId());
        double moneyRequired = nextRealm.getMoneyRequired();

        placeholders.put("{next_realm}", nextRealm.getFormattedName());
        placeholders.put("{tuvi_required}", RealmManager.formatNumber(nextRealm.getTuViRequired()));
        placeholders.put("{thuc_luc}", realmManager.getThucLucDisplay(player.getUniqueId()));
        placeholders.put("{thuc_luc_required}", RealmManager.formatNumber(thucLucRequired));
        placeholders.put("{money}", RealmManager.formatMoney(money));
        placeholders.put("{money_required}", RealmManager.formatMoney(moneyRequired));
        placeholders.put("{lightning_bolts}", String.valueOf(nextRealm.getLightningBolts()));
        placeholders.put("{damage_per_bolt}", String.valueOf(nextRealm.getDamagePerBolt()));
        placeholders.put("{total_damage}", String.format("%.0f", nextRealm.getTotalDamageSuccess()));
        placeholders.put("{status_tuvi}", tuVi >= nextRealm.getTuViRequired() ? "§a✅" : "§c❌");
        placeholders.put("{status_thuc_luc}", thucLuc >= thucLucRequired ? "§a✅" : "§c❌");
        placeholders.put("{status_money}", money >= moneyRequired ? "§a✅" : "§c❌");
        placeholders.put("{status_cooldown}", !pr.isOnCooldown() ? "§a✅" : "§c❌");
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

    /**
     * Create a progress bar: §a█████§7█████ 50%
     */
    private String createProgressBar(double progress) {
        int filled = (int) (progress * 20);
        int empty = 20 - filled;
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) bar.append("█");
        bar.append("§7");
        for (int i = 0; i < empty; i++) bar.append("█");
        return bar.toString();
    }
}
