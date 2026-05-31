package com.turtle.tutiencore.core.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RankupCommand implements CommandExecutor, TabCompleter {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private final JavaPlugin plugin;

    public RankupCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        ensureConfigExists();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(loadConfig().getString("rankup.messages.only-player", "&cOnly players can use this command.")));
            return true;
        }

        YamlConfiguration config = loadConfig();
        if (!config.getBoolean("rankup.enabled", true)) {
            send(player, config, "disabled");
            return true;
        }
        if (!player.hasPermission("tutiencore.rankup")) {
            send(player, config, "no-permission");
            return true;
        }

        List<Rank> ranks = loadRanks(config);
        if (ranks.isEmpty()) {
            player.sendMessage(color("&cRankup config chưa có ranks."));
            return true;
        }

        Rank current = findCurrentRank(player, ranks);
        Rank target = args.length > 0 ? findRank(ranks, args[0]) : nextRank(ranks, current);
        if (target == null) {
            send(player, config, args.length > 0 ? "rank-not-found" : "max-rank",
                    Placeholder.of("rank", args.length > 0 ? args[0] : ""),
                    Placeholder.of("next", ""));
            return true;
        }
        if (target.order() <= current.order()) {
            send(player, config, "already-or-lower",
                    Placeholder.of("rank", target.displayName()),
                    Placeholder.of("next", ""));
            return true;
        }

        boolean requireSequential = config.getBoolean("rankup.require-sequential", true);
        Rank expectedNext = nextRank(ranks, current);
        if (requireSequential && (expectedNext == null || target.order() != expectedNext.order())) {
            send(player, config, "must-rankup-next",
                    Placeholder.of("rank", target.displayName()),
                    Placeholder.of("next", expectedNext == null ? "" : expectedNext.displayName()));
            return true;
        }

        if (config.getBoolean("rankup.luckperms.enabled", true)
                && config.getBoolean("rankup.luckperms.required", true)
                && !Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            send(player, config, "luckperms-missing");
            return true;
        }

        int cost = Math.max(0, target.cost());
        int balance = getPlayerPoints(player.getUniqueId());
        String currency = config.getString("rankup.playerpoints.currency-name", "Points");
        if (config.getBoolean("rankup.playerpoints.enabled", true)) {
            if (balance < 0) {
                send(player, config, "playerpoints-missing");
                return true;
            }
            if (balance < cost) {
                send(player, config, "not-enough-points",
                        Placeholder.of("cost", String.valueOf(cost)),
                        Placeholder.of("balance", String.valueOf(balance)),
                        Placeholder.of("currency", currency));
                return true;
            }
            if (cost > 0 && !takePlayerPoints(player.getUniqueId(), cost)) {
                send(player, config, "take-points-failed",
                        Placeholder.of("currency", currency));
                return true;
            }
        }

        boolean stackBonuses = isStackBonuses(config);
        applyRank(player, config, ranks, current, target, stackBonuses);

        Totals totals = totalsAfterRankup(player, ranks, current, target, stackBonuses);
        sendList(player, config, "rankup.messages.success",
                Placeholder.of("rank", target.displayName()),
                Placeholder.of("rank_id", target.id()),
                Placeholder.of("group", target.group()),
                Placeholder.of("cost", String.valueOf(cost)),
                Placeholder.of("balance", String.valueOf(Math.max(0, balance - cost))),
                Placeholder.of("currency", currency),
                Placeholder.of("bonus_mode", stackBonuses ? "Cộng dồn" : "Chỉ rank hiện tại"),
                Placeholder.of("total_tuvi", formatNumber(totals.tuvi())),
                Placeholder.of("total_forge_luck", formatNumber(totals.forgeLuck())),
                Placeholder.of("total_mythic_money", formatNumber(totals.mythicMoney())));
        broadcastRankup(config,
                Placeholder.of("player", player.getName()),
                Placeholder.of("player_display", player.getDisplayName()),
                Placeholder.of("rank", target.displayName()),
                Placeholder.of("rank_id", target.id()),
                Placeholder.of("group", target.group()),
                Placeholder.of("cost", String.valueOf(cost)),
                Placeholder.of("balance", String.valueOf(Math.max(0, balance - cost))),
                Placeholder.of("currency", currency),
                Placeholder.of("bonus_mode", stackBonuses ? "Cá»™ng dá»“n" : "Chá»‰ rank hiá»‡n táº¡i"),
                Placeholder.of("total_tuvi", formatNumber(totals.tuvi())),
                Placeholder.of("total_forge_luck", formatNumber(totals.forgeLuck())),
                Placeholder.of("total_mythic_money", formatNumber(totals.mythicMoney())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return loadRanks(loadConfig()).stream()
                .filter(rank -> rank.order() > 0)
                .map(Rank::id)
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }

    private void applyRank(Player player, YamlConfiguration config, List<Rank> ranks, Rank current, Rank target,
                           boolean stackBonuses) {
        boolean luckPerms = config.getBoolean("rankup.luckperms.enabled", true);
        if (!luckPerms) {
            return;
        }

        if (stackBonuses && current.order() > 0) {
            grantRankPermissions(player, config, current);
        }

        String groupCommand = config.getString("rankup.luckperms.set-group-command",
                "lp user {player} parent set {group}");
        if (target.group() != null && !target.group().isBlank()) {
            dispatch(player, groupCommand, target, null);
        }

        if (!stackBonuses) {
            clearOtherRankPermissions(player, config, ranks, target);
        }

        grantRankPermissions(player, config, target);
        for (String command : target.commands()) {
            dispatch(player, command, target, null);
        }
    }

    private void grantRankPermissions(Player player, YamlConfiguration config, Rank rank) {
        String permissionCommand = config.getString("rankup.luckperms.permission-command",
                "lp user {player} permission set {permission} true");
        Set<String> permissions = new HashSet<>();
        if (rank.permission() != null && !rank.permission().isBlank()) {
            permissions.add(rank.permission());
        }
        permissions.addAll(rank.permissions());

        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                continue;
            }
            dispatch(player, permissionCommand, rank, permission);
        }
    }

    private void clearOtherRankPermissions(Player player, YamlConfiguration config, List<Rank> ranks, Rank target) {
        for (Rank rank : ranks) {
            if (rank.order() <= 0 || rank.id().equals(target.id())) {
                continue;
            }
            revokeRankPermissions(player, config, rank);
        }
    }

    private void revokeRankPermissions(Player player, YamlConfiguration config, Rank rank) {
        String permissionCommand = config.getString("rankup.luckperms.permission-unset-command",
                "lp user {player} permission unset {permission}");
        Set<String> permissions = new HashSet<>();
        if (rank.permission() != null && !rank.permission().isBlank()) {
            permissions.add(rank.permission());
        }
        permissions.addAll(rank.permissions());

        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                continue;
            }
            dispatch(player, permissionCommand, rank, permission);
        }
    }

    private void dispatch(Player player, String command, Rank rank, String permission) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{rank}", rank.id())
                .replace("{rank_id}", rank.id())
                .replace("{rank_name}", rank.displayName())
                .replace("{group}", rank.group() == null ? "" : rank.group())
                .replace("{permission}", permission == null ? "" : permission);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    private Totals totalsAfterRankup(Player player, List<Rank> ranks, Rank current, Rank target, boolean stackBonuses) {
        if (!stackBonuses) {
            return new Totals(target.tuviBonus(), target.forgeLuckBonus(), target.mythicMoneyBonus());
        }

        Set<String> counted = new HashSet<>();
        double tuvi = 0.0;
        double forgeLuck = 0.0;
        double mythicMoney = 0.0;

        for (Rank rank : ranks) {
            boolean active = hasRank(player, rank) || rank.id().equals(current.id()) || rank.id().equals(target.id());
            if (!active || !counted.add(rank.id())) {
                continue;
            }
            tuvi += rank.tuviBonus();
            forgeLuck += rank.forgeLuckBonus();
            mythicMoney += rank.mythicMoneyBonus();
        }

        return new Totals(tuvi, forgeLuck, mythicMoney);
    }

    private boolean isStackBonuses(YamlConfiguration config) {
        if (config.contains("rankup.stack-bonuses")) {
            return config.getBoolean("rankup.stack-bonuses", true);
        }
        return config.getBoolean("rankup.preserve-current-rank-perks", true);
    }

    private Rank findCurrentRank(Player player, List<Rank> ranks) {
        Rank current = ranks.get(0);
        for (Rank rank : ranks) {
            if (rank.order() >= current.order() && hasRank(player, rank)) {
                current = rank;
            }
        }
        return current;
    }

    private boolean hasRank(Player player, Rank rank) {
        if (rank.order() <= 0) {
            return true;
        }
        if (rank.permission() != null && !rank.permission().isBlank() && player.hasPermission(rank.permission())) {
            return true;
        }
        return rank.group() != null && !rank.group().isBlank() && player.hasPermission("group." + rank.group());
    }

    private Rank findRank(List<Rank> ranks, String id) {
        for (Rank rank : ranks) {
            if (rank.id().equalsIgnoreCase(id)) {
                return rank;
            }
        }
        return null;
    }

    private Rank nextRank(List<Rank> ranks, Rank current) {
        for (Rank rank : ranks) {
            if (rank.order() > current.order()) {
                return rank;
            }
        }
        return null;
    }

    private List<Rank> loadRanks(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section == null) {
            return List.of();
        }

        List<Rank> ranks = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection rankSection = section.getConfigurationSection(id);
            if (rankSection == null) {
                continue;
            }
            ConfigurationSection bonuses = rankSection.getConfigurationSection("bonuses");
            int order = rankSection.getInt("order", parseOrder(id));
            ranks.add(new Rank(
                    id,
                    order,
                    rankSection.getString("group", id),
                    rankSection.getString("permission", ""),
                    rankSection.getString("display-name", id),
                    rankSection.getInt("cost", 0),
                    bonuses == null ? 0.0 : bonuses.getDouble("tuvi", 0.0),
                    bonuses == null ? 0.0 : bonuses.getDouble("forge-luck", 0.0),
                    bonuses == null ? 0.0 : bonuses.getDouble("mythic-money", 0.0),
                    rankSection.getStringList("permissions"),
                    rankSection.getStringList("commands")
            ));
        }

        ranks.sort(Comparator.comparingInt(Rank::order));
        return ranks;
    }

    private int parseOrder(String id) {
        String digits = id.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int getPlayerPoints(UUID uuid) {
        Object api = playerPointsApi();
        if (api == null) {
            return -1;
        }
        try {
            Method look = api.getClass().getMethod("look", UUID.class);
            Object value = look.invoke(api, uuid);
            return value instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Cannot read PlayerPoints balance: " + exception.getMessage());
            return -1;
        }
    }

    private boolean takePlayerPoints(UUID uuid, int amount) {
        Object api = playerPointsApi();
        if (api == null) {
            return false;
        }
        try {
            Method take = api.getClass().getMethod("take", UUID.class, int.class);
            Object value = take.invoke(api, uuid, amount);
            return !(value instanceof Boolean) || (Boolean) value;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Cannot take PlayerPoints: " + exception.getMessage());
            return false;
        }
    }

    private Object playerPointsApi() {
        Plugin playerPoints = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (playerPoints == null || !playerPoints.isEnabled()) {
            return null;
        }
        try {
            return playerPoints.getClass().getMethod("getAPI").invoke(playerPoints);
        } catch (ReflectiveOperationException ignored) {
            try {
                Class<?> clazz = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
                Object instance = clazz.getMethod("getInstance").invoke(null);
                return instance.getClass().getMethod("getAPI").invoke(instance);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("Cannot access PlayerPoints API: " + exception.getMessage());
                return null;
            }
        }
    }

    private YamlConfiguration loadConfig() {
        ensureConfigExists();
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "rankup.yml"));
    }

    private void ensureConfigExists() {
        File file = new File(plugin.getDataFolder(), "rankup.yml");
        if (!file.exists()) {
            plugin.saveResource("rankup.yml", false);
        }
    }

    private void send(Player player, YamlConfiguration config, String key, Placeholder... placeholders) {
        String message = config.getString("rankup.messages." + key, "&c" + key);
        for (Placeholder placeholder : placeholders) {
            message = message.replace("{" + placeholder.key() + "}", placeholder.value());
        }
        player.sendMessage(color(message));
    }

    private void sendList(Player player, YamlConfiguration config, String path, Placeholder... placeholders) {
        List<String> messages = config.getStringList(path);
        if (messages.isEmpty()) {
            String single = config.getString(path, "");
            if (!single.isBlank()) {
                messages = List.of(single);
            }
        }
        for (String line : messages) {
            for (Placeholder placeholder : placeholders) {
                line = line.replace("{" + placeholder.key() + "}", placeholder.value());
            }
            player.sendMessage(color(line));
        }
    }

    private void broadcastRankup(YamlConfiguration config, Placeholder... placeholders) {
        if (!config.getBoolean("rankup.broadcast.enabled", true)) {
            return;
        }

        List<String> lines = config.getStringList("rankup.broadcast.messages");
        if (lines.isEmpty()) {
            String single = config.getString("rankup.broadcast.message", "");
            if (!single.isBlank()) {
                lines = List.of(single);
            }
        }
        if (lines.isEmpty()) {
            lines = List.of("&6&lRANKUP &8» &f{player} &ađã nâng lên {rank}&a!");
        }

        BroadcastFormat format = loadBroadcastFormat(config);
        for (int index = 0; index < lines.size(); index++) {
            String rawLine = applyPlaceholders(lines.get(index), placeholders);
            String formatted = color(applyBroadcastFormat(rawLine, index, format));
            long delay = Math.max(0L, format.lineDelayTicks()) * index;
            if (delay <= 0L) {
                broadcastLine(formatted);
                continue;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> broadcastLine(formatted), delay);
        }
    }

    private BroadcastFormat loadBroadcastFormat(YamlConfiguration config) {
        BroadcastFormat format = new BroadcastFormat(
                "\uA45A",
                "\uA45C",
                "&#6CF6FF{first-line-icon} &f{message}",
                "&#6CF6FF{chained-line-icon} &f{message}",
                20L
        );

        if (config.getBoolean("rankup.broadcast.use-turtlebroadcast-format", true)) {
            File turtleBroadcastConfig = resolveTurtleBroadcastConfig(config);
            if (turtleBroadcastConfig.exists()) {
                YamlConfiguration turtleConfig = YamlConfiguration.loadConfiguration(turtleBroadcastConfig);
                format = new BroadcastFormat(
                        turtleConfig.getString("format.first-line-icon", format.firstLineIcon()),
                        turtleConfig.getString("format.chained-line-icon", format.chainedLineIcon()),
                        turtleConfig.getString("format.first-line", format.firstLine()),
                        turtleConfig.getString("format.chained-line", format.chainedLine()),
                        Math.max(0L, turtleConfig.getLong("format.line-delay-ticks", format.lineDelayTicks()))
                );
            }
        }

        return new BroadcastFormat(
                optionalString(config, "rankup.broadcast.format.first-line-icon", format.firstLineIcon()),
                optionalString(config, "rankup.broadcast.format.chained-line-icon", format.chainedLineIcon()),
                optionalString(config, "rankup.broadcast.format.first-line", format.firstLine()),
                optionalString(config, "rankup.broadcast.format.chained-line", format.chainedLine()),
                config.getLong("rankup.broadcast.line-delay-ticks", -1L) >= 0L
                        ? config.getLong("rankup.broadcast.line-delay-ticks")
                        : format.lineDelayTicks()
        );
    }

    private File resolveTurtleBroadcastConfig(YamlConfiguration config) {
        String configured = config.getString("rankup.broadcast.turtlebroadcast-config", "");
        if (configured != null && !configured.isBlank()) {
            File file = new File(configured);
            if (file.isAbsolute()) {
                return file;
            }
            return new File(plugin.getDataFolder().getParentFile(), configured);
        }
        return new File(new File(plugin.getDataFolder().getParentFile(), "TurtleBroadcast"), "config.yml");
    }

    private String optionalString(YamlConfiguration config, String path, String fallback) {
        String value = config.getString(path, "");
        return value == null || value.isBlank() ? fallback : value;
    }

    private String applyBroadcastFormat(String rawLine, int lineIndex, BroadcastFormat format) {
        boolean chained = lineIndex > 0;
        String lineIcon = chained ? format.chainedLineIcon() : format.firstLineIcon();
        String template = chained ? format.chainedLine() : format.firstLine();
        return template
                .replace("{line-icon}", lineIcon)
                .replace("{first-line-icon}", format.firstLineIcon())
                .replace("{chained-line-icon}", format.chainedLineIcon())
                .replace("{message}", rawLine);
    }

    private void broadcastLine(String line) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private String applyPlaceholders(String message, Placeholder... placeholders) {
        String parsed = message == null ? "" : message;
        for (Placeholder placeholder : placeholders) {
            parsed = parsed.replace("{" + placeholder.key() + "}", placeholder.value());
        }
        return parsed;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', translateHexColors(text == null ? "" : text));
    }

    private String translateHexColors(String text) {
        return translateHexPattern(translateHexPattern(text, MINI_HEX_PATTERN), HEX_PATTERN);
    }

    private String translateHexPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char character : hex.toCharArray()) {
                replacement.append('&').append(character);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record Placeholder(String key, String value) {
        static Placeholder of(String key, String value) {
            return new Placeholder(key, value == null ? "" : value);
        }
    }

    private record Rank(String id, int order, String group, String permission, String displayName, int cost,
                        double tuviBonus, double forgeLuckBonus, double mythicMoneyBonus,
                        List<String> permissions, List<String> commands) {
    }

    private record Totals(double tuvi, double forgeLuck, double mythicMoney) {
    }

    private record BroadcastFormat(String firstLineIcon, String chainedLineIcon,
                                   String firstLine, String chainedLine, long lineDelayTicks) {
    }
}
