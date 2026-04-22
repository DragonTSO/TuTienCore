package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.RealmTier;
import com.turtle.tutiencore.api.realm.SubRealm;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
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

/**
 * Quản lý hệ thống Cảnh Giới (Realm) cho người chơi.
 * 
 * - Load 19 cảnh giới từ realms.yml
 * - Track realm + sub-realm cho mỗi player
 * - Save/load player realm data
 * - Tự động kiểm tra và đột phá tầng nhỏ
 */
public class RealmManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<Integer, Realm> realms = new LinkedHashMap<>();
    private final Map<UUID, PlayerRealm> playerRealms = new HashMap<>();

    // Realm config
    private FileConfiguration realmConfig;
    private File realmConfigFile;

    // Player data
    private FileConfiguration playerDataConfig;
    private File playerDataFile;

    // Sub-realm breakthrough settings
    private int subSoKyToTrungKyBolts, subTrungKyToHauKyBolts, subHauKyToDinhPhongBolts, subDinhPhongToVienManBolts;
    private double subSoKyToTrungKyDmg, subTrungKyToHauKyDmg, subHauKyToDinhPhongDmg, subDinhPhongToVienManDmg;

    // General breakthrough settings
    private int cooldownSeconds;
    private int lightningIntervalTicks;
    private int weatherRadius;
    private int subRealmBroadcastRadius;
    private int danLossPercent;
    private double failDamageMultiplier;
    private String dotPhaDanItem;
    private Map<Integer, Integer> dotPhaDanAmounts = new HashMap<>();

    public RealmManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadRealmConfig();
        loadPlayerData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Load online players (in case of reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerRealm(player.getUniqueId());
        }
    }

    // ==========================================
    // CONFIG LOADING
    // ==========================================

    private void loadRealmConfig() {
        realmConfigFile = new File(plugin.getDataFolder(), "realms.yml");
        if (!realmConfigFile.exists()) {
            plugin.saveResource("realms.yml", false);
        }
        realmConfig = YamlConfiguration.loadConfiguration(realmConfigFile);

        // Load all 19 realms
        ConfigurationSection realmsSection = realmConfig.getConfigurationSection("realms");
        if (realmsSection != null) {
            for (String key : realmsSection.getKeys(false)) {
                int id = Integer.parseInt(key);
                ConfigurationSection rs = realmsSection.getConfigurationSection(key);
                if (rs == null) continue;

                String name = rs.getString("name", "Unknown");
                String english = rs.getString("english", "Unknown");
                RealmTier tier = RealmTier.valueOf(rs.getString("tier", "PHAM_GIOI"));
                String color = rs.getString("color", "&7");
                String displayName = rs.getString("display-name", color + name);
                long tuViRequired = rs.getLong("tuvi-required", 0);
                long thucLucRequired = rs.getLong("thuc-luc-required", 0);

                // Parse sub-realms (nested: tuvi + display-name)
                ConfigurationSection sub = rs.getConfigurationSection("sub-realms");
                long soKy = 0, trungKy = 0, hauKy = 0, dinhPhong = 0, vienMan = 0;
                String trungKyDisplay = null, hauKyDisplay = null, dinhPhongDisplay = null, vienManDisplay = null;

                if (sub != null) {
                    // Sơ Kỳ
                    ConfigurationSection soKySec = sub.getConfigurationSection("so-ky");
                    soKy = soKySec != null ? soKySec.getLong("tuvi", 0) : sub.getLong("so-ky", 0);
                    // Trung Kỳ
                    ConfigurationSection trungKySec = sub.getConfigurationSection("trung-ky");
                    trungKy = trungKySec != null ? trungKySec.getLong("tuvi", 0) : sub.getLong("trung-ky", 0);
                    trungKyDisplay = trungKySec != null ? trungKySec.getString("display-name") : null;
                    // Hậu Kỳ
                    ConfigurationSection hauKySec = sub.getConfigurationSection("hau-ky");
                    hauKy = hauKySec != null ? hauKySec.getLong("tuvi", 0) : sub.getLong("hau-ky", 0);
                    hauKyDisplay = hauKySec != null ? hauKySec.getString("display-name") : null;
                    // Đỉnh Phong
                    ConfigurationSection dinhPhongSec = sub.getConfigurationSection("dinh-phong");
                    dinhPhong = dinhPhongSec != null ? dinhPhongSec.getLong("tuvi", 0) : sub.getLong("dinh-phong", 0);
                    dinhPhongDisplay = dinhPhongSec != null ? dinhPhongSec.getString("display-name") : null;
                    // Viên Mãn
                    ConfigurationSection vienManSec = sub.getConfigurationSection("vien-man");
                    vienMan = vienManSec != null ? vienManSec.getLong("tuvi", 0) : sub.getLong("vien-man", 0);
                    vienManDisplay = vienManSec != null ? vienManSec.getString("display-name") : null;
                }

                ConfigurationSection bt = rs.getConfigurationSection("breakthrough");
                int bolts = bt != null ? bt.getInt("lightning-bolts", 0) : 0;
                double dmgPerBolt = bt != null ? bt.getDouble("damage-per-bolt", 0) : 0;
                double successRate = bt != null ? bt.getDouble("success-rate", 100) : 100;

                Realm realm = new Realm(id, name, displayName, english, tier,
                        tuViRequired, thucLucRequired, color,
                        soKy, trungKy, hauKy, dinhPhong, vienMan,
                        bolts, dmgPerBolt, successRate);

                // Set sub-realm display names
                if (trungKyDisplay != null) realm.setSubRealmDisplayName(SubRealm.TRUNG_KY, trungKyDisplay);
                if (hauKyDisplay != null) realm.setSubRealmDisplayName(SubRealm.HAU_KY, hauKyDisplay);
                if (dinhPhongDisplay != null) realm.setSubRealmDisplayName(SubRealm.DINH_PHONG, dinhPhongDisplay);
                if (vienManDisplay != null) realm.setSubRealmDisplayName(SubRealm.VIEN_MAN, vienManDisplay);

                realms.put(id, realm);
            }
        }

        // Load sub-realm breakthrough settings
        ConfigurationSection subBt = realmConfig.getConfigurationSection("sub-realm-breakthrough");
        if (subBt != null) {
            subSoKyToTrungKyBolts = subBt.getInt("so-ky-to-trung-ky.lightning-bolts", 3);
            subSoKyToTrungKyDmg = subBt.getDouble("so-ky-to-trung-ky.damage-per-bolt", 1.0);
            subTrungKyToHauKyBolts = subBt.getInt("trung-ky-to-hau-ky.lightning-bolts", 3);
            subTrungKyToHauKyDmg = subBt.getDouble("trung-ky-to-hau-ky.damage-per-bolt", 1.5);
            subHauKyToDinhPhongBolts = subBt.getInt("hau-ky-to-dinh-phong.lightning-bolts", 4);
            subHauKyToDinhPhongDmg = subBt.getDouble("hau-ky-to-dinh-phong.damage-per-bolt", 1.5);
            subDinhPhongToVienManBolts = subBt.getInt("dinh-phong-to-vien-man.lightning-bolts", 5);
            subDinhPhongToVienManDmg = subBt.getDouble("dinh-phong-to-vien-man.damage-per-bolt", 2.0);
        }

        // Load general breakthrough settings
        ConfigurationSection btGeneral = realmConfig.getConfigurationSection("breakthrough");
        if (btGeneral != null) {
            cooldownSeconds = btGeneral.getInt("cooldown-seconds", 3600);
            lightningIntervalTicks = btGeneral.getInt("lightning-interval-ticks", 50);
            weatherRadius = btGeneral.getInt("weather-radius", 50);
            subRealmBroadcastRadius = btGeneral.getInt("sub-realm-broadcast-radius", 30);
            danLossPercent = btGeneral.getInt("dan-loss-percent", 50);
            failDamageMultiplier = btGeneral.getDouble("fail-damage-multiplier", 2.0);
            dotPhaDanItem = btGeneral.getString("dot-pha-dan-item", "DOT_PHA_DAN");

            ConfigurationSection danAmounts = btGeneral.getConfigurationSection("dot-pha-dan-amounts");
            if (danAmounts != null) {
                for (String key : danAmounts.getKeys(false)) {
                    dotPhaDanAmounts.put(Integer.parseInt(key), danAmounts.getInt(key, 1));
                }
            }
        }

        plugin.getLogger().info("Loaded " + realms.size() + " realms from realms.yml");
    }

    // ==========================================
    // PLAYER DATA
    // ==========================================

    private void loadPlayerData() {
        playerDataFile = new File(plugin.getDataFolder(), "player-realms.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player-realms.yml!");
                e.printStackTrace();
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    public void loadPlayerRealm(UUID uuid) {
        String path = uuid.toString();
        int realmId = playerDataConfig.getInt(path + ".realm-id", 1);
        String subRealmStr = playerDataConfig.getString(path + ".sub-realm", "SO_KY");
        long cooldown = playerDataConfig.getLong(path + ".breakthrough-cooldown", 0);

        SubRealm subRealm;
        try {
            subRealm = SubRealm.valueOf(subRealmStr);
        } catch (Exception e) {
            subRealm = SubRealm.SO_KY;
        }

        PlayerRealm pr = new PlayerRealm(realmId, subRealm);
        pr.setBreakthroughCooldown(cooldown);
        playerRealms.put(uuid, pr);
    }

    public void savePlayerRealm(UUID uuid) {
        PlayerRealm pr = playerRealms.get(uuid);
        if (pr == null) return;

        String path = uuid.toString();
        playerDataConfig.set(path + ".realm-id", pr.getRealmId());
        playerDataConfig.set(path + ".sub-realm", pr.getSubRealm().name());
        playerDataConfig.set(path + ".breakthrough-cooldown", pr.getBreakthroughCooldown());

        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player-realms.yml!");
            e.printStackTrace();
        }
    }

    public void saveAllPlayerRealms() {
        for (UUID uuid : playerRealms.keySet()) {
            PlayerRealm pr = playerRealms.get(uuid);
            if (pr == null) continue;
            String path = uuid.toString();
            playerDataConfig.set(path + ".realm-id", pr.getRealmId());
            playerDataConfig.set(path + ".sub-realm", pr.getSubRealm().name());
            playerDataConfig.set(path + ".breakthrough-cooldown", pr.getBreakthroughCooldown());
        }
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player-realms.yml!");
            e.printStackTrace();
        }
    }

    // ==========================================
    // REALM ACCESS
    // ==========================================

    /**
     * Get a realm by its ID (1-19)
     */
    public Realm getRealm(int id) {
        return realms.get(id);
    }

    /**
     * Get all realms
     */
    public Map<Integer, Realm> getAllRealms() {
        return Collections.unmodifiableMap(realms);
    }

    /**
     * Get a player's current realm state
     */
    public PlayerRealm getPlayerRealm(UUID uuid) {
        return playerRealms.computeIfAbsent(uuid, k -> new PlayerRealm(1, SubRealm.SO_KY));
    }

    /**
     * Get the Realm object for a player's current realm
     */
    public Realm getPlayerCurrentRealm(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        return realms.get(pr.getRealmId());
    }

    /**
     * Get the next realm for a player (null if at max)
     */
    public Realm getNextRealm(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        int nextId = pr.getRealmId() + 1;
        return realms.get(nextId);
    }

    /**
     * Get the maximum realm ID
     */
    public int getMaxRealmId() {
        return realms.keySet().stream().max(Integer::compareTo).orElse(19);
    }

    /**
     * Check if player is at the maximum realm
     */
    public boolean isMaxRealm(UUID uuid) {
        return getPlayerRealm(uuid).getRealmId() >= getMaxRealmId();
    }

    // ==========================================
    // SUB-REALM DETECTION
    // ==========================================

    /**
     * Determine the current sub-realm based on Tu Vi within the current realm
     */
    public SubRealm calculateSubRealm(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        Realm realm = realms.get(pr.getRealmId());
        if (realm == null) return SubRealm.SO_KY;

        double tuVi = TuTien.getApi().getTuVi(uuid);

        if (tuVi >= realm.getVienManTuVi()) return SubRealm.VIEN_MAN;
        if (tuVi >= realm.getDinhPhongTuVi()) return SubRealm.DINH_PHONG;
        if (tuVi >= realm.getHauKyTuVi()) return SubRealm.HAU_KY;
        if (tuVi >= realm.getTrungKyTuVi()) return SubRealm.TRUNG_KY;
        return SubRealm.SO_KY;
    }

    /**
     * Check and update player's sub-realm if Tu Vi has increased.
     * Returns true if sub-realm changed.
     */
    public boolean checkAndUpdateSubRealm(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        SubRealm current = pr.getSubRealm();
        SubRealm calculated = calculateSubRealm(uuid);

        if (calculated.getOrder() > current.getOrder()) {
            pr.setSubRealm(calculated);
            savePlayerRealm(uuid);
            return true;
        }
        return false;
    }

    // ==========================================
    // BREAKTHROUGH CHECKS
    // ==========================================

    /**
     * Check if a player can breakthrough to the next major realm.
     * Returns a list of failed conditions (empty = all conditions met).
     */
    public List<String> checkBreakthroughConditions(UUID uuid) {
        List<String> failures = new ArrayList<>();
        PlayerRealm pr = getPlayerRealm(uuid);
        Realm currentRealm = realms.get(pr.getRealmId());
        Realm nextRealm = realms.get(pr.getRealmId() + 1);

        if (nextRealm == null) {
            failures.add("§cBạn đã đạt Cảnh Giới tối đa!");
            return failures;
        }

        // Check sub-realm is Viên Mãn
        if (pr.getSubRealm() != SubRealm.VIEN_MAN) {
            failures.add("§cTầng nhỏ chưa đạt Viên Mãn! Hiện tại: " + pr.getSubRealm().getDisplayName());
        }

        // Check Tu Vi
        double tuVi = TuTien.getApi().getTuVi(uuid);
        if (tuVi < nextRealm.getTuViRequired()) {
            failures.add("§cTu Vi chưa đủ! Cần: " + formatNumber(nextRealm.getTuViRequired()) + " | Hiện tại: " + formatNumber((long) tuVi));
        }

        // Check cooldown
        if (pr.isOnCooldown()) {
            long remaining = pr.getRemainingCooldownSeconds();
            int minutes = (int) (remaining / 60);
            int seconds = (int) (remaining % 60);
            failures.add("§cĐang trong thời gian hồi phục! Còn: " + minutes + " phút " + seconds + " giây");
        }

        return failures;
    }

    /**
     * Perform the major realm breakthrough (advance to next realm)
     */
    public void advanceRealm(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        int nextRealmId = pr.getRealmId() + 1;
        Realm nextRealm = realms.get(nextRealmId);
        if (nextRealm == null) return;

        pr.setRealmId(nextRealmId);
        pr.setSubRealm(SubRealm.SO_KY);
        savePlayerRealm(uuid);
    }

    /**
     * Handle breakthrough failure
     */
    public void handleBreakthroughFailure(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        pr.applyCooldown(cooldownSeconds * 1000L);
        savePlayerRealm(uuid);
    }

    // ==========================================
    // DISPLAY HELPERS
    // ==========================================

    /**
     * Get formatted realm display for a player (translated § codes)
     * Example: §a[Luyện Khí — Đỉnh Phong]
     */
    public String getPlayerRealmDisplay(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        Realm realm = realms.get(pr.getRealmId());
        if (realm == null) return "§7[Phàm Nhân — Sơ Kỳ]";
        return realm.getFullDisplay(pr.getSubRealm());
    }

    /**
     * Get the smart display-name (translated § codes).
     * Sơ Kỳ → realm display-name (e.g. §a「Luyện Khí」)
     * Trung Kỳ+ → sub-realm display-name (e.g. §a「Luyện Khí · Đỉnh Phong」)
     */
    public String getPlayerDisplayName(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        Realm realm = realms.get(pr.getRealmId());
        if (realm == null) return "§7「Phàm Nhân」";
        return realm.getSubRealmDisplayNameTranslated(pr.getSubRealm());
    }

    /**
     * Get the display-name with & codes (raw, for LuckPerms prefix)
     * Example: &a「Luyện Khí」
     */
    public String getPlayerDisplayNameRaw(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        Realm realm = realms.get(pr.getRealmId());
        if (realm == null) return "&7「Phàm Nhân」";
        return realm.getDisplayName();
    }

    /**
     * Get just the realm name with color (translated §)
     * Example: §aLuyện Khí
     */
    public String getPlayerRealmName(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        Realm realm = realms.get(pr.getRealmId());
        if (realm == null) return "§7Phàm Nhân";
        return realm.getFormattedName();
    }

    /**
     * Get sub-realm display name
     */
    public String getPlayerSubRealmName(UUID uuid) {
        PlayerRealm pr = getPlayerRealm(uuid);
        return pr.getSubRealm().getDisplayName();
    }

    // ==========================================
    // SUB-REALM BREAKTHROUGH SETTINGS
    // ==========================================

    public int getSubRealmBolts(SubRealm from) {
        switch (from) {
            case SO_KY: return subSoKyToTrungKyBolts;
            case TRUNG_KY: return subTrungKyToHauKyBolts;
            case HAU_KY: return subHauKyToDinhPhongBolts;
            case DINH_PHONG: return subDinhPhongToVienManBolts;
            default: return 0;
        }
    }

    public double getSubRealmDmg(SubRealm from) {
        switch (from) {
            case SO_KY: return subSoKyToTrungKyDmg;
            case TRUNG_KY: return subTrungKyToHauKyDmg;
            case HAU_KY: return subHauKyToDinhPhongDmg;
            case DINH_PHONG: return subDinhPhongToVienManDmg;
            default: return 0;
        }
    }

    // ==========================================
    // GENERAL SETTINGS
    // ==========================================

    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getLightningIntervalTicks() { return lightningIntervalTicks; }
    public int getWeatherRadius() { return weatherRadius; }
    public int getSubRealmBroadcastRadius() { return subRealmBroadcastRadius; }
    public int getDanLossPercent() { return danLossPercent; }
    public double getFailDamageMultiplier() { return failDamageMultiplier; }
    public String getDotPhaDanItem() { return dotPhaDanItem; }

    public int getDotPhaDanRequired(int targetRealmId) {
        return dotPhaDanAmounts.getOrDefault(targetRealmId, 1);
    }

    // ==========================================
    // EVENTS
    // ==========================================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayerRealm(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayerRealm(event.getPlayer().getUniqueId());
        playerRealms.remove(event.getPlayer().getUniqueId());
    }

    // ==========================================
    // UTILITY
    // ==========================================

    public static String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}
