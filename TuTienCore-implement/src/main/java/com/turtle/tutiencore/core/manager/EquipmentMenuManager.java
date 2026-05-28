package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.Realm;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.player.PlayerData;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class EquipmentMenuManager implements Listener, CommandExecutor {

    private static final String MOD_PREFIX = "tutien_equipment_";

    private final JavaPlugin plugin;
    private final RealmManager realmManager;
    private final File configFile;
    private final File dataFile;
    private final NamespacedKey actionKey;
    private final NamespacedKey boundOffhandKey;

    private FileConfiguration config;
    private FileConfiguration data;
    private final Map<String, EquipSlot> slots = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ItemStack>> equipped = new HashMap<>();

    public EquipmentMenuManager(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        this.configFile = new File(plugin.getDataFolder(), "equipment-menu.yml");
        this.dataFile = new File(plugin.getDataFolder(), "equipment-data.yml");
        this.actionKey = new NamespacedKey(plugin, "equipment_action");
        this.boundOffhandKey = new NamespacedKey(plugin, "equipment_bound_offhand");
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        if (!configFile.exists()) {
            plugin.saveResource("equipment-menu.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadSlots();
        equipped.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
            applyStats(player);
            ensureBoundOffhand(player);
        }
    }

    public void saveAll() {
        for (UUID uuid : equipped.keySet()) {
            savePlayer(uuid);
        }
        saveDataFile();
    }

    private void saveDataFile() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save equipment-data.yml: " + exception.getMessage());
        }
    }

    public void removeAllOnlineModifiers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeStats(player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("only-player"));
            return true;
        }
        openEquipment(player);
        return true;
    }

    public void openEquipment(Player player) {
        ensureBoundOffhand(player);
        loadPlayer(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(player, config.getInt("gui.size", 54), color(config.getString("gui.title", "&8Trang Bị Tu Tiên")));
        fill(inventory);
        inventory.setItem(config.getInt("gui.info-slot", 4), infoItem(player));
        Map<String, ItemStack> playerItems = equipped.getOrDefault(player.getUniqueId(), Map.of());
        for (EquipSlot slot : slots.values()) {
            inventory.setItem(slot.guiSlot(), playerItems.getOrDefault(slot.id(), emptySlotItem(slot)));
        }
        player.openInventory(inventory);
    }

    private void openUpgrade(Player player) {
        ensureBoundOffhand(player);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        UpgradeRule rule = findUpgrade(offhand);
        Inventory inventory = Bukkit.createInventory(player, config.getInt("gui.upgrade-size", 27), color(config.getString("gui.upgrade-title", "&8Tiến Hoá Offhand")));
        fill(inventory);
        inventory.setItem(config.getInt("gui.upgrade-slots.source", 11), offhand == null ? new ItemStack(Material.AIR) : offhand.clone());
        if (rule != null) {
            inventory.setItem(config.getInt("gui.upgrade-slots.result", 15), previewItem(rule));
            inventory.setItem(config.getInt("gui.upgrade-slots.confirm", 13), confirmItem(rule));
        } else {
            inventory.setItem(config.getInt("gui.upgrade-slots.confirm", 13), named(Material.BARRIER, "&cKhông thể tiến hoá", List.of(message("no-upgrade"))));
        }
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyStats(event.getPlayer());
            ensureBoundOffhand(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayer(event.getPlayer().getUniqueId());
        saveDataFile();
        removeStats(event.getPlayer());
        equipped.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.getBoolean("enabled", true)) return;
        ItemStack offhand = event.getPlayer().getInventory().getItemInOffHand();
        if (!isInfoOffhandItem(offhand)) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            openEquipment(event.getPlayer());
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            openUpgrade(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isLockedOffhandClick(event, player)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> ensureBoundOffhand(player));
            return;
        }
        String title = event.getView().getTitle();
        if (title.equals(color(config.getString("gui.title", "&8Trang Bị Tu Tiên")))) {
            handleEquipmentClick(event, player);
        } else if (title.equals(color(config.getString("gui.upgrade-title", "&8Tiến Hoá Offhand")))) {
            handleUpgradeClick(event, player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            savePlayer(player.getUniqueId());
            saveDataFile();
        }
    }

    private void handleEquipmentClick(InventoryClickEvent event, Player player) {
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getInventory().getSize()) return;
        EquipSlot slot = slotAt(raw);
        if (slot == null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        loadPlayer(player.getUniqueId());
        Map<String, ItemStack> playerItems = equipped.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());

        if (event.getClick() == ClickType.RIGHT || event.getCursor() == null || event.getCursor().getType() == Material.AIR) {
            ItemStack current = playerItems.remove(slot.id());
            if (current != null) {
                giveOrDrop(player, current);
                player.sendMessage(message("unequipped").replace("%slot%", slot.id()));
            }
        } else {
            ItemStack cursor = event.getCursor();
            if (!slot.accepts(mmoType(cursor))) {
                player.sendMessage(message("invalid-item"));
                return;
            }
            ItemStack one = cursor.clone();
            one.setAmount(1);
            ItemStack old = playerItems.put(slot.id(), one);
            cursor.setAmount(cursor.getAmount() - 1);
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            if (old != null) giveOrDrop(player, old);
            player.sendMessage(message("equipped").replace("%slot%", slot.id()));
        }

        applyStats(player);
        savePlayer(player.getUniqueId());
        saveDataFile();
        openEquipment(player);
    }

    private void handleUpgradeClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getRawSlot() != config.getInt("gui.upgrade-slots.confirm", 13)) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        UpgradeRule rule = findUpgrade(offhand);
        if (rule == null) {
            player.sendMessage(message("no-upgrade"));
            return;
        }
        if (rule.takeSource()) {
            player.getInventory().setItemInOffHand(null);
        }
        for (String command : rule.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%from_type%", rule.fromType())
                    .replace("%from_id%", rule.fromId())
                    .replace("%to_type%", rule.toType())
                    .replace("%to_id%", rule.toId()));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
        player.sendMessage(message("upgrade-success"));
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> ensureBoundOffhand(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!config.getBoolean("offhand.bound-item.enabled", true)) return;
        if (!isBoundOffhandItem(event.getOffHandItem()) && !isBoundOffhandItem(event.getMainHandItem())) return;
        event.setCancelled(true);
        ensureBoundOffhand(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isBoundOffhandItem(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        ensureBoundOffhand(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!config.getBoolean("offhand.bound-item.enabled", true)) return;
        if (event.getInventorySlots().contains(40) || isBoundOffhandItem(event.getOldCursor())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> ensureBoundOffhand(player));
        }
    }

    private boolean isLockedOffhandClick(InventoryClickEvent event, Player player) {
        if (!config.getBoolean("offhand.bound-item.enabled", true)) return false;
        if (event.getClick() == ClickType.SWAP_OFFHAND) return true;
        if (isBoundOffhandItem(event.getCurrentItem()) || isBoundOffhandItem(event.getCursor())) return true;
        return event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && event.getSlot() == 40
                && isBoundOffhandItem(player.getInventory().getItemInOffHand());
    }

    private void ensureBoundOffhand(Player player) {
        if (!config.getBoolean("enabled", true) || !config.getBoolean("offhand.bound-item.enabled", true)) return;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isBoundOffhandItem(offhand)) {
            refreshBoundOffhandLore(player, offhand);
            return;
        }

        if (offhand != null && !offhand.getType().isAir()) {
            if (!config.getBoolean("offhand.bound-item.replace-existing", true)) return;
            giveOrDrop(player, offhand);
        }

        ItemStack item = createBoundOffhandItem(player);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().setItemInOffHand(item);
        }
    }

    private ItemStack createBoundOffhandItem(Player player) {
        String typeId = config.getString("offhand.bound-item.type", "OFF_CATALYST");
        String itemId = config.getString("offhand.bound-item.id", "HA_MACH_HO_MENH_TIEN_HOAN");
        ItemStack item = createMmoItem(player, typeId, itemId);
        if (item == null || item.getType().isAir()) {
            item = named(
                    Material.matchMaterial(config.getString("offhand.bound-item.fallback-material", "NETHER_STAR")),
                    config.getString("offhand.bound-item.name", "&dHộ Mệnh Tiên Hoàn"),
                    config.getStringList("offhand.bound-item.lore")
            );
        }
        markBoundOffhand(item);
        refreshBoundOffhandLore(player, item);
        return item;
    }

    private ItemStack createMmoItem(Player player, String typeId, String itemId) {
        if (typeId == null || typeId.isBlank() || itemId == null || itemId.isBlank()) return null;
        try {
            Type type = MMOItems.plugin.getTypes().get(normalize(typeId));
            if (type == null) return null;
            return MMOItems.plugin.getItem(type, normalize(itemId), PlayerData.get(player));
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not create bound offhand MMOItem " + typeId + ":" + itemId + ": " + throwable.getMessage());
            return null;
        }
    }

    private void refreshBoundOffhandLore(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String configuredName = config.getString("offhand.bound-item.name", "");
        if (configuredName != null && !configuredName.isBlank()) {
            meta.setDisplayName(color(configuredName));
        }

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("offhand.bound-item.lore")) {
            lore.add(color(line.replace("%player%", player.getName())));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(boundOffhandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private void markBoundOffhand(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(boundOffhandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private boolean isBoundOffhandItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(boundOffhandKey, PersistentDataType.BYTE);
    }

    private void applyStats(Player player) {
        removeStats(player);
        Map<String, Double> totals = totalStats(player.getUniqueId());
        if (totals.isEmpty()) return;
        try {
            MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());
            if (mmoData == null || mmoData.getStatMap() == null) return;
            StatMap statMap = mmoData.getStatMap();
            for (Map.Entry<String, Double> entry : totals.entrySet()) {
                StatInstance instance = statMap.getInstance(entry.getKey());
                if (instance == null) continue;
                instance.addModifier(new StatModifier(MOD_PREFIX + entry.getKey(), entry.getKey(), entry.getValue(), ModifierType.FLAT));
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not apply equipment stats to " + player.getName() + ": " + throwable.getMessage());
        }
    }

    private void removeStats(Player player) {
        try {
            MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());
            if (mmoData == null || mmoData.getStatMap() == null) return;
            Set<String> stats = new HashSet<>();
            for (EquipSlot slot : slots.values()) {
                stats.addAll(slot.stats().keySet());
            }
            for (String stat : stats) {
                StatInstance instance = mmoData.getStatMap().getInstance(stat);
                if (instance != null) instance.remove(MOD_PREFIX + stat);
            }
        } catch (Throwable ignored) {
        }
    }

    private Map<String, Double> totalStats(UUID uuid) {
        Map<String, Double> total = new LinkedHashMap<>();
        Map<String, ItemStack> playerItems = equipped.getOrDefault(uuid, Map.of());
        for (EquipSlot slot : slots.values()) {
            if (!playerItems.containsKey(slot.id())) continue;
            slot.stats().forEach((stat, value) -> total.merge(stat, value, Double::sum));
        }
        return total;
    }

    private void loadSlots() {
        slots.clear();
        ConfigurationSection section = config.getConfigurationSection("slots");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String path = "slots." + id;
            Set<String> acceptedTypes = new HashSet<>();
            for (String type : config.getStringList(path + ".accepted-types")) {
                acceptedTypes.add(normalize(type));
            }
            Map<String, Double> stats = new LinkedHashMap<>();
            ConfigurationSection statsSection = config.getConfigurationSection(path + ".stats");
            if (statsSection != null) {
                for (String stat : statsSection.getKeys(false)) {
                    stats.put(normalize(stat), statsSection.getDouble(stat, 0.0D));
                }
            }
            slots.put(id, new EquipSlot(id, config.getInt(path + ".slot"), acceptedTypes, stats));
        }
    }

    private void loadPlayer(UUID uuid) {
        if (equipped.containsKey(uuid)) return;
        Map<String, ItemStack> playerItems = new HashMap<>();
        for (String slotId : slots.keySet()) {
            ItemStack item = data.getItemStack(uuid + "." + slotId);
            if (item != null && item.getType() != Material.AIR) {
                playerItems.put(slotId, item);
            }
        }
        equipped.put(uuid, playerItems);
    }

    private void savePlayer(UUID uuid) {
        Map<String, ItemStack> playerItems = equipped.getOrDefault(uuid, Map.of());
        for (String slotId : slots.keySet()) {
            data.set(uuid + "." + slotId, playerItems.get(slotId));
        }
    }

    private EquipSlot slotAt(int guiSlot) {
        for (EquipSlot slot : slots.values()) {
            if (slot.guiSlot() == guiSlot) return slot;
        }
        return null;
    }

    private boolean isInfoOffhandItem(ItemStack item) {
        String type = mmoType(item);
        if (type == null) return false;
        String id = mmoId(item);

        ConfigurationSection items = config.getConfigurationSection("offhand.info-items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                String allowedType = normalize(config.getString("offhand.info-items." + key + ".type", ""));
                String allowedId = normalize(config.getString("offhand.info-items." + key + ".id", ""));
                if (type.equals(allowedType) && (allowedId.isBlank() || allowedId.equals(id))) {
                    return true;
                }
            }
        }

        List<Map<?, ?>> itemMaps = config.getMapList("offhand.info-items");
        for (Map<?, ?> map : itemMaps) {
            Object typeValue = map.get("type");
            Object idValue = map.get("id");
            String allowedType = normalize(typeValue == null ? "" : String.valueOf(typeValue));
            String allowedId = normalize(idValue == null ? "" : String.valueOf(idValue));
            if (type.equals(allowedType) && (allowedId.isBlank() || allowedId.equals(id))) {
                return true;
            }
        }

        for (String allowed : config.getStringList("offhand.info-item-types")) {
            if (type.equals(normalize(allowed))) return true;
        }
        return false;
    }

    private UpgradeRule findUpgrade(ItemStack item) {
        String type = mmoType(item);
        String id = mmoId(item);
        if (type == null || id == null) return null;
        ConfigurationSection section = config.getConfigurationSection("offhand.upgrades");
        if (section == null) return null;
        for (String key : section.getKeys(false)) {
            String path = "offhand.upgrades." + key;
            String fromType = normalize(config.getString(path + ".from-type", ""));
            String fromId = normalize(config.getString(path + ".from-id", ""));
            if (!type.equals(fromType) || !id.equals(fromId)) continue;
            return new UpgradeRule(
                    fromType,
                    fromId,
                    normalize(config.getString(path + ".to-type", "")),
                    normalize(config.getString(path + ".to-id", "")),
                    config.getBoolean(path + ".take-source", true),
                    config.getStringList(path + ".commands")
            );
        }
        return null;
    }

    private String mmoType(ItemStack item) {
        return mmoString(item, "MMOITEMS_ITEM_TYPE", "MMOITEMS_TYPE", "type");
    }

    private String mmoId(ItemStack item) {
        return mmoString(item, "MMOITEMS_ITEM_ID", "MMOITEMS_ID", "id");
    }

    private String mmoString(ItemStack item, String... keys) {
        if (item == null || item.getType() == Material.AIR) return null;
        try {
            NBTItem nbt = NBTItem.get(item);
            for (String key : keys) {
                String value = nbt.getString(key);
                if (value != null && !value.isBlank()) return normalize(value);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ItemStack infoItem(Player player) {
        ItemStack item = named(
                Material.matchMaterial(config.getString("gui.info-item.material", "NETHER_STAR")),
                config.getString("gui.info-item.name", "&6Thông Tin Người Chơi"),
                replaceInfo(config.getStringList("gui.info-item.lore"), player)
        );
        return item;
    }

    private List<String> replaceInfo(List<String> lines, Player player) {
        List<String> result = new ArrayList<>();
        Map<String, Double> stats = totalStats(player.getUniqueId());
        Realm realm = realmManager.getPlayerCurrentRealm(player.getUniqueId());
        String realmText = realmManager.getPlayerRealmDisplay(player.getUniqueId());
        String tuVi = String.valueOf((long) TuTien.getApi().getTuVi(player.getUniqueId()));
        for (String line : lines) {
            String formatted = line
                    .replace("%player%", player.getName())
                    .replace("%realm%", realm == null ? realmText : realmText)
                    .replace("%tuvi%", tuVi);
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                formatted = formatted.replace("%stat_" + entry.getKey() + "%", formatNumber(entry.getValue()));
            }
            result.add(color(formatted.replaceAll("%stat_[A-Z0-9_]+%", "0")));
        }
        return result;
    }

    private ItemStack emptySlotItem(EquipSlot slot) {
        String path = "slots." + slot.id() + ".empty";
        return named(
                Material.matchMaterial(config.getString(path + ".material", "GRAY_STAINED_GLASS_PANE")),
                config.getString(path + ".name", "&7" + slot.id()),
                config.getStringList(path + ".lore")
        );
    }

    private ItemStack confirmItem(UpgradeRule rule) {
        ItemStack item = named(
                Material.matchMaterial(config.getString("gui.upgrade-confirm.material", "EMERALD")),
                config.getString("gui.upgrade-confirm.name", "&aTiến Hoá"),
                config.getStringList("gui.upgrade-confirm.lore").stream()
                        .map(line -> line
                                .replace("%from_type%", rule.fromType())
                                .replace("%from_id%", rule.fromId())
                                .replace("%to_type%", rule.toType())
                                .replace("%to_id%", rule.toId()))
                        .toList()
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "upgrade_confirm");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack previewItem(UpgradeRule rule) {
        return named(Material.EMERALD, "&e" + rule.toType() + ":" + rule.toId(), List.of("&7Item nhận qua command cấu hình."));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = named(
                Material.matchMaterial(config.getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE")),
                config.getString("gui.filler.name", " "),
                List.of()
        );
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(this::color).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private String message(String key) {
        return color(config.getString("messages." + key, key));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.2f", value);
    }

    private record EquipSlot(String id, int guiSlot, Set<String> acceptedTypes, Map<String, Double> stats) {
        boolean accepts(String type) {
            return type != null && acceptedTypes.contains(type);
        }
    }

    private record UpgradeRule(String fromType, String fromId, String toType, String toId, boolean takeSource, List<String> commands) {
    }
}
