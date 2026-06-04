package com.turtle.tutiencore.core.infusion;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record InfusionType(String id, String displayName, String material, double weight, Map<String, Double> stats,
                           double tuViBonusPercent, TuluyenDropConfig tuluyenDrops) {

    public InfusionType(String id, String displayName, String material, double weight, Map<String, Double> stats) {
        this(id, displayName, material, weight, stats, 0.0D, TuluyenDropConfig.disabled());
    }

    public InfusionType(String id, String displayName, String material, double weight, Map<String, Double> stats,
                        TuluyenDropConfig tuluyenDrops) {
        this(id, displayName, material, weight, stats, 0.0D, tuluyenDrops);
    }

    public InfusionType {
        stats = stats == null ? Collections.emptyMap() : Collections.unmodifiableMap(stats);
        tuViBonusPercent = Math.max(0.0D, tuViBonusPercent);
        tuluyenDrops = tuluyenDrops == null ? TuluyenDropConfig.disabled() : tuluyenDrops;
    }

    public record TuluyenDropConfig(boolean enabled, int rollsPerInterval, boolean requireTurtleIslandBonus,
                                    List<TuluyenDropItem> items) {

        public TuluyenDropConfig {
            rollsPerInterval = Math.max(1, rollsPerInterval);
            items = items == null ? List.of() : List.copyOf(items);
        }

        public static TuluyenDropConfig disabled() {
            return new TuluyenDropConfig(false, 1, true, List.of());
        }
    }

    public record TuluyenDropItem(String type, String mmoType, String id, double chance, int amount) {

        public TuluyenDropItem {
            type = type == null || type.isBlank() ? "MMOITEMS" : type.trim();
            mmoType = mmoType == null ? "" : mmoType.trim();
            id = id == null ? "" : id.trim();
            chance = Math.max(0.0D, Math.min(100.0D, chance));
            amount = Math.max(1, amount);
        }
    }
}
