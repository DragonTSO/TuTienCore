package com.turtle.tutiencore.core.command;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.core.manager.PlayerDataManager;
import com.turtle.tutiencore.core.manager.RealmManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lệnh /tuvi — Quản lý Tu Vi người chơi
 *
 * /tuvi set <player> <amount>  — Set Tu Vi
 * /tuvi add <player> <amount>  — Cộng thêm Tu Vi
 * /tuvi take <player> <amount> — Trừ Tu Vi
 * /tuvi reset <player>         — Reset Tu Vi về 0
 * /tuvi resetall               — Reset Tu Vi tất cả người chơi online
 * /tuvi info <player>          — Xem Tu Vi
 */
public class TuViCommand implements CommandExecutor, TabCompleter {

    private final PlayerDataManager playerDataManager;

    public TuViCommand(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
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
            case "add":
                handleAdd(sender, args);
                break;
            case "take":
                handleTake(sender, args);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "resetall":
                handleResetAll(sender);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cCách dùng: /tuvi set <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi: §e" + args[1]);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cSố không hợp lệ: §e" + args[2]);
            return;
        }

        TuTien.getApi().setTuVi(target.getUniqueId(), amount);
        playerDataManager.savePlayer(target.getUniqueId());

        sender.sendMessage("§a✅ Đã set Tu Vi cho §e" + target.getName() + "§a: §b" + RealmManager.formatNumber((long) amount));
        target.sendMessage("§b✨ Tu Vi của bạn đã được thay đổi: §e" + RealmManager.formatNumber((long) amount));
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cCách dùng: /tuvi add <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi: §e" + args[1]);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cSố không hợp lệ: §e" + args[2]);
            return;
        }

        TuTien.getApi().addTuVi(target.getUniqueId(), amount);
        double newAmount = TuTien.getApi().getTuVi(target.getUniqueId());
        playerDataManager.savePlayer(target.getUniqueId());

        sender.sendMessage("§a✅ Đã cộng §e" + RealmManager.formatNumber((long) amount) 
                + " §aTu Vi cho §e" + target.getName() + "§a. Tổng: §b" + RealmManager.formatNumber((long) newAmount));
        target.sendMessage("§b✨ Bạn nhận được §e" + RealmManager.formatNumber((long) amount) + " §bTu Vi!");
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cCách dùng: /tuvi take <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi: §e" + args[1]);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cSố không hợp lệ: §e" + args[2]);
            return;
        }

        TuTien.getApi().takeTuVi(target.getUniqueId(), amount);
        double newAmount = TuTien.getApi().getTuVi(target.getUniqueId());
        playerDataManager.savePlayer(target.getUniqueId());

        sender.sendMessage("§a✅ Đã trừ §e" + RealmManager.formatNumber((long) amount)
                + " §aTu Vi của §e" + target.getName() + "§a. Còn: §b" + RealmManager.formatNumber((long) newAmount));
        target.sendMessage("§c⚠ Bạn bị trừ §e" + RealmManager.formatNumber((long) amount) + " §cTu Vi!");
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /tuvi reset <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi: §e" + args[1]);
            return;
        }

        TuTien.getApi().setTuVi(target.getUniqueId(), 0);
        playerDataManager.savePlayer(target.getUniqueId());

        sender.sendMessage("§a✅ Đã reset Tu Vi của §e" + target.getName() + " §avề §c0");
        target.sendMessage("§c⚠ Tu Vi của bạn đã bị reset về 0!");
    }

    private void handleResetAll(CommandSender sender) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            TuTien.getApi().setTuVi(player.getUniqueId(), 0);
            playerDataManager.savePlayer(player.getUniqueId());
            player.sendMessage("§c⚠ Tu Vi của bạn đã bị reset về 0!");
            count++;
        }

        sender.sendMessage("§a✅ Đã reset Tu Vi của §e" + count + " §angười chơi online về §c0");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /tuvi info <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cKhông tìm thấy người chơi: §e" + args[1]);
            return;
        }

        double tuvi = TuTien.getApi().getTuVi(target.getUniqueId());
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§b✨ Tu Vi: §f" + target.getName());
        sender.sendMessage("§7  Nguyên: §b" + String.format("%,.0f", tuvi));
        sender.sendMessage("§7  Gọn: §b" + RealmManager.formatNumber((long) tuvi));
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§b✨ TuTienCore — Tu Vi Commands");
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e/tuvi set <player> <amount> §7— Set Tu Vi");
        sender.sendMessage("§e/tuvi add <player> <amount> §7— Cộng Tu Vi");
        sender.sendMessage("§e/tuvi take <player> <amount> §7— Trừ Tu Vi");
        sender.sendMessage("§e/tuvi reset <player> §7— Reset về 0");
        sender.sendMessage("§e/tuvi resetall §7— Reset tất cả online");
        sender.sendMessage("§e/tuvi info <player> §7— Xem Tu Vi");
        sender.sendMessage("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tutiencore.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return filterStart(Arrays.asList("set", "add", "take", "reset", "resetall", "info"), args[0]);
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("resetall")) {
            return null; // Online player names
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("set") || sub.equals("add") || sub.equals("take")) {
                return Arrays.asList("100", "1000", "10000", "100000");
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStart(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
