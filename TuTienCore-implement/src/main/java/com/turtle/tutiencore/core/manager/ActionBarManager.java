package com.turtle.tutiencore.core.manager;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionBarManager implements Listener {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final String TUTIEN_LEVEL_EXP_EVENT_CLASS = "com.turtle.tutienlevel.api.event.TuTienLevelExpGainEvent";

    private final JavaPlugin plugin;
    private final Map<UUID, ExpGainToast> expGainToasts = new HashMap<>();
    private BukkitTask task;
    private boolean expGainEnabled;
    private String expGainFormat;
    private long expGainDurationTicks;
    private List<String> expGainSourcePrefixes;
    private boolean expGainListenerRegistered;

    public ActionBarManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        reloadSettings();
        registerTuTienLevelExpListener();
        if (!plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            return;
        }

        long ticksToUpdate = Math.max(1L, plugin.getConfig().getLong("action-bar.ticks-to-update", 5L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sendActionBars, 1L, ticksToUpdate);
    }

    public void reload() {
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void sendActionBars() {
        String format = plugin.getConfig().getString("action-bar.format", "");
        if (format.isEmpty()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldSuppressCinematicUi(player)) {
                continue;
            }
            String text = applyBuiltInPlaceholders(format, player.getHealth(), player.getMaxHealth());
            text = text + renderExpGain(player);
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(text)));
        }
    }

    private void reloadSettings() {
        expGainEnabled = plugin.getConfig().getBoolean("action-bar.exp-gain.enabled", true);
        expGainFormat = plugin.getConfig().getString("action-bar.exp-gain.format", " &8| &a+{exp_formatted} EXP");
        expGainDurationTicks = Math.max(1L, plugin.getConfig().getLong("action-bar.exp-gain.duration-ticks", 40L));
        if (plugin.getConfig().isList("action-bar.exp-gain.source-prefixes")) {
            expGainSourcePrefixes = plugin.getConfig().getStringList("action-bar.exp-gain.source-prefixes");
        } else {
            expGainSourcePrefixes = List.of("MythicMob:");
        }
    }

    private void registerTuTienLevelExpListener() {
        if (expGainListenerRegistered) {
            return;
        }

        try {
            Class<?> eventType = Class.forName(TUTIEN_LEVEL_EXP_EVENT_CLASS);
            if (!Event.class.isAssignableFrom(eventType)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> bukkitEventType = (Class<? extends Event>) eventType;
            EventExecutor executor = (listener, event) -> handleTuTienLevelExpGain(event);
            Bukkit.getPluginManager().registerEvent(bukkitEventType, this, EventPriority.MONITOR, executor, plugin, true);
            expGainListenerRegistered = true;
        } catch (ClassNotFoundException ignored) {
            // TuTienLevel is optional; the action bar just skips EXP popups when it is not installed.
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[ActionBar] Could not hook TuTienLevel EXP event: " + exception.getMessage());
        }
    }

    private void handleTuTienLevelExpGain(Event event) {
        if (!expGainEnabled || event == null || event instanceof Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }

        Player player = invokePlayer(event, "getPlayer");
        long amount = invokeLong(event, "getAmount");
        String source = invokeString(event, "getSource");
        if (player == null || amount <= 0L || !isSourceAllowed(source, expGainSourcePrefixes)) {
            return;
        }

        long expiresAtMillis = System.currentTimeMillis() + (expGainDurationTicks * 50L);
        ExpGainToast current = expGainToasts.get(player.getUniqueId());
        if (current != null && current.expiresAtMillis() > System.currentTimeMillis()) {
            amount += current.amount();
        }
        expGainToasts.put(player.getUniqueId(), new ExpGainToast(amount, source, expiresAtMillis));
    }

    private String renderExpGain(Player player) {
        if (!expGainEnabled || player == null || expGainFormat == null || expGainFormat.isBlank()) {
            return "";
        }

        ExpGainToast toast = expGainToasts.get(player.getUniqueId());
        if (toast == null) {
            return "";
        }
        if (toast.expiresAtMillis() <= System.currentTimeMillis()) {
            expGainToasts.remove(player.getUniqueId());
            return "";
        }
        return applyExpGainPlaceholders(expGainFormat, toast.amount(), toast.source());
    }

    private static Player invokePlayer(Event event, String methodName) {
        Object value = invoke(event, methodName);
        return value instanceof Player player ? player : null;
    }

    private static long invokeLong(Event event, String methodName) {
        Object value = invoke(event, methodName);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String invokeString(Event event, String methodName) {
        Object value = invoke(event, methodName);
        return value != null ? String.valueOf(value) : "";
    }

    private static Object invoke(Event event, String methodName) {
        try {
            Method method = event.getClass().getMethod(methodName);
            return method.invoke(event);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static boolean shouldSuppressCinematicUi(Player player) {
        return player != null && player.getGameMode() == GameMode.SPECTATOR;
    }

    static String applyBuiltInPlaceholders(String text, double health, double maxHealth) {
        return text
                .replace("{health}", formatNumber(health))
                .replace("{max_health}", formatNumber(maxHealth));
    }

    static String applyExpGainPlaceholders(String text, long amount, String source) {
        String safeSource = source == null ? "" : source;
        return text
                .replace("{exp}", String.valueOf(amount))
                .replace("{exp_formatted}", formatWholeNumber(amount))
                .replace("{source}", safeSource)
                .replace("{mob}", extractMobName(safeSource));
    }

    static boolean isSourceAllowed(String source, List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return true;
        }
        String safeSource = source == null ? "" : source;
        for (String prefix : prefixes) {
            if (prefix != null && safeSource.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
        }
        return false;
    }

    private static String colorize(String text) {
        Matcher matcher = HEX_COLOR_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private static String formatNumber(double value) {
        return String.valueOf((int) Math.round(value));
    }

    private static String formatWholeNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String extractMobName(String source) {
        int separator = source.indexOf(':');
        if (separator < 0 || separator + 1 >= source.length()) {
            return source;
        }
        return source.substring(separator + 1);
    }

    private record ExpGainToast(long amount, String source, long expiresAtMillis) {
    }
}
