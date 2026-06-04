package com.turtle.tutiencore.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.OptionalDouble;

public final class TurtleIslandHook {

    private static final String PLUGIN_NAME = "TurtleIsland";
    private static final String PROVIDER_CLASS = "com.turtleisland.api.TurtleIslandProvider";

    public double getCultivationBonusPercent(Player player) {
        if (player == null) {
            return 0.0;
        }

        Plugin turtleIsland = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (turtleIsland == null || !turtleIsland.isEnabled()) {
            return 0.0;
        }

        OptionalDouble providerBonus = findProviderBonusPercent(player, turtleIsland.getClass().getClassLoader());
        if (providerBonus.isPresent()) {
            return providerBonus.getAsDouble();
        }

        return readApiBonusPercent(turtleIsland, player);
    }

    public boolean canReceiveCultivationBonus(Player player) {
        if (player == null) {
            return false;
        }

        Plugin turtleIsland = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (turtleIsland == null || !turtleIsland.isEnabled()) {
            return false;
        }

        Boolean providerAllowed = findProviderCanReceive(player, turtleIsland.getClass().getClassLoader());
        if (providerAllowed != null) {
            return providerAllowed;
        }

        return readApiBonusPercent(turtleIsland, player) > 0.0;
    }

    static double readProviderBonusPercent(Player player, ClassLoader classLoader) {
        return findProviderBonusPercent(player, classLoader).orElse(0.0);
    }

    static boolean readProviderCanReceive(Player player, ClassLoader classLoader) {
        Boolean allowed = findProviderCanReceive(player, classLoader);
        return allowed != null && allowed;
    }

    private static OptionalDouble findProviderBonusPercent(Player player, ClassLoader classLoader) {
        if (player == null || classLoader == null) {
            return OptionalDouble.empty();
        }

        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS, false, classLoader);
            Object api = providerClass.getMethod("get").invoke(null);
            if (api == null) {
                return OptionalDouble.empty();
            }
            Boolean allowed = readApiCanReceive(api, player);
            if (allowed != null && !allowed) {
                return OptionalDouble.of(0.0);
            }
            return OptionalDouble.of(readApiBonusPercent(api, player));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | LinkageError ignored) {
            return OptionalDouble.empty();
        }
    }

    private static Boolean findProviderCanReceive(Player player, ClassLoader classLoader) {
        if (player == null || classLoader == null) {
            return null;
        }

        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS, false, classLoader);
            Object api = providerClass.getMethod("get").invoke(null);
            if (api == null) {
                return null;
            }
            Boolean allowed = readApiCanReceive(api, player);
            if (allowed != null) {
                return allowed;
            }
            return readApiBonusPercent(api, player) > 0.0;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | LinkageError ignored) {
            return null;
        }
    }

    private static Boolean readApiCanReceive(Object api, Player player)
            throws IllegalAccessException, InvocationTargetException {
        try {
            Object allowed = api.getClass()
                    .getMethod("canReceiveIslandCultivationBonus", Player.class)
                    .invoke(api, player);
            return allowed instanceof Boolean value ? value : false;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static double readApiBonusPercent(Object api, Player player) {
        try {
            Object bonus = api.getClass()
                    .getMethod("getCultivationTuViBonusPercent", Player.class)
                    .invoke(api, player);
            if (bonus instanceof Number number) {
                return Math.max(0.0, number.doubleValue());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0.0;
        }

        return 0.0;
    }
}
