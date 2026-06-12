package com.turtle.tutiencore.core.hook;

import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.config.ConfigManager;
import com.turtle.tutiencore.core.manager.RealmManager;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.version.Sounds;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.build.LoreBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.player.RPGPlayer;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.ItemRestriction;
import net.Indyuce.mmoitems.stat.type.StringStat;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class MMOItemsRealmRequirementStat extends StringStat implements ItemRestriction {

    static final String STAT_ID = "TUTIEN_REALM_REQUIREMENT";
    private static final String DEFAULT_STAT_FORMAT = "{value}";
    private static final String BYPASS_PERMISSION = "tutiencore.bypass.realm_requirement";

    private final RealmManager realmManager;
    private final ConfigManager configManager;

    MMOItemsRealmRequirementStat(RealmManager realmManager) {
        this(realmManager, null);
    }

    MMOItemsRealmRequirementStat(RealmManager realmManager, ConfigManager configManager) {
        super(
                STAT_ID,
                Material.ENDER_EYE,
                "Yeu Cau Canh Gioi",
                new String[]{
                        "Canh gioi toi thieu de dung vat pham.",
                        "Ho tro ca dang '4' va '4:trung-ky'."
                },
                new String[]{"!block", "all"}
        );
        this.realmManager = realmManager;
        this.configManager = configManager;
        this.generalStatFormat = DEFAULT_STAT_FORMAT;
    }

    @Override
    public void whenApplied(net.Indyuce.mmoitems.api.item.build.ItemStackBuilder item, StringData data) {
        MMOItemsRealmRequirement requirement = MMOItemsRealmRequirement.parse(data.toString());

        item.addItemTag(getAppliedNBT(new StringData(requirement.asConfigValue())));
        applyRequirementLore(item.getLore(), requirement);
    }

    void applyRequirementLore(LoreBuilder lore, MMOItemsRealmRequirement requirement) {
        String formatted = getGeneralStatFormat().replace("{value}", formatRequirement(requirement));
        String marker = "#" + getPath() + "#";

        if (lore.getLore().contains(marker)) {
            lore.insert(getPath(), formatted);
            return;
        }

        lore.getLore().add(formatted);
    }

    @Override
    public boolean canUse(RPGPlayer rpgPlayer, NBTItem item, boolean message) {
        return canUse(rpgPlayer.getPlayer(), item, message);
    }

    boolean canUse(Player player, NBTItem item, boolean message) {
        // First try reading NBT tag directly (items built after stat was registered)
        String raw = item.getString(getNBTPath());

        // Fallback: if NBT tag is missing (item built before stat was registered),
        // read the requirement directly from the MMOItems template config
        if (raw == null || raw.isEmpty()) {
            raw = readRawFromTemplate(item);
        }

        if (raw == null || raw.isEmpty()) {
            return true;
        }

        if (player.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }

        MMOItemsRealmRequirement requirement;
        try {
            requirement = MMOItemsRealmRequirement.parse(raw);
        } catch (IllegalArgumentException ex) {
            return true;
        }

        PlayerRealm playerRealm = realmManager.getPlayerRealm(player.getUniqueId());
        if (requirement.isMetBy(playerRealm.getRealmId(), playerRealm.getSubRealm())) {
            return true;
        }

        if (message) {
            String msg = configManager != null
                ? configManager.getMessage("realm-requirement-failed",
                    "&cCảnh giới của bạn chưa đủ để sử dụng vật phẩm này. Cần: &e{required}")
                    .replace("{required}", formatRequirement(requirement))
                : ChatColor.RED + "Canh gioi cua ban chua du de su dung vat pham nay. Can: " + formatRequirement(requirement);
            player.sendMessage(msg);
            player.playSound(player.getLocation(), Sounds.ENTITY_VILLAGER_NO, 1f, 1.5f);
        }
        return false;
    }

    /**
     * Fallback for items that were generated before this stat was registered and
     * therefore do not carry the MMOITEMS_TUTIEN_REALM_REQUIREMENT NBT tag.
     * Reads the requirement directly from the MMOItems template config.
     */
    private static String readRawFromTemplate(NBTItem item) {
        try {
            String typeId = item.getString("MMOITEMS_ITEM_TYPE");
            String itemId = item.getString("MMOITEMS_ITEM_ID");
            if (typeId == null || typeId.isEmpty() || itemId == null || itemId.isEmpty()) {
                return null;
            }

            Type type = Type.get(typeId);
            if (type == null) return null;

            MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, itemId);
            if (mmoItem == null) return null;

            net.Indyuce.mmoitems.stat.type.ItemStat<?, ?> stat = MMOItems.plugin.getStats().get(STAT_ID);
            if (stat == null) return null;

            net.Indyuce.mmoitems.stat.data.type.StatData data = mmoItem.getData(stat);
            if (data instanceof StringData) {
                return ((StringData) data).toString();
            }
        } catch (Exception ignored) {
            // Defensive: if anything fails, fall through to allow (safe default)
        }
        return null;
    }

    private String formatRequirement(MMOItemsRealmRequirement requirement) {
        String configValue = requirement.asConfigValue();
        if (realmManager == null) {
            return configValue;
        }

        Realm realm = realmManager.getRealm(requirement.realmId());
        if (realm == null) {
            return ChatColor.RED + configValue;
        }

        String display;
        if (requirement.subRealm().isEmpty()) {
            display = realm.getDisplayNameTranslated();
        } else {
            SubRealm subRealm = requirement.subRealm().orElseThrow();
            display = realm.getSubRealmDisplayNameTranslated(subRealm);
        }

        return ChatColor.GRAY + configValue + ChatColor.DARK_GRAY + " - " + display;
    }
}
