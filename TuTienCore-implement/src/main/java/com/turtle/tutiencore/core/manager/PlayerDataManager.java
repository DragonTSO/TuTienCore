package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTienAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerDataManager implements Listener, TuTienAPI {
    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;
    private final Map<UUID, Double> tuviCache = new HashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Load online players (in case of plugin reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "players.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create players.yml!");
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveAll() {
        for (Map.Entry<UUID, Double> entry : tuviCache.entrySet()) {
            config.set(entry.getKey().toString() + ".tuvi", entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void savePlayer(UUID uuid) {
        if (tuviCache.containsKey(uuid)) {
            config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadPlayer(UUID uuid) {
        double tuvi = config.getDouble(uuid.toString() + ".tuvi", 0.0);
        tuviCache.put(uuid, tuvi);
    }

    @Override
    public double getTuVi(UUID uuid) {
        return tuviCache.getOrDefault(uuid, 0.0);
    }

    @Override
    public void setTuVi(UUID uuid, double amount) {
        tuviCache.put(uuid, amount);
    }

    @Override
    public void addTuVi(UUID uuid, double amount) {
        setTuVi(uuid, getTuVi(uuid) + amount);
    }

    @Override
    public void takeTuVi(UUID uuid, double amount) {
        double current = getTuVi(uuid);
        if (current - amount < 0) {
            setTuVi(uuid, 0);
        } else {
            setTuVi(uuid, current - amount);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Automatically save names to config for the top system
        config.set(event.getPlayer().getUniqueId().toString() + ".name", event.getPlayer().getName());
        loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayer(event.getPlayer().getUniqueId());
        tuviCache.remove(event.getPlayer().getUniqueId());
    }

    // --- TOP SYSTEM ---
    private List<Map.Entry<String, Double>> topCache = new ArrayList<>();
    private long lastTopUpdate = 0;

    public void updateTop() {
        saveAll(); // Ensure memory is flushed to config first
        Map<String, Double> allTuVi = new HashMap<>();
        
        for (String key : config.getKeys(false)) {
            double tuvi = config.getDouble(key + ".tuvi", 0.0);
            String name = config.getString(key + ".name", "Unknown");
            allTuVi.put(name, tuvi);
        }

        topCache = allTuVi.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        lastTopUpdate = System.currentTimeMillis();
    }

    public List<Map.Entry<String, Double>> getTopTuVi() {
        // Update top cache every 5 minutes max
        if (System.currentTimeMillis() - lastTopUpdate > 300000) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::updateTop);
        }
        if (topCache.isEmpty() && lastTopUpdate == 0) {
            updateTop(); // Initial synchronous update if empty
        }
        return topCache;
    }
}
