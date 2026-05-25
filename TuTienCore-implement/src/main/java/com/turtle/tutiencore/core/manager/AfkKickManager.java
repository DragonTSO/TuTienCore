package com.turtle.tutiencore.core.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AfkKickManager implements Listener {

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([a-zA-ZÀ-ỹ]+)");

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTitleAt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> warned = new ConcurrentHashMap<>();
    private final Map<Integer, AfkRank> ranks = new ConcurrentHashMap<>();

    private BukkitTask task;
    private boolean enabled;
    private String permissionPrefix;
    private String bypassPermission;
    private int defaultRank;
    private long checkIntervalTicks;
    private long afkStartAfterMillis;
    private long warningBeforeKickMillis;
    private boolean warningEnabled;
    private boolean titleEnabled;
    private String titleText;
    private String subtitleText;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;
    private long titleRepeatMillis;
    private boolean ignoreLookOnly;
    private double movementThresholdSquared;
    private String warningMessage;
    private String kickMessage;

    public AfkKickManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            markActive(player);
        }
    }

    public void reload() {
        injectDefaults();

        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("afk-kick.enabled", true);
        permissionPrefix = config.getString("afk-kick.permission-prefix", "tutiencore.vip.");
        bypassPermission = config.getString("afk-kick.bypass-permission", "tutiencore.afk.bypass");
        defaultRank = config.getInt("afk-kick.default-rank", 0);
        checkIntervalTicks = Math.max(20L, parseTicks(config.getString("afk-kick.check-interval", "10s"), 200L));
        afkStartAfterMillis = Math.max(0L, parseDurationMillis(config.getString("afk-kick.afk-start-after", "10s"), 10_000L));
        warningEnabled = config.getBoolean("afk-kick.warning.enabled", true);
        warningBeforeKickMillis = parseDurationMillis(config.getString("afk-kick.warning.before-kick", "30s"), 30_000L);
        titleEnabled = config.getBoolean("afk-kick.title.enabled", true);
        titleText = config.getString("afk-kick.title.title", "&6&lĐang AFK");
        subtitleText = config.getString("afk-kick.title.subtitle", "&eCòn &c%time% &enữa sẽ bị kick.");
        titleFadeIn = Math.max(0, config.getInt("afk-kick.title.fade-in", 5));
        titleStay = Math.max(1, config.getInt("afk-kick.title.stay", 45));
        titleFadeOut = Math.max(0, config.getInt("afk-kick.title.fade-out", 10));
        titleRepeatMillis = parseDurationMillis(config.getString("afk-kick.title.repeat-interval", "10s"), 10_000L);
        ignoreLookOnly = config.getBoolean("afk-kick.activity.ignore-look-only", true);
        double movementThreshold = Math.max(0.0, config.getDouble("afk-kick.activity.movement-threshold", 0.08));
        movementThresholdSquared = movementThreshold * movementThreshold;
        warningMessage = config.getString("afk-kick.warning.message",
                "&eBạn đang AFK quá lâu. Còn &c%time% &enữa sẽ bị rời khỏi máy chủ.");
        kickMessage = config.getString("afk-kick.kick-message",
                "&cBạn đã AFK quá lâu. Hãy quay lại khi sẵn sàng tiếp tục tu luyện.");

        loadRanks(config);
        restartTask();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastActivity.clear();
        lastTitleAt.clear();
        warned.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastActivity.remove(uuid);
        lastTitleAt.remove(uuid);
        warned.remove(uuid);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (ignoreLookOnly && from.getWorld() == to.getWorld()
                && from.getX() == to.getX()
                && from.getY() == to.getY()
                && from.getZ() == to.getZ()) {
            return;
        }
        if (from.getWorld() == to.getWorld() && from.distanceSquared(to) < movementThresholdSquared) {
            return;
        }
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            markActive(player);
        }
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markActive(player);
        }
    }

    private void restartTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!enabled) {
            return;
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, checkIntervalTicks, checkIntervalTicks);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(bypassPermission)) {
                markActive(player);
                continue;
            }

            AfkRank rank = getRank(player);
            if (rank.kickAfterMillis() < 0) {
                markActive(player);
                continue;
            }

            long idleMillis = now - lastActivity.getOrDefault(player.getUniqueId(), now);
            if (idleMillis < afkStartAfterMillis) {
                lastTitleAt.remove(player.getUniqueId());
                warned.remove(player.getUniqueId());
                continue;
            }

            long afkMillis = idleMillis - afkStartAfterMillis;
            long remainingMillis = rank.kickAfterMillis() - afkMillis;
            if (remainingMillis <= 0) {
                player.kickPlayer(color(applyPlaceholders(kickMessage, rank, 0L, afkMillis)));
                continue;
            }

            sendAfkTitle(player, rank, remainingMillis, afkMillis, now);

            if (warningEnabled && warningBeforeKickMillis > 0 && remainingMillis <= warningBeforeKickMillis
                    && !warned.getOrDefault(player.getUniqueId(), false)) {
                player.sendMessage(color(applyPlaceholders(warningMessage, rank, remainingMillis, afkMillis)));
                warned.put(player.getUniqueId(), true);
            }
        }
    }

    private void markActive(Player player) {
        if (player == null) {
            return;
        }
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        lastTitleAt.remove(player.getUniqueId());
        warned.remove(player.getUniqueId());
    }

    private void sendAfkTitle(Player player, AfkRank rank, long remainingMillis, long afkMillis, long now) {
        if (!titleEnabled) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Long lastSent = lastTitleAt.get(uuid);
        if (lastSent != null && (titleRepeatMillis < 0 || now - lastSent < titleRepeatMillis)) {
            return;
        }
        player.sendTitle(
                color(applyPlaceholders(titleText, rank, remainingMillis, afkMillis)),
                color(applyPlaceholders(subtitleText, rank, remainingMillis, afkMillis)),
                titleFadeIn,
                titleStay,
                titleFadeOut);
        lastTitleAt.put(uuid, now);
    }

    private String applyPlaceholders(String message, AfkRank rank, long remainingMillis, long afkMillis) {
        return (message == null ? "" : message)
                .replace("%rank%", String.valueOf(rank.level()))
                .replace("%time%", formatDuration(remainingMillis))
                .replace("%remaining%", formatDuration(remainingMillis))
                .replace("%afk_time%", formatDuration(afkMillis))
                .replace("%kick_time%", formatDuration(rank.kickAfterMillis()));
    }

    private void loadRanks(FileConfiguration config) {
        ranks.clear();
        ConfigurationSection section = config.getConfigurationSection("afk-kick.ranks");
        if (section == null) {
            ranks.put(defaultRank, new AfkRank(defaultRank, "", parseDurationMillis("10m", 600_000L)));
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection rankSection = section.getConfigurationSection(key);
            if (rankSection == null) {
                continue;
            }
            int level = parseRankLevel(key, rankSection.getInt("level", defaultRank));
            String permission = rankSection.getString("permission", permissionPrefix + level);
            long kickAfter = parseDurationMillis(rankSection.getString("kick-after", "10m"), 600_000L);
            ranks.put(level, new AfkRank(level, permission == null ? "" : permission, kickAfter));
        }

        ranks.putIfAbsent(defaultRank, new AfkRank(defaultRank, "", parseDurationMillis("10m", 600_000L)));
    }

    private AfkRank getRank(Player player) {
        return ranks.values().stream()
                .filter(rank -> rank.permission().isBlank() || player.hasPermission(rank.permission()))
                .max(Comparator.comparingInt(AfkRank::level))
                .orElseGet(() -> ranks.getOrDefault(defaultRank,
                        new AfkRank(defaultRank, "", parseDurationMillis("10m", 600_000L))));
    }

    private int parseRankLevel(String key, int fallback) {
        try {
            return Integer.parseInt(key.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseTicks(String value, long fallbackTicks) {
        long millis = parseDurationMillis(value, fallbackTicks * 50L);
        if (millis < 0) {
            return fallbackTicks;
        }
        return Math.max(1L, millis / 50L);
    }

    private long parseDurationMillis(String value, long fallbackMillis) {
        if (value == null || value.isBlank()) {
            return fallbackMillis;
        }

        String input = value.trim().toLowerCase();
        if (input.equals("never") || input.equals("none") || input.equals("off") || input.equals("false")
                || input.equals("disable") || input.equals("disabled") || input.equals("khong")
                || input.equals("không") || input.equals("-1")) {
            return -1L;
        }

        if (input.matches("\\d+(?:\\.\\d+)?")) {
            return (long) (Double.parseDouble(input) * 1000L);
        }

        long total = 0L;
        Matcher matcher = DURATION_PART.matcher(input);
        while (matcher.find()) {
            double amount = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            total += (long) (amount * unitToMillis(unit));
        }
        return total > 0L ? total : fallbackMillis;
    }

    private long unitToMillis(String unit) {
        return switch (unit) {
            case "ms", "milli", "millis", "millisecond", "milliseconds" -> 1L;
            case "s", "sec", "secs", "second", "seconds", "giay", "giây" -> 1000L;
            case "m", "min", "mins", "minute", "minutes", "phut", "phút" -> 60_000L;
            case "h", "hr", "hrs", "hour", "hours", "gio", "giờ" -> 3_600_000L;
            case "d", "day", "days", "ngay", "ngày" -> 86_400_000L;
            default -> 1000L;
        };
    }

    private String formatDuration(long millis) {
        if (millis < 0) {
            return "không giới hạn";
        }
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) {
            return String.format("%d giờ %02d phút %02d giây", hours, minutes, secs);
        }
        if (minutes > 0) {
            return String.format("%d phút %02d giây", minutes, secs);
        }
        return secs + " giây";
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    private void injectDefaults() {
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;
        boolean missingAfkStartAfter = !config.contains("afk-kick.afk-start-after");
        changed |= setDefault(config, "afk-kick.enabled", true);
        changed |= setDefault(config, "afk-kick.permission-prefix", "tutiencore.vip.");
        changed |= setDefault(config, "afk-kick.bypass-permission", "tutiencore.afk.bypass");
        changed |= setDefault(config, "afk-kick.default-rank", 0);
        changed |= setDefault(config, "afk-kick.check-interval", "1s");
        changed |= setDefault(config, "afk-kick.afk-start-after", "10s");
        if (missingAfkStartAfter && "10s".equalsIgnoreCase(config.getString("afk-kick.check-interval", ""))) {
            config.set("afk-kick.check-interval", "1s");
            changed = true;
        }
        changed |= setDefault(config, "afk-kick.kick-message",
                "&cBạn đã AFK quá lâu. Hãy quay lại khi sẵn sàng tiếp tục tu luyện.");
        changed |= setDefault(config, "afk-kick.title.enabled", true);
        changed |= setDefault(config, "afk-kick.title.title", "&6&lĐang AFK");
        changed |= setDefault(config, "afk-kick.title.subtitle", "&eCòn &c%time% &enữa sẽ bị rời khỏi máy chủ.");
        changed |= setDefault(config, "afk-kick.title.fade-in", 5);
        changed |= setDefault(config, "afk-kick.title.stay", 45);
        changed |= setDefault(config, "afk-kick.title.fade-out", 10);
        changed |= setDefault(config, "afk-kick.title.repeat-interval", "10s");
        changed |= setDefault(config, "afk-kick.warning.enabled", true);
        changed |= setDefault(config, "afk-kick.warning.before-kick", "30s");
        changed |= setDefault(config, "afk-kick.warning.message",
                "&eBạn đang AFK quá lâu. Còn &c%time% &enữa sẽ bị rời khỏi máy chủ.");
        changed |= setDefault(config, "afk-kick.activity.ignore-look-only", true);
        changed |= setDefault(config, "afk-kick.activity.movement-threshold", 0.08);
        changed |= setDefault(config, "afk-kick.ranks.0.permission", "");
        changed |= setDefault(config, "afk-kick.ranks.0.kick-after", "10m");
        changed |= setDefault(config, "afk-kick.ranks.1.permission", "tutiencore.vip.1");
        changed |= setDefault(config, "afk-kick.ranks.1.kick-after", "30m");
        changed |= setDefault(config, "afk-kick.ranks.2.permission", "tutiencore.vip.2");
        changed |= setDefault(config, "afk-kick.ranks.2.kick-after", "1h");
        changed |= setDefault(config, "afk-kick.ranks.3.permission", "tutiencore.vip.3");
        changed |= setDefault(config, "afk-kick.ranks.3.kick-after", "2h");
        changed |= setDefault(config, "afk-kick.ranks.4.permission", "tutiencore.vip.4");
        changed |= setDefault(config, "afk-kick.ranks.4.kick-after", "never");
        changed |= setDefault(config, "afk-kick.ranks.5.permission", "tutiencore.vip.5");
        changed |= setDefault(config, "afk-kick.ranks.5.kick-after", "never");
        if (changed) {
            plugin.saveConfig();
        }
    }

    private boolean setDefault(FileConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private record AfkRank(int level, String permission, long kickAfterMillis) {
    }
}
