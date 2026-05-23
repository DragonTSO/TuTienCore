package com.turtle.tutiencore.core.manager;

final class InfusionStatCalculator {

    private InfusionStatCalculator() {
    }

    static int calculateAtkDefGain(int baseValue, int enhancementLevel) {
        if (enhancementLevel >= 15) {
            return baseValue * 3;
        }

        double multiplier = 1.0D;
        int firstBand = Math.min(enhancementLevel, 5);
        int secondBand = Math.min(Math.max(enhancementLevel - 5, 0), 5);
        int thirdBand = Math.min(Math.max(enhancementLevel - 10, 0), 5);
        multiplier += firstBand * 0.10D;
        multiplier += secondBand * 0.15D;
        multiplier += thirdBand * 0.25D;
        return (int) Math.floor(baseValue * multiplier);
    }
}
