package com.turtle.tutiencore;

import com.turtle.tutiencore.core.TuTienCore;
import org.bukkit.plugin.java.JavaPlugin;

public class TuTienCorePlugin extends JavaPlugin {

    private TuTienCore core;

    @Override
    public void onEnable() {
        if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().severe("ProtocolLib not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        core = new TuTienCore(this);
        core.onEnable();
        getLogger().info("TuTienCore enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.onDisable();
        }
        getLogger().info("TuTienCore disabled successfully.");
    }
}
