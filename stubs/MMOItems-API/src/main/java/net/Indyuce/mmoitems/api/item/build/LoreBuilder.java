package net.Indyuce.mmoitems.api.item.build;

import java.util.ArrayList;
import java.util.List;

public class LoreBuilder {
    private final List<String> lore = new ArrayList<>();

    public List<String> getLore() {
        return lore;
    }

    public void insert(String path, String... values) {
        for (String value : values) {
            lore.add(value);
        }
    }

    public void insert(String path, List<String> values) {
        lore.addAll(values);
    }
}
