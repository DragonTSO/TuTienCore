package com.turtleisland.api;

public final class TurtleIslandProvider {

    private static Object api;

    private TurtleIslandProvider() {
    }

    public static void setApi(Object api) {
        TurtleIslandProvider.api = api;
    }

    public static Object get() {
        if (api == null) {
            throw new IllegalStateException("API not registered");
        }
        return api;
    }
}
