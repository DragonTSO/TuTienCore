package com.turtle.tutiencore.core.manager;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KillRewardHologramManager {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,###");

    private final JavaPlugin plugin;
    private final Set<TextDisplay> displays = ConcurrentHashMap.newKeySet();

    public KillRewardHologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void showMoney(Location location, Player player, long baseMoney, long finalMoney, long bonusMoney, String mobId) {
        if (location == null || location.getWorld() == null || player == null || finalMoney <= 0L) {
            return;
        }
        if (!plugin.getConfig().getBoolean("kill-reward-hologram.enabled", true)
                || !plugin.getConfig().getBoolean("kill-reward-hologram.money.enabled", true)) {
            return;
        }

        String text = plugin.getConfig().getString("kill-reward-hologram.money.text", "&6+%money% Linh Thach");
        if (text == null || text.isBlank()) {
            return;
        }

        double bonusPercent = baseMoney <= 0L ? 0.0D : (double) bonusMoney * 100.0D / (double) baseMoney;
        String rendered = applyMoneyPlaceholders(text, player, baseMoney, finalMoney, bonusMoney, bonusPercent, mobId);
        spawn(location, rendered);
    }

    public void removeAll() {
        for (TextDisplay display : new HashSet<>(displays)) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
    }

    private void spawn(Location location, String text) {
        int durationTicks = Math.max(1, plugin.getConfig().getInt("kill-reward-hologram.duration-ticks", 35));
        double yOffset = plugin.getConfig().getDouble("kill-reward-hologram.y-offset", 1.35D);
        double rise = plugin.getConfig().getDouble("kill-reward-hologram.rise", 0.8D);
        double xOffset = plugin.getConfig().getDouble("kill-reward-hologram.x-offset", 0.0D);
        double zOffset = plugin.getConfig().getDouble("kill-reward-hologram.z-offset", 0.0D);
        double randomRadius = Math.max(0.0D, plugin.getConfig().getDouble("kill-reward-hologram.random-xz-radius", 0.25D));
        byte startOpacity = parseOpacity(plugin.getConfig().getInt("kill-reward-hologram.start-opacity", 255));
        byte endOpacity = parseOpacity(plugin.getConfig().getInt("kill-reward-hologram.end-opacity", 0));
        boolean shadow = plugin.getConfig().getBoolean("kill-reward-hologram.shadow", true);
        boolean seeThrough = plugin.getConfig().getBoolean("kill-reward-hologram.see-through", true);
        int lineWidth = Math.max(1, plugin.getConfig().getInt("kill-reward-hologram.line-width", 160));

        double randomX = randomRadius <= 0.0D ? 0.0D : (Math.random() - 0.5D) * randomRadius * 2.0D;
        double randomZ = randomRadius <= 0.0D ? 0.0D : (Math.random() - 0.5D) * randomRadius * 2.0D;
        Location start = location.clone().add(xOffset + randomX, yOffset, zOffset + randomZ);

        TextDisplay display = location.getWorld().spawn(start, TextDisplay.class, hologram -> {
            hologram.setText(colorize(text));
            hologram.setBillboard(Display.Billboard.CENTER);
            hologram.setShadowed(shadow);
            hologram.setSeeThrough(seeThrough);
            hologram.setTextOpacity(startOpacity);
            hologram.setLineWidth(lineWidth);
            hologram.setGravity(false);
            hologram.setPersistent(false);
            hologram.setInvulnerable(true);
        });
        displays.add(display);

        // Animation step interval. The hologram only rises + fades, so updating every other tick
        // (default) is visually indistinguishable but halves the per-tick wake-ups when many kill
        // popups overlap (mob farms). Progress still advances by `interval` ticks so the popup
        // finishes after the same `durationTicks` wall-clock duration regardless of the interval.
        int interval = Math.max(1, plugin.getConfig().getInt("kill-reward-hologram.animation-interval-ticks", 2));
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (display.isDead() || !display.isValid()) {
                    displays.remove(display);
                    cancel();
                    return;
                }

                tick += interval;
                double progress = Math.min(1.0D, (double) tick / durationTicks);
                display.teleport(start.clone().add(0.0D, rise * progress, 0.0D));
                display.setTextOpacity(interpolateOpacity(startOpacity, endOpacity, progress));

                if (tick >= durationTicks) {
                    displays.remove(display);
                    display.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private String applyMoneyPlaceholders(String text, Player player, long baseMoney, long finalMoney,
                                          long bonusMoney, double bonusPercent, String mobId) {
        String mob = mobId == null ? "" : mobId;
        return text
                .replace("%player%", player.getName())
                .replace("{player}", player.getName())
                .replace("%mob%", mob)
                .replace("{mob}", mob)
                .replace("%money%", String.valueOf(finalMoney))
                .replace("{money}", String.valueOf(finalMoney))
                .replace("%money_formatted%", formatInteger(finalMoney))
                .replace("{money_formatted}", formatInteger(finalMoney))
                .replace("%base_money%", String.valueOf(baseMoney))
                .replace("{base_money}", String.valueOf(baseMoney))
                .replace("%base_money_formatted%", formatInteger(baseMoney))
                .replace("{base_money_formatted}", formatInteger(baseMoney))
                .replace("%bonus_money%", String.valueOf(bonusMoney))
                .replace("{bonus_money}", String.valueOf(bonusMoney))
                .replace("%bonus_money_formatted%", formatInteger(bonusMoney))
                .replace("{bonus_money_formatted}", formatInteger(bonusMoney))
                .replace("%bonus_percent%", formatDecimal(bonusPercent))
                .replace("{bonus_percent}", formatDecimal(bonusPercent));
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', translateHexColors(text == null ? "" : text));
    }

    private static String translateHexColors(String text) {
        return translateHexPattern(translateHexPattern(text, MINI_HEX_PATTERN), HEX_PATTERN);
    }

    private static String translateHexPattern(String text, Pattern pattern) {
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

    private static byte parseOpacity(int value) {
        return (byte) Math.max(0, Math.min(255, value));
    }

    private static byte interpolateOpacity(byte start, byte end, double progress) {
        int startValue = Byte.toUnsignedInt(start);
        int endValue = Byte.toUnsignedInt(end);
        int value = (int) Math.round(startValue + ((endValue - startValue) * progress));
        return parseOpacity(value);
    }

    private static String formatInteger(long value) {
        return INTEGER_FORMAT.format(value);
    }

    private static String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

}
