package com.turtle.tutiencore.core.infusion;

import java.util.UUID;

public record OwnedInfusion(String id, String typeId, String rarityId, long createdAt) {

    public static OwnedInfusion create(String typeId, String rarityId, long createdAt) {
        return new OwnedInfusion(UUID.randomUUID().toString(), typeId, rarityId, createdAt);
    }
}
