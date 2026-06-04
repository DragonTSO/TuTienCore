package com.turtle.tutiencore.core.hook;

import io.lumine.mythic.bukkit.events.MythicMobLootDropEvent;

import com.turtle.tutiencore.core.manager.ActionBarManager;
import com.turtle.tutiencore.core.manager.EquipmentMenuManager;
import com.turtle.tutiencore.core.manager.KillRewardHologramManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public final class MythicMobsMoneyBonusHook implements Listener {

    private static final String DEFAULT_PERMISSION_PREFIX = "tutiencore.mythicmoney.bonus.";

    private final JavaPlugin plugin;
    private final ActionBarManager actionBarManager;
    private final KillRewardHologramManager killRewardHologramManager;
    private final EquipmentMenuManager equipmentMenuManager;
    private boolean registered;

    public MythicMobsMoneyBonusHook(JavaPlugin plugin, ActionBarManager actionBarManager,
                                    KillRewardHologramManager killRewardHologramManager,
                                    EquipmentMenuManager equipmentMenuManager) {
        this.plugin = plugin;
        this.actionBarManager = actionBarManager;
        this.killRewardHologramManager = killRewardHologramManager;
        this.equipmentMenuManager = equipmentMenuManager;
    }

    public void register() {
        if (registered) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            plugin.getLogger().info("MythicMobs not found; Mythic money bonus hook skipped.");
            return;
        }

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
            plugin.getLogger().info("Registered MythicMobs money bonus hook.");
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not register MythicMobs money bonus hook: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMythicMobLootDrop(MythicMobLootDropEvent event) {
        if (!plugin.getConfig().getBoolean("mythicmobs-money-bonus.enabled", true)) {
            return;
        }

        int baseMoney = event.getMoney();
        if (baseMoney <= 0) {
            return;
        }

        LivingEntity killer = event.getKiller();
        if (!(killer instanceof Player player)) {
            return;
        }

        String prefix = plugin.getConfig().getString("mythicmobs-money-bonus.permission-prefix", DEFAULT_PERMISSION_PREFIX);
        boolean stack = plugin.getConfig().getBoolean("mythicmobs-money-bonus.stack", true);
        double bonusPercent = resolveMoneyBonus(player.getEffectivePermissions(), prefix, stack) + getEquipmentMoneyBonus(player);
        int bonusMoney = calculateBonusMoney(baseMoney, bonusPercent);
        int finalMoney = Math.max(0, baseMoney + bonusMoney);
        if (bonusMoney > 0) {
            event.setMoney(finalMoney);
        }

        String mobId = event.getMobType() == null ? "unknown" : event.getMobType().getInternalName();
        if (actionBarManager != null) {
            actionBarManager.showMoneyGain(player, baseMoney, finalMoney, "MythicMob:" + mobId);
        }
        if (killRewardHologramManager != null) {
            killRewardHologramManager.showMoney(getDeathLocation(event), player, baseMoney, finalMoney, bonusMoney, mobId);
        }

        if (plugin.getConfig().getBoolean("mythicmobs-money-bonus.debug", false)) {
            plugin.getLogger().info("Mythic money bonus: " + player.getName()
                    + " killed " + mobId
                    + ", base=" + baseMoney
                    + ", bonus=" + bonusMoney
                    + " (" + bonusPercent + "%)");
        }
    }

    private double getEquipmentMoneyBonus(Player player) {
        if (equipmentMenuManager == null || player == null) {
            return 0.0D;
        }
        return Math.max(0.0D, equipmentMenuManager.getEquippedSystemStatBonus(player, EquipmentMenuManager.DAN_DUOC_MYTHIC_MONEY_BONUS_STAT));
    }

    private Location getDeathLocation(MythicMobLootDropEvent event) {
        if (event.getEntity() != null) {
            return event.getEntity().getLocation().clone();
        }
        return null;
    }

    static double resolveMoneyBonus(Collection<PermissionAttachmentInfo> permissions, String prefix, boolean stack) {
        double totalBonus = 0.0;
        double highestBonus = 0.0;
        String actualPrefix = prefix == null || prefix.isBlank() ? DEFAULT_PERMISSION_PREFIX : prefix;

        for (PermissionAttachmentInfo permission : permissions) {
            if (permission == null || !permission.getValue()) {
                continue;
            }

            double value = parseMoneyBonusPermission(permission.getPermission(), actualPrefix);
            if (stack) {
                totalBonus += value;
            } else {
                highestBonus = Math.max(highestBonus, value);
            }
        }

        return stack ? totalBonus : highestBonus;
    }

    static double parseMoneyBonusPermission(String permission, String prefix) {
        String actualPrefix = prefix == null || prefix.isBlank() ? DEFAULT_PERMISSION_PREFIX : prefix;
        if (permission == null || !permission.startsWith(actualPrefix)) {
            return 0.0;
        }

        try {
            return Math.max(0.0, Double.parseDouble(permission.substring(actualPrefix.length())));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    static int calculateBonusMoney(int baseMoney, double bonusPercent) {
        if (baseMoney <= 0 || bonusPercent <= 0.0) {
            return 0;
        }
        return (int) Math.round(baseMoney * bonusPercent / 100.0);
    }
}
