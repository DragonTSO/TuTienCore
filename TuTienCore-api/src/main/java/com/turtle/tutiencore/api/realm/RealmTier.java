package com.turtle.tutiencore.api.realm;

/**
 * Đại Giới — 3 tầng lớn của hệ thống tu luyện
 */
public enum RealmTier {
    PHAM_GIOI("Phàm Giới", "Mortal Realm", "§a"),
    TIEN_GIOI("Tiên Giới", "Immortal Realm", "§b"),
    THAN_GIOI("Thần Giới", "Divine Realm", "§d");

    private final String displayName;
    private final String englishName;
    private final String color;

    RealmTier(String displayName, String englishName, String color) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getEnglishName() { return englishName; }
    public String getColor() { return color; }
}
