package com.turtle.tutiencore.core.command;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.manager.FlySwordManager;
import com.turtle.tutiencore.core.manager.PlayerHologramManager;
import com.turtle.tutiencore.core.manager.RealmManager;
import com.turtle.tutiencore.core.manager.TuLuyenManager;
import com.turtle.tutiencore.core.manager.ZoneManager;
import com.turtle.tutiencore.core.model.CuboidZone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TuTienCommand implements CommandExecutor {

    private final TuLuyenManager tuLuyenManager;
    private final ZoneManager zoneManager;
    private final ConfigManager config;
    private final DotPhaCommand dotPhaCommand;
    private final FlySwordManager flySwordManager;
    private final RealmManager realmManager;
    private final PlayerHologramManager playerHologramManager;
    private final Runnable commandAliasReloader;

    public TuTienCommand(TuLuyenManager tuLuyenManager, ZoneManager zoneManager, ConfigManager config,
            DotPhaCommand dotPhaCommand, FlySwordManager flySwordManager, RealmManager realmManager,
            PlayerHologramManager playerHologramManager, Runnable commandAliasReloader) {
        this.tuLuyenManager = tuLuyenManager;
        this.zoneManager = zoneManager;
        this.config = config;
        this.dotPhaCommand = dotPhaCommand;
        this.flySwordManager = flySwordManager;
        this.realmManager = realmManager;
        this.playerHologramManager = playerHologramManager;
        this.commandAliasReloader = commandAliasReloader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Player shortcut for cultivation
        if (label.equalsIgnoreCase("tuluyen")) {
            if (!(sender instanceof Player)) return true;
            tuLuyenManager.toggleTuLuyen((Player) sender);
            return true;
        }

        // Main command /ttc
        if (args.length == 0) {
            sender.sendMessage("§e[TuTienCore] Commands:");
            sender.sendMessage("§e/ttc tuluyen §7- Toggle cultivation");
            if (sender.hasPermission("tutiencore.admin")) {
                sender.sendMessage("§c/ttc reload §7- Reload configuration");
                sender.sendMessage("§c/ttc wand §7- Get Zone Wand");
                sender.sendMessage("§c/ttc create <zoneName> §7- Create Zone");
                sender.sendMessage("§c/ttc zonecenter <zoneName> §7- Set Center for particles");
                sender.sendMessage("§c/ttc admin tuvi <give|remove|set|check|reset|resetall> <player> [amount]");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("tuluyen") && sender instanceof Player) {
            tuLuyenManager.toggleTuLuyen((Player) sender);
            return true;
        }

        // --- ADMIN COMMANDS BELOW ---
        if (!sender.hasPermission("tutiencore.admin")) {
            sender.sendMessage(config.getMessage("admin.no-permission", "§cBạn không có quyền dùng lệnh này."));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            config.load();
            realmManager.reload();
            dotPhaCommand.loadConfig();
            flySwordManager.loadConfig();
            playerHologramManager.reload();
            if (commandAliasReloader != null) {
                commandAliasReloader.run();
            }
            sender.sendMessage("§a[TuTienCore] Đã nạp lại cấu hình!");
            return true;
        }

        if (args[0].equalsIgnoreCase("wand")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            ItemStack wand = new ItemStack(zoneManager.getWandMaterial());
            ItemMeta meta = wand.getItemMeta();
            meta.setDisplayName("§bTuTien Zone Wand");
            wand.setItemMeta(meta);
            player.getInventory().addItem(wand);
            player.sendMessage(config.getMessage("admin.wand-received", "§aĐã nhận Gậy Tạo Zone. Chuột trái: pos1, Chuột phải: pos2."));
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (args.length < 2) {
                player.sendMessage(config.getMessage("admin.usage-create", "§cCách dùng: /ttc create <zoneName>"));
                return true;
            }
            Location pos1 = zoneManager.getPos1(player);
            Location pos2 = zoneManager.getPos2(player);
            if (pos1 == null || pos2 == null) {
                player.sendMessage(config.getMessage("admin.select-first", "§cBạn cần chọn pos1 và pos2 bằng gậy wand trước tiên."));
                return true;
            }
            String zoneName = args[1];
            if (zoneManager.getZone(zoneName) != null) {
                player.sendMessage(config.getMessage("admin.zone-exists", "§cKhu vực này đã tồn tại."));
                return true;
            }
            zoneManager.createZone(zoneName, pos1, pos2);
            player.sendMessage(config.getMessage("admin.zone-created", "§aTạo thành công Khu vực: %zone%").replace("%zone%", zoneName));
            return true;
        }

        if (args[0].equalsIgnoreCase("zonecenter")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (args.length < 2) {
                player.sendMessage(config.getMessage("admin.usage-zonecenter", "§cCách dùng: /ttc zonecenter <zoneName>"));
                return true;
            }
            String zoneName = args[1];
            CuboidZone zone = zoneManager.getZone(zoneName);
            if (zone == null) {
                player.sendMessage(config.getMessage("admin.zone-not-found", "§cKhông tìm thấy khu vực nào có tên này."));
                return true;
            }
            zone.setCenter(player.getLocation());
            zoneManager.saveZones();
            player.sendMessage(config.getMessage("admin.zone-center-set", "§aĐã đặt tâm hạt particle cho khu vực %zone% tại vị trí của bạn.").replace("%zone%", zoneName));
            return true;
        }

        if (args[0].equalsIgnoreCase("admin") && args.length >= 2 && args[1].equalsIgnoreCase("tuvi")) {
            if (args.length < 4 && !args[2].equalsIgnoreCase("resetall")) {
                sender.sendMessage(config.getMessage("admin.usage-admin-tuvi", "§cCách dùng: /ttc admin tuvi <give|remove|set|check|reset|resetall> <player> [amount]"));
                return true;
            }

            String action = args[2].toLowerCase();

            if (action.equals("resetall")) {
               sender.sendMessage("§cResetAll logic not implemented yet.");
               return true;
            }

            Player target = Bukkit.getPlayer(args[3]);
            if (target == null && !action.equals("check")) { 
                sender.sendMessage(config.getMessage("admin.player-not-found", "§cNgười chơi không online hoặc không tồn tại."));
                return true;
            }

            switch (action) {
                case "give":
                case "add":
                    if (args.length < 5) return true;
                    double addAmount = Double.parseDouble(args[4]);
                    TuTien.getApi().addTuVi(target.getUniqueId(), addAmount);
                    sender.sendMessage(config.getMessage("admin.tuvi-add", "§aĐã cộng %amount% Tu Vi cho %player%")
                            .replace("%amount%", String.valueOf(addAmount))
                            .replace("%player%", target.getName()));
                    break;
                case "remove":
                    if (args.length < 5) return true;
                    double rmAmount = Double.parseDouble(args[4]);
                    TuTien.getApi().takeTuVi(target.getUniqueId(), rmAmount);
                    sender.sendMessage(config.getMessage("admin.tuvi-remove", "§aĐã trừ %amount% Tu Vi từ %player%")
                            .replace("%amount%", String.valueOf(rmAmount))
                            .replace("%player%", target.getName()));
                    break;
                case "set":
                    if (args.length < 5) return true;
                    double setAmount = Double.parseDouble(args[4]);
                    TuTien.getApi().setTuVi(target.getUniqueId(), setAmount);
                    sender.sendMessage(config.getMessage("admin.tuvi-set", "§aĐã đặt Tu Vi của %player% thành %amount%")
                            .replace("%amount%", String.valueOf(setAmount))
                            .replace("%player%", target.getName()));
                    break;
                case "reset":
                    TuTien.getApi().setTuVi(target.getUniqueId(), 0);
                    sender.sendMessage(config.getMessage("admin.tuvi-reset", "§aĐã reset Tu Vi của %player% về 0.")
                            .replace("%player%", target.getName()));
                    break;
                case "check":
                    Player t = Bukkit.getPlayer(args[3]);
                    double tuvi = 0;
                    String pName = args[3];
                    if (t != null) {
                        tuvi = TuTien.getApi().getTuVi(t.getUniqueId());
                        pName = t.getName();
                    } else {
                        sender.sendMessage(config.getMessage("admin.player-not-found", "§cNgười chơi không online hoặc không tồn tại."));
                        return true;
                    }
                    sender.sendMessage(config.getMessage("admin.tuvi-check", "§aĐiểm Tu Vi của %player% là: %amount%")
                            .replace("%player%", pName)
                            .replace("%amount%", String.valueOf(tuvi)));
                    break;
                default:
                    sender.sendMessage(config.getMessage("admin.tuvi-invalid-action", "§cThao tác Tu Vi không hợp lệ."));
            }
            return true;
        }

        return true;
    }
}
