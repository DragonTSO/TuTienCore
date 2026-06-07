package com.turtle.tutiencore.core.infusion;

import com.turtle.tutiencore.core.manager.PlayerDataManager;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.player.PlayerData;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class InfusionManager implements Listener {

    public enum GiveResult {
        SUCCESS,
        DISABLED,
        INVALID_TYPE,
        INVALID_RARITY,
        INVENTORY_FULL,
        SAVE_FAILED
    }

    private static final String MODIFIER_PREFIX = "tutien_infusion_";
    private static final String TU_VI_BONUS_STAT = "tu_vi_bonus";
    private static final String DROP_TYPE_MMOITEMS = "mmoitems";
    private static final String DEFAULT_MMO_FLAME_TYPE = "LUA_THAN";
    private static final int GUI_SIZE = 27;
    private static final int EQUIPPED_SLOT = 13;
    private static final int PREV_SLOT = 9;
    private static final int INFO_SLOT = 10;
    private static final int CLOSE_SLOT = 16;
    private static final int NEXT_SLOT = 17;
    private static final List<Integer> STORAGE_SLOTS = List.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            18, 19, 20, 21, 22, 23, 24, 25, 26
    );
    private static final Map<String, String> STAT_DISPLAY_NAMES = createStatDisplayNames();

    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final NamespacedKey flameTypeKey;
    private final NamespacedKey flameRarityKey;
    private final NamespacedKey flameIdKey;
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Set<String> missingStatWarnings = new HashSet<>();
    private final Set<String> missingDropWarnings = new HashSet<>();
    private final Set<String> missingMmoFlameWarnings = new HashSet<>();

    private File configFile;
    private FileConfiguration config;
    private String guiTitle;
    private boolean featureEnabled;
    private boolean mmoItemsFlamesEnabled;
    private String mmoItemsFlameType;
    private String mmoItemsIdFormat;

    private final Map<String, InfusionType> typesByLookup = new LinkedHashMap<>();
    private final Map<String, InfusionRarity> raritiesByLookup = new LinkedHashMap<>();
    private final Map<String, InfusionType> typeAliasesByLookup = new HashMap<>();
    private final Map<String, InfusionRarity> rarityAliasesByLookup = new HashMap<>();

    public InfusionManager(JavaPlugin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.flameTypeKey = new NamespacedKey(plugin, "lua_than_type");
        this.flameRarityKey = new NamespacedKey(plugin, "lua_than_rarity");
        this.flameIdKey = new NamespacedKey(plugin, "lua_than_id");
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyEquippedInfusion(player), 2L);
        }
    }

    public void reload() {
        loadConfig();
        loadDefinitions();
        missingStatWarnings.clear();
        missingDropWarnings.clear();
        missingMmoFlameWarnings.clear();
    }

    public boolean isFeatureEnabled() {
        return featureEnabled;
    }

    public String message(String key) {
        return colorize(config.getString("messages." + key, "&cMissing message: messages." + key));
    }

    public String message(String key, Map<String, String> placeholders) {
        String raw = config.getString("messages." + key, "&cMissing message: messages." + key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        return colorize(raw);
    }

    public List<String> getTypeSuggestions() {
        List<String> out = new ArrayList<>();
        for (InfusionType type : typesByLookup.values()) {
            out.add(type.id());
        }
        return out;
    }

    public List<String> getRaritySuggestions() {
        Set<String> out = new LinkedHashSet<>();
        for (InfusionRarity rarity : raritiesByLookup.values()) {
            out.add(toSuggestionKey(rarity.displayName()));
        }
        return new ArrayList<>(out);
    }

    public GiveResult giveInfusion(UUID targetUuid, String typeInput, String rarityInput) {
        if (!featureEnabled) {
            return GiveResult.DISABLED;
        }

        InfusionType type = findType(typeInput);
        if (type == null) {
            return GiveResult.INVALID_TYPE;
        }

        InfusionRarity rarity = findRarity(rarityInput);
        if (rarity == null) {
            return GiveResult.INVALID_RARITY;
        }

        if (!playerDataManager.canAddInfusion(targetUuid)) {
            return GiveResult.INVENTORY_FULL;
        }

        boolean saved = playerDataManager.addInfusion(targetUuid, OwnedInfusion.create(type.id(), rarity.id(), System.currentTimeMillis()));
        if (!saved) {
            return GiveResult.SAVE_FAILED;
        }

        return GiveResult.SUCCESS;
    }

    public GiveResult giveFlameItem(Player target, String typeInput, String rarityInput) {
        if (!featureEnabled) {
            return GiveResult.DISABLED;
        }
        if (target == null) {
            return GiveResult.SAVE_FAILED;
        }

        InfusionType type = findType(typeInput);
        if (type == null) {
            return GiveResult.INVALID_TYPE;
        }

        InfusionRarity rarity = findRarity(rarityInput);
        if (rarity == null) {
            return GiveResult.INVALID_RARITY;
        }

        ItemStack item = createFlameItem(target, type, rarity);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        for (ItemStack extra : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), extra);
        }
        scheduleHeldRefresh(target);
        return GiveResult.SUCCESS;
    }

    public void refreshIfOpen(Player player) {
        if (player == null || player.getOpenInventory() == null) {
            return;
        }
        if (!guiTitle.equals(player.getOpenInventory().getTitle())) {
            return;
        }
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        open(player, page);
    }

    public void open(Player player) {
        open(player, currentPage.getOrDefault(player.getUniqueId(), 0));
    }

    public double getEquippedTuViBonusPercent(Player player) {
        if (player == null) {
            return 0.0D;
        }

        Optional<HeldInfusion> held = getHeldInfusion(player);
        if (held.isEmpty()) {
            return 0.0D;
        }

        InfusionType type = findType(held.get().typeId());
        InfusionRarity rarity = findRarity(held.get().rarityId());
        if (type == null || rarity == null) {
            return 0.0D;
        }

        return computeTuViBonusPercent(type, rarity);
    }

    public Optional<HeldInfusion> getHeldInfusion(Player player) {
        if (player == null || player.getInventory() == null) {
            return Optional.empty();
        }

        Optional<HeldInfusion> mainHand = readFlameItem(player.getInventory().getItemInMainHand());
        if (mainHand.isPresent()) {
            return mainHand;
        }
        return readFlameItem(player.getInventory().getItemInOffHand());
    }

    public void rollHeldTuluyenDrops(Player player, boolean turtleIslandEligible) {
        if (!featureEnabled || player == null) {
            return;
        }

        Optional<HeldInfusion> held = getHeldInfusion(player);
        if (held.isEmpty()) {
            return;
        }

        InfusionType type = findType(held.get().typeId());
        if (type == null || !type.tuluyenDrops().enabled() || type.tuluyenDrops().items().isEmpty()) {
            return;
        }
        if (type.tuluyenDrops().requireTurtleIslandBonus() && !turtleIslandEligible) {
            return;
        }

        for (int roll = 0; roll < type.tuluyenDrops().rollsPerInterval(); roll++) {
            for (InfusionType.TuluyenDropItem drop : type.tuluyenDrops().items()) {
                if (!rollChance(drop.chance(), ThreadLocalRandom.current().nextDouble(100.0D))) {
                    continue;
                }

                giveDrop(player, drop);
            }
        }
    }

    private void open(Player player, int requestedPage) {
        if (player == null) {
            return;
        }

        if (!featureEnabled) {
            player.sendMessage(message("feature-disabled"));
            return;
        }

        List<OwnedInfusion> inventory = playerDataManager.getInfusionInventory(player.getUniqueId());
        int pageCount = Math.max(1, (int) Math.ceil((double) inventory.size() / STORAGE_SLOTS.size()));
        int page = clampPage(requestedPage, pageCount);
        currentPage.put(player.getUniqueId(), page);

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, guiTitle);
        fillBackground(gui);

        gui.setItem(EQUIPPED_SLOT, buildEquippedItem(player));
        gui.setItem(INFO_SLOT, buildInfoItem(player, page, pageCount));
        gui.setItem(CLOSE_SLOT, buildTemplateItem("gui.item-close", Material.BARRIER, "&cDong", List.of("&7Dong menu.")));
        gui.setItem(PREV_SLOT, buildPageButton("gui.item-prev-page", Material.ARROW, "&eTrang truoc", page > 0));
        gui.setItem(NEXT_SLOT, buildPageButton("gui.item-next-page", Material.ARROW, "&eTrang sau", page + 1 < pageCount));

        int start = page * STORAGE_SLOTS.size();
        for (int i = 0; i < STORAGE_SLOTS.size(); i++) {
            int index = start + i;
            if (index >= inventory.size()) {
                continue;
            }
            OwnedInfusion owned = inventory.get(index);
            gui.setItem(STORAGE_SLOTS.get(i), buildStorageItem(player, owned));
        }

        player.openInventory(gui);
    }

    public void applyEquippedInfusion(Player player) {
        removeAllModifiers(player);
        refreshHeldFlameLore(player);
        Optional<HeldInfusion> held = getHeldInfusion(player);
        if (held.isEmpty()) {
            return;
        }

        try {
            applyInfusionOrThrow(player, held.get().typeId(), held.get().rarityId());
        } catch (Exception exception) {
            plugin.getLogger().warning("Lua Than apply failed for " + player.getName() + ": " + exception.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyEquippedInfusion(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeAllModifiers(event.getPlayer());
        currentPage.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleHeldRefresh(player);

        if (!guiTitle.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (!featureEnabled) {
            player.sendMessage(message("feature-disabled"));
            player.closeInventory();
            return;
        }

        UUID uuid = player.getUniqueId();
        int page = currentPage.getOrDefault(uuid, 0);

        if (slot == PREV_SLOT) {
            open(player, page - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            open(player, page + 1);
            return;
        }

        if (slot == EQUIPPED_SLOT) {
            if (unequip(player)) {
                open(player, page);
            } else {
                player.sendMessage(message("unequip-failed"));
            }
            return;
        }

        int storageIndexInPage = STORAGE_SLOTS.indexOf(slot);
        if (storageIndexInPage < 0) {
            return;
        }

        List<OwnedInfusion> inventory = playerDataManager.getInfusionInventory(uuid);
        int absoluteIndex = page * STORAGE_SLOTS.size() + storageIndexInPage;
        if (absoluteIndex < 0 || absoluteIndex >= inventory.size()) {
            player.sendMessage(message("item-no-longer-available"));
            open(player, page);
            return;
        }

        OwnedInfusion selected = inventory.get(absoluteIndex);
        if (equip(player, selected.id())) {
            open(player, page);
        } else {
            player.sendMessage(message("equip-failed"));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleHeldRefresh(player);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        scheduleHeldRefresh(event.getPlayer());
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        scheduleHeldRefresh(event.getPlayer());
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        scheduleHeldRefresh(event.getPlayer());
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            scheduleHeldRefresh(player);
        }
    }

    private boolean equip(Player player, String infusionId) {
        UUID uuid = player.getUniqueId();
        Optional<OwnedInfusion> selected = playerDataManager.findInfusion(uuid, infusionId);
        if (selected.isEmpty()) {
            return false;
        }

        String previousEquippedId = playerDataManager.getEquippedInfusionId(uuid);
        Optional<OwnedInfusion> previous = playerDataManager.getEquippedInfusion(uuid);

        removeAllModifiers(player);
        try {
            applyOwnedInfusionOrThrow(player, selected.get());
        } catch (Exception applyEx) {
            removeAllModifiers(player);
            restorePrevious(player, previous);
            return false;
        }

        boolean saved = playerDataManager.setEquippedInfusionId(uuid, selected.get().id());
        if (!saved) {
            removeAllModifiers(player);
            restorePrevious(player, previous);
            if (previousEquippedId == null || previousEquippedId.isBlank()) {
                playerDataManager.setEquippedInfusionId(uuid, null);
            } else {
                playerDataManager.setEquippedInfusionId(uuid, previousEquippedId);
            }
            return false;
        }

        return true;
    }

    private boolean unequip(Player player) {
        UUID uuid = player.getUniqueId();
        String previousId = playerDataManager.getEquippedInfusionId(uuid);
        if (previousId == null || previousId.isBlank()) {
            return true;
        }

        removeAllModifiers(player);
        boolean saved = playerDataManager.setEquippedInfusionId(uuid, null);
        if (!saved) {
            playerDataManager.setEquippedInfusionId(uuid, previousId);
            applyEquippedInfusion(player);
            return false;
        }
        return true;
    }

    private void restorePrevious(Player player, Optional<OwnedInfusion> previous) {
        if (previous.isEmpty()) {
            return;
        }
        try {
            applyOwnedInfusionOrThrow(player, previous.get());
        } catch (Exception ignored) {
        }
    }

    private void applyOwnedInfusionOrThrow(Player player, OwnedInfusion owned) {
        applyInfusionOrThrow(player, owned.typeId(), owned.rarityId());
    }

    private void applyInfusionOrThrow(Player player, String typeId, String rarityId) {
        InfusionType type = findType(typeId);
        InfusionRarity rarity = findRarity(rarityId);
        if (type == null || rarity == null) {
            throw new IllegalStateException("Missing type or rarity config for held flame");
        }

        MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());
        if (mmoData == null || mmoData.getStatMap() == null) {
            throw new IllegalStateException("MythicLib stat map unavailable");
        }

        StatMap statMap = mmoData.getStatMap();
        for (Map.Entry<String, Double> entry : type.stats().entrySet()) {
            String stat = entry.getKey();
            StatInstance instance = statMap.getInstance(stat);
            if (instance == null) {
                logMissingStat(type.id(), rarity.id(), stat);
                continue;
            }

            double relativeValue = computeRelativeValue(entry.getValue(), rarity.multiplier());
            if (relativeValue <= 0D) {
                continue;
            }

            instance.addModifier(new StatModifier(modifierKey(stat), stat, relativeValue, ModifierType.RELATIVE));
        }
    }

    private void removeAllModifiers(Player player) {
        try {
            MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());
            if (mmoData == null || mmoData.getStatMap() == null) {
                return;
            }

            StatMap statMap = mmoData.getStatMap();
            for (String stat : getAllStatIds()) {
                StatInstance instance = statMap.getInstance(stat);
                if (instance != null) {
                    instance.remove(modifierKey(stat));
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Lua Than remove modifiers failed for " + player.getName() + ": " + exception.getMessage());
        }
    }

    private void loadConfig() {
        File folder = new File(plugin.getDataFolder(), "nhapthan");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        configFile = new File(folder, "infusion.yml");
        if (!configFile.exists()) {
            plugin.saveResource("nhapthan/infusion.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        guiTitle = colorize(config.getString("gui.title", "&6&lLua Than"));
        mmoItemsFlamesEnabled = config.getBoolean("mmoitems.enabled", true);
        mmoItemsFlameType = normalizeMmoKey(config.getString("mmoitems.type", DEFAULT_MMO_FLAME_TYPE));
        if (mmoItemsFlameType.isBlank()) {
            mmoItemsFlameType = DEFAULT_MMO_FLAME_TYPE;
        }
        mmoItemsIdFormat = config.getString("mmoitems.id-format", "{type}_{rarity}");
        if (mmoItemsIdFormat == null || mmoItemsIdFormat.isBlank()) {
            mmoItemsIdFormat = "{type}_{rarity}";
        }
    }

    private void loadDefinitions() {
        typesByLookup.clear();
        raritiesByLookup.clear();
        typeAliasesByLookup.clear();
        rarityAliasesByLookup.clear();
        featureEnabled = true;

        ConfigurationSection raritySection = config.getConfigurationSection("rarities");
        ConfigurationSection typeSection = config.getConfigurationSection("types");
        if (raritySection == null || typeSection == null) {
            featureEnabled = false;
            plugin.getLogger().warning("Lua Than disabled: missing 'rarities' or 'types' section.");
            return;
        }

        for (String key : raritySection.getKeys(false)) {
            ConfigurationSection sec = raritySection.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            InfusionRarity rarity = new InfusionRarity(
                    key,
                    sec.getString("display-name", key),
                    sec.getString("color", "&7"),
                    sec.getDouble("weight", 1.0D),
                    sec.getDouble("multiplier", 1.0D),
                    sec.getDouble("tu-vi-bonus", 0.0D)
            );
            raritiesByLookup.put(lookupKey(key), rarity);
            putRarityAlias(key, rarity);
            putRarityAlias(rarity.displayName(), rarity);
            putRarityAlias(toSuggestionKey(rarity.displayName()), rarity);
        }

        for (String key : typeSection.getKeys(false)) {
            ConfigurationSection sec = typeSection.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }

            Map<String, Double> stats = new LinkedHashMap<>();
            ConfigurationSection statsSection = sec.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String statKey : statsSection.getKeys(false)) {
                    stats.put(statKey, statsSection.getDouble(statKey, 0.0D));
                }
            }

            InfusionType type = new InfusionType(
                    key,
                    sec.getString("display-name", key),
                    sec.getString("material", "PAPER"),
                    sec.getDouble("weight", 1.0D),
                    stats,
                    sec.getDouble("tu-vi-bonus", 0.0D),
                    readTuluyenDropConfig(sec.getConfigurationSection("tuluyen-drops"))
            );
            typesByLookup.put(lookupKey(key), type);
            putTypeAlias(key, type);
            putTypeAlias(type.displayName(), type);
            putTypeAlias(toSuggestionKey(type.displayName()), type);
        }

        if (typesByLookup.isEmpty() || raritiesByLookup.isEmpty()) {
            featureEnabled = false;
            plugin.getLogger().warning("Lua Than disabled: no valid types or rarities loaded.");
        }
    }

    private InfusionType findType(String input) {
        return typeAliasesByLookup.get(lookupKey(input));
    }

    private InfusionRarity findRarity(String input) {
        return rarityAliasesByLookup.get(lookupKey(input));
    }

    public InfusionType resolveType(String input) {
        return findType(input);
    }

    public InfusionRarity resolveRarity(String input) {
        return findRarity(input);
    }

    private ItemStack buildEquippedItem(Player player) {
        Optional<OwnedInfusion> equipped = playerDataManager.getEquippedInfusion(player.getUniqueId());
        if (equipped.isEmpty()) {
            return buildTemplateItem("gui.item-equipped", Material.GRAY_DYE, "&7Chưa cầm Lửa Thần", List.of("&7Cầm Lửa Thần trên tay để kích hoạt buff."));
        }

        InfusionType type = findType(equipped.get().typeId());
        InfusionRarity rarity = findRarity(equipped.get().rarityId());
        if (type == null || rarity == null) {
            return buildTemplateItem("gui.item-equipped", Material.BARRIER, "&cDữ liệu lỗi", List.of("&7Lửa Thần đang cầm không hợp lệ."));
        }

        List<String> lore = buildInfusionLore(player, type, rarity);
        lore.add(colorize("&8"));
        lore.add(colorize("&e▶ Click để tháo Lửa Thần."));
        return createItem(materialOrDefault(type.material()), buildInfusionDisplayName(type, rarity), lore);
    }

    private ItemStack buildStorageItem(Player player, OwnedInfusion owned) {
        InfusionType type = findType(owned.typeId());
        InfusionRarity rarity = findRarity(owned.rarityId());
        if (type == null || rarity == null) {
            return createItem(Material.BARRIER, "&cLửa Thần lỗi", List.of(colorize("&7Dữ liệu loại hoặc độ hiếm đã mất.")));
        }

        List<String> lore = buildInfusionLore(player, type, rarity);
        lore.add(colorize("&8"));
        lore.add(colorize("&e▶ Click để trang bị."));
        return createItem(materialOrDefault(type.material()), buildInfusionDisplayName(type, rarity), lore);
    }

    private ItemStack createFlameItem(Player player, InfusionType type, InfusionRarity rarity) {
        ItemStack mmoItem = createMmoFlameItem(player, type, rarity);
        if (mmoItem != null) {
            return tagFlameItem(mmoItem, type, rarity, mmoFlameItemId(type, rarity));
        }

        String itemId = UUID.randomUUID().toString();
        List<String> lore = buildInfusionLore(player, type, rarity);
        lore.add(colorize("&8"));
        lore.add(colorize("&e▶ Cầm main-hand/off-hand để kích hoạt."));
        lore.add(colorize("&7ID: &8" + itemId));

        ItemStack item = createItem(materialOrDefault(type.material()), buildInfusionDisplayName(type, rarity), lore);
        return tagFlameItem(item, type, rarity, itemId);
    }

    private ItemStack createMmoFlameItem(Player player, InfusionType type, InfusionRarity rarity) {
        if (!mmoItemsFlamesEnabled || player == null || !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return null;
        }

        try {
            Type mmoType = MMOItems.plugin.getTypes().get(mmoItemsFlameType);
            if (mmoType == null) {
                logMissingMmoFlame("type:" + mmoItemsFlameType);
                return null;
            }

            String itemId = mmoFlameItemId(type, rarity);
            ItemStack item = MMOItems.plugin.getItem(mmoType, itemId, PlayerData.get(player));
            if (item == null || item.getType().isAir()) {
                logMissingMmoFlame(mmoItemsFlameType + ":" + itemId);
                return null;
            }
            return item;
        } catch (Throwable throwable) {
            logMissingMmoFlame("api:" + throwable.getClass().getSimpleName());
            return null;
        }
    }

    private ItemStack tagFlameItem(ItemStack item, InfusionType type, InfusionRarity rarity, String itemId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(flameTypeKey, PersistentDataType.STRING, type.id());
        container.set(flameRarityKey, PersistentDataType.STRING, rarity.id());
        container.set(flameIdKey, PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }

    private void refreshHeldFlameLore(Player player) {
        refreshFlameLore(player, player.getInventory().getItemInMainHand());
        refreshFlameLore(player, player.getInventory().getItemInOffHand());
    }

    private void refreshFlameLore(Player player, ItemStack item) {
        Optional<HeldInfusion> held = readFlameItem(item);
        if (held.isEmpty()) {
            return;
        }
        if (isMmoFlameItem(item)) {
            return;
        }

        InfusionType type = findType(held.get().typeId());
        InfusionRarity rarity = findRarity(held.get().rarityId());
        if (type == null || rarity == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        List<String> lore = buildInfusionLore(player, type, rarity);
        lore.add(colorize("&8"));
        lore.add(colorize("&e▶ Cầm main-hand/off-hand để kích hoạt."));
        if (!held.get().itemId().isBlank()) {
            lore.add(colorize("&7ID: &8" + held.get().itemId()));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    static String buildInfusionDisplayName(InfusionType type, InfusionRarity rarity) {
        return rarity.color() + "&l" + type.displayName()
                + " &8[" + rarity.color() + "&l" + rarity.displayName() + "&8]";
    }

    private List<String> buildInfusionLore(Player player, InfusionType type, InfusionRarity rarity) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&8&m━━━━━━━━━━━━━━━━━━━━"));
        lore.add(colorize("&7Bậc: " + rarity.color() + "&l" + rarity.displayName()
                + " &8• &7Hệ số: &ex" + formatDecimal(rarity.multiplier())));
        lore.add(colorize("&7Loại: &f" + type.displayName()));
        lore.add(colorize("&8&m━━━━━━━━━━━━━━━━━━━━"));
        lore.add(colorize("&6✦ &eHiệu ứng tu luyện"));
        double tuViBonus = computeTuViBonusPercent(type, rarity);
        lore.add(colorize("&8┃ &fTu Vi nhận thêm: &a+" + formatDecimal(tuViBonus) + "%"));
        lore.add(colorize("&8┃ &fKích hoạt: &7Cầm main-hand/off-hand"));
        lore.add(colorize("&8"));
        lore.add(colorize("&d✦ &fThuộc tính Lửa Thần"));

        boolean hasCombatStats = false;
        for (Map.Entry<String, Double> entry : type.stats().entrySet()) {
            if (TU_VI_BONUS_STAT.equals(normalize(entry.getKey()))) {
                continue;
            }
            double percent = roundHalfUp(entry.getValue() * rarity.multiplier(), 4);
            lore.add(colorize("&8┃ &f" + statDisplayName(entry.getKey()) + ": &a+" + formatDecimal(percent) + "%"));
            hasCombatStats = true;
        }
        if (!hasCombatStats) {
            lore.add(colorize("&8┃ &7Không có thuộc tính chiến đấu."));
        }

        appendTuluyenDropLore(player, lore, type.tuluyenDrops());
        return lore;
    }

    private void appendTuluyenDropLore(Player player, List<String> lore, InfusionType.TuluyenDropConfig dropConfig) {
        if (dropConfig == null || !dropConfig.enabled() || dropConfig.items().isEmpty()) {
            return;
        }

        lore.add(colorize("&8"));
        lore.add(colorize("&b✦ &fCó drop thêm khi tu luyện"));
        lore.add(colorize("&8┃ &7Vật phẩm:"));
        for (InfusionType.TuluyenDropItem item : dropConfig.items()) {
            lore.add(colorize("&8┃ &b• &f" + formatDropLoreItem(player, item)));
        }
        String condition = dropConfig.requireTurtleIslandBonus()
                ? "Tu luyện gần &bTiên Phù &7để nhận."
                : "Có thể nhận ở &amọi điểm tu luyện&7.";
        lore.add(colorize("&8┃ &7" + condition));
    }

    private String formatDropLoreItem(Player player, InfusionType.TuluyenDropItem drop) {
        return resolveDropDisplayName(player, drop)
                + " &8x&f" + drop.amount()
                + " &a" + formatDecimal(drop.chance()) + "%";
    }

    private String resolveDropDisplayName(Player player, InfusionType.TuluyenDropItem drop) {
        ItemStack item = createDropPreviewItem(player, drop);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
        }
        return drop.mmoType() + ":" + drop.id();
    }

    private ItemStack createDropPreviewItem(Player player, InfusionType.TuluyenDropItem drop) {
        if (player == null || !DROP_TYPE_MMOITEMS.equals(normalize(drop.type()))) {
            return null;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return null;
        }

        try {
            Type mmoType = MMOItems.plugin.getTypes().get(normalizeMmoKey(drop.mmoType()));
            if (mmoType == null) {
                return null;
            }
            return MMOItems.plugin.getItem(mmoType, normalizeMmoKey(drop.id()), PlayerData.get(player));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Optional<HeldInfusion> readFlameItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }

        Optional<HeldInfusion> mmoInfusion = readMmoFlameItem(item);
        if (mmoInfusion.isPresent()) {
            return mmoInfusion;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String typeId = container.get(flameTypeKey, PersistentDataType.STRING);
        String rarityId = container.get(flameRarityKey, PersistentDataType.STRING);
        String itemId = container.get(flameIdKey, PersistentDataType.STRING);
        if (typeId == null || typeId.isBlank() || rarityId == null || rarityId.isBlank()) {
            return Optional.empty();
        }
        if (findType(typeId) == null || findRarity(rarityId) == null) {
            return Optional.empty();
        }

        return Optional.of(new HeldInfusion(itemId == null || itemId.isBlank() ? "" : itemId, typeId, rarityId));
    }

    private Optional<HeldInfusion> readMmoFlameItem(ItemStack item) {
        try {
            NBTItem nbt = NBTItem.get(item);
            String type = firstMmoString(nbt, "MMOITEMS_ITEM_TYPE", "MMOITEMS_TYPE", "type");
            if (!mmoItemsFlameType.equals(normalizeMmoKey(type))) {
                return Optional.empty();
            }

            String id = firstMmoString(nbt, "MMOITEMS_ITEM_ID", "MMOITEMS_ID", "id");
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            return resolveMmoFlameItemId(id);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private Optional<HeldInfusion> resolveMmoFlameItemId(String mmoItemId) {
        String normalizedId = normalizeMmoKey(mmoItemId);
        for (InfusionType type : new LinkedHashSet<>(typesByLookup.values())) {
            for (InfusionRarity rarity : new LinkedHashSet<>(raritiesByLookup.values())) {
                String configuredId = mmoFlameItemId(type, rarity);
                String typeFirstId = normalizeMmoKey(type.id()) + "_" + normalizeMmoKey(rarity.id());
                String rarityFirstId = normalizeMmoKey(rarity.id()) + "_" + normalizeMmoKey(type.id());
                if (normalizedId.equals(configuredId)
                        || normalizedId.equals(typeFirstId)
                        || normalizedId.equals(rarityFirstId)) {
                    return Optional.of(new HeldInfusion(mmoItemId, type.id(), rarity.id()));
                }
            }
        }
        return Optional.empty();
    }

    private boolean isMmoFlameItem(ItemStack item) {
        try {
            NBTItem nbt = NBTItem.get(item);
            return mmoItemsFlameType.equals(normalizeMmoKey(firstMmoString(nbt,
                    "MMOITEMS_ITEM_TYPE", "MMOITEMS_TYPE", "type")));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String firstMmoString(NBTItem nbt, String... keys) {
        for (String key : keys) {
            String value = nbt.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private InfusionType.TuluyenDropConfig readTuluyenDropConfig(ConfigurationSection section) {
        if (section == null) {
            return InfusionType.TuluyenDropConfig.disabled();
        }

        List<InfusionType.TuluyenDropItem> items = new ArrayList<>();
        for (Map<?, ?> row : section.getMapList("items")) {
            String type = asString(row.get("type"), "MMOITEMS");
            String mmoType = asString(firstPresent(row, "mmo-type", "mmoitems-type", "mmoType"), "");
            String id = asString(row.get("id"), "");
            double chance = asDouble(row.get("chance"), 0.0D);
            int amount = asInt(row.get("amount"), 1);
            if (mmoType.isBlank() || id.isBlank() || chance <= 0.0D) {
                continue;
            }
            items.add(new InfusionType.TuluyenDropItem(type, mmoType, id, chance, amount));
        }

        boolean enabled = section.getBoolean("enabled", false);
        int rolls = Math.max(1, section.getInt("rolls-per-interval", 1));
        boolean requireTurtleIslandBonus = section.getBoolean("require-turtleisland-bonus", true);
        return new InfusionType.TuluyenDropConfig(enabled, rolls, requireTurtleIslandBonus, items);
    }

    private static Map<String, String> createStatDisplayNames() {
        Map<String, String> names = new HashMap<>();
        names.put("attack_damage", "Sát thương");
        names.put("pve_damage", "Sát thương quái");
        names.put("critical_strike_chance", "Tỷ lệ bạo kích");
        names.put("critical_strike_power", "Sát thương bạo kích");
        names.put("skill_critical_strike_chance", "Tỷ lệ bạo kích kỹ năng");
        names.put("skill_critical_strike_power", "Sát thương bạo kích kỹ năng");
        names.put("max_health", "Sinh lực");
        names.put("health_regeneration", "Hồi phục sinh lực");
        names.put("damage_reduction", "Giảm sát thương");
        names.put("max_mana", "Linh lực tối đa");
        names.put("mana_regeneration", "Hồi phục linh lực");
        names.put("movement_speed", "Thân pháp");
        names.put("tu_vi_bonus", "Tu vi bồi dưỡng");
        return Collections.unmodifiableMap(names);
    }

    private String statDisplayName(String statKey) {
        String mapped = STAT_DISPLAY_NAMES.get(normalize(statKey));
        if (mapped != null) {
            return mapped;
        }

        String[] parts = normalize(statKey).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.length() == 0 ? statKey : builder.toString();
    }

    static String statDisplayNameForTest(String statKey) {
        return STAT_DISPLAY_NAMES.get(normalizeKey(statKey));
    }

    private ItemStack buildInfoItem(Player player, int page, int pageCount) {
        int owned = playerDataManager.getInfusionInventory(player.getUniqueId()).size();
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7So huu: &e" + owned + "&7/270"));
        lore.add(colorize("&7Trang: &e" + (page + 1) + "&7/&e" + pageCount));
        lore.add(colorize("&8"));
        lore.add(colorize("&7Cam Lua Than tren main-hand/off-hand de kich hoat buff."));
        return buildTemplateItem("gui.item-info", Material.BOOK, "&dThong tin Lua Than", lore);
    }

    private ItemStack buildPageButton(String path, Material fallback, String fallbackName, boolean enabled) {
        List<String> lore = enabled
                ? List.of(colorize("&7Click de chuyen trang."))
                : List.of(colorize("&8Khong the chuyen trang."));
        ItemStack item = buildTemplateItem(path, fallback, fallbackName, lore);
        if (!enabled) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colorize("&8" + ChatColor.stripColor(meta.getDisplayName())));
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack buildTemplateItem(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        String materialKey = config.getString(path + ".material", fallbackMaterial.name());
        String name = config.getString(path + ".name", fallbackName);
        List<String> lore = config.getStringList(path + ".lore");
        if (lore.isEmpty()) {
            lore = fallbackLore;
        } else {
            List<String> mapped = new ArrayList<>();
            for (String line : lore) {
                mapped.add(colorize(line));
            }
            lore = mapped;
        }
        return createItem(materialOrDefault(materialKey), name, lore);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(colorize(name));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBackground(Inventory inventory) {
        Material borderMaterial = materialOrDefault(config.getString("gui.border-material", "PURPLE_STAINED_GLASS_PANE"));
        ItemStack filler = new ItemStack(borderMaterial);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == EQUIPPED_SLOT || slot == PREV_SLOT || slot == INFO_SLOT || slot == CLOSE_SLOT || slot == NEXT_SLOT || STORAGE_SLOTS.contains(slot)) {
                continue;
            }
            inventory.setItem(slot, filler);
        }
    }

    private Set<String> getAllStatIds() {
        Set<String> out = new HashSet<>();
        for (InfusionType type : typesByLookup.values()) {
            out.addAll(type.stats().keySet());
        }
        return out;
    }

    private void logMissingStat(String typeId, String rarityId, String statKey) {
        String key = typeId + "|" + rarityId + "|" + statKey;
        if (missingStatWarnings.add(key)) {
            plugin.getLogger().warning("Lua Than stat not found in MythicLib mapping: " + key);
        }
    }

    private void giveDrop(Player player, InfusionType.TuluyenDropItem drop) {
        ItemStack item = createDropItem(player, drop);
        if (item == null || item.getType().isAir()) {
            return;
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private ItemStack createDropItem(Player player, InfusionType.TuluyenDropItem drop) {
        if (!DROP_TYPE_MMOITEMS.equals(normalize(drop.type()))) {
            logMissingDrop("unsupported:" + drop.type() + ":" + drop.mmoType() + ":" + drop.id());
            return null;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            logMissingDrop("plugin:MMOItems");
            return null;
        }

        try {
            Type mmoType = MMOItems.plugin.getTypes().get(normalizeMmoKey(drop.mmoType()));
            if (mmoType == null) {
                logMissingDrop("type:" + drop.mmoType());
                return null;
            }

            ItemStack item = MMOItems.plugin.getItem(mmoType, normalizeMmoKey(drop.id()), PlayerData.get(player));
            if (item == null || item.getType().isAir()) {
                logMissingDrop("item:" + drop.mmoType() + ":" + drop.id());
                return null;
            }

            item.setAmount(drop.amount());
            return item;
        } catch (Throwable throwable) {
            String key = "error:" + drop.mmoType() + ":" + drop.id();
            if (missingDropWarnings.add(key)) {
                plugin.getLogger().warning("Lua Than drop failed for " + drop.mmoType() + ":" + drop.id()
                        + ": " + throwable.getMessage());
            }
            return null;
        }
    }

    private void logMissingDrop(String key) {
        if (missingDropWarnings.add(key)) {
            plugin.getLogger().warning("Lua Than drop unavailable: " + key);
        }
    }

    private double computeRelativeValue(double basePercent, double rarityMultiplier) {
        double percent = roundHalfUp(basePercent * rarityMultiplier, 4);
        return roundHalfUp(percent / 100.0D, 4);
    }

    private static double roundHalfUp(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    static boolean rollChance(double chancePercent, double rollPercent) {
        return chancePercent > 0.0D && rollPercent < Math.min(100.0D, chancePercent);
    }

    static double computeTuViBonusPercent(Map<String, Double> stats, InfusionRarity rarity) {
        if (rarity == null) {
            return 0.0D;
        }

        double totalPercent = rarity.tuViBonusPercent();
        if (stats != null) {
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                if (!TU_VI_BONUS_STAT.equals(normalizeKey(entry.getKey()))) {
                    continue;
                }
                totalPercent += roundHalfUp(entry.getValue() * rarity.multiplier(), 4);
            }
        }
        return Math.max(0.0D, totalPercent);
    }

    static double computeTuViBonusPercent(InfusionType type, InfusionRarity rarity) {
        if (type == null || rarity == null) {
            return 0.0D;
        }

        double totalPercent = computeTuViBonusPercent(type.stats(), rarity);
        totalPercent += roundHalfUp(type.tuViBonusPercent() * rarity.multiplier(), 4);
        return Math.max(0.0D, totalPercent);
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private int clampPage(int page, int pageCount) {
        if (page < 0) {
            return 0;
        }
        if (page >= pageCount) {
            return pageCount - 1;
        }
        return page;
    }

    private String normalize(String value) {
        return normalizeKey(value);
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String lookupKey(String value) {
        if (value == null) {
            return "";
        }

        String ascii = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String toSuggestionKey(String value) {
        return lookupKey(value).toUpperCase(Locale.ROOT);
    }

    private void putTypeAlias(String alias, InfusionType type) {
        String key = lookupKey(alias);
        if (!key.isBlank()) {
            typeAliasesByLookup.put(key, type);
        }
    }

    private void putRarityAlias(String alias, InfusionRarity rarity) {
        String key = lookupKey(alias);
        if (!key.isBlank()) {
            rarityAliasesByLookup.put(key, rarity);
        }
    }

    private String normalizeMmoKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String mmoFlameItemId(InfusionType type, InfusionRarity rarity) {
        String itemId = mmoItemsIdFormat
                .replace("{type}", normalizeMmoKey(type.id()))
                .replace("{rarity}", normalizeMmoKey(rarity.id()))
                .replace("{type_lower}", lookupKey(type.id()))
                .replace("{rarity_lower}", lookupKey(rarity.id()));
        return normalizeMmoKey(itemId);
    }

    private void logMissingMmoFlame(String key) {
        if (missingMmoFlameWarnings.add(key)) {
            plugin.getLogger().warning("Lua Than MMOItems item unavailable: " + key
                    + ". Falling back to legacy item.");
        }
    }

    private void scheduleHeldRefresh(Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyEquippedInfusion(player);
            }
        }, 1L);
    }

    private static Object firstPresent(Map<?, ?> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Material materialOrDefault(String materialName) {
        try {
            return Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Material.PAPER;
        }
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private String modifierKey(String stat) {
        return MODIFIER_PREFIX + stat;
    }

    public void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save nhapthan/infusion.yml: " + e.getMessage());
        }
    }

    public Map<String, InfusionType> getTypes() {
        return Collections.unmodifiableMap(typesByLookup);
    }

    public Map<String, InfusionRarity> getRarities() {
        return Collections.unmodifiableMap(raritiesByLookup);
    }

    public record HeldInfusion(String itemId, String typeId, String rarityId) {
    }
}
