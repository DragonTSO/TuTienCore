package com.turtle.tutiencore.core.config;

public record TuViPointRange(int min, int max) {

    public static TuViPointRange parse(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fixed(fallback);
        }

        String[] parts = value.trim().split("-", 2);
        try {
            if (parts.length == 1) {
                return fixed(Integer.parseInt(parts[0].trim()));
            }

            return fixed(Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ignored) {
            return fixed(fallback);
        }
    }

    public int roll() {
        return min;
    }

    private static TuViPointRange fixed(int value) {
        return new TuViPointRange(value, value);
    }
}
