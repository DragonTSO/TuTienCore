package com.turtle.tutiencore.core.gui;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.List;

/**
 * Hệ thống Requirements kiểu DeluxeMenus.
 * Hỗ trợ: has permission, has money, has item, comparators (==, !=, >, <, >=, <=),
 *          string equals, string equals ignorecase, string contains
 * Đảo ngược bằng "!" trước type.
 */
public class RequirementChecker {

    /**
     * Check all requirements in a section. Returns true if ALL requirements pass.
     */
    public static boolean checkRequirements(Player player, ConfigurationSection reqSection) {
        if (reqSection == null) return true;

        ConfigurationSection requirements = reqSection.getConfigurationSection("requirements");
        if (requirements == null) return true;

        int minimumRequired = reqSection.getInt("minimum_requirements", -1);
        boolean stopAtSuccess = reqSection.getBoolean("stop_at_success", false);
        int passed = 0;

        for (String key : requirements.getKeys(false)) {
            ConfigurationSection req = requirements.getConfigurationSection(key);
            if (req == null) continue;

            boolean result = evaluateRequirement(player, req);

            if (result) {
                passed++;
                // Execute per-requirement success commands
                executeActions(player, req.getStringList("success_commands"));

                if (minimumRequired > 0 && passed >= minimumRequired && stopAtSuccess) {
                    return true;
                }
            } else {
                // Execute per-requirement deny commands
                executeActions(player, req.getStringList("deny_commands"));
            }
        }

        if (minimumRequired > 0) {
            if (passed >= minimumRequired) return true;
            // Execute global deny commands
            executeActions(player, reqSection.getStringList("deny_commands"));
            return false;
        }

        // All must pass
        boolean allPassed = passed == requirements.getKeys(false).size();
        if (!allPassed) {
            executeActions(player, reqSection.getStringList("deny_commands"));
        }
        return allPassed;
    }

    /**
     * Evaluate a single requirement.
     */
    private static boolean evaluateRequirement(Player player, ConfigurationSection req) {
        String type = req.getString("type", "");
        boolean invert = type.startsWith("!");
        if (invert) type = type.substring(1).trim();

        boolean result;
        switch (type.toLowerCase()) {
            case "has permission":
                result = checkPermission(player, req);
                break;
            case "has money":
                result = checkMoney(player, req);
                break;
            case "has item":
                result = checkItem(player, req);
                break;
            case "string equals":
                result = checkStringEquals(player, req, false);
                break;
            case "string equals ignorecase":
                result = checkStringEquals(player, req, true);
                break;
            case "string contains":
                result = checkStringContains(player, req);
                break;
            case "==":
            case "!=":
            case ">":
            case "<":
            case ">=":
            case "<=":
                result = checkComparator(player, req, type);
                break;
            default:
                result = true;
                break;
        }

        return invert != result; // XOR: invert flips the result
    }

    // --- Requirement Types ---

    private static boolean checkPermission(Player player, ConfigurationSection req) {
        String permission = req.getString("permission", "");
        return player.hasPermission(permission);
    }

    private static boolean checkMoney(Player player, ConfigurationSection req) {
        double amount = req.getDouble("amount", 0);
        // Check via Vault using reflection to avoid compile dependency
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(econClass);
            if (registration == null) return false;
            Object econ = registration.getClass().getMethod("getProvider").invoke(registration);
            double balance = (double) econ.getClass().getMethod("getBalance", org.bukkit.OfflinePlayer.class).invoke(econ, player);
            return balance >= amount;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkItem(Player player, ConfigurationSection req) {
        String materialName = parsePlaceholders(player, req.getString("material", "STONE"));
        int amount = req.getInt("amount", 1);
        String name = req.getString("name", null);

        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }

        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != material) continue;
            if (name != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String translatedName = ChatColor.translateAlternateColorCodes('&', name);
                if (!item.getItemMeta().getDisplayName().equals(translatedName)) continue;
            } else if (name != null) {
                continue;
            }
            count += item.getAmount();
        }
        return count >= amount;
    }

    private static boolean checkStringEquals(Player player, ConfigurationSection req, boolean ignoreCase) {
        String input = parsePlaceholders(player, req.getString("input", ""));
        String output = parsePlaceholders(player, req.getString("output", ""));
        return ignoreCase ? input.equalsIgnoreCase(output) : input.equals(output);
    }

    private static boolean checkStringContains(Player player, ConfigurationSection req) {
        String input = parsePlaceholders(player, req.getString("input", ""));
        String output = parsePlaceholders(player, req.getString("output", ""));
        return input.contains(output);
    }

    private static boolean checkComparator(Player player, ConfigurationSection req, String operator) {
        String inputStr = parsePlaceholders(player, req.getString("input", "0"));
        String outputStr = parsePlaceholders(player, req.getString("output", "0"));

        try {
            double input = Double.parseDouble(inputStr);
            double output = Double.parseDouble(outputStr);

            switch (operator) {
                case "==": return input == output;
                case "!=": return input != output;
                case ">":  return input > output;
                case "<":  return input < output;
                case ">=": return input >= output;
                case "<=": return input <= output;
                default: return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // --- Actions ---

    /**
     * Execute action commands (DeluxeMenus-style).
     * Supported: [message], [player], [console], [sound], [close]
     */
    public static void executeActions(Player player, List<String> actions) {
        if (actions == null || actions.isEmpty()) return;

        for (String action : actions) {
            action = parsePlaceholders(player, action);

            if (action.startsWith("[message] ")) {
                String msg = ChatColor.translateAlternateColorCodes('&', action.substring(10));
                player.sendMessage(msg);
            } else if (action.startsWith("[player] ")) {
                String cmd = action.substring(9);
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("TuTienCore"),
                    () -> player.performCommand(cmd)
                );
            } else if (action.startsWith("[console] ")) {
                String cmd = action.substring(10);
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("TuTienCore"),
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                );
            } else if (action.startsWith("[sound] ")) {
                String soundName = action.substring(8).trim();
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase());
                    player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
                } catch (IllegalArgumentException ignored) {}
            } else if (action.equals("[close]")) {
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("TuTienCore"),
                    player::closeInventory
                );
            }
        }
    }

    // --- Utility ---

    private static String parsePlaceholders(Player player, String text) {
        if (text == null) return "";
        text = text.replace("%player%", player.getName());
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}
