package com.turtle.tutiencore.core.manager;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionBarManager {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private final JavaPlugin plugin;
    private BukkitTask task;

    public ActionBarManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
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
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(text)));
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
}
