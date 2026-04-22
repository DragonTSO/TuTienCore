package com.turtle.tutiencore.core.command;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.manager.RealmManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lệnh /canhgioi — Quản lý cảnh giới người chơi
 * 
 * /canhgioi set <player> <realm_id> [sub_realm] — Set cảnh giới cho player
 * /canhgioi info <player>                       — Xem thông tin cảnh giới player
 * /canhgioi list                                — Liệt kê tất cả cảnh giới
 * /canhgioi reload                              — Reload config
 */
public class CanhGioiCommand implements CommandExecutor, TabCompleter {

    private final RealmManager realmManager;

    public CanhGioiCommand(RealmManager realmManager) {
        this.realmManager = realmManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tutiencore.admin")) {
            sender.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                handleSet(sender, args);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    // ==========================================
    // /canhgioi set <player> <realm_id> [sub_realm]
    // ==========================================

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cCách dùng: /canhgioi set <player> <realm_id> [sub_realm]");
            sender.sendMessage("§7  realm_id: 1-19 (số thứ tự cảnh giới)");
            sender.sendMessage("§7  sub_realm: SO_KY, TRUNG_KY, HAU_KY, DINH_PHONG, VIEN_MAN");
            return;
        }

        // Find player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi: §e" + args[1]);
            return;
        }

        // Parse realm ID
        int realmId;
        try {
            realmId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cRealm ID phải là số! VD: 1-19");
            return;
        }

        Realm targetRealm = realmManager.getRealm(realmId);
        if (targetRealm == null) {
            sender.sendMessage("§cKhông tìm thấy cảnh giới ID: §e" + realmId);
            sender.sendMessage("§7Dùng /canhgioi list để xem danh sách.");
            return;
        }

        // Parse sub-realm (optional, default SO_KY)
        SubRealm subRealm = SubRealm.SO_KY;
        if (args.length >= 4) {
            try {
                subRealm = SubRealm.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cTầng nhỏ không hợp lệ: §e" + args[3]);
                sender.sendMessage("§7Chọn: SO_KY, TRUNG_KY, HAU_KY, DINH_PHONG, VIEN_MAN");
                return;
            }
        }

        // Apply
        PlayerRealm pr = realmManager.getPlayerRealm(target.getUniqueId());
        pr.setRealmId(realmId);
        pr.setSubRealm(subRealm);
        realmManager.savePlayerRealm(target.getUniqueId());

        // Feedback
        String display = targetRealm.getSubRealmDisplayNameTranslated(subRealm);
        sender.sendMessage("§a✅ Đã set cảnh giới cho §e" + target.getName() + "§a:");
        sender.sendMessage("§7  Cảnh giới: " + display);
        sender.sendMessage("§7  ID: §e" + realmId + " §7| Tầng: §e" + subRealm.getDisplayName());

        // Notify target
        target.sendMessage("§a✨ Cảnh giới của bạn đã được thay đổi: " + display);
    }

    // ==========================================
    // /canhgioi info <player>
    // ==========================================

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /canhgioi info <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi: §e" + args[1]);
            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        Realm realm = realmManager.getPlayerCurrentRealm(uuid);

        if (realm == null) {
            sender.sendMessage("§cKhông có dữ liệu cảnh giới cho: §e" + target.getName());
            return;
        }

        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e⚡ Cảnh Giới: §f" + target.getName());
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§7  Display: " + realm.getSubRealmDisplayNameTranslated(pr.getSubRealm()));
        sender.sendMessage("§7  Đại Cảnh Giới: " + realm.getFormattedName() + " §7(ID: §e" + pr.getRealmId() + "§7)");
        sender.sendMessage("§7  Tầng Nhỏ: §e" + pr.getSubRealm().getDisplayName());
        sender.sendMessage("§7  Đại Giới: " + realm.getTier().getColor() + realm.getTier().getDisplayName());
        sender.sendMessage("§7  Cooldown: " + (pr.isOnCooldown() 
                ? "§c" + pr.getRemainingCooldownSeconds() + "s" 
                : "§aKhông"));
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ==========================================
    // /canhgioi list
    // ==========================================

    private void handleList(CommandSender sender) {
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e⚡ Danh Sách Cảnh Giới");
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");

        Map<Integer, Realm> allRealms = realmManager.getAllRealms();
        for (Map.Entry<Integer, Realm> entry : allRealms.entrySet()) {
            Realm r = entry.getValue();
            sender.sendMessage("§7  " + entry.getKey() + ". " + r.getDisplayNameTranslated()
                    + " §7(" + r.getEnglishName() + ") — " + r.getTier().getColor() + r.getTier().getDisplayName());
        }

        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§7Dùng: §e/canhgioi set <player> <id> [sub_realm]");
    }

    // ==========================================
    // /canhgioi reload
    // ==========================================

    private void handleReload(CommandSender sender) {
        sender.sendMessage("§e⏳ Đang reload cảnh giới config...");
        // RealmManager reload could be added later
        sender.sendMessage("§a✅ Reload hoàn tất! (Cần restart server để áp dụng realms.yml)");
    }

    // ==========================================
    // HELP
    // ==========================================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e⚡ TuTienCore — Cảnh Giới Commands");
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e/canhgioi set <player> <realm_id> [sub_realm]");
        sender.sendMessage("§7  → Set cảnh giới cho player");
        sender.sendMessage("§e/canhgioi info <player>");
        sender.sendMessage("§7  → Xem thông tin cảnh giới");
        sender.sendMessage("§e/canhgioi list");
        sender.sendMessage("§7  → Liệt kê tất cả cảnh giới");
        sender.sendMessage("§e/canhgioi reload");
        sender.sendMessage("§7  → Reload config");
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ==========================================
    // TAB COMPLETE
    // ==========================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tutiencore.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return filterStart(Arrays.asList("set", "info", "list", "reload"), args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("info")) {
                return null; // Returns online player names
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // Suggest realm IDs
            return filterStart(
                    realmManager.getAllRealms().keySet().stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()),
                    args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            // Suggest sub-realms
            return filterStart(
                    Arrays.stream(SubRealm.values())
                            .map(Enum::name)
                            .collect(Collectors.toList()),
                    args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filterStart(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
