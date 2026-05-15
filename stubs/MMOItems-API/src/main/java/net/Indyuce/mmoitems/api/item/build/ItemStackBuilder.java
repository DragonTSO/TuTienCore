package net.Indyuce.mmoitems.api.item.build;

import java.util.List;

public class ItemStackBuilder {
    private final LoreBuilder lore = new LoreBuilder();

    public void addItemTag(List tags) {
    }

    public LoreBuilder getLore() {
        return lore;
    }
}
