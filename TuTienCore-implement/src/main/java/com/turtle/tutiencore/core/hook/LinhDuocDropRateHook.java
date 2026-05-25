package com.turtle.tutiencore.core.hook;

import io.lumine.mythic.lib.api.item.NBTItem;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.ItemDropEvent;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;
import net.Indyuce.mmoitems.api.player.PlayerData;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class LinhDuocDropRateHook implements Listener {

    private static final String STAT_NBT_PATH = "MMOITEMS_LINH_DUOC_DROP_RATE";
    private static final String MATERIAL_TYPE_ID = "MATERIAL";
    private static final String LINH_DUOC_ID = "LINH_DUOC";

    private final JavaPlugin plugin;
    private final Map<Material, DropRule> dropRules = new EnumMap<>(Material.class);
    private boolean initialized;

    public LinhDuocDropRateHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        initialize();
        Bukkit.getScheduler().runTask(plugin, this::initialize);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 100L);
    }

    @EventHandler
    public void onMMOItemsReload(MMOItemsReloadEvent event) {
        reloadDropRules();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(ItemDropEvent event) {
        if (event.getCause() != ItemDropEvent.DropCause.NORMAL_BLOCK || !(event.getWhoDropped() instanceof Player player)) {
            return;
        }

        Block block = event.getMinedBlock();
        if (block == null) {
            return;
        }

        DropRule rule = dropRules.get(block.getType());
        if (rule == null) {
            return;
        }

        double bonusPercent = readBonusPercent(player.getInventory().getItemInMainHand());
        if (bonusPercent <= 0) {
            return;
        }

        int amount = rollExtraAmount(rule, bonusPercent);
        if (amount <= 0) {
            return;
        }

        ItemStack linhDuoc = createLinhDuoc(player, amount);
        if (linhDuoc != null) {
            event.getDrops().add(linhDuoc);
        }
    }

    private void initialize() {
        if (initialized || !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }

        try {
            reloadDropRules();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            initialized = true;
            plugin.getLogger().info("Registered MMOItems Linh Duoc drop rate hook.");
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not register Linh Duoc drop rate hook: " + exception.getMessage());
        }
    }

    private void reloadDropRules() {
        dropRules.clear();

        Plugin mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null) {
            return;
        }

        File dropsFile = new File(mmoItems.getDataFolder(), "drops.yml");
        if (!dropsFile.isFile()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dropsFile);
        ConfigurationSection blocks = config.getConfigurationSection("blocks");
        if (blocks == null) {
            return;
        }

        for (String blockKey : blocks.getKeys(false)) {
            Material material = Material.matchMaterial(normalizeMaterialKey(blockKey));
            if (material == null) {
                continue;
            }

            DropRule rule = readDropRule(blocks.getConfigurationSection(blockKey));
            if (rule != null && rule.chancePercent() > 0) {
                dropRules.put(material, rule);
            }
        }
    }

    private DropRule readDropRule(ConfigurationSection blockSection) {
        if (blockSection == null) {
            return null;
        }

        double totalCoef = 0;
        double weightedChance = 0;
        int minAmount = 1;
        int maxAmount = 1;
        boolean found = false;

        for (String subtableKey : blockSection.getKeys(false)) {
            ConfigurationSection subtable = blockSection.getConfigurationSection(subtableKey);
            if (subtable == null) {
                continue;
            }

            double coef = Math.max(0, subtable.getDouble("coef", 0));
            ParsedDrop parsedDrop = parseDropInfo(subtable.getString("items." + MATERIAL_TYPE_ID + "." + LINH_DUOC_ID));
            if (coef <= 0 || parsedDrop == null) {
                continue;
            }

            totalCoef += coef;
            weightedChance += coef * parsedDrop.chancePercent();
            minAmount = found ? Math.min(minAmount, parsedDrop.minAmount()) : parsedDrop.minAmount();
            maxAmount = found ? Math.max(maxAmount, parsedDrop.maxAmount()) : parsedDrop.maxAmount();
            found = true;
        }

        if (!found || totalCoef <= 0) {
            return null;
        }

        return new DropRule(weightedChance / totalCoef, minAmount, maxAmount);
    }

    private ParsedDrop parseDropInfo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] parts = raw.split(",");
        if (parts.length < 2) {
            return null;
        }

        try {
            double chancePercent = Math.max(0, Double.parseDouble(parts[0].trim()));
            String[] amountParts = parts[1].trim().split("-");
            int minAmount = Math.max(1, Integer.parseInt(amountParts[0].trim()));
            int maxAmount = amountParts.length > 1 ? Math.max(minAmount, Integer.parseInt(amountParts[1].trim())) : minAmount;
            return new ParsedDrop(chancePercent, minAmount, maxAmount);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Could not read LINH_DUOC drop entry '" + raw + "': " + exception.getMessage());
            return null;
        }
    }

    private double readBonusPercent(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }

        try {
            NBTItem nbtItem = NBTItem.get(item);
            if (nbtItem == null || !nbtItem.hasTag(STAT_NBT_PATH)) {
                return 0;
            }

            double numericValue = Math.max(0, nbtItem.getDouble(STAT_NBT_PATH));
            return numericValue > 0 ? numericValue : parsePercent(nbtItem.getString(STAT_NBT_PATH));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private double parsePercent(String raw) {
        if (raw == null) {
            return 0;
        }

        String normalized = ChatColor.stripColor(raw)
                .trim()
                .replace("%", "")
                .replace(",", ".");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return 0;
        }

        try {
            return Math.max(0, Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int rollExtraAmount(DropRule rule, double bonusPercent) {
        double extraChance = rule.chancePercent() * (bonusPercent / 100.0);
        int successCount = 0;

        while (extraChance >= 100.0) {
            successCount++;
            extraChance -= 100.0;
        }

        if (ThreadLocalRandom.current().nextDouble(100.0) < extraChance) {
            successCount++;
        }

        int totalAmount = 0;
        for (int i = 0; i < successCount; i++) {
            totalAmount += rule.rollAmount();
        }
        return totalAmount;
    }

    private ItemStack createLinhDuoc(Player player, int amount) {
        Type materialType = MMOItems.plugin.getTypes().get(MATERIAL_TYPE_ID);
        if (materialType == null) {
            return null;
        }

        ItemStack item = MMOItems.plugin.getItem(materialType, LINH_DUOC_ID, PlayerData.get(player));
        if (item == null || item.getType().isAir()) {
            return null;
        }

        item.setAmount(amount);
        return item;
    }

    private String normalizeMaterialKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }

    private record DropRule(double chancePercent, int minAmount, int maxAmount) {
        int rollAmount() {
            return maxAmount > minAmount
                    ? ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1)
                    : minAmount;
        }
    }

    private record ParsedDrop(double chancePercent, int minAmount, int maxAmount) {
    }
}
