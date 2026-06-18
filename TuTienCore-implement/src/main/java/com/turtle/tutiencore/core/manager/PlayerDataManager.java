package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTienAPI;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.infusion.OwnedInfusion;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
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
    private static final int MAX_INFUSION_INVENTORY = 270;

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;
    private final Object configLock = new Object();
    // Serializing players.yml happens on the main thread (consistent snapshot under configLock),
    // but the disk write is pushed off-thread. diskWriteLock serializes the actual file writes so a
    // sync save (shutdown) and a queued async save (player quit) never write the file concurrently.
    private final Object diskWriteLock = new Object();
    private final java.util.concurrent.atomic.AtomicLong saveSeq = new java.util.concurrent.atomic.AtomicLong();
    private volatile long lastWrittenSeq = 0L;
    private final Map<UUID, Double> tuviCache = new HashMap<>();
    private final Map<UUID, Long> tuLuyenTotalSecondsCache = new HashMap<>();
    private final Map<UUID, List<OwnedInfusion>> infusionInventoryCache = new HashMap<>();
    private final Map<UUID, String> equippedInfusionIdCache = new HashMap<>();

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
        synchronized (configLock) {
            for (Map.Entry<UUID, Double> entry : tuviCache.entrySet()) {
                config.set(entry.getKey().toString() + ".tuvi", entry.getValue());
            }
            for (Map.Entry<UUID, Long> entry : tuLuyenTotalSecondsCache.entrySet()) {
                writeTuLuyenTime(entry.getKey(), entry.getValue());
            }
            Set<UUID> uuids = new HashSet<>();
            uuids.addAll(infusionInventoryCache.keySet());
            uuids.addAll(equippedInfusionIdCache.keySet());
            for (UUID uuid : uuids) {
                writeInfusionState(uuid);
            }
            saveToDisk();
        }
    }

    public void savePlayer(UUID uuid) {
        synchronized (configLock) {
            if (tuviCache.containsKey(uuid)) {
                config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
            }
            if (tuLuyenTotalSecondsCache.containsKey(uuid)) {
                writeTuLuyenTime(uuid, tuLuyenTotalSecondsCache.get(uuid));
            }
            if (infusionInventoryCache.containsKey(uuid) || equippedInfusionIdCache.containsKey(uuid)) {
                writeInfusionState(uuid);
            }
            saveToDisk();
        }
    }

    /**
     * Like {@link #savePlayer(UUID)} but pushes the disk write off the main thread. The cache values
     * are copied into the config under {@code configLock} on the calling (main) thread, so the
     * snapshot is consistent; only the file write runs async. Use this on player quit to avoid
     * blocking the main thread with a full-file YAML write. Do NOT use on shutdown.
     */
    public void savePlayerAsync(UUID uuid) {
        synchronized (configLock) {
            if (tuviCache.containsKey(uuid)) {
                config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
            }
            if (tuLuyenTotalSecondsCache.containsKey(uuid)) {
                writeTuLuyenTime(uuid, tuLuyenTotalSecondsCache.get(uuid));
            }
            if (infusionInventoryCache.containsKey(uuid) || equippedInfusionIdCache.containsKey(uuid)) {
                writeInfusionState(uuid);
            }
            saveToDiskAsync();
        }
    }

    public boolean savePlayerSafely(UUID uuid) {
        synchronized (configLock) {
            if (tuviCache.containsKey(uuid)) {
                config.set(uuid.toString() + ".tuvi", tuviCache.get(uuid));
            }
            if (tuLuyenTotalSecondsCache.containsKey(uuid)) {
                writeTuLuyenTime(uuid, tuLuyenTotalSecondsCache.get(uuid));
            }
            if (infusionInventoryCache.containsKey(uuid) || equippedInfusionIdCache.containsKey(uuid)) {
                writeInfusionState(uuid);
            }
            return saveToDisk();
        }
    }

    /**
     * Flushes in-memory caches to the config map without serializing or writing to disk.
     * Use this when you need the config to reflect current cache state (e.g., for top queries)
     * but don't need a full disk save. Avoids the expensive sanitize + YAML serialization.
     */
    public void flushCachesToConfig() {
        synchronized (configLock) {
            for (Map.Entry<UUID, Double> entry : tuviCache.entrySet()) {
                config.set(entry.getKey().toString() + ".tuvi", entry.getValue());
            }
            for (Map.Entry<UUID, Long> entry : tuLuyenTotalSecondsCache.entrySet()) {
                writeTuLuyenTime(entry.getKey(), entry.getValue());
            }
            Set<UUID> uuids = new HashSet<>();
            uuids.addAll(infusionInventoryCache.keySet());
            uuids.addAll(equippedInfusionIdCache.keySet());
            for (UUID uuid : uuids) {
                writeInfusionState(uuid);
            }
        }
    }

    public void loadPlayer(UUID uuid) {
        synchronized (configLock) {
            double tuvi = config.getDouble(uuid.toString() + ".tuvi", 0.0);
            tuviCache.put(uuid, tuvi);
            tuLuyenTotalSecondsCache.put(uuid, readTuLuyenTotalSeconds(uuid));

            InfusionState state = readInfusionState(uuid);
            infusionInventoryCache.put(uuid, new ArrayList<>(state.inventory()));
            if (state.equippedId() == null || state.equippedId().isBlank()) {
                equippedInfusionIdCache.remove(uuid);
            } else {
                equippedInfusionIdCache.put(uuid, state.equippedId());
            }
        }
    }

    public List<OwnedInfusion> getInfusionInventory(UUID uuid) {
        return List.copyOf(infusionInventoryCache.getOrDefault(uuid, Collections.emptyList()));
    }

    public Optional<OwnedInfusion> getEquippedInfusion(UUID uuid) {
        String equippedId = equippedInfusionIdCache.get(uuid);
        if (equippedId == null || equippedId.isBlank()) {
            return Optional.empty();
        }
        return getInfusionInventory(uuid).stream()
                .filter(owned -> equippedId.equals(owned.id()))
                .findFirst();
    }

    public Optional<OwnedInfusion> findInfusion(UUID uuid, String infusionId) {
        if (infusionId == null || infusionId.isBlank()) {
            return Optional.empty();
        }
        return getInfusionInventory(uuid).stream()
                .filter(owned -> infusionId.equals(owned.id()))
                .findFirst();
    }

    public boolean canAddInfusion(UUID uuid) {
        return getInfusionInventory(uuid).size() < MAX_INFUSION_INVENTORY;
    }

    public boolean addInfusion(UUID uuid, OwnedInfusion infusion) {
        if (infusion == null || !canAddInfusion(uuid)) {
            return false;
        }

        List<OwnedInfusion> inventory = new ArrayList<>(infusionInventoryCache.getOrDefault(uuid, Collections.emptyList()));
        inventory.add(infusion);
        infusionInventoryCache.put(uuid, inventory);
        return savePlayerSafely(uuid);
    }

    public boolean setEquippedInfusionId(UUID uuid, String infusionId) {
        if (infusionId != null && !infusionId.isBlank()) {
            boolean exists = getInfusionInventory(uuid).stream().anyMatch(owned -> infusionId.equals(owned.id()));
            if (!exists) {
                return false;
            }
            equippedInfusionIdCache.put(uuid, infusionId);
        } else {
            equippedInfusionIdCache.remove(uuid);
        }
        return savePlayerSafely(uuid);
    }

    public String getEquippedInfusionId(UUID uuid) {
        return equippedInfusionIdCache.get(uuid);
    }

    private long readTuLuyenTotalSeconds(UUID uuid) {
        String path = uuid.toString() + ".tuluyen";
        if (config.contains(path + ".total-seconds")) {
            return Math.max(0L, config.getLong(path + ".total-seconds", 0L));
        }

        // Legacy-friendly fallback if a previous/manual config used total-time.
        return Math.max(0L, config.getLong(path + ".total-time", 0L));
    }

    private void writeTuLuyenTime(UUID uuid, long seconds) {
        config.set(uuid.toString() + ".tuluyen.total-seconds", Math.max(0L, seconds));
    }

    private InfusionState readInfusionState(UUID uuid) {
        String path = uuid.toString() + ".infusion";

        List<OwnedInfusion> inventory = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        List<Map<?, ?>> raw = config.getMapList(path + ".inventory");
        for (Map<?, ?> row : raw) {
            String id = asString(row.get("id"));
            String typeId = asString(row.get("type"));
            String rarityId = asString(row.get("rarity"));
            long createdAt = asLong(row.get("created-at"), System.currentTimeMillis());

            if (typeId.isBlank() || rarityId.isBlank()) {
                continue;
            }

            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            while (!seenIds.add(id)) {
                id = UUID.randomUUID().toString();
            }

            inventory.add(new OwnedInfusion(id, typeId, rarityId, createdAt));
        }

        // Legacy migration: claimed/type/rarity/created-at
        if (inventory.isEmpty() && config.getBoolean(path + ".claimed", false)) {
            String typeId = config.getString(path + ".type", "");
            String rarityId = config.getString(path + ".rarity", "");
            if (!typeId.isBlank() && !rarityId.isBlank()) {
                inventory.add(OwnedInfusion.create(typeId, rarityId, config.getLong(path + ".created-at", System.currentTimeMillis())));
            }
        }

        String equippedId = config.getString(path + ".equipped-id", "");
        boolean equippedExists = false;
        if (!equippedId.isBlank()) {
            for (OwnedInfusion owned : inventory) {
                if (equippedId.equals(owned.id())) {
                    equippedExists = true;
                    break;
                }
            }
        }
        if (equippedId.isBlank() || !equippedExists) {
            equippedId = null;
        }

        return new InfusionState(inventory, equippedId);
    }

    private void writeInfusionState(UUID uuid) {
        String path = uuid.toString() + ".infusion";

        List<OwnedInfusion> inventory = infusionInventoryCache.getOrDefault(uuid, Collections.emptyList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OwnedInfusion infusion : inventory) {
            if (infusion == null) {
                continue;
            }
            String id = asString(infusion.id());
            String typeId = asString(infusion.typeId());
            String rarityId = asString(infusion.rarityId());
            if (id.isBlank() || typeId.isBlank() || rarityId.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("type", typeId);
            row.put("rarity", rarityId);
            row.put("created-at", infusion.createdAt());
            rows.add(row);
        }
        config.set(path + ".inventory", rows);

        String equippedId = equippedInfusionIdCache.get(uuid);
        if (equippedId == null || equippedId.isBlank()) {
            config.set(path + ".equipped-id", null);
        } else {
            config.set(path + ".equipped-id", equippedId);
        }
    }

    private boolean saveToDisk() {
        final String serialized;
        final long seq;
        synchronized (configLock) {
            try {
                sanitizeConfigBeforeSave();
                serialized = config.saveToString();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not serialize players.yml: " + e.getMessage());
                return false;
            }
            seq = saveSeq.incrementAndGet();
        }
        return writeSerialized(serialized, seq);
    }

    /**
     * Serializes players.yml on the calling (main) thread for a consistent snapshot under
     * {@code configLock}, then pushes the disk write off-thread. Use this on player quit so the
     * full-file YAML write never blocks the main thread.
     *
     * <p>Must NOT be used on shutdown: the Bukkit scheduler is torn down before the async task can
     * run, which would lose the write. Use {@link #saveToDisk()} (synchronous) there instead.
     */
    private void saveToDiskAsync() {
        final String serialized;
        final long seq;
        synchronized (configLock) {
            try {
                sanitizeConfigBeforeSave();
                serialized = config.saveToString();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not serialize players.yml: " + e.getMessage());
                return;
            }
            seq = saveSeq.incrementAndGet();
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeSerialized(serialized, seq));
    }

    /**
     * Writes a pre-serialized snapshot to disk. {@code seq} is the snapshot's monotonic version;
     * an older snapshot is dropped if a newer one already landed, so a slow async write can never
     * overwrite fresher data. The write itself is serialized by {@code diskWriteLock}.
     */
    private boolean writeSerialized(String serialized, long seq) {
        synchronized (diskWriteLock) {
            if (seq <= lastWrittenSeq) {
                return true; // A newer snapshot already hit disk; this one is stale, skip it.
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                java.nio.file.Files.write(file.toPath(), serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                lastWrittenSeq = seq;
                return true;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
                return false;
            }
        }
    }

    private void sanitizeConfigBeforeSave() {
        sanitizeInfusionConfigBeforeSave();

        List<String> paths = new ArrayList<>(config.getKeys(true));
        for (String path : paths) {
            Object value = config.get(path);
            if (value == null || value instanceof ConfigurationSection) {
                continue;
            }

            Object sanitized = sanitizeYamlValue(value);
            if (sanitized == null) {
                config.set(path, null);
                continue;
            }
            if (!Objects.equals(value, sanitized)) {
                config.set(path, sanitized);
            }
        }
    }

    private Object sanitizeYamlValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Character) {
            return value;
        }
        if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d)) {
                return 0.0D;
            }
            if (number instanceof Float f && !Float.isFinite(f)) {
                return 0.0F;
            }
            return value;
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof ConfigurationSerializable serializable) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            sanitized.put("==", ConfigurationSerialization.getAlias(serializable.getClass()));
            for (Map.Entry<String, Object> entry : serializable.serialize().entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                Object child = sanitizeYamlValue(entry.getValue());
                if (child != null) {
                    sanitized.put(entry.getKey(), child);
                }
            }
            return sanitized;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object rawKey = entry.getKey();
                if (rawKey == null) {
                    continue;
                }
                String key = String.valueOf(rawKey).trim();
                if (key.isBlank()) {
                    continue;
                }
                Object child = sanitizeYamlValue(entry.getValue());
                if (child != null) {
                    sanitized.put(key, child);
                }
            }
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                Object child = sanitizeYamlValue(item);
                if (child != null) {
                    sanitized.add(child);
                }
            }
            return sanitized;
        }
        if (value.getClass().isArray()) {
            List<Object> sanitized = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object child = sanitizeYamlValue(java.lang.reflect.Array.get(value, i));
                if (child != null) {
                    sanitized.add(child);
                }
            }
            return sanitized;
        }
        return String.valueOf(value);
    }

    private void sanitizeInfusionConfigBeforeSave() {
        for (String key : config.getKeys(false)) {
            String inventoryPath = key + ".infusion.inventory";
            if (!config.isList(inventoryPath)) {
                continue;
            }

            List<?> rawRows = config.getList(inventoryPath, Collections.emptyList());
            List<Map<String, Object>> sanitizedRows = new ArrayList<>();
            boolean changed = false;

            for (Object rawRow : rawRows) {
                if (!(rawRow instanceof Map<?, ?> row)) {
                    changed = true;
                    continue;
                }

                String id = asString(row.get("id"));
                String typeId = asString(row.get("type"));
                String rarityId = asString(row.get("rarity"));
                if (id.isBlank() || typeId.isBlank() || rarityId.isBlank()) {
                    changed = true;
                    continue;
                }

                Map<String, Object> sanitizedRow = new LinkedHashMap<>();
                sanitizedRow.put("id", id);
                sanitizedRow.put("type", typeId);
                sanitizedRow.put("rarity", rarityId);
                sanitizedRow.put("created-at", asLong(row.get("created-at"), System.currentTimeMillis()));
                sanitizedRows.add(sanitizedRow);

                if (!Objects.equals(row, sanitizedRow)) {
                    changed = true;
                }
            }

            if (changed) {
                config.set(inventoryPath, sanitizedRows);
            }
        }
    }

    private static String asString(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }

    private static long asLong(Object input, long fallback) {
        if (input instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(asString(input));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record InfusionState(List<OwnedInfusion> inventory, String equippedId) {
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
        // Keep the breakthrough counter in sync with the realm set directly (e.g. admin command),
        // otherwise the stacking cultivation bonus stays stuck at the old value until the player
        // performs a real breakthrough. Use max() so earned breakthroughs from past failures are
        // never wiped when the realm is set.
        int derived = RealmManager.deriveBreakthroughCount(realmId, subRealm);
        pr.setBreakthroughCount(Math.max(pr.getBreakthroughCount(), derived));
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

    @Override
    public long getTuLuyenTotalSeconds(UUID uuid) {
        Long cached = tuLuyenTotalSecondsCache.get(uuid);
        if (cached != null) {
            return Math.max(0L, cached);
        }
        synchronized (configLock) {
            return readTuLuyenTotalSeconds(uuid);
        }
    }

    @Override
    public void setTuLuyenTotalSeconds(UUID uuid, long seconds) {
        tuLuyenTotalSecondsCache.put(uuid, Math.max(0L, seconds));
    }

    @Override
    public void addTuLuyenTotalSeconds(UUID uuid, long seconds) {
        if (seconds <= 0L) {
            return;
        }
        tuLuyenTotalSecondsCache.put(uuid, getTuLuyenTotalSeconds(uuid) + seconds);
    }

    @Override
    public long getTuLuyenSessionSeconds(UUID uuid) {
        if (tuLuyenManager == null) return 0L;
        return tuLuyenManager.getSessionSeconds(uuid);
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
        synchronized (configLock) {
            config.set(event.getPlayer().getUniqueId().toString() + ".name", event.getPlayer().getName());
        }
        loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Snapshot + serialize happens synchronously under configLock inside savePlayerAsync; only
        // the disk write is off-thread, so evicting the caches immediately after is safe.
        savePlayerAsync(event.getPlayer().getUniqueId());
        tuviCache.remove(event.getPlayer().getUniqueId());
        tuLuyenTotalSecondsCache.remove(event.getPlayer().getUniqueId());
        infusionInventoryCache.remove(event.getPlayer().getUniqueId());
        equippedInfusionIdCache.remove(event.getPlayer().getUniqueId());
    }

    // --- TOP SYSTEM ---
    private List<Map.Entry<String, Double>> topCache = new ArrayList<>();
    private long lastTopUpdate = 0;
    private List<Map.Entry<String, Long>> topTuLuyenTimeCache = new ArrayList<>();
    private long lastTopTuLuyenTimeUpdate = 0;

    public void updateTop() {
        flushCachesToConfig(); // Flush caches to config map (no disk write, no serialization)
        Map<String, Double> allTuVi = new HashMap<>();

        synchronized (configLock) {
            for (String key : config.getKeys(false)) {
                double tuvi = config.getDouble(key + ".tuvi", 0.0);
                String name = config.getString(key + ".name", "Unknown");
                allTuVi.put(name, tuvi);
            }
        }

        topCache = allTuVi.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        lastTopUpdate = System.currentTimeMillis();
    }

    public List<Map.Entry<String, Double>> getTopTuVi() {
        // Update top cache every 5 minutes max
        if (System.currentTimeMillis() - lastTopUpdate > 300000) {
            if (Bukkit.isPrimaryThread()) {
                updateTop();
            } else {
                Bukkit.getScheduler().runTask(plugin, this::updateTop);
            }
        }
        if (topCache.isEmpty() && lastTopUpdate == 0) {
            updateTop(); // Initial synchronous update if empty
        }
        return topCache;
    }

    public void updateTopTuLuyenTime() {
        flushCachesToConfig(); // Flush caches to config map (no disk write, no serialization)
        Map<String, Long> allTimes = new HashMap<>();

        synchronized (configLock) {
            for (String key : config.getKeys(false)) {
                long seconds = Math.max(0L, config.getLong(key + ".tuluyen.total-seconds",
                        config.getLong(key + ".tuluyen.total-time", 0L)));
                String name = config.getString(key + ".name", "Unknown");
                allTimes.put(name, seconds);
            }
        }

        topTuLuyenTimeCache = allTimes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());
        lastTopTuLuyenTimeUpdate = System.currentTimeMillis();
    }

    @Override
    public List<Map.Entry<String, Long>> getTopTuLuyenTime() {
        // Update top cache every 5 minutes max
        if (System.currentTimeMillis() - lastTopTuLuyenTimeUpdate > 300000) {
            if (Bukkit.isPrimaryThread()) {
                updateTopTuLuyenTime();
            } else {
                Bukkit.getScheduler().runTask(plugin, this::updateTopTuLuyenTime);
            }
        }
        if (topTuLuyenTimeCache.isEmpty() && lastTopTuLuyenTimeUpdate == 0) {
            updateTopTuLuyenTime(); // Initial synchronous update if empty
        }
        return topTuLuyenTimeCache;
    }
}
