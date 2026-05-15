package com.turtle.tutiencore.core.hook;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MMOItemsMMOCoreStatsHook implements Listener {

    private final JavaPlugin plugin;
    private final List<ItemStat<?, ?>> stats = List.of(
            new DoubleStat(
                    "HEALTH_REGENERATION",
                    Material.BREAD,
                    "Health Regeneration",
                    new String[]{"Amount of health regenerated every second."}
            ),
            new DoubleStat(
                    "MAX_HEALTH_REGENERATION",
                    Material.BREAD,
                    "Max Health Regeneration",
                    new String[]{"Percentage of max health regenerated every second."}
            )
    );
    private boolean initialized;

    public MMOItemsMMOCoreStatsHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        initialize();
        Bukkit.getScheduler().runTask(plugin, this::initialize);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 100L);
    }

    @EventHandler
    public void onMMOItemsReload(MMOItemsReloadEvent event) {
        registerStats();
    }

    private void initialize() {
        if (initialized || !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }

        try {
            registerStats();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            initialized = true;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not register MMOItems MMOCore compatibility stats: "
                    + exception.getMessage());
        }
    }

    private void registerStats() {
        int registered = 0;
        for (ItemStat<?, ?> stat : stats) {
            if (MMOItems.plugin.getStats().get(stat.getId()) != null) {
                continue;
            }

            MMOItemsStatRegistry.registerOrReplace(stat);
            registered++;
        }

        if (registered > 0) {
            plugin.getLogger().info("Registered " + registered + " MMOItems MMOCore compatibility stat(s).");
        }
    }
}
