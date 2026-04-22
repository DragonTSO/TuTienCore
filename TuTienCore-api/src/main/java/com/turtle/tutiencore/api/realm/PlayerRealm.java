package com.turtle.tutiencore.api.realm;

/**
 * Represents a player's current realm state: major realm + sub-realm
 */
public class PlayerRealm {

    private int realmId;
    private SubRealm subRealm;
    private long breakthroughCooldown; // timestamp when cooldown expires

    public PlayerRealm(int realmId, SubRealm subRealm) {
        this.realmId = realmId;
        this.subRealm = subRealm;
        this.breakthroughCooldown = 0;
    }

    public int getRealmId() { return realmId; }
    public SubRealm getSubRealm() { return subRealm; }
    public long getBreakthroughCooldown() { return breakthroughCooldown; }

    public void setRealmId(int realmId) { this.realmId = realmId; }
    public void setSubRealm(SubRealm subRealm) { this.subRealm = subRealm; }
    public void setBreakthroughCooldown(long breakthroughCooldown) { this.breakthroughCooldown = breakthroughCooldown; }

    /**
     * Check if the player is currently on breakthrough cooldown
     */
    public boolean isOnCooldown() {
        return System.currentTimeMillis() < breakthroughCooldown;
    }

    /**
     * Get remaining cooldown time in seconds
     */
    public long getRemainingCooldownSeconds() {
        long remaining = breakthroughCooldown - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    /**
     * Apply breakthrough cooldown (1 hour = 3600000ms)
     */
    public void applyCooldown(long durationMs) {
        this.breakthroughCooldown = System.currentTimeMillis() + durationMs;
    }
}
