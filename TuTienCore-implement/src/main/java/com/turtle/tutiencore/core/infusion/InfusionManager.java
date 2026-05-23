package com.turtle.tutiencore.core.infusion;

import com.turtle.tutiencore.core.manager.PlayerDataManager;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Set<String> missingStatWarnings = new HashSet<>();

    private File configFile;
    private FileConfiguration config;
    private String guiTitle;
    private boolean featureEnabled;

    private final Map<String, InfusionType> typesByLookup = new LinkedHashMap<>();
    private final Map<String, InfusionRarity> raritiesByLookup = new LinkedHashMap<>();

    public InfusionManager(JavaPlugin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
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
        List<String> out = new ArrayList<>();
        for (InfusionRarity rarity : raritiesByLookup.values()) {
            out.add(rarity.id());
        }
        return out;
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

        Optional<OwnedInfusion> equipped = playerDataManager.getEquippedInfusion(player.getUniqueId());
        if (equipped.isEmpty()) {
            return 0.0D;
        }

        InfusionType type = findType(equipped.get().typeId());
        InfusionRarity rarity = findRarity(equipped.get().rarityId());
        if (type == null || rarity == null) {
            return 0.0D;
        }

        double totalPercent = 0.0D;
        for (Map.Entry<String, Double> entry : type.stats().entrySet()) {
            if (!TU_VI_BONUS_STAT.equals(normalize(entry.getKey()))) {
                continue;
            }
            totalPercent += roundHalfUp(entry.getValue() * rarity.multiplier(), 4);
        }
        return Math.max(0.0D, totalPercent);
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
            gui.setItem(STORAGE_SLOTS.get(i), buildStorageItem(owned));
        }

        player.openInventory(gui);
    }

    public void applyEquippedInfusion(Player player) {
        removeAllModifiers(player);
        Optional<OwnedInfusion> equipped = playerDataManager.getEquippedInfusion(player.getUniqueId());
        if (equipped.isEmpty()) {
            return;
        }

        try {
            applyOwnedInfusionOrThrow(player, equipped.get());
        } catch (Exception exception) {
            plugin.getLogger().warning("Nhap Than apply failed for " + player.getName() + ": " + exception.getMessage());
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
        InfusionType type = findType(owned.typeId());
        InfusionRarity rarity = findRarity(owned.rarityId());
        if (type == null || rarity == null) {
            throw new IllegalStateException("Missing type or rarity config for equipped infusion");
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
            plugin.getLogger().warning("Nhap Than remove modifiers failed for " + player.getName() + ": " + exception.getMessage());
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
        guiTitle = colorize(config.getString("gui.title", "&5&lNhap Than"));
    }

    private void loadDefinitions() {
        typesByLookup.clear();
        raritiesByLookup.clear();
        featureEnabled = true;

        ConfigurationSection raritySection = config.getConfigurationSection("rarities");
        ConfigurationSection typeSection = config.getConfigurationSection("types");
        if (raritySection == null || typeSection == null) {
            featureEnabled = false;
            plugin.getLogger().warning("Nhap Than disabled: missing 'rarities' or 'types' section.");
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
                    sec.getDouble("multiplier", 1.0D)
            );
            raritiesByLookup.put(normalize(key), rarity);
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
                    stats
            );
            typesByLookup.put(normalize(key), type);
        }

        if (typesByLookup.isEmpty() || raritiesByLookup.isEmpty()) {
            featureEnabled = false;
            plugin.getLogger().warning("Nhap Than disabled: no valid types or rarities loaded.");
        }
    }

    private InfusionType findType(String input) {
        return typesByLookup.get(normalize(input));
    }

    private InfusionRarity findRarity(String input) {
        return raritiesByLookup.get(normalize(input));
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
            return buildTemplateItem("gui.item-equipped", Material.GRAY_DYE, "&7Chua trang bi", List.of("&7Chon mot Nhap Than trong kho de trang bi."));
        }

        InfusionType type = findType(equipped.get().typeId());
        InfusionRarity rarity = findRarity(equipped.get().rarityId());
        if (type == null || rarity == null) {
            return buildTemplateItem("gui.item-equipped", Material.BARRIER, "&cDu lieu loi", List.of("&7Nhap Than dang trang bi khong hop le."));
        }

        List<String> lore = buildInfusionLore(type, rarity);
        lore.add(colorize("&8"));
        lore.add(colorize("&eClick de thao Nhap Than."));
        return createItem(materialOrDefault(type.material()), rarity.color() + type.displayName(), lore);
    }

    private ItemStack buildStorageItem(OwnedInfusion owned) {
        InfusionType type = findType(owned.typeId());
        InfusionRarity rarity = findRarity(owned.rarityId());
        if (type == null || rarity == null) {
            return createItem(Material.BARRIER, "&cNhap Than loi", List.of(colorize("&7Du lieu loai hoac do hiem da mat.")));
        }

        List<String> lore = buildInfusionLore(type, rarity);
        lore.add(colorize("&8"));
        lore.add(colorize("&eClick de trang bi."));
        return createItem(materialOrDefault(type.material()), rarity.color() + type.displayName(), lore);
    }

    private List<String> buildInfusionLore(InfusionType type, InfusionRarity rarity) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Do hiem: " + rarity.color() + rarity.displayName()));
        lore.add(colorize("&7He so: &ex" + formatDecimal(rarity.multiplier())));
        lore.add(colorize("&8"));
        lore.add(colorize("&d✦ &fThuoc tinh linh hoa"));

        for (Map.Entry<String, Double> entry : type.stats().entrySet()) {
            double percent = roundHalfUp(entry.getValue() * rarity.multiplier(), 4);
            lore.add(colorize("&8✧ &f" + statDisplayName(entry.getKey()) + ": &a+" + formatDecimal(percent) + "%"));
        }
        return lore;
    }

    private static Map<String, String> createStatDisplayNames() {
        Map<String, String> names = new HashMap<>();
        names.put("attack_damage", "Sat thuong");
        names.put("pve_damage", "Sat thuong quai");
        names.put("critical_strike_chance", "Ty le bao kich");
        names.put("critical_strike_power", "Sat thuong bao kich");
        names.put("skill_critical_strike_chance", "Ty le bao kich ky nang");
        names.put("skill_critical_strike_power", "Sat thuong bao kich ky nang");
        names.put("max_health", "Sinh luc");
        names.put("health_regeneration", "Hoi phuc sinh luc");
        names.put("damage_reduction", "Giam sat thuong");
        names.put("max_mana", "Linh luc toi da");
        names.put("mana_regeneration", "Hoi phuc linh luc");
        names.put("movement_speed", "Than phap");
        names.put("tu_vi_bonus", "Tu vi boi duong");
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

    private ItemStack buildInfoItem(Player player, int page, int pageCount) {
        int owned = playerDataManager.getInfusionInventory(player.getUniqueId()).size();
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7So huu: &e" + owned + "&7/270"));
        lore.add(colorize("&7Trang: &e" + (page + 1) + "&7/&e" + pageCount));
        lore.add(colorize("&8"));
        lore.add(colorize("&7Chi co Nhap Than dang trang bi moi kich hoat buff."));
        return buildTemplateItem("gui.item-info", Material.BOOK, "&dThong tin Nhap Than", lore);
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
            plugin.getLogger().warning("Nhap Than stat not found in MythicLib mapping: " + key);
        }
    }

    private double computeRelativeValue(double basePercent, double rarityMultiplier) {
        double percent = roundHalfUp(basePercent * rarityMultiplier, 4);
        return roundHalfUp(percent / 100.0D, 4);
    }

    private static double roundHalfUp(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
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
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
}
