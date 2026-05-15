package net.Indyuce.mmoitems.stat.type;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.api.player.RPGPlayer;

public interface ItemRestriction {
    boolean canUse(RPGPlayer rpgPlayer, NBTItem item, boolean message);
}
