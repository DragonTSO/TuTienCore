package com.turtle.tutiencore.core.command;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.manager.BreakthroughManager;
import com.turtle.tutiencore.core.manager.RealmManager;
import com.turtle.tutiencore.core.gui.RealmListGUI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

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

    private static final String GUI_TITLE = "§5§l⚡ Đột Phá Cảnh Giới ⚡";
    private static final String CONFIRM_GUI_TITLE = "§c§l⚡ XÁC NHẬN ĐỘT PHÁ ⚡";

    // Track which players have the GUI open
    private final Set<UUID> openGuis = new HashSet<>();
    private final Set<UUID> confirmGuis = new HashSet<>();

    public DotPhaCommand(JavaPlugin plugin, RealmManager realmManager, BreakthroughManager breakthroughManager, RealmListGUI realmListGUI) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        this.breakthroughManager = breakthroughManager;
        this.realmListGUI = realmListGUI;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        Realm nextRealm = realmManager.getNextRealm(uuid);
        double tuVi = TuTien.getApi().getTuVi(uuid);

        // ==========================================
        // Row 1: Decorative border (glass panes)
        // ==========================================
        ItemStack borderPane = createItem(Material.PURPLE_STAINED_GLASS_PANE, "§5", Collections.emptyList());
        for (int i = 0; i < 9; i++) gui.setItem(i, borderPane);
        for (int i = 45; i < 54; i++) gui.setItem(i, borderPane);
        for (int i = 9; i < 45; i += 9) gui.setItem(i, borderPane);
        for (int i = 17; i < 54; i += 9) gui.setItem(i, borderPane);

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

        gui.setItem(13, createItem(Material.NETHER_STAR, 
                currentRealm.getFormattedName() + " §7— §e" + pr.getSubRealm().getDisplayName(),
                currentRealmLore));

        // ==========================================
        // Slot 20: Sub-Realm Breakthrough (if not Viên Mãn)
        // ==========================================
        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            SubRealm nextSub = pr.getSubRealm().next();
            if (nextSub != null) {
                long subRequired = currentRealm.getTuViForSubRealm(nextSub);
                boolean canSubBreak = tuVi >= subRequired;

                List<String> subLore = new ArrayList<>();
                subLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
                subLore.add("§7Đột phá: §e" + pr.getSubRealm().getDisplayName() + " §7→ §a" + nextSub.getDisplayName());
                subLore.add("");
                subLore.add(canSubBreak ? "§a✅ Tu Vi đủ!" : "§c❌ Tu Vi chưa đủ!");
                subLore.add("§7Cần: §e" + RealmManager.formatNumber(subRequired));
                subLore.add("§7Hiện tại: §b" + RealmManager.formatNumber((long) tuVi));
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

                gui.setItem(20, createItem(
                        canSubBreak ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                        "§e⚡ Đột Phá Tầng Nhỏ",
                        subLore));
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

        gui.setItem(22, createItem(Material.BOOK, "§b§l📖 Danh Sách Cảnh Giới", infoLore));

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
            majorLore.add("§7  Tỉ lệ: §e" + String.format("%.0f%%", nextRealm.getSuccessRate()));
            majorLore.add("§7  DMG fail roll: §4" + String.format("%.0f", nextRealm.getTotalDamageFail()) + " ❤ §c(x2!)");
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

            gui.setItem(24, createItem(
                    canBreak ? Material.END_CRYSTAL : Material.BARRIER,
                    "§c§l⚡ Đột Phá Đại Cảnh Giới",
                    majorLore));
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

            gui.setItem(24, createItem(Material.BARRIER, "§8⚡ Đột Phá Đại Cảnh Giới §c(Chưa mở)", waitLore));
        } else {
            // Max realm
            List<String> maxLore = new ArrayList<>();
            maxLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            maxLore.add("§a§lBạn đã đạt cực đỉnh!");
            maxLore.add("§6Hồng Mông — Đạo tối thượng");
            maxLore.add("§8━━━━━━━━━━━━━━━━━━━━━");

            gui.setItem(24, createItem(Material.DRAGON_EGG, "§4§l✦ Cực Đỉnh ✦", maxLore));
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

        gui.setItem(31, createItem(Material.WRITABLE_BOOK, "§e§l💡 Chiến Thuật", tipsLore));

        // ==========================================
        // Slot 49: Close button
        // ==========================================
        gui.setItem(49, createItem(Material.BARRIER, "§c§lĐóng Menu", 
                Collections.singletonList("§7Click để đóng")));

        openGuis.add(uuid);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    // ==========================================
    // CONFIRMATION GUI
    // ==========================================

    private void openConfirmMenu(Player player, boolean isMajor) {
        UUID uuid = player.getUniqueId();
        Inventory gui = Bukkit.createInventory(null, 27, CONFIRM_GUI_TITLE);

        // Fill background
        ItemStack bgPane = createItem(Material.BLACK_STAINED_GLASS_PANE, "§0", Collections.emptyList());
        for (int i = 0; i < 27; i++) gui.setItem(i, bgPane);

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
            confirmLore.add("§c  Nếu chết → Thất bại + Cooldown");
            confirmLore.add("§c  Mất 50% Đột Phá Đan đã dùng");
            confirmLore.add("§8━━━━━━━━━━━━━━━━━━━━━");
            confirmLore.add("§e  Tỉ lệ thành công: §a" + String.format("%.0f%%", nextRealm.getSuccessRate()));
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

        gui.setItem(13, createItem(Material.LIGHTNING_ROD, "§e§l⚡ Thông Tin Đột Phá", confirmLore));

        // Confirm button (slot 11)
        List<String> yesLore = new ArrayList<>();
        yesLore.add("§a§lBắt đầu Thiên Lôi Kiếp!");
        yesLore.add("§7Click để xác nhận");
        gui.setItem(11, createItem(Material.LIME_CONCRETE, "§a§l✔ XÁC NHẬN", yesLore));

        // Cancel button (slot 15)
        List<String> noLore = new ArrayList<>();
        noLore.add("§cHủy bỏ đột phá");
        noLore.add("§7Click để quay lại");
        gui.setItem(15, createItem(Material.RED_CONCRETE, "§c§l✗ HỦY BỎ", noLore));

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
        if (title.equals(GUI_TITLE) && openGuis.contains(uuid)) {
            event.setCancelled(true);

            int slot = event.getRawSlot();

            // Slot 20: Sub-realm breakthrough
            if (slot == 20) {
                PlayerRealm pr = realmManager.getPlayerRealm(uuid);
                if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
                    SubRealm nextSub = pr.getSubRealm().next();
                    if (nextSub != null) {
                        Realm realm = realmManager.getPlayerCurrentRealm(uuid);
                        long required = realm.getTuViForSubRealm(nextSub);
                        double tuVi = TuTien.getApi().getTuVi(uuid);
                        if (tuVi >= required) {
                            openConfirmMenu(player, false);
                        } else {
                            player.sendMessage("§cTu Vi chưa đủ để đột phá tầng nhỏ!");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
                        }
                    }
                }
            }

            // Slot 24: Major realm breakthrough
            if (slot == 24) {
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
            if (slot == 22) {
                openGuis.remove(uuid);
                realmListGUI.open(player);
            }

            // Slot 49: Close
            if (slot == 49) {
                player.closeInventory();
            }
        }

        // Confirmation menu
        if (title.equals(CONFIRM_GUI_TITLE) && confirmGuis.contains(uuid)) {
            event.setCancelled(true);

            int slot = event.getRawSlot();

            // Slot 11: Confirm
            if (slot == 11) {
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
            if (slot == 15) {
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
            if (title.equals(GUI_TITLE)) {
                openGuis.remove(uuid);
            } else if (title.equals(CONFIRM_GUI_TITLE)) {
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
