package com.turtle.tutiencore.core.infusion;

import java.util.Collections;
import java.util.Map;

public record InfusionType(String id, String displayName, String material, double weight, Map<String, Double> stats) {

    public InfusionType {
        stats = stats == null ? Collections.emptyMap() : Collections.unmodifiableMap(stats);
    }
}
