package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTienAPI;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;

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
import java.util.*;
import java.util.stream.Collectors;

public class PlayerDataManager implements Listener, TuTienAPI {
    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;
    private final Map<UUID, Double> tuviCache = new HashMap<>();

    // These are injected after construction
    private RealmManager realmManager;
    private BreakthroughManager breakthroughManager;
    private TuLuyenManager tuLuyenManager;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Load online players (in case of plugin reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }
    }

    /**
     * Inject managers after they are constructed (avoids circular dependency).
     */
    public void injectManagers(RealmManager realmManager, BreakthroughManager breakthroughManager, TuLuyenManager tuLuyenManager) {
        this.realmManager = realmManager;
        this.breakthroughManager = breakthroughManager;
        this.tuLuyenManager = tuLuyenManager;
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

    // ==========================================
    // TU VI OPERATIONS
    // ==========================================

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
        double current = getTuVi(uuid);
        if (realmManager == null || amount <= 0) {
            setTuVi(uuid, current + amount);
            return;
        }

        PlayerRealm playerRealm = realmManager.getPlayerRealm(uuid);
        Realm currentRealm = realmManager.getPlayerCurrentRealm(uuid);
        Realm nextRealm = realmManager.getNextRealm(uuid);
        setTuVi(uuid, capTuViAfterAdd(current, amount, playerRealm, currentRealm, nextRealm));
    }

    static double capTuViAfterAdd(double currentTuVi, double amount, PlayerRealm playerRealm, Realm currentRealm, Realm nextRealm) {
        double result = currentTuVi + amount;
        if (amount <= 0 || playerRealm == null || currentRealm == null) return result;

        SubRealm nextSubRealm = playerRealm.getSubRealm().next();
        long cap = nextSubRealm != null
                ? currentRealm.getTuViForSubRealm(nextSubRealm)
                : nextRealm != null ? nextRealm.getTuViRequired() : currentRealm.getTuViForSubRealm(SubRealm.VIEN_MAN);

        return cap > 0 ? Math.min(result, cap) : result;
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

    // ==========================================
    // REALM ACCESS (delegates to RealmManager)
    // ==========================================

    @Override
    public int getRealmId(UUID uuid) {
        if (realmManager == null) return 1;
        return realmManager.getPlayerRealm(uuid).getRealmId();
    }

    @Override
    public Realm getRealm(UUID uuid) {
        if (realmManager == null) return null;
        return realmManager.getPlayerCurrentRealm(uuid);
    }

    @Override
    public Realm getRealmById(int realmId) {
        if (realmManager == null) return null;
        return realmManager.getRealm(realmId);
    }

    @Override
    public Map<Integer, Realm> getAllRealms() {
        if (realmManager == null) return Collections.emptyMap();
        return realmManager.getAllRealms();
    }

    @Override
    public int getMaxRealmId() {
        if (realmManager == null) return 19;
        return realmManager.getMaxRealmId();
    }

    @Override
    public PlayerRealm getPlayerRealmData(UUID uuid) {
        if (realmManager == null) return new PlayerRealm(1, SubRealm.SO_KY);
        return realmManager.getPlayerRealm(uuid);
    }

    @Override
    public SubRealm getSubRealm(UUID uuid) {
        if (realmManager == null) return SubRealm.SO_KY;
        return realmManager.getPlayerRealm(uuid).getSubRealm();
    }

    @Override
    public void setRealm(UUID uuid, int realmId, SubRealm subRealm) {
        if (realmManager == null) return;
        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        pr.setRealmId(realmId);
        pr.setSubRealm(subRealm);
        realmManager.savePlayerRealm(uuid);
    }

    @Override
    public boolean isMaxRealm(UUID uuid) {
        if (realmManager == null) return false;
        return realmManager.isMaxRealm(uuid);
    }

    // ==========================================
    // REALM DISPLAY (delegates to RealmManager)
    // ==========================================

    @Override
    public String getRealmDisplay(UUID uuid) {
        if (realmManager == null) return "§7[Phàm Nhân — Sơ Kỳ]";
        return realmManager.getPlayerRealmDisplay(uuid);
    }

    @Override
    public String getRealmDisplayName(UUID uuid) {
        if (realmManager == null) return "§7「Phàm Nhân」";
        return realmManager.getPlayerDisplayName(uuid);
    }

    @Override
    public String getRealmName(UUID uuid) {
        if (realmManager == null) return "§7Phàm Nhân";
        return realmManager.getPlayerRealmName(uuid);
    }

    @Override
    public String getSubRealmName(UUID uuid) {
        if (realmManager == null) return "Sơ Kỳ";
        return realmManager.getPlayerSubRealmName(uuid);
    }

    @Override
    public String getRealmTierName(UUID uuid) {
        if (realmManager == null) return "Phàm Giới";
        Realm realm = realmManager.getPlayerCurrentRealm(uuid);
        return realm != null ? realm.getTier().getDisplayName() : "Phàm Giới";
    }

    // ==========================================
    // BREAKTHROUGH (delegates to BreakthroughManager)
    // ==========================================

    @Override
    public boolean isInBreakthrough(UUID uuid) {
        if (breakthroughManager == null) return false;
        return breakthroughManager.isInBreakthrough(uuid);
    }

    @Override
    public boolean isOnBreakthroughCooldown(UUID uuid) {
        if (realmManager == null) return false;
        return realmManager.getPlayerRealm(uuid).isOnCooldown();
    }

    @Override
    public long getBreakthroughCooldownRemaining(UUID uuid) {
        if (realmManager == null) return 0;
        return realmManager.getPlayerRealm(uuid).getRemainingCooldownSeconds();
    }

    @Override
    public boolean canBreakthrough(UUID uuid) {
        if (realmManager == null) return false;
        return realmManager.checkBreakthroughConditions(uuid).isEmpty();
    }

    // ==========================================
    // TU LUYEN (delegates to TuLuyenManager)
    // ==========================================

    @Override
    public boolean isTuLuyen(UUID uuid) {
        if (tuLuyenManager == null) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        return tuLuyenManager.isTuLuyen(player);
    }

    @Override
    public Collection<UUID> getTuLuyenPlayers() {
        if (tuLuyenManager == null) return Collections.emptyList();
        List<UUID> uuids = new ArrayList<>();
        for (Player p : tuLuyenManager.getTuLuyenPlayers()) {
            uuids.add(p.getUniqueId());
        }
        return uuids;
    }

    // ==========================================
    // UTILITY
    // ==========================================

    @Override
    public String formatNumber(long number) {
        return RealmManager.formatNumber(number);
    }

    // ==========================================
    // EVENTS
    // ==========================================

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
