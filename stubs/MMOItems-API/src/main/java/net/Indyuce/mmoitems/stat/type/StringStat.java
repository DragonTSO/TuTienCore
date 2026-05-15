package net.Indyuce.mmoitems.stat.type;

import java.util.ArrayList;
import org.bukkit.Material;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.player.RPGPlayer;
import net.Indyuce.mmoitems.stat.data.StringData;

public class StringStat implements ItemStat {
    protected String generalStatFormat = "{value}";
    private final String id;

    public StringStat(String id, Material material, String name, String[] lore, String[] options, Material... extraMaterials) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public ArrayList getAppliedNBT(StringData data) {
        return new ArrayList<>();
    }

    public String getGeneralStatFormat() {
        return generalStatFormat;
    }

    public String getPath() {
        return id.toLowerCase();
    }

    public String getNBTPath() {
        return getPath();
    }

    public void whenApplied(ItemStackBuilder item, StringData data) {
    }

    public boolean canUse(RPGPlayer rpgPlayer, NBTItem item, boolean message) {
        return true;
    }
}
