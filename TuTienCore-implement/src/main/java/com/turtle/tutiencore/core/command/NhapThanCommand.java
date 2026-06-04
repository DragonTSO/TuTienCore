package com.turtle.tutiencore.core.command;

import com.turtle.tutiencore.core.infusion.InfusionManager;
import com.turtle.tutiencore.core.infusion.InfusionRarity;
import com.turtle.tutiencore.core.infusion.InfusionType;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class NhapThanCommand implements CommandExecutor, TabCompleter {

    private final InfusionManager infusionManager;

    public NhapThanCommand(InfusionManager infusionManager) {
        this.infusionManager = infusionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isLuaThanCommand(command, label)) {
            return handleLuaThan(sender, args);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(infusionManager.message("player-only"));
            return true;
        }

        if (!player.hasPermission("tutiencore.use")) {
            player.sendMessage(infusionManager.message("no-permission"));
            return true;
        }

        infusionManager.open(player);
        return true;
    }

    private boolean handleLuaThan(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return handleLuaThanGive(sender, args);
        }

        sender.sendMessage(infusionManager.message("give-usage"));
        return true;
    }

    private boolean handleLuaThanGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tutiencore.luathan.give")) {
            sender.sendMessage(infusionManager.message("no-permission"));
            return true;
        }

        if (args.length != 4) {
            sender.sendMessage(infusionManager.message("give-usage"));
            return true;
        }

        String targetInput = args[1];
        Player target = Bukkit.getPlayerExact(targetInput);
        if (target == null) {
            sender.sendMessage(infusionManager.message("give-player-not-found"));
            return true;
        }

        String typeInput = args[2];
        String rarityInput = args[3];
        InfusionManager.GiveResult result = infusionManager.giveFlameItem(target, typeInput, rarityInput);
        switch (result) {
            case SUCCESS -> {
                InfusionType type = infusionManager.resolveType(typeInput);
                InfusionRarity rarity = infusionManager.resolveRarity(rarityInput);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("{player}", target.getName());
                placeholders.put("{type}", type == null ? typeInput : type.displayName());
                placeholders.put("{rarity}", rarity == null ? rarityInput : rarity.displayName());
                placeholders.put("{rarity_display}", rarity == null ? rarityInput : rarity.displayName());
                placeholders.put("{rarity_color}", rarity == null ? "" : rarity.color());
                sender.sendMessage(infusionManager.message("give-success", placeholders));
            }
            case DISABLED -> sender.sendMessage(infusionManager.message("feature-disabled"));
            case INVALID_TYPE -> {
                Map<String, String> placeholders = Map.of("{type}", typeInput);
                sender.sendMessage(infusionManager.message("give-invalid-type", placeholders));
            }
            case INVALID_RARITY -> {
                Map<String, String> placeholders = Map.of("{rarity}", rarityInput);
                sender.sendMessage(infusionManager.message("give-invalid-rarity", placeholders));
            }
            case INVENTORY_FULL, SAVE_FAILED -> sender.sendMessage(infusionManager.message("give-failed"));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tutiencore.nhapthan.give")) {
            sender.sendMessage(infusionManager.message("no-permission"));
            return true;
        }

        if (args.length != 4) {
            sender.sendMessage(infusionManager.message("give-usage"));
            return true;
        }

        String targetInput = args[1];
        ResolvedTarget target = resolveTarget(targetInput);
        if (target == null) {
            sender.sendMessage(infusionManager.message("give-player-not-found"));
            return true;
        }

        String typeInput = args[2];
        String rarityInput = args[3];

        InfusionManager.GiveResult result = infusionManager.giveInfusion(target.uuid(), typeInput, rarityInput);
        switch (result) {
            case SUCCESS -> {
                InfusionType type = infusionManager.resolveType(typeInput);
                InfusionRarity rarity = infusionManager.resolveRarity(rarityInput);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("{player}", target.displayName());
                placeholders.put("{type}", type == null ? typeInput : type.displayName());
                placeholders.put("{rarity}", rarity == null ? rarityInput : rarity.displayName());
                placeholders.put("{rarity_display}", rarity == null ? rarityInput : rarity.displayName());
                placeholders.put("{rarity_color}", rarity == null ? "" : rarity.color());
                sender.sendMessage(infusionManager.message("give-success", placeholders));

                Player online = Bukkit.getPlayer(target.uuid());
                if (online != null) {
                    infusionManager.refreshIfOpen(online);
                }
            }
            case DISABLED -> sender.sendMessage(infusionManager.message("feature-disabled"));
            case INVALID_TYPE -> {
                Map<String, String> placeholders = Map.of("{type}", typeInput);
                sender.sendMessage(infusionManager.message("give-invalid-type", placeholders));
            }
            case INVALID_RARITY -> {
                Map<String, String> placeholders = Map.of("{rarity}", rarityInput);
                sender.sendMessage(infusionManager.message("give-invalid-rarity", placeholders));
            }
            case INVENTORY_FULL -> sender.sendMessage(infusionManager.message("inventory-full"));
            case SAVE_FAILED -> sender.sendMessage(infusionManager.message("give-failed"));
        }
        return true;
    }

    private ResolvedTarget resolveTarget(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName());
        }

        UUID parsedUuid = parseUuid(input);
        if (parsedUuid != null) {
            OfflinePlayer byUuid = Bukkit.getOfflinePlayer(parsedUuid);
            if (byUuid.hasPlayedBefore() || byUuid.isOnline()) {
                return new ResolvedTarget(byUuid.getUniqueId(), byUuid.getName() == null ? input : byUuid.getName());
            }
            return null;
        }

        OfflinePlayer cached = getOfflinePlayerIfCached(input);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return new ResolvedTarget(cached.getUniqueId(), cached.getName() == null ? input : cached.getName());
        }
        return null;
    }

    private OfflinePlayer getOfflinePlayerIfCached(String name) {
        try {
            Method method = Bukkit.class.getMethod("getOfflinePlayerIfCached", String.class);
            Object result = method.invoke(null, name);
            if (result instanceof OfflinePlayer offlinePlayer) {
                return offlinePlayer;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        // Fallback for API versions without Bukkit#getOfflinePlayerIfCached.
        // This still stays local-only because Bukkit#getOfflinePlayers() is server cache data.
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String offlineName = offlinePlayer.getName();
            if (offlineName != null && offlineName.equals(name)) {
                return offlinePlayer;
            }
        }
        return null;
    }

    static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (isLuaThanCommand(command, alias)) {
            if (args.length == 1) {
                if (!sender.hasPermission("tutiencore.luathan.give")) {
                    return Collections.emptyList();
                }
                return filterByPrefix(List.of("give"), args[0]);
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("tutiencore.luathan.give")) {
                    return Collections.emptyList();
                }
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filterByPrefix(names, args[1]);
            }

            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("tutiencore.luathan.give")) {
                    return Collections.emptyList();
                }
                return filterByPrefix(infusionManager.getTypeSuggestions(), args[2]);
            }

            if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("tutiencore.luathan.give")) {
                    return Collections.emptyList();
                }
                return filterByPrefix(infusionManager.getRaritySuggestions(), args[3]);
            }

            return Collections.emptyList();
        }

        if (args.length == 1) {
            if (!sender.hasPermission("tutiencore.nhapthan.give")) {
                return Collections.emptyList();
            }
            return filterByPrefix(List.of("give"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("tutiencore.nhapthan.give")) {
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filterByPrefix(names, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("tutiencore.nhapthan.give")) {
                return Collections.emptyList();
            }
            return filterByPrefix(infusionManager.getTypeSuggestions(), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("tutiencore.nhapthan.give")) {
                return Collections.emptyList();
            }
            return filterByPrefix(infusionManager.getRaritySuggestions(), args[3]);
        }

        return Collections.emptyList();
    }

    static List<String> filterByPrefix(List<String> values, String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> output = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                output.add(value);
            }
        }
        return output;
    }

    private boolean isLuaThanCommand(Command command, String label) {
        String commandName = command == null ? "" : command.getName();
        return "luathan".equalsIgnoreCase(commandName) || "luathan".equalsIgnoreCase(label);
    }

    private record ResolvedTarget(UUID uuid, String displayName) {
    }
}
