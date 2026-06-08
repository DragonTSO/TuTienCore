package com.turtle.tutiencore.api;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main API interface for TuTienCore.
 * <p>
 * Obtain an instance via {@link TuTien#getApi()}.
 * <p>
 * Usage from another plugin:
 * <pre>
 *   TuTienAPI api = TuTien.getApi();
 *   double tuvi = api.getTuVi(player.getUniqueId());
 *   Realm realm = api.getPlayerRealm(player.getUniqueId());
 * </pre>
 */
public interface TuTienAPI {

    // ==========================================
    // TU VI (Cultivation Points)
    // ==========================================

    /**
     * Get the cultivation points (Tu Vi) of a player.
     * @param uuid Player's UUID
     * @return Amount of Tu Vi
     */
    double getTuVi(UUID uuid);

    /**
     * Set the cultivation points (Tu Vi) of a player.
     * @param uuid Player's UUID
     * @param amount New Tu Vi amount
     */
    void setTuVi(UUID uuid, double amount);

    /**
     * Add cultivation points (Tu Vi) to a player.
     * @param uuid Player's UUID
     * @param amount Amount to add
     */
    void addTuVi(UUID uuid, double amount);

    /**
     * Take cultivation points (Tu Vi) from a player.
     * @param uuid Player's UUID
     * @param amount Amount to remove
     */
    void takeTuVi(UUID uuid, double amount);

    /**
     * Get the top players by Tu Vi.
     * @return List of entries mapping Player Name to Tu Vi amount
     */
    List<Map.Entry<String, Double>> getTopTuVi();

    /**
     * Get the top players by total /tuluyen time.
     * @return List of entries mapping Player Name to total /tuluyen seconds
     */
    List<Map.Entry<String, Long>> getTopTuLuyenTime();

    // ==========================================
    // REALM (Cảnh Giới)
    // ==========================================

    /**
     * Get a player's current realm ID (1-19).
     * @param uuid Player's UUID
     * @return Realm ID (1 = Phàm Nhân, 19 = Hồng Mông)
     */
    int getRealmId(UUID uuid);

    /**
     * Get a player's current Realm object (contains name, tier, thresholds, etc.).
     * @param uuid Player's UUID
     * @return Realm object, or null if not found
     */
    Realm getRealm(UUID uuid);

    /**
     * Get a Realm by its ID.
     * @param realmId Realm ID (1-19)
     * @return Realm object, or null
     */
    Realm getRealmById(int realmId);

    /**
     * Get all registered realms.
     * @return Unmodifiable map of realm ID → Realm
     */
    Map<Integer, Realm> getAllRealms();

    /**
     * Get the maximum realm ID configured.
     * @return Max realm ID (default 19)
     */
    int getMaxRealmId();

    /**
     * Get the PlayerRealm data (realm ID + sub-realm + cooldown).
     * @param uuid Player's UUID
     * @return PlayerRealm object
     */
    PlayerRealm getPlayerRealmData(UUID uuid);

    /**
     * Get a player's current sub-realm.
     * @param uuid Player's UUID
     * @return SubRealm enum value
     */
    SubRealm getSubRealm(UUID uuid);

    /**
     * Set a player's realm directly (admin/debug use).
     * Does NOT trigger breakthrough events.
     * @param uuid Player's UUID
     * @param realmId Target realm ID (1-19)
     * @param subRealm Target sub-realm
     */
    void setRealm(UUID uuid, int realmId, SubRealm subRealm);

    /**
     * Check if a player is at the maximum realm.
     * @param uuid Player's UUID
     * @return true if at max realm
     */
    boolean isMaxRealm(UUID uuid);

    // ==========================================
    // REALM DISPLAY
    // ==========================================

    /**
     * Get the formatted realm display for a player.
     * Example: "§a[Luyện Khí — Đỉnh Phong]"
     * @param uuid Player's UUID
     * @return Formatted display string (with § color codes)
     */
    String getRealmDisplay(UUID uuid);

    /**
     * Get the display name for a player's current realm + sub-realm.
     * Uses sub-realm-specific display names from config.
     * Example: "§a「Luyện Khí ♦ Đỉnh Phong」"
     * @param uuid Player's UUID
     * @return Display name (with § color codes)
     */
    String getRealmDisplayName(UUID uuid);

    /**
     * Get the realm name with color (no sub-realm).
     * Example: "§aLuyện Khí"
     * @param uuid Player's UUID
     * @return Realm name with color
     */
    String getRealmName(UUID uuid);

    /**
     * Get the sub-realm display name.
     * Example: "Đỉnh Phong"
     * @param uuid Player's UUID
     * @return Sub-realm name
     */
    String getSubRealmName(UUID uuid);

    /**
     * Get the Đại Giới (realm tier) display name.
     * Example: "Phàm Giới", "Tiên Giới", "Thần Giới"
     * @param uuid Player's UUID
     * @return Tier display name
     */
    String getRealmTierName(UUID uuid);

    // ==========================================
    // BREAKTHROUGH (Đột Phá)
    // ==========================================

    /**
     * Check if a player is currently in the middle of a breakthrough.
     * @param uuid Player's UUID
     * @return true if breakthrough is active
     */
    boolean isInBreakthrough(UUID uuid);

    /**
     * Check if a player is on breakthrough cooldown.
     * @param uuid Player's UUID
     * @return true if on cooldown
     */
    boolean isOnBreakthroughCooldown(UUID uuid);

    /**
     * Get remaining breakthrough cooldown in seconds.
     * @param uuid Player's UUID
     * @return Seconds remaining (0 if no cooldown)
     */
    long getBreakthroughCooldownRemaining(UUID uuid);

    /**
     * Check if a player can breakthrough to the next major realm.
     * @param uuid Player's UUID
     * @return true if all conditions are met (Tu Vi, sub-realm Viên Mãn, no cooldown)
     */
    boolean canBreakthrough(UUID uuid);

    // ==========================================
    // TU LUYEN (Meditation)
    // ==========================================

    /**
     * Check if a player is currently meditating (tu luyện).
     * @param uuid Player's UUID
     * @return true if meditating
     */
    boolean isTuLuyen(UUID uuid);

    /**
     * Get all players currently meditating.
     * @return Collection of UUIDs
     */
    Collection<UUID> getTuLuyenPlayers();

    /**
     * Get total online cultivation time accumulated by a player.
     * @param uuid Player's UUID
     * @return Total seconds spent in /tuluyen
     */
    long getTuLuyenTotalSeconds(UUID uuid);

    /**
     * Set total online cultivation time for a player.
     * @param uuid Player's UUID
     * @param seconds Total seconds spent in /tuluyen
     */
    void setTuLuyenTotalSeconds(UUID uuid, long seconds);

    /**
     * Add online cultivation time to a player.
     * @param uuid Player's UUID
     * @param seconds Seconds to add
     */
    void addTuLuyenTotalSeconds(UUID uuid, long seconds);

    /**
     * Get the current /tuluyen session duration.
     * @param uuid Player's UUID
     * @return Current session seconds, or 0 if not cultivating
     */
    long getTuLuyenSessionSeconds(UUID uuid);

    // ==========================================
    // UTILITY
    // ==========================================

    /**
     * Format a number in compact form (e.g. 1.5K, 2.3M, 1.0B).
     * @param number The number to format
     * @return Formatted string
     */
    String formatNumber(long number);
}
