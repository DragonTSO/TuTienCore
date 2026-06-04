package com.turtle.tutiencore.core.infusion;

public record InfusionRarity(String id, String displayName, String color, double weight, double multiplier,
                             double tuViBonusPercent) {

    public InfusionRarity {
        tuViBonusPercent = Math.max(0.0D, tuViBonusPercent);
    }
}
