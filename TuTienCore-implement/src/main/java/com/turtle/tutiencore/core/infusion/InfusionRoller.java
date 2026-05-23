package com.turtle.tutiencore.core.infusion;

import java.util.List;
import java.util.Random;

public final class InfusionRoller {

    private static final Random RANDOM = new Random();

    private InfusionRoller() {
    }

    public static WeightedInfusionOption selectWeighted(List<WeightedInfusionOption> options) {
        double total = options.stream()
                .mapToDouble(WeightedInfusionOption::weight)
                .filter(weight -> weight > 0)
                .sum();
        return selectWeighted(options, RANDOM.nextDouble(total));
    }

    static WeightedInfusionOption selectWeighted(List<WeightedInfusionOption> options, double roll) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options cannot be empty");
        }

        double current = 0;
        WeightedInfusionOption fallback = options.get(options.size() - 1);
        for (WeightedInfusionOption option : options) {
            if (option.weight() <= 0) {
                continue;
            }

            current += option.weight();
            if (roll < current) {
                return option;
            }
            fallback = option;
        }

        return fallback;
    }
}
