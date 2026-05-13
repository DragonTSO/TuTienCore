package com.turtle.tutiencore.core.hook;

import java.util.Locale;

public final class MMOCoreActionBarFilter {

    private MMOCoreActionBarFilter() {
    }

    public static boolean looksLikeMMOCoreHud(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalize(text);
        boolean hasHealth = normalized.contains("hp") || normalized.contains("health") || normalized.contains("❤");
        boolean hasMana = normalized.contains("mana") || normalized.contains("✦") || normalized.contains("✧");
        boolean hasDefense = normalized.contains("def") || normalized.contains("defense") || normalized.contains("⛨") || normalized.contains("🛡");

        return (hasHealth && hasMana) || (hasHealth && hasDefense) || (hasMana && hasDefense);
    }

    private static String normalize(String text) {
        return text.replaceAll("§.", "")
                .replaceAll("&.", "")
                .toLowerCase(Locale.ROOT);
    }
}
