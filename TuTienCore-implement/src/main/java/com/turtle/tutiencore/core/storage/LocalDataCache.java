package com.turtle.tutiencore.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Ultra-fast local JSON cache that acts as a write-through layer between the in-memory
 * managers and the remote MySQL database.
 *
 * <h3>Why this exists</h3>
 * MySQL writes are async — there is a window between when data changes in memory and when
 * the DB confirms the write.  If the server crashes or the player relogs before the async
 * write completes, the DB still holds stale data and the player loses progress (Tu Vi → 0)
 * or sees phantom items (equipment dupe).
 *
 * <h3>Solution</h3>
 * <ul>
 *   <li>Every mutation also writes a tiny JSON file under {@code data/cache/<uuid>.json}
 *       <em>synchronously</em> (the file is small — a few hundred bytes — so the main-thread
 *       cost is negligible).
 *   </li>
 *   <li>On player join, data is read from the JSON cache immediately (no async wait), then
 *       the DB load may overwrite it later — but only if the DB timestamp is newer or the
 *       cache file does not exist.
 *   </li>
 *   <li>On server enable / crash recovery, any leftover cache files are flushed into the DB
 *       and then deleted.</li>
 * </ul>
 *
 * <h3>Equipment anti-dupe</h3>
 * The equipment cache stores the <em>current</em> slot state (type+id+duration) after every
 * equip/unequip.  On load the cache takes precedence over the DB, so a player who unequipped
 * an item and then immediately quit will never see that item re-appear.
 */
public class LocalDataCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JavaPlugin plugin;
    private final File cacheDir;

    public LocalDataCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cacheDir = new File(plugin.getDataFolder(), "data/cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLAYER PROGRESS (Tu Vi, Tu Luyen, Infusion, Realm)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes player progress to cache synchronously. Call on every mutation.
     */
    public void savePlayerProgress(UUID uuid, String playerName,
                                   double tuvi, long tuLuyenSeconds, String equippedInfusionId,
                                   List<InfusionEntry> infusions,
                                   int realmId, String subRealm,
                                   int breakthroughCount, long breakthroughCooldown) {
        JsonObject root = readOrNew(uuid);

        JsonObject progress = new JsonObject();
        progress.addProperty("playerName", playerName != null ? playerName : "");
        progress.addProperty("tuvi", tuvi);
        progress.addProperty("tuLuyenSeconds", tuLuyenSeconds);
        progress.addProperty("equippedInfusionId", equippedInfusionId != null ? equippedInfusionId : "");
        progress.addProperty("realmId", realmId);
        progress.addProperty("subRealm", subRealm != null ? subRealm : "SO_KY");
        progress.addProperty("breakthroughCount", breakthroughCount);
        progress.addProperty("breakthroughCooldown", breakthroughCooldown);
        progress.addProperty("savedAt", System.currentTimeMillis());

        JsonArray infusionArr = new JsonArray();
        if (infusions != null) {
            for (InfusionEntry e : infusions) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", e.id());
                obj.addProperty("typeId", e.typeId());
                obj.addProperty("rarityId", e.rarityId());
                obj.addProperty("createdAt", e.createdAt());
                infusionArr.add(obj);
            }
        }
        progress.add("infusions", infusionArr);
        root.add("progress", progress);
        write(uuid, root);
    }

    /** Returns null if no cache exists for this player. */
    public PlayerProgressSnapshot loadPlayerProgress(UUID uuid) {
        JsonObject root = read(uuid);
        if (root == null || !root.has("progress")) return null;
        JsonObject p = root.getAsJsonObject("progress");
        try {
            String playerName = str(p, "playerName", "");
            double tuvi = p.has("tuvi") ? p.get("tuvi").getAsDouble() : 0.0;
            long tuLuyenSeconds = p.has("tuLuyenSeconds") ? p.get("tuLuyenSeconds").getAsLong() : 0L;
            String equippedInfusionId = str(p, "equippedInfusionId", null);
            int realmId = p.has("realmId") ? p.get("realmId").getAsInt() : 1;
            String subRealm = str(p, "subRealm", "SO_KY");
            int breakthroughCount = p.has("breakthroughCount") ? p.get("breakthroughCount").getAsInt() : 0;
            long breakthroughCooldown = p.has("breakthroughCooldown") ? p.get("breakthroughCooldown").getAsLong() : 0L;

            List<InfusionEntry> infusions = new ArrayList<>();
            if (p.has("infusions")) {
                for (JsonElement el : p.getAsJsonArray("infusions")) {
                    JsonObject o = el.getAsJsonObject();
                    infusions.add(new InfusionEntry(
                            str(o, "id", ""),
                            str(o, "typeId", ""),
                            str(o, "rarityId", ""),
                            o.has("createdAt") ? o.get("createdAt").getAsLong() : System.currentTimeMillis()
                    ));
                }
            }
            return new PlayerProgressSnapshot(playerName, tuvi, tuLuyenSeconds, equippedInfusionId,
                    infusions, realmId, subRealm, breakthroughCount, breakthroughCooldown);
        } catch (Exception e) {
            plugin.getLogger().warning("[LocalDataCache] Corrupt progress cache for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EQUIPMENT (/trangbi slots + bound offhand)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes equipment slot state synchronously. The list contains ALL current slots
     * (empty slots should NOT be included — omitting a slot means it is empty).
     */
    public void saveEquipment(UUID uuid, List<EquipmentSlotEntry> slots, String offhandType, String offhandId) {
        JsonObject root = readOrNew(uuid);

        JsonArray slotsArr = new JsonArray();
        if (slots != null) {
            for (EquipmentSlotEntry s : slots) {
                JsonObject obj = new JsonObject();
                obj.addProperty("slotId", s.slotId());
                obj.addProperty("type", s.mmoType());
                obj.addProperty("id", s.mmoId());
                obj.addProperty("remaining", s.remainingSeconds());
                obj.addProperty("total", s.totalSeconds());
                slotsArr.add(obj);
            }
        }
        JsonObject equip = new JsonObject();
        equip.add("slots", slotsArr);
        equip.addProperty("offhandType", offhandType != null ? offhandType : "");
        equip.addProperty("offhandId", offhandId != null ? offhandId : "");
        equip.addProperty("savedAt", System.currentTimeMillis());
        root.add("equipment", equip);
        write(uuid, root);
    }

    /** Returns null if no equipment cache exists for this player. */
    public EquipmentSnapshot loadEquipment(UUID uuid) {
        JsonObject root = read(uuid);
        if (root == null || !root.has("equipment")) return null;
        JsonObject e = root.getAsJsonObject("equipment");
        try {
            List<EquipmentSlotEntry> slots = new ArrayList<>();
            if (e.has("slots")) {
                for (JsonElement el : e.getAsJsonArray("slots")) {
                    JsonObject o = el.getAsJsonObject();
                    slots.add(new EquipmentSlotEntry(
                            str(o, "slotId", ""),
                            str(o, "type", ""),
                            str(o, "id", ""),
                            o.has("remaining") ? o.get("remaining").getAsLong() : -1L,
                            o.has("total") ? o.get("total").getAsLong() : 0L
                    ));
                }
            }
            String offhandType = str(e, "offhandType", null);
            String offhandId = str(e, "offhandId", null);
            return new EquipmentSnapshot(slots, offhandType, offhandId);
        } catch (Exception ex) {
            plugin.getLogger().warning("[LocalDataCache] Corrupt equipment cache for " + uuid + ": " + ex.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLY SWORD
    // ─────────────────────────────────────────────────────────────────────────

    public void saveFlySword(UUID uuid, int level) {
        JsonObject root = readOrNew(uuid);
        JsonObject fs = new JsonObject();
        fs.addProperty("level", level);
        fs.addProperty("savedAt", System.currentTimeMillis());
        root.add("flySword", fs);
        write(uuid, root);
    }

    /** Returns -1 if not cached. */
    public int loadFlySwordLevel(UUID uuid) {
        JsonObject root = read(uuid);
        if (root == null || !root.has("flySword")) return -1;
        JsonObject fs = root.getAsJsonObject("flySword");
        return fs.has("level") ? fs.get("level").getAsInt() : -1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OFFLINE TU LUYEN
    // ─────────────────────────────────────────────────────────────────────────

    public void saveOfflineTuLuyen(UUID uuid, double pendingTuVi, long lastOfflineStart,
                                   long lastEarnedSeconds, long lastRealOfflineSeconds,
                                   double lastEarnedMultiplier) {
        JsonObject root = readOrNew(uuid);
        JsonObject ol = new JsonObject();
        ol.addProperty("pendingTuVi", pendingTuVi);
        ol.addProperty("lastOfflineStart", lastOfflineStart);
        ol.addProperty("lastEarnedSeconds", lastEarnedSeconds);
        ol.addProperty("lastRealOfflineSeconds", lastRealOfflineSeconds);
        ol.addProperty("lastEarnedMultiplier", lastEarnedMultiplier);
        ol.addProperty("savedAt", System.currentTimeMillis());
        root.add("offlineTuLuyen", ol);
        write(uuid, root);
    }

    public OfflineTuLuyenSnapshot loadOfflineTuLuyen(UUID uuid) {
        JsonObject root = read(uuid);
        if (root == null || !root.has("offlineTuLuyen")) return null;
        JsonObject o = root.getAsJsonObject("offlineTuLuyen");
        try {
            return new OfflineTuLuyenSnapshot(
                    o.has("pendingTuVi") ? o.get("pendingTuVi").getAsDouble() : 0.0,
                    o.has("lastOfflineStart") ? o.get("lastOfflineStart").getAsLong() : 0L,
                    o.has("lastEarnedSeconds") ? o.get("lastEarnedSeconds").getAsLong() : 0L,
                    o.has("lastRealOfflineSeconds") ? o.get("lastRealOfflineSeconds").getAsLong() : 0L,
                    o.has("lastEarnedMultiplier") ? o.get("lastEarnedMultiplier").getAsDouble() : 1.0
            );
        } catch (Exception e) {
            plugin.getLogger().warning("[LocalDataCache] Corrupt offline tuluyen cache for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called on server enable. Returns all UUIDs that have leftover cache files
     * so the caller (TuTienCore) can flush them into the DB.
     */
    public List<UUID> getPendingFlushUUIDs() {
        List<UUID> result = new ArrayList<>();
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;
        for (File f : files) {
            String name = f.getName();
            String uuidStr = name.substring(0, name.length() - 5); // strip .json
            try {
                result.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    /** Deletes the cache file for a player after a successful DB flush. */
    public void evict(UUID uuid) {
        File f = fileFor(uuid);
        if (f.exists()) f.delete();
    }

    /** Deletes the cache file (call on clean quit after DB save confirmed). */
    public void evictOnQuit(UUID uuid) {
        evict(uuid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOW-LEVEL I/O
    // ─────────────────────────────────────────────────────────────────────────

    private File fileFor(UUID uuid) {
        return new File(cacheDir, uuid + ".json");
    }

    private JsonObject read(UUID uuid) {
        File f = fileFor(uuid);
        if (!f.exists()) return null;
        try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[LocalDataCache] Failed to read " + uuid + ".json: " + e.getMessage());
            return null;
        }
    }

    private JsonObject readOrNew(UUID uuid) {
        JsonObject obj = read(uuid);
        return obj != null ? obj : new JsonObject();
    }

    private void write(UUID uuid, JsonObject root) {
        File f = fileFor(uuid);
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs();
            try (FileWriter writer = new FileWriter(f, StandardCharsets.UTF_8, false)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[LocalDataCache] Failed to write " + uuid + ".json: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        if (!o.has(key)) return fallback;
        JsonElement el = o.get(key);
        if (el.isJsonNull()) return fallback;
        String v = el.getAsString();
        return (v == null || v.isBlank()) ? fallback : v;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DATA RECORDS
    // ─────────────────────────────────────────────────────────────────────────

    public record InfusionEntry(String id, String typeId, String rarityId, long createdAt) {}

    public record EquipmentSlotEntry(String slotId, String mmoType, String mmoId,
                                     long remainingSeconds, long totalSeconds) {}

    public record PlayerProgressSnapshot(String playerName, double tuvi, long tuLuyenSeconds,
                                         String equippedInfusionId, List<InfusionEntry> infusions,
                                         int realmId, String subRealm,
                                         int breakthroughCount, long breakthroughCooldown) {}

    public record EquipmentSnapshot(List<EquipmentSlotEntry> slots,
                                    String offhandType, String offhandId) {}

    public record OfflineTuLuyenSnapshot(double pendingTuVi, long lastOfflineStart,
                                          long lastEarnedSeconds, long lastRealOfflineSeconds,
                                          double lastEarnedMultiplier) {}
}
