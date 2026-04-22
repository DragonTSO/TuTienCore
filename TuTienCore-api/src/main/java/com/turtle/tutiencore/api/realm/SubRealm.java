package com.turtle.tutiencore.api.realm;

/**
 * Tầng nhỏ — 5 bậc trong mỗi Đại Cảnh Giới
 */
public enum SubRealm {
    SO_KY("Sơ Kỳ", "Early Stage", 0),
    TRUNG_KY("Trung Kỳ", "Middle Stage", 1),
    HAU_KY("Hậu Kỳ", "Late Stage", 2),
    DINH_PHONG("Đỉnh Phong", "Peak", 3),
    VIEN_MAN("Viên Mãn", "Perfection", 4);

    private final String displayName;
    private final String englishName;
    private final int order;

    SubRealm(String displayName, String englishName, int order) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.order = order;
    }

    public String getDisplayName() { return displayName; }
    public String getEnglishName() { return englishName; }
    public int getOrder() { return order; }

    /**
     * Get the next sub-realm, or null if already at VIEN_MAN
     */
    public SubRealm next() {
        SubRealm[] values = values();
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal >= values.length) return null;
        return values[nextOrdinal];
    }

    /**
     * Check if this is the final sub-realm (Viên Mãn)
     */
    public boolean isMax() {
        return this == VIEN_MAN;
    }
}
