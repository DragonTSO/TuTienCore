package com.turtle.tutiencore.core.hook;

import io.lumine.mythic.lib.api.item.NBTItem;

import net.Indyuce.mmoitems.stat.type.DoubleStat;

import org.bukkit.ChatColor;
import org.bukkit.Material;

final class MMOItemsMaxHealthPercentStat extends DoubleStat {

    static final String STAT_ID = "MAX_HEALTH_PERCENT";

    MMOItemsMaxHealthPercentStat() {
        super(
                STAT_ID,
                Material.GOLDEN_APPLE,
                "Max Health Percent",
                new String[]{
                        "Increases MythicLib MAX_HEALTH by percent.",
                        "Example: 10 = +10% max health."
                },
                new String[]{"!block", "all"}
        );
    }

    double readPercent(NBTItem item) {
        if (item == null || !item.hasTag(getNBTPath())) {
            return 0;
        }

        double numericValue = Math.max(0, item.getDouble(getNBTPath()));
        return numericValue > 0 ? numericValue : parsePercent(item.getString(getNBTPath()));
    }

    private static double parsePercent(String raw) {
        if (raw == null) {
            return 0;
        }

        String normalized = ChatColor.stripColor(raw)
                .trim()
                .replace("%", "")
                .replace(",", ".");

        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return 0;
        }

        try {
            return Math.max(0, Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
