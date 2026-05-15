package io.lumine.mythic.lib.api.item;

import org.bukkit.inventory.ItemStack;

public class NBTItem {
    public static NBTItem get(ItemStack item) {
        return new NBTItem();
    }

    public String getString(String path) {
        return null;
    }
}
