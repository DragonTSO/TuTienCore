package com.turtle.tutiencore.api.realm;

import org.bukkit.ChatColor;

import java.util.Collections;
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
    private final double moneyRequired;
    private final String color;

    // Sub-realm Tu Vi thresholds
    private final long soKyTuVi;
    private final long trungKyTuVi;
    private final long hauKyTuVi;
    private final long dinhPhongTuVi;
    private final long vienManTuVi;

    // Sub-realm display names (key = SubRealm enum)
    private final Map<SubRealm, String> subRealmDisplayNames = new HashMap<>();
    private final Map<SubRealm, Long> subRealmThucLucRequirements = new HashMap<>();
    private final Map<SubRealm, Double> subRealmMoneyRequirements = new HashMap<>();

    // Breakthrough settings
    private final int lightningBolts;
    private final double damagePerBolt;
    private final double successRate;

    // Stat bonus on breakthrough success (stat name → percent value)
    private final Map<String, Double> statBonuses;

    public Realm(int id, String name, String displayName, String englishName, RealmTier tier,
                  long tuViRequired, long thucLucRequired, String color,
                  long soKyTuVi, long trungKyTuVi, long hauKyTuVi,
                  long dinhPhongTuVi, long vienManTuVi,
                 int lightningBolts, double damagePerBolt, double successRate,
                 Map<String, Double> statBonuses) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.englishName = englishName;
        this.tier = tier;
        this.tuViRequired = tuViRequired;
        this.thucLucRequired = thucLucRequired;
        this.moneyRequired = 0;
        this.color = color;
        this.soKyTuVi = soKyTuVi;
        this.trungKyTuVi = trungKyTuVi;
        this.hauKyTuVi = hauKyTuVi;
        this.dinhPhongTuVi = dinhPhongTuVi;
        this.vienManTuVi = vienManTuVi;
        this.lightningBolts = lightningBolts;
        this.damagePerBolt = damagePerBolt;
        this.successRate = successRate;
        this.statBonuses = statBonuses != null ? new HashMap<>(statBonuses) : new HashMap<>();
    }

    public Realm(int id, String name, String displayName, String englishName, RealmTier tier,
                 long tuViRequired, long thucLucRequired, double moneyRequired, String color,
                 long soKyTuVi, long trungKyTuVi, long hauKyTuVi,
                 long dinhPhongTuVi, long vienManTuVi,
                 int lightningBolts, double damagePerBolt, double successRate,
                 Map<String, Double> statBonuses) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.englishName = englishName;
        this.tier = tier;
        this.tuViRequired = tuViRequired;
        this.thucLucRequired = thucLucRequired;
        this.moneyRequired = moneyRequired;
        this.color = color;
        this.soKyTuVi = soKyTuVi;
        this.trungKyTuVi = trungKyTuVi;
        this.hauKyTuVi = hauKyTuVi;
        this.dinhPhongTuVi = dinhPhongTuVi;
        this.vienManTuVi = vienManTuVi;
        this.lightningBolts = lightningBolts;
        this.damagePerBolt = damagePerBolt;
        this.successRate = successRate;
        this.statBonuses = statBonuses != null ? new HashMap<>(statBonuses) : new HashMap<>();
    }

    // --- Sub-realm display name management ---

    public void setSubRealmDisplayName(SubRealm subRealm, String displayName) {
        subRealmDisplayNames.put(subRealm, displayName);
    }

    public void setSubRealmRequirements(SubRealm subRealm, long thucLucRequired, double moneyRequired) {
        subRealmThucLucRequirements.put(subRealm, thucLucRequired);
        subRealmMoneyRequirements.put(subRealm, moneyRequired);
    }

    public void setSubRealmThucLucRequirement(SubRealm subRealm, long thucLucRequired) {
        subRealmThucLucRequirements.put(subRealm, thucLucRequired);
    }

    public void setSubRealmMoneyRequirement(SubRealm subRealm, double moneyRequired) {
        subRealmMoneyRequirements.put(subRealm, moneyRequired);
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
    public double getMoneyRequired() { return moneyRequired; }
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

    /**
     * Get all stat bonuses for this realm (stat name → percent value).
     */
    public Map<String, Double> getStatBonuses() {
        return Collections.unmodifiableMap(statBonuses);
    }

    /**
     * Get the bonus percent for a specific stat.
     */
    public double getStatBonus(String statName) {
        return statBonuses.getOrDefault(statName, 0.0);
    }

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

    public long getThucLucForSubRealm(SubRealm subRealm) {
        Long configured = subRealmThucLucRequirements.get(subRealm);
        return configured != null ? configured : scaleSubRealmRequirement(thucLucRequired, subRealm);
    }

    public double getMoneyForSubRealm(SubRealm subRealm) {
        Double configured = subRealmMoneyRequirements.get(subRealm);
        return configured != null ? configured : scaleSubRealmRequirement(moneyRequired, subRealm);
    }

    private long scaleSubRealmRequirement(long requirement, SubRealm subRealm) {
        return (long) scaleSubRealmRequirement((double) requirement, subRealm);
    }

    private double scaleSubRealmRequirement(double requirement, SubRealm subRealm) {
        if (requirement <= 0 || subRealm == null) return 0;
        switch (subRealm) {
            case SO_KY: return 0;
            case TRUNG_KY: return requirement * 0.25;
            case HAU_KY: return requirement * 0.5;
            case DINH_PHONG: return requirement * 0.75;
            case VIEN_MAN: return requirement;
            default: return 0;
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
