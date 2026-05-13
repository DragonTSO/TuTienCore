package com.turtle.tutiencore.core.gui;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.manager.RealmManager;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GUI hiển thị danh sách 19 Cảnh Giới — đọc từ gui/realm-list.yml
 */
public class RealmListGUI implements Listener {

    private final JavaPlugin plugin;
    private final RealmManager realmManager;
    private final Set<UUID> openGuis = new HashSet<>();

    private FileConfiguration config;
    private String guiTitle;

    public RealmListGUI(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "gui/realm-list.yml");
        if (!file.exists()) {
            plugin.saveResource("gui/realm-list.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        guiTitle = ChatColor.translateAlternateColorCodes('&', config.getString("title", "&5&l✦ Danh Sách Cảnh Giới ✦"));
    }

    public void reloadConfig() {
        loadConfig();
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        int size = config.getInt("size", 54);
        Inventory gui = Bukkit.createInventory(null, size, guiTitle);

        PlayerRealm pr = realmManager.getPlayerRealm(uuid);
        int currentRealmId = pr.getRealmId();
        double playerTuVi = TuTien.getApi().getTuVi(uuid);

        // Fill borders
        fillBorders(gui);

        // Title item
        ConfigurationSection titleSec = config.getConfigurationSection("title-item");
        if (titleSec != null) {
            gui.setItem(titleSec.getInt("slot", 4), buildConfigItem(titleSec, player));
        }

        // Player info
        ConfigurationSection playerSec = config.getConfigurationSection("player-info");
        if (playerSec != null) {
            ItemStack head = buildConfigItem(playerSec, player);
            // Set player head
            if (head.getType() == Material.PLAYER_HEAD) {
                org.bukkit.inventory.meta.SkullMeta skullMeta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                skullMeta.setOwningPlayer(player);
                // Copy existing lore/name
                ItemMeta oldMeta = head.getItemMeta();
                if (oldMeta.hasDisplayName()) skullMeta.setDisplayName(oldMeta.getDisplayName());
                if (oldMeta.hasLore()) skullMeta.setLore(oldMeta.getLore());
                head.setItemMeta(skullMeta);
            }
            // Add progress bar to lore
            Realm nextRealm = realmManager.getNextRealm(uuid);
            if (nextRealm != null && head.hasItemMeta()) {
                ItemMeta meta = head.getItemMeta();
                List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                double progress = Math.min((playerTuVi / nextRealm.getTuViRequired()) * 100, 100);
                lore.add("");
                lore.add("§7Tiến độ đến " + nextRealm.getDisplayNameTranslated() + "§7:");
                lore.add("§7  " + buildProgressBar(progress) + " §e" + String.format("%.1f%%", progress));
                lore.add("§7  Cần: §b" + RealmManager.formatNumber(nextRealm.getTuViRequired()));
                meta.setLore(lore);
                head.setItemMeta(meta);
            } else if (head.hasItemMeta()) {
                ItemMeta meta = head.getItemMeta();
                List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add("§d§l✦ Đã đạt cảnh giới tối cao! ✦");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            gui.setItem(playerSec.getInt("slot", 40), head);
        }

        // Close button
        ConfigurationSection closeSec = config.getConfigurationSection("close-button");
        if (closeSec != null) {
            gui.setItem(closeSec.getInt("slot", 49), buildConfigItem(closeSec, player));
        }

        // Realm items
        List<Integer> realmSlots = config.getIntegerList("realm-slots");
        Map<Integer, Realm> allRealms = realmManager.getAllRealms();

        for (int i = 0; i < realmSlots.size(); i++) {
            int realmId = i + 1;
            Realm realm = allRealms.get(realmId);
            if (realm == null) continue;

            ItemStack item = buildRealmItem(realm, realmId, currentRealmId, pr.getSubRealm(), playerTuVi);
            gui.setItem(realmSlots.get(i), item);
        }

        openGuis.add(uuid);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    // ==========================================
    // BUILD REALM ITEM FROM CONFIG
    // ==========================================

    private ItemStack buildRealmItem(Realm realm, int realmId, int currentId, SubRealm currentSub, double tuVi) {
        boolean isCurrent = (realmId == currentId);
        boolean isPast = (realmId < currentId);

        // Determine template key
        String templateKey;
        if (isCurrent) templateKey = "realm-item.current";
        else if (isPast) templateKey = "realm-item.past";
        else templateKey = "realm-item.future";

        ConfigurationSection template = config.getConfigurationSection(templateKey);
        if (template == null) return new ItemStack(Material.PAPER);

        // Material
        Material mat = getMaterialForRealm(realmId, realm, templateKey);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // Name
        String name = template.getString("name", "{realm_display}");
        name = replaceRealmPlaceholders(name, realm, realmId, tuVi);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        // Lore
        List<String> loreTemplate = template.getStringList("lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreTemplate) {
            // Special: sub-realm progress (only for current)
            if (line.contains("{sub_realm_progress}") && isCurrent) {
                for (SubRealm sub : SubRealm.values()) {
                    long required = realm.getTuViForSubRealm(sub);
                    boolean reached = sub.ordinal() <= currentSub.ordinal();
                    String icon = reached ? "§a✔" : "§8○";
                    String color = reached ? "§a" : "§7";
                    String subName = sub == currentSub
                            ? "§e§l" + sub.getDisplayName() + " §e←"
                            : color + sub.getDisplayName();
                    lore.add("  " + icon + " " + subName + " §8(" + RealmManager.formatNumber(required) + ")");
                }
                continue;
            }

            // Tu Vi status for future realms
            if (line.contains("{tuvi_status}")) {
                String tuViStr = RealmManager.formatNumber(realm.getTuViRequired());
                boolean enough = tuVi >= realm.getTuViRequired();
                String format = enough
                        ? config.getString("tuvi-format.enough", "&a{amount} &a✔")
                        : config.getString("tuvi-format.not-enough", "&c{amount} &c✗");
                line = line.replace("{tuvi_status}", format.replace("{amount}", tuViStr));
            }

            line = replaceRealmPlaceholders(line, realm, realmId, tuVi);
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // Glow
        if (template.getBoolean("glow", false)) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        }

        item.setItemMeta(meta);
        return item;
    }

    private String replaceRealmPlaceholders(String text, Realm realm, int realmId, double tuVi) {
        text = text.replace("{realm_name}", realm.getName());
        text = text.replace("{realm_display}", realm.getDisplayNameTranslated());
        text = text.replace("{realm_english}", realm.getEnglishName());
        text = text.replace("{realm_tier}", realm.getTier().getDisplayName());
        text = text.replace("{realm_tier_color}", realm.getTier().getColor());
        text = text.replace("{realm_tuvi}", RealmManager.formatNumber(realm.getTuViRequired()));
        text = text.replace("{realm_thuc_luc}", RealmManager.formatNumber(realm.getThucLucRequired()));
        text = text.replace("{realm_money}", RealmManager.formatMoney(realm.getMoneyRequired()));
        text = text.replace("{realm_dot_pha_dan}", String.valueOf(realmManager.getDotPhaDanRequired(realmId)));
        text = text.replace("{realm_bolts}", String.valueOf(realm.getLightningBolts()));
        text = text.replace("{realm_damage}", String.format("%.1f", realm.getDamagePerBolt()));
        text = text.replace("{realm_success}", String.format("%.0f%%", realm.getSuccessRate()));
        text = text.replace("{realm_id}", String.valueOf(realmId));
        return text;
    }

    // ==========================================
    // CONFIG ITEM BUILDER
    // ==========================================

    private ItemStack buildConfigItem(ConfigurationSection sec, Player player) {
        Material mat;
        try {
            mat = Material.valueOf(sec.getString("material", "PAPER").toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String name = sec.getString("name", "");
        name = parsePlaceholders(player, name);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> lore = sec.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream()
                    .map(l -> parsePlaceholders(player, l))
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                    .collect(Collectors.toList()));
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    private void fillBorders(Inventory gui) {
        ConfigurationSection borders = config.getConfigurationSection("borders");
        if (borders == null) return;

        for (String key : borders.getKeys(false)) {
            ConfigurationSection border = borders.getConfigurationSection(key);
            if (border == null) continue;

            Material mat;
            try {
                mat = Material.valueOf(border.getString("material", "GRAY_STAINED_GLASS_PANE").toUpperCase());
            } catch (IllegalArgumentException e) {
                mat = Material.GRAY_STAINED_GLASS_PANE;
            }

            String name = ChatColor.translateAlternateColorCodes('&', border.getString("name", " "));
            String slotsStr = border.getString("slots", "");

            ItemStack pane = new ItemStack(mat);
            ItemMeta meta = pane.getItemMeta();
            meta.setDisplayName(name);
            pane.setItemMeta(meta);

            for (String slotStr : slotsStr.split(",")) {
                try {
                    int slot = Integer.parseInt(slotStr.trim());
                    gui.setItem(slot, pane);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private Material getMaterialForRealm(int realmId, Realm realm, String templateKey) {
        String stateMat = config.getString(templateKey + ".material");
        if (stateMat != null) {
            try {
                return Material.valueOf(stateMat.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        // Check per-realm override
        String matName = config.getString("realm-materials." + realmId);
        if (matName != null) {
            try {
                return Material.valueOf(matName.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        // Fallback to default per tier
        String defaultMat = config.getString("default-materials." + realm.getTier().name());
        if (defaultMat != null) {
            try {
                return Material.valueOf(defaultMat.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return Material.PAPER;
    }

    private String buildProgressBar(double percent) {
        int filled = (int) (percent / 5);
        int empty = 20 - filled;
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) sb.append("█");
        sb.append("§8");
        for (int i = 0; i < empty; i++) sb.append("█");
        return sb.toString();
    }

    private String parsePlaceholders(Player player, String text) {
        if (text == null) return "";
        text = text.replace("%player_name%", player.getName());
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    // ==========================================
    // EVENT HANDLERS
    // ==========================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        if (!event.getView().getTitle().equals(guiTitle) || !openGuis.contains(uuid)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Close button
        int closeSlot = config.getInt("close-button.slot", 49);
        if (slot == closeSlot) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.0f);
        }

        // Click realm items - play sound
        List<Integer> realmSlots = config.getIntegerList("realm-slots");
        if (realmSlots.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.5f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (event.getView().getTitle().equals(guiTitle)) {
                openGuis.remove(uuid);
            }
        }
    }
}
