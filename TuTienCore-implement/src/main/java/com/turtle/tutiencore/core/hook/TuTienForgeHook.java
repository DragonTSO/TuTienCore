package com.turtle.tutiencore.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Soft hook into the TuTienForge plugin to read the Tu Vi cultivation bonus (%)
 * granted by its currently active "virtual weather".
 * <p>
 * TuTienForge depends on TuTienCore (not the other way around), so this hook uses
 * reflection against {@code org.hoangson.tuTienForge.api.TuTienForgeAPI} and degrades
 * gracefully (returns 0.0) when the plugin is missing or the API method is unavailable.
 */
public final class TuTienForgeHook {

    private static final String PLUGIN_NAME = "TuTienForge";
    private static final String API_CLASS = "org.hoangson.tuTienForge.api.TuTienForgeAPI";
    private static final String METHOD_NAME = "getActiveWeatherTuViBonusPercent";

    private Method cachedMethod;
    private boolean lookupFailed;

    /**
     * The server-wide Tu Vi cultivation bonus percent provided by the active virtual weather.
     * Returns 0.0 when TuTienForge is absent, disabled, or no weather is active.
     */
    public double getActiveWeatherTuViBonusPercent() {
        Plugin forge = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (forge == null || !forge.isEnabled()) {
            return 0.0;
        }

        Method method = resolveMethod(forge.getClass().getClassLoader());
        if (method == null) {
            return 0.0;
        }

        try {
            Object value = method.invoke(null);
            if (value instanceof Number number) {
                return Math.max(0.0, number.doubleValue());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Leave as 0.0 on any failure.
        }
        return 0.0;
    }

    private Method resolveMethod(ClassLoader classLoader) {
        if (cachedMethod != null) {
            return cachedMethod;
        }
        if (lookupFailed || classLoader == null) {
            return null;
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS, false, classLoader);
            cachedMethod = apiClass.getMethod(METHOD_NAME);
            return cachedMethod;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
            lookupFailed = true;
            return null;
        }
    }
}
