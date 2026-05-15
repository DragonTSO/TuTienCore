package com.turtle.tutiencore.core.hook;

import io.lumine.mythic.lib.api.item.NBTItem;

import net.Indyuce.mmoitems.api.item.build.LoreBuilder;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.StringStat;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.math.BigDecimal;

final class MMOItemsMaxHealthPercentStat extends StringStat {

    static final String STAT_ID = "MAX_HEALTH_PERCENT";
    private static final String DEFAULT_STAT_FORMAT =
            ChatColor.GRAY + "Max Health: " + ChatColor.GREEN + "+{value}%";

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
        this.generalStatFormat = DEFAULT_STAT_FORMAT;
    }

    @Override
    public void whenApplied(net.Indyuce.mmoitems.api.item.build.ItemStackBuilder item, StringData data) {
        double value = parsePercent(data.toString());
        String normalized = formatPercent(value);

        item.addItemTag(getAppliedNBT(new StringData(normalized)));
        applyLore(item.getLore(), normalized);
    }

    double readPercent(NBTItem item) {
        return parsePercent(item.getString(getNBTPath()));
    }

    private void applyLore(LoreBuilder lore, String value) {
        String formatted = getGeneralStatFormat().replace("{value}", value);
        String marker = "#" + getPath() + "#";

        if (lore.getLore().contains(marker)) {
            lore.insert(getPath(), formatted);
            return;
        }

        lore.getLore().add(formatted);
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

    private static String formatPercent(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
