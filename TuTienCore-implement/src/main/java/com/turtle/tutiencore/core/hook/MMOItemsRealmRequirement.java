package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.api.realm.SubRealm;

import java.util.Locale;
import java.util.Optional;

final class MMOItemsRealmRequirement {

    private final int realmId;
    private final SubRealm subRealm;

    private MMOItemsRealmRequirement(int realmId, SubRealm subRealm) {
        this.realmId = realmId;
        this.subRealm = subRealm;
    }

    static MMOItemsRealmRequirement parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Realm requirement cannot be blank");
        }

        String[] parts = raw.trim().split(":", 2);
        int realmId = Integer.parseInt(parts[0].trim());
        if (realmId <= 0) {
            throw new IllegalArgumentException("Realm requirement must be a positive realm id");
        }

        SubRealm subRealm = null;
        if (parts.length == 2 && !parts[1].trim().isEmpty()) {
            subRealm = parseSubRealm(parts[1]);
        }

        return new MMOItemsRealmRequirement(realmId, subRealm);
    }

    Optional<SubRealm> subRealm() {
        return Optional.ofNullable(subRealm);
    }

    int realmId() {
        return realmId;
    }

    boolean isMetBy(int currentRealmId, SubRealm currentSubRealm) {
        if (currentRealmId > realmId) return true;
        if (currentRealmId < realmId) return false;
        if (subRealm == null) return true;
        return currentSubRealm != null && currentSubRealm.getOrder() >= subRealm.getOrder();
    }

    String asConfigValue() {
        if (subRealm == null) {
            return String.valueOf(realmId);
        }
        return realmId + ":" + toConfigToken(subRealm);
    }

    private static SubRealm parseSubRealm(String raw) {
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return SubRealm.valueOf(normalized);
    }

    private static String toConfigToken(SubRealm subRealm) {
        return subRealm.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
