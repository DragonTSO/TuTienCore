package com.turtle.tutiencore.core.storage;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.core.manager.PlayerDataManager;
import com.turtle.tutiencore.core.manager.RealmManager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.UUID;

public class PlayerProgressDatabaseSync {
    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final RealmManager realmManager;
    private final PlayerProgressDatabase database;

    public PlayerProgressDatabaseSync(JavaPlugin plugin, PlayerDataManager playerDataManager,
                                      RealmManager realmManager, PlayerProgressDatabase database) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.realmManager = realmManager;
        this.database = database;
    }

    public void initialize() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.initialize();
                plugin.getLogger().info("TuTienCore database sync enabled.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not initialize TuTienCore database: " + e.getMessage());
            }
        });
    }

    public void sync(UUID uuid) {
        PlayerRealm realm = realmManager.getPlayerRealm(uuid);
        double tuvi = playerDataManager.getTuVi(uuid);
        String playerName = resolvePlayerName(uuid);
        int realmId = realm.getRealmId();
        String subRealm = realm.getSubRealm().name();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.saveProgress(uuid, playerName, tuvi, realmId, subRealm);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not sync TuTienCore database for " + playerName + ": " + e.getMessage());
            }
        });
    }

    private String resolvePlayerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 16) : name;
    }
}
