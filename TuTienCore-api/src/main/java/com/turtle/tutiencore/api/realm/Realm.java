package com.turtle.tutiencore.api.realm;

import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Đại diện cho một Đại Cảnh Giới (Major Realm) trong hệ thống tu luyện.
 */
public class Realm {

    private final int id;
    private final String name;
    private final String displayName;
    private final String englishName;
    private final RealmTier tier;
    private final long tuViRequired;
    private final long thucLucRequired;
    private final String color;

    // Sub-realm Tu Vi thresholds
    private final long soKyTuVi;
    private final long trungKyTuVi;
    private final long hauKyTuVi;
    private final long dinhPhongTuVi;
    private final long vienManTuVi;

    // Sub-realm display names (key = SubRealm enum)
    private final Map<SubRealm, String> subRealmDisplayNames = new HashMap<>();

    // Breakthrough settings
    private final int lightningBolts;
    private final double damagePerBolt;
    private final double successRate;

    public Realm(int id, String name, String displayName, String englishName, RealmTier tier,
                 long tuViRequired, long thucLucRequired, String color,
                 long soKyTuVi, long trungKyTuVi, long hauKyTuVi,
                 long dinhPhongTuVi, long vienManTuVi,
                 int lightningBolts, double damagePerBolt, double successRate) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.englishName = englishName;
        this.tier = tier;
        this.tuViRequired = tuViRequired;
        this.thucLucRequired = thucLucRequired;
        this.color = color;
        this.soKyTuVi = soKyTuVi;
        this.trungKyTuVi = trungKyTuVi;
        this.hauKyTuVi = hauKyTuVi;
        this.dinhPhongTuVi = dinhPhongTuVi;
        this.vienManTuVi = vienManTuVi;
        this.lightningBolts = lightningBolts;
        this.damagePerBolt = damagePerBolt;
        this.successRate = successRate;
    }

    // --- Sub-realm display name management ---

    public void setSubRealmDisplayName(SubRealm subRealm, String displayName) {
        subRealmDisplayNames.put(subRealm, displayName);
    }

    /**
     * Get the display name for a specific sub-realm (translated § codes).
     * Falls back to main realm display-name if not set (e.g. Sơ Kỳ).
     */
    public String getSubRealmDisplayNameTranslated(SubRealm subRealm) {
        String raw = subRealmDisplayNames.get(subRealm);
        if (raw == null) {
            // Fallback to main display-name
            return getDisplayNameTranslated();
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    // --- Getters ---

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEnglishName() { return englishName; }
    public RealmTier getTier() { return tier; }
    public long getTuViRequired() { return tuViRequired; }
    public long getThucLucRequired() { return thucLucRequired; }
    public String getColor() { return color; }
    public String getColorTranslated() { return ChatColor.translateAlternateColorCodes('&', color); }
    public String getDisplayName() { return displayName; }
    public String getDisplayNameTranslated() { return ChatColor.translateAlternateColorCodes('&', displayName); }

    public long getSoKyTuVi() { return soKyTuVi; }
    public long getTrungKyTuVi() { return trungKyTuVi; }
    public long getHauKyTuVi() { return hauKyTuVi; }
    public long getDinhPhongTuVi() { return dinhPhongTuVi; }
    public long getVienManTuVi() { return vienManTuVi; }

    public int getLightningBolts() { return lightningBolts; }
    public double getDamagePerBolt() { return damagePerBolt; }
    public double getSuccessRate() { return successRate; }

    public long getTuViForSubRealm(SubRealm subRealm) {
        switch (subRealm) {
            case SO_KY: return soKyTuVi;
            case TRUNG_KY: return trungKyTuVi;
            case HAU_KY: return hauKyTuVi;
            case DINH_PHONG: return dinhPhongTuVi;
            case VIEN_MAN: return vienManTuVi;
            default: return soKyTuVi;
        }
    }

    public String getFormattedName() {
        return getColorTranslated() + name;
    }

    public String getFullDisplay(SubRealm subRealm) {
        return getColorTranslated() + "[" + name + " — " + subRealm.getDisplayName() + "]";
    }

    public double getTotalDamageSuccess() { return lightningBolts * damagePerBolt; }
    public double getTotalDamageFail() { return lightningBolts * damagePerBolt * 2; }

    @Override
    public String toString() {
        return "Realm{id=" + id + ", name=" + name + "}";
    }
}
