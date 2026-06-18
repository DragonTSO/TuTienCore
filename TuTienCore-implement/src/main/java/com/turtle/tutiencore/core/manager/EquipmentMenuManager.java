package com.turtle.tutiencore.core.manager;

import com.turtle.tutiencore.api.TuTien;
import com.turtle.tutiencore.api.realm.PlayerRealm;
import com.turtle.tutiencore.api.realm.Realm;
import com.turtle.tutiencore.api.realm.SubRealm;
import com.turtle.tutiencore.core.hook.MMOItemsRealmRequirementHook;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class EquipmentMenuManager implements Listener, CommandExecutor {

    private static final String MOD_PREFIX = "tutien_equipment_";
    private static final String CONSUMABLE_MOD_PREFIX = "tutien_consumable_";
    private static final String BOUND_OFFHAND_DATA_PATH = "bound-offhand";
    private static final Pattern STAT_PLACEHOLDER = Pattern.compile("%stat_([A-Z0-9_]+)%");
    private static final String DURATION_STAT_ID = "DAN_DUOC_DURATION";
    public static final String DAN_DUOC_TU_VI_BONUS_STAT = "DAN_DUOC_TUVI_BONUS";
    public static final String DAN_DUOC_MYTHIC_MONEY_BONUS_STAT = "DAN_DUOC_MYTHIC_MONEY_BONUS";
    public static final String DAN_DUOC_FORGE_LUCK_BONUS_STAT = "DAN_DUOC_FORGE_LUCK_BONUS";
    private static final Set<String> SYSTEM_STAT_IDS = Set.of(
            DAN_DUOC_TU_VI_BONUS_STAT,
            DAN_DUOC_MYTHIC_MONEY_BONUS_STAT,
            DAN_DUOC_FORGE_LUCK_BONUS_STAT
    );
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(d|day|days|h|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)?", Pattern.CASE_INSENSITIVE);

    private final JavaPlugin plugin;
    private final RealmManager realmManager;
    private final File configFile;
    private final File dataFile;
    private final NamespacedKey actionKey;
    private final NamespacedKey boundOffhandKey;
    private final NamespacedKey durationRemainingKey;
    private final NamespacedKey durationTotalKey;
    private final NamespacedKey ancientDurationSecondsKey;

    private FileConfiguration config;
    private FileConfiguration data;
    private final Map<String, EquipSlot> slots = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ItemStack>> equipped = new HashMap<>();
    private final Map<UUID, Set<String>> activeConsumables = new HashMap<>();
    // Lightweight identity cache: survives the equipped-map eviction on quit so that a player who
    // relogs within the same JVM session can have their slots restored instantly without touching
    // the YAML or waiting for MMOItems PlayerData to load.
    // Structure: uuid -> (slotId -> SlotIdentity)
    private final Map<UUID, Map<String, SlotIdentity>> slotIdentityCache = new HashMap<>();
    // Players who still have at least one equipped slot that needs to be regenerated from its stored
    // MMOItems identity (type + id). Regeneration can fail right after join because MMOItems has not
    // finished loading the player's PlayerData yet; these players are retried on later loadPlayer
    // calls (driven by the 1s tickTimedEquipment loop) until every slot is restored.
    private final Set<UUID> pendingSlotRegen = new HashSet<>();
    private int durationSaveCounter;
    private long consumableCounter;
    private volatile boolean dataDirty;
    private final java.util.concurrent.atomic.AtomicBoolean dataWriteInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);
    // Monotonically-increasing counter stamped onto every async write. saveDataFileNow() bumps it
    // before writing synchronously so that any older async task that completes afterwards knows its
    // snapshot is stale and must not touch the file.
    private final java.util.concurrent.atomic.AtomicLong writeGeneration = new java.util.concurrent.atomic.AtomicLong(0);

    public EquipmentMenuManager(JavaPlugin plugin, RealmManager realmManager) {
        this.plugin = plugin;
        this.realmManager = realmManager;
        this.configFile = new File(plugin.getDataFolder(), "equipment-menu.yml");
        this.dataFile = new File(plugin.getDataFolder(), "equipment-data.yml");
        this.actionKey = new NamespacedKey(plugin, "equipment_action");
        this.boundOffhandKey = new NamespacedKey(plugin, "equipment_bound_offhand");
        this.durationRemainingKey = new NamespacedKey(plugin, "equipment_duration_remaining_seconds");
        this.durationTotalKey = new NamespacedKey(plugin, "equipment_duration_total_seconds");
        this.ancientDurationSecondsKey = new NamespacedKey("tutienancient", "dan_duoc_duration_seconds");
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickTimedEquipment, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::ensureOnlineBoundOffhands, 40L, 40L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushDirtyData, 20L, 20L);
    }

    public void reload() {
        // Flush any pending dirty data before we replace the in-memory config from disk,
        // otherwise debounced changes would be lost when reassigning `data`.
        // Also wait for any in-flight async write so the disk is fully up-to-date before
        // we read it back; without this wait the reload could pick up a stale snapshot.
        if (dataDirty || dataWriteInProgress.get()) {
            saveDataFileNow();
        }
        if (!configFile.exists()) {
            plugin.saveResource("equipment-menu.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeConsumableStats(player);
        }
        activeConsumables.clear();
        loadSlots();
        equipped.clear();
        pendingSlotRegen.clear();
        slotIdentityCache.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
            applyStats(player);
            ensureBoundOffhand(player);
        }
    }

    public void saveAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            saveCurrentBoundOffhandState(player);
        }
        for (UUID uuid : equipped.keySet()) {
            savePlayer(uuid);
        }
        saveDataFileNow();
    }

    /**
     * Marks the equipment data as dirty instead of writing immediately. The actual write is
     * debounced and pushed off the main thread by {@link #flushDirtyData()} (runs once per second).
     * <p>
     * Previously this serialized the entire YAML (all players) and wrote it to disk synchronously on
     * every inventory close / equip click / bound-offhand refresh, which caused MSPT spikes
     * (YamlConfiguration.saveToString on the server thread). Collapsing many back-to-back saves into
     * at most one write per second removes that hot path.
     */
    private void saveDataFile() {
        dataDirty = true;
    }

    /**
     * Debounced flusher: snapshots the data once per second (only when dirty) and both serializes
     * <em>and</em> writes it to disk asynchronously.
     * <p>
     * The expensive part of a YAML save is {@code YamlConfiguration.saveToString()} —
     * SnakeYAML's node-tree construction ({@code toNodeTree}) plus string emission
     * ({@code Serializer.serialize}). Previously that ran on the main thread (Spark showed it at
     * ~12% of server-thread time) and only the disk I/O was off-thread. We now take a cheap,
     * mutation-safe snapshot of the config tree on the main thread (cloning ItemStacks so the
     * async serializer never touches live items that {@link #tickTimedEquipment} mutates each
     * second) and push the whole serialize+write onto the async thread.
     * <p>
     * Each async task is stamped with the current {@link #writeGeneration}. If
     * {@link #saveDataFileNow()} runs while the task is queued or in-flight, it bumps the generation
     * so the stale task silently skips the file write — preventing it from clobbering the newer
     * synchronous write.
     */
    private void flushDirtyData() {
        if (!dataDirty || dataWriteInProgress.get()) {
            return;
        }
        dataDirty = false;

        // Cheap main-thread snapshot: copies the config tree and clones ItemStacks. This avoids the
        // costly SnakeYAML serialization on the tick while guaranteeing the async serializer works
        // on an isolated, immutable-by-then structure (no shared live ItemStack references).
        final YamlConfiguration snapshot;
        try {
            snapshot = snapshotData();
        } catch (Throwable throwable) {
            dataDirty = true;
            plugin.getLogger().warning("Could not snapshot equipment-data.yml: " + throwable.getMessage());
            return;
        }

        final long myGeneration = writeGeneration.get();
        dataWriteInProgress.set(true);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // A newer saveDataFileNow() call has superseded this snapshot — skip the work.
                if (writeGeneration.get() != myGeneration) {
                    return;
                }
                // Heavy serialization now happens off the server thread.
                String serialized = snapshot.saveToString();
                // Re-check the generation: a sync write may have landed while we were serializing.
                if (writeGeneration.get() != myGeneration) {
                    return;
                }
                writeDataToDisk(serialized);
            } catch (Throwable throwable) {
                dataDirty = true;
                plugin.getLogger().warning("Could not save equipment-data.yml: " + throwable.getMessage());
            } finally {
                dataWriteInProgress.set(false);
            }
        });
    }

    /**
     * Builds an isolated copy of the live {@link #data} configuration on the main thread so that the
     * expensive YAML serialization can run on an async thread without racing concurrent mutations.
     * <p>
     * Immutable leaf values (strings, numbers, booleans) are copied by reference — safe to share.
     * {@link ItemStack} values are {@link ItemStack#clone() cloned} because the same instances live
     * in the {@link #equipped} map and have their meta mutated by {@link #tickTimedEquipment} (the
     * per-second duration countdown); sharing them with the async serializer would risk torn reads
     * or {@code ConcurrentModificationException} during node-tree construction.
     */
    private YamlConfiguration snapshotData() {
        YamlConfiguration copy = new YamlConfiguration();
        copySection(data, copy);
        return copy;
    }

    private void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            Object value = from.get(key);
            if (value instanceof ConfigurationSection section) {
                copySection(section, to.createSection(key));
            } else if (value instanceof ItemStack item) {
                to.set(key, item.clone());
            } else {
                to.set(key, value);
            }
        }
    }

    /**
     * Synchronous full write. Used on shutdown/reload where we must not lose data to a pending flush.
     * Bumps {@link #writeGeneration} so any in-flight async write task knows its snapshot is stale
     * and must not overwrite the file that this method is about to produce.
     */
    private void saveDataFileNow() {
        dataDirty = false;
        // Bump before writing so any async task that checks afterwards sees the new generation
        // and skips its (older) file write.
        writeGeneration.incrementAndGet();
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save equipment-data.yml: " + exception.getMessage());
        }
    }

    private void writeDataToDisk(String serialized) throws IOException {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        java.nio.file.Files.write(dataFile.toPath(), serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void removeAllOnlineModifiers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeStats(player);
            removeConsumableStats(player);
        }
        activeConsumables.clear();
    }

    public double getEquippedSystemStatBonus(Player player, String statId) {
        if (player == null || statId == null || statId.isBlank() || !config.getBoolean("enabled", true)) {
            return 0.0D;
        }
        String targetStat = normalizeStatId(statId);
        if (targetStat.isBlank()) {
            return 0.0D;
        }

        loadPlayer(player.getUniqueId());
        Map<String, ItemStack> playerItems = equipped.getOrDefault(player.getUniqueId(), Map.of());
        double total = 0.0D;
        for (EquipSlot slot : slots.values()) {
            if (!playerItems.containsKey(slot.id())) {
                continue;
            }
            total += itemStatValue(playerItems.get(slot.id()), targetStat);
            if (slot.useConfigStats()) {
                total += slot.stats().getOrDefault(targetStat, 0.0D);
            }
        }
        return total;
    }

    public EquippedMmoItem getEquippedMmoItem(Player player, String slotId) {
        if (player == null || slotId == null || slotId.isBlank() || !config.getBoolean("enabled", true)) {
            return null;
        }

        String resolvedSlotId = resolveSlotId(slotId);
        if (resolvedSlotId == null) {
            return null;
        }

        loadPlayer(player.getUniqueId());
        ItemStack item = equipped.getOrDefault(player.getUniqueId(), Map.of()).get(resolvedSlotId);
        String type = mmoType(item);
        String id = mmoId(item);
        if (type == null || id == null) {
            return null;
        }
        return new EquippedMmoItem(type, id);
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
            ItemStack equippedItem = playerItems.get(slot.id());
            inventory.setItem(slot.guiSlot(), equippedItem == null ? emptySlotItem(slot) : displayEquippedItem(slot, equippedItem));
        }
        player.openInventory(inventory);
        // If some slots are still pending regeneration (MMOItems PlayerData not ready yet), schedule
        // a deferred refresh so the GUI auto-corrects once the items become available rather than
        // showing empty placeholder slots until the player closes and reopens.
        if (pendingSlotRegen.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && pendingSlotRegen.contains(player.getUniqueId())) {
                    loadPlayer(player.getUniqueId());
                    refreshOpenEquipment(player);
                    applyStats(player);
                }
            }, 40L);
        }
    }

    private void openUpgrade(Player player) {
        ensureBoundOffhand(player);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        UpgradeRule rule = findUpgrade(offhand);
        Inventory inventory = Bukkit.createInventory(player, config.getInt("gui.upgrade-size", 27), color(config.getString("gui.upgrade-title", "&8Tiến Hoá Offhand")));
        fill(inventory);
        inventory.setItem(config.getInt("gui.upgrade-slots.source", 11), offhand == null ? new ItemStack(Material.AIR) : offhand.clone());
        if (rule != null) {
            inventory.setItem(config.getInt("gui.upgrade-slots.result", 15), previewItem(player, rule));
            inventory.setItem(config.getInt("gui.upgrade-slots.confirm", 13), confirmItem(player, offhand, rule));
        } else {
            inventory.setItem(config.getInt("gui.upgrade-slots.confirm", 13), named(Material.BARRIER, "&cKhông thể tiến hoá", List.of(message("no-upgrade"))));
        }
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player == null || !player.isOnline()) return;
            applyStats(player);
            ensureBoundOffhand(player);
            // If any slot is still pending regeneration (MMOItems PlayerData not ready at join time),
            // force a retry now that the player has been online for a full second. This covers cases
            // where tickTimedEquipment hasn't fired yet or MMOItems takes longer than usual to load.
            if (pendingSlotRegen.contains(player.getUniqueId())) {
                loadPlayer(player.getUniqueId());
                applyStats(player);
            }
        }, 20L);
        // Second retry at 3 seconds — belt-and-suspenders for slow MMOItems PlayerData loads.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player == null || !player.isOnline()) return;
            if (pendingSlotRegen.contains(player.getUniqueId())) {
                loadPlayer(player.getUniqueId());
                applyStats(player);
                refreshOpenEquipment(player);
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        saveCurrentBoundOffhandState(event.getPlayer());
        savePlayer(uuid);
        // Remove from the cache immediately after saving so that any InventoryCloseEvent that
        // Paper fires after PlayerQuitEvent (for players who disconnect with a GUI open) finds no
        // entry in `equipped` and skips its own savePlayer call. Without this ordering,
        // onInventoryClose would write an empty map and mark the data dirty, causing flushDirtyData
        // to later overwrite the correct on-disk state with nulls.
        equipped.remove(uuid);
        pendingSlotRegen.remove(uuid);
        removeStats(event.getPlayer());
        removeConsumableStats(event.getPlayer());
        // Write synchronously after removing from cache so the correct data is on disk before the
        // player can rejoin. saveDataFileNow() bumps writeGeneration so any queued async write
        // (stale snapshot) will not clobber this save.
        saveDataFileNow();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureBoundOffhand(event.getPlayer()), 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.getBoolean("enabled", true)) return;
        if (handleConsumableDanDuoc(event)) return;
        if (!config.getBoolean("offhand.bound-item.open-on-world-click", false)) return;
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
        if (handleBoundOffhandInventoryClick(event, player)) {
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
        if (!(event.getPlayer() instanceof Player player)) return;
        // Skip if the player has already been removed from the equipped cache (i.e. onQuit already
        // ran and called savePlayer + saveDataFileNow). On Paper, InventoryCloseEvent fires after
        // PlayerQuitEvent for disconnecting players, so equipped.remove(uuid) will have already
        // executed; calling savePlayer here would persist an empty map and overwrite the correct
        // on-disk data written by onQuit.
        if (!equipped.containsKey(player.getUniqueId())) return;
        savePlayer(player.getUniqueId());
        saveDataFile();
    }

    private void handleEquipmentClick(InventoryClickEvent event, Player player) {
        int raw = event.getRawSlot();
        if (raw < 0) return;
        if (raw >= event.getInventory().getSize()) {
            // Click inside the player's own inventory while the GUI is open.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                handleQuickEquip(event, player);
            }
            return;
        }
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
                sendEquipmentMessage(player, "unequipped", slot, current);
            }
        } else {
            ItemStack cursor = event.getCursor();
            if (!slot.accepts(mmoType(cursor))) {
                player.sendMessage(message("invalid-item"));
                return;
            }
            if (!canUseMmoItem(player, cursor)) {
                return;
            }
            ItemStack one = cursor.clone();
            one.setAmount(1);
            if (!prepareTimedItemForEquip(slot, one)) {
                player.sendMessage(message("expired-item"));
                return;
            }
            ItemStack old = playerItems.put(slot.id(), one);
            cursor.setAmount(cursor.getAmount() - 1);
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            if (old != null) giveOrDrop(player, old);
            sendEquipmentMessage(player, "equipped", slot, one);
            plugin.getLogger().info("[EquipClick] " + player.getName() + " equipped " + mmoType(one) + ":" + mmoId(one)
                    + " into slot=" + slot.id() + " guiSlot=" + slot.guiSlot()
                    + " playerItemsSize=" + playerItems.size()
                    + " playerItemsKeys=" + playerItems.keySet());
        }

        applyStats(player);
        savePlayer(player.getUniqueId());
        saveDataFile();
        openEquipment(player);
    }

    private void handleQuickEquip(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        String type = mmoType(clicked);
        if (type == null) {
            player.sendMessage(message("invalid-item"));
            return;
        }

        loadPlayer(player.getUniqueId());
        Map<String, ItemStack> playerItems = equipped.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());

        EquipSlot target = findQuickEquipSlot(type, playerItems);
        if (target == null) {
            player.sendMessage(message("invalid-item"));
            return;
        }

        if (!canUseMmoItem(player, clicked)) {
            return;
        }

        ItemStack one = clicked.clone();
        one.setAmount(1);
        if (!prepareTimedItemForEquip(target, one)) {
            player.sendMessage(message("expired-item"));
            return;
        }

        ItemStack old = playerItems.put(target.id(), one);
        clicked.setAmount(clicked.getAmount() - 1);
        event.setCurrentItem(clicked.getAmount() <= 0 ? null : clicked);
        if (old != null) {
            giveOrDrop(player, old);
        }
        sendEquipmentMessage(player, "equipped", target, one);

        applyStats(player);
        savePlayer(player.getUniqueId());
        saveDataFile();
        openEquipment(player);
    }

    private EquipSlot findQuickEquipSlot(String type, Map<String, ItemStack> playerItems) {
        EquipSlot firstMatch = null;
        for (EquipSlot slot : slots.values()) {
            if (!slot.accepts(type)) {
                continue;
            }
            if (firstMatch == null) {
                firstMatch = slot;
            }
            if (playerItems.get(slot.id()) == null) {
                return slot;
            }
        }
        return firstMatch;
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
        List<String> failures = upgradeFailures(player, rule);
        if (!failures.isEmpty()) {
            player.sendMessage(message("upgrade-requirement-failed"));
            failures.forEach(player::sendMessage);
            return;
        }
        ItemStack resultItem = createUpgradeResultItem(player, rule);
        if (resultItem == null || resultItem.getType().isAir()) {
            player.sendMessage(message("upgrade-create-failed", "&cKhông tạo được item tiến hoá. Hãy kiểm tra to-type/to-id."));
            return;
        }
        if (rule.cost() > 0 && !withdrawMoney(player, rule.cost())) {
            player.sendMessage(message("not-enough-money").replace("%cost%", formatMoney(rule.cost())));
            return;
        }
        if (rule.takeSource()) {
            player.getInventory().setItemInOffHand(resultItem);
            saveBoundOffhandState(player.getUniqueId(), resultItem);
            saveDataFile();
            scheduleBoundOffhandLoreAppend(player, 4L);
        } else {
            giveOrDrop(player, resultItem);
        }
        for (String command : executableUpgradeCommands(rule.commands())) {
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

    private boolean handleBoundOffhandInventoryClick(InventoryClickEvent event, Player player) {
        if (!isLockedOffhandClick(event, player)) return false;
        if (!isPlayerOffhandSlot(event, player) || !isBoundOffhandItem(event.getCurrentItem())) return true;

        ClickType click = event.getClick();
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            Bukkit.getScheduler().runTask(plugin, () -> openEquipment(player));
        } else if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            Bukkit.getScheduler().runTask(plugin, () -> openUpgrade(player));
        }
        return true;
    }

    private boolean isPlayerOffhandSlot(InventoryClickEvent event, Player player) {
        return event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && event.getSlot() == 40;
    }

    private void ensureBoundOffhand(Player player) {
        if (!config.getBoolean("enabled", true) || !config.getBoolean("offhand.bound-item.enabled", true)) return;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isManagedOffhandItem(offhand)) {
            markBoundOffhand(offhand);
            saveBoundOffhandState(player.getUniqueId(), offhand);
            refreshBoundOffhandLore(player, offhand);
            scheduleBoundOffhandLoreAppend(player, 4L, 10L);
            return;
        }

        if (offhand != null && !offhand.getType().isAir()) {
            if (!config.getBoolean("offhand.bound-item.replace-existing", true)) return;
            giveOrDrop(player, offhand);
        }

        ItemStack item = createBoundOffhandItem(player);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().setItemInOffHand(item);
            saveBoundOffhandState(player.getUniqueId(), item);
            saveDataFile();
            scheduleBoundOffhandLoreAppend(player, 1L, 4L, 10L, 20L);
        }
    }

    private void ensureOnlineBoundOffhands() {
        if (!config.getBoolean("enabled", true) || !config.getBoolean("offhand.bound-item.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureBoundOffhand(player);
        }
    }

    private ItemStack createBoundOffhandItem(Player player) {
        BoundOffhandState state = boundOffhandState(player.getUniqueId());
        String typeId = state.type();
        String itemId = state.id();
        ItemStack item = createMmoItem(player, typeId, itemId);
        BoundOffhandState fallbackState = defaultBoundOffhandState();
        if ((item == null || item.getType().isAir()) && !state.equals(fallbackState)) {
            item = createMmoItem(player, fallbackState.type(), fallbackState.id());
            if (item != null && !item.getType().isAir()) {
                typeId = fallbackState.type();
                itemId = fallbackState.id();
            }
        }
        if (item == null || item.getType().isAir()) {
            item = named(
                    Material.matchMaterial(config.getString("offhand.bound-item.fallback-material", "NETHER_STAR")),
                    config.getString("offhand.bound-item.name", "&dHộ Mệnh Tiên Hoàn"),
                    config.getStringList("offhand.bound-item.lore")
            );
        }
        markBoundOffhand(item);
        saveBoundOffhandState(player.getUniqueId(), typeId, itemId);
        return item;
    }

    private BoundOffhandState defaultBoundOffhandState() {
        return new BoundOffhandState(
                normalize(config.getString("offhand.bound-item.type", "OFF_CATALYST")),
                normalize(config.getString("offhand.bound-item.id", "HA_MACH_HO_MENH_TIEN_HOAN"))
        );
    }

    private BoundOffhandState boundOffhandState(UUID uuid) {
        BoundOffhandState fallback = defaultBoundOffhandState();
        String path = uuid + "." + BOUND_OFFHAND_DATA_PATH;
        String type = normalize(data.getString(path + ".type", fallback.type()));
        String id = normalize(data.getString(path + ".id", fallback.id()));
        if (type.isBlank() || id.isBlank()) {
            return fallback;
        }
        return new BoundOffhandState(type, id);
    }

    private void saveCurrentBoundOffhandState(Player player) {
        if (player == null) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isManagedOffhandItem(offhand)) {
            saveBoundOffhandState(player.getUniqueId(), offhand);
        }
    }

    private void saveBoundOffhandState(UUID uuid, ItemStack item) {
        String type = mmoType(item);
        String id = mmoId(item);
        if (type == null || id == null || type.isBlank() || id.isBlank()) return;
        saveBoundOffhandState(uuid, type, id);
    }

    private void saveBoundOffhandState(UUID uuid, String type, String id) {
        if (uuid == null) return;
        String normalizedType = normalize(type);
        String normalizedId = normalize(id);
        if (normalizedType.isBlank() || normalizedId.isBlank()) return;
        String path = uuid + "." + BOUND_OFFHAND_DATA_PATH;
        data.set(path + ".type", normalizedType);
        data.set(path + ".id", normalizedId);
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

    /**
     * Like {@link #createMmoItem} but logs at a finer level so slot-restore failures are
     * distinguishable from bound-offhand failures in the server log.
     */
    private ItemStack createMmoItemForSlot(Player player, String typeId, String itemId, String slotId) {
        if (typeId == null || typeId.isBlank() || itemId == null || itemId.isBlank()) return null;
        try {
            Type type = MMOItems.plugin.getTypes().get(normalize(typeId));
            if (type == null) {
                plugin.getLogger().warning("[EquipSlot] regenerate " + slotId + ": MMOItems type not found: " + typeId);
                return null;
            }
            net.Indyuce.mmoitems.api.player.PlayerData pd = PlayerData.get(player);
            if (pd == null) {
                plugin.getLogger().fine("[EquipSlot] regenerate " + slotId + ": MMOItems PlayerData not ready for " + player.getName() + " (will retry)");
                return null;
            }
            ItemStack result = MMOItems.plugin.getItem(type, normalize(itemId), pd);
            if (result == null || result.getType().isAir()) {
                plugin.getLogger().warning("[EquipSlot] regenerate " + slotId + ": MMOItems returned null/air for " + typeId + ":" + itemId + " (player=" + player.getName() + ")");
            }
            return result;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[EquipSlot] regenerate " + slotId + " (" + typeId + ":" + itemId + ") failed for " + player.getName() + ": " + throwable.getMessage());
            return null;
        }
    }

    private ItemStack createUpgradeResultItem(Player player, UpgradeRule rule) {
        ItemStack item = createMmoItem(player, rule.toType(), rule.toId());
        if (item == null || item.getType().isAir()) {
            return null;
        }

        ItemStack result = item.clone();
        result.setAmount(1);
        markBoundOffhand(result);
        refreshBoundOffhandLore(player, result);
        return result;
    }

    private void refreshBoundOffhandLore(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        if (hasUnparsedMmoItemsExpansionLore(item)) {
            parseMmoItemsExpansionLore(player, item);
        }
        appendBoundOffhandLore(player, item);
    }

    private void appendBoundOffhandLore(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        boolean syncToOffhand = isCurrentManagedOffhand(player, item);

        List<String> appendix = boundOffhandAppendix(player);
        boolean changed = false;

        if (!meta.getPersistentDataContainer().has(boundOffhandKey, PersistentDataType.BYTE)) {
            meta.getPersistentDataContainer().set(boundOffhandKey, PersistentDataType.BYTE, (byte) 1);
            changed = true;
        }

        if (!appendix.isEmpty()) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            List<String> newLore = stripBoundOffhandAppendix(lore);
            newLore.addAll(appendix);

            if (!lore.equals(newLore)) {
                meta.setLore(newLore);
                changed = true;
            }
        }

        if (!changed) return;

        item.setItemMeta(meta);
        if (syncToOffhand) {
            player.getInventory().setItemInOffHand(item);
        }
    }

    private boolean isCurrentManagedOffhand(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) return false;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType().isAir()) return false;
        if (item == offhand) return true;

        String itemType = mmoType(item);
        String itemId = mmoId(item);
        String offhandType = mmoType(offhand);
        String offhandId = mmoId(offhand);
        if (itemType != null && itemId != null && offhandType != null && offhandId != null) {
            return itemType.equals(offhandType) && itemId.equals(offhandId);
        }

        return isBoundOffhandItem(item) && isBoundOffhandItem(offhand) && item.getType() == offhand.getType();
    }

    private void scheduleBoundOffhandLoreAppend(Player player, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (!isManagedOffhandItem(offhand)) return;
            markBoundOffhand(offhand);
            if (hasUnparsedMmoItemsExpansionLore(offhand)) {
                parseMmoItemsExpansionLore(player, offhand);
            }
            appendBoundOffhandLore(player, offhand);
        }, delay);
    }

    private void scheduleBoundOffhandLoreAppend(Player player, long... delays) {
        for (long delay : delays) {
            scheduleBoundOffhandLoreAppend(player, delay);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseMmoItemsExpansionLore(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return;
        org.bukkit.plugin.Plugin expansion = Bukkit.getPluginManager().getPlugin("MMOItemsExpansion");
        if (expansion == null || !expansion.isEnabled()) return;
        try {
            Object parsed = expansion.getClass()
                    .getMethod("applyLorePlaceholders", Player.class, ItemStack.class, List.class)
                    .invoke(expansion, player, item, meta.getLore());
            if (parsed instanceof List<?> list) {
                meta.setLore((List<String>) list);
                item.setItemMeta(meta);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private boolean hasUnparsedMmoItemsExpansionLore(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return false;
        return meta.getLore().stream().anyMatch(line -> line.contains("{can-use}") || line.contains("#can-use#"));
    }

    private List<String> boundOffhandAppendix(Player player) {
        List<String> appendix = new ArrayList<>();
        for (String line : config.getStringList("offhand.bound-item.lore")) {
            appendix.add(color(line.replace("%player%", player.getName())));
        }
        return appendix;
    }

    private void ensureBoundOffhandDefaultGuides(List<String> appendix) {
        boolean hasRightClick = appendix.stream().anyMatch(line -> {
            String normalized = normalizeLoreLine(line);
            return normalized.contains("chuot phai") && normalized.contains("thong tin");
        });
        boolean hasLeftClick = appendix.stream().anyMatch(line -> {
            String normalized = normalizeLoreLine(line);
            return normalized.contains("chuot trai") && normalized.contains("nang cap");
        });
        boolean hasCannotRemove = appendix.stream().anyMatch(line -> {
            String normalized = normalizeLoreLine(line);
            return normalized.contains("khong the") && normalized.contains("thao") && normalized.contains("tay phu");
        });

        if (!hasRightClick || !hasLeftClick) {
            int insertIndex = 0;
            if (appendix.isEmpty() || !isBlankLoreLine(appendix.get(0))) {
                appendix.add(0, color(""));
            }
            insertIndex = 1;
            if (!hasRightClick) {
                appendix.add(insertIndex++, color("&e  Chuột phải &7để mở thông tin/trang bị."));
            }
            if (!hasLeftClick) {
                appendix.add(insertIndex, color("&e  Chuột trái &7để mở nâng cấp."));
            }
        }

        if (!hasCannotRemove) {
            if (!appendix.isEmpty() && !isBlankLoreLine(appendix.get(appendix.size() - 1))) {
                appendix.add(color(""));
            }
            appendix.add(color("&c  Không thể tháo khỏi tay phụ."));
        }
    }

    private List<String> stripBoundOffhandAppendix(List<String> lore) {
        List<String> stripped = new ArrayList<>();
        for (String line : lore) {
            if (!isBoundOffhandGuideLine(line)) {
                stripped.add(line);
            }
        }
        while (!stripped.isEmpty() && isBlankLoreLine(stripped.get(stripped.size() - 1))) {
            stripped.remove(stripped.size() - 1);
        }
        return stripped;
    }

    private List<String> ensureCompleteBoundOffhandLore(List<String> lore) {
        List<String> fixed = new ArrayList<>(lore == null ? List.of() : lore);
        boolean hasRightClick = fixed.stream().anyMatch(line -> {
            String normalized = normalizeLoreLine(line);
            return normalized.contains("chuot phai") && normalized.contains("thong tin");
        });
        boolean hasLeftClick = fixed.stream().anyMatch(line -> {
            String normalized = normalizeLoreLine(line);
            return normalized.contains("chuot trai") && normalized.contains("nang cap");
        });
        boolean hasCannotRemove = fixed.stream().anyMatch(line -> {
            String normalized = normalizeLoreLine(line);
            return normalized.contains("khong the") && normalized.contains("thao") && normalized.contains("tay phu");
        });

        if (hasRightClick && hasLeftClick && hasCannotRemove) {
            return fixed;
        }

        int insertIndex = firstCannotRemoveIndex(fixed);
        if (insertIndex < 0) {
            if (!fixed.isEmpty() && !isBlankLoreLine(fixed.get(fixed.size() - 1))) {
                fixed.add(color(""));
            }
            insertIndex = fixed.size();
        } else if (insertIndex > 0 && !isBlankLoreLine(fixed.get(insertIndex - 1))) {
            fixed.add(insertIndex++, color(""));
        }

        if (!hasRightClick) {
            fixed.add(insertIndex++, color("&e  Chuột phải &7để mở thông tin/trang bị."));
        }
        if (!hasLeftClick) {
            fixed.add(insertIndex++, color("&e  Chuột trái &7để mở nâng cấp."));
        }
        if (!hasCannotRemove) {
            if (insertIndex > 0 && !isBlankLoreLine(fixed.get(insertIndex - 1))) {
                fixed.add(insertIndex++, color(""));
            }
            fixed.add(insertIndex, color("&c  Không thể tháo khỏi tay phụ."));
        }
        return fixed;
    }

    private int firstCannotRemoveIndex(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String normalized = normalizeLoreLine(lore.get(i));
            if (normalized.contains("khong the") && normalized.contains("thao") && normalized.contains("tay phu")) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBoundOffhandGuideLine(String line) {
        String normalized = normalizeLoreLine(line);
        return normalized.equals("vat pham tay phu cua he thong.")
                || normalized.equals("de mo thong tin/trang bi.")
                || normalized.equals("de mo tien hoa.")
                || normalized.equals("chuot phai de mo thong tin/trang bi.")
                || normalized.equals("chuot trai de mo nang cap.")
                || normalized.equals("chuot trai de mo tien hoa.")
                || normalized.equals("khong the thao khoi tay phu.")
                || line.contains("ꐣ")
                || line.contains("ꐝ")
                || (normalized.contains("vat pham") && normalized.contains("tay phu") && normalized.contains("he thong"))
                || (normalized.contains("de mo") && normalized.contains("thong tin"))
                || (normalized.contains("de mo") && normalized.contains("trang bi"))
                || (normalized.contains("de mo") && normalized.contains("tien hoa"))
                || (normalized.contains("chuot phai") && normalized.contains("thong tin"))
                || (normalized.contains("chuot trai") && normalized.contains("nang cap"))
                || (normalized.contains("chuot trai") && normalized.contains("tien hoa"))
                || (normalized.contains("khong the") && normalized.contains("thao") && normalized.contains("tay phu"));
    }

    private boolean isBlankLoreLine(String line) {
        return normalizeLoreLine(line).isEmpty();
    }

    private String normalizeLoreLine(String line) {
        String plain = ChatColor.stripColor(line);
        if (plain == null) plain = line == null ? "" : line;
        return java.text.Normalizer.normalize(plain, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
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

    private boolean isManagedOffhandItem(ItemStack item) {
        return isBoundOffhandItem(item) || isInfoOffhandItem(item);
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
            for (StatInstance instance : mmoData.getStatMap().getInstances()) {
                if (instance != null) {
                    instance.removeIf(key -> key.startsWith(MOD_PREFIX));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void tickTimedEquipment() {
        if (!config.getBoolean("enabled", true)) return;

        boolean hasDirtyData = false;
        boolean saveNow = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
            Map<String, ItemStack> playerItems = equipped.get(player.getUniqueId());
            if (playerItems == null || playerItems.isEmpty()) continue;

            boolean playerChanged = false;
            boolean statsChanged = false;
            for (EquipSlot slot : slots.values()) {
                DurationSettings duration = slot.duration();
                if (!duration.enabled()) continue;

                ItemStack item = playerItems.get(slot.id());
                if (item == null || item.getType().isAir()) continue;

                long remaining = remainingDurationSeconds(item);
                if (remaining < 0) {
                    if (!prepareTimedItemForEquip(slot, item)) {
                        playerItems.remove(slot.id());
                        playerChanged = true;
                        statsChanged = true;
                        saveNow = true;
                        continue;
                    }
                    remaining = remainingDurationSeconds(item);
                }
                if (remaining < 0) {
                    continue;
                }
                ensureTotalDurationSeconds(slot, item, remaining);
                if (remaining <= 0) {
                    expireTimedItem(player, slot, item, playerItems);
                    playerChanged = true;
                    statsChanged = true;
                    saveNow = true;
                    continue;
                }

                remaining--;
                if (remaining <= 0) {
                    setRemainingDurationSeconds(slot, item, 0);
                    expireTimedItem(player, slot, item, playerItems);
                    playerChanged = true;
                    statsChanged = true;
                    saveNow = true;
                    continue;
                }

                setRemainingDurationSeconds(slot, item, remaining);
                updateDurationItemLore(slot, item);
                refreshOpenEquipmentSlot(player, slot);
                playerChanged = true;
            }

            if (statsChanged) {
                applyStats(player);
            }
            if (playerChanged) {
                savePlayer(player.getUniqueId());
                hasDirtyData = true;
            }
        }

        if (!hasDirtyData) return;
        durationSaveCounter++;
        int saveInterval = Math.max(1, config.getInt("duration.save-interval-seconds", 30));
        if (saveNow || durationSaveCounter >= saveInterval) {
            durationSaveCounter = 0;
            saveDataFile();
        }
    }

    private void expireTimedItem(Player player, EquipSlot slot, ItemStack item, Map<String, ItemStack> playerItems) {
        playerItems.remove(slot.id());
        setRemainingDurationSeconds(slot, item, 0);
        updateDurationItemLore(slot, item);
        if (!slot.duration().consumeWhenExpired()) {
            giveOrDrop(player, item);
        }
        refreshOpenEquipmentSlot(player, slot);
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7F, 1.4F);
        player.sendMessage(applyEquipmentMessagePlaceholders(message("duration-expired"),
                slot.id(), slotDisplayName(slot), displayName(item, slotDisplayName(slot)), "0s"));
    }

    private boolean prepareTimedItemForEquip(EquipSlot slot, ItemStack item) {
        if (slot == null || !slot.duration().enabled() || item == null || item.getType().isAir()) {
            return true;
        }

        long current = remainingDurationSeconds(item);
        if (current == 0) {
            return false;
        }
        if (current > 0) {
            ensureTotalDurationSeconds(slot, item, current);
            syncMmoDurationSeconds(slot, item, current);
            updateDurationItemLore(slot, item);
            return true;
        }

        long duration = initialDurationSeconds(slot, item);
        if (duration <= 0) {
            return true;
        }
        setTotalDurationSeconds(item, duration);
        setRemainingDurationSeconds(slot, item, duration);
        updateDurationItemLore(slot, item);
        return true;
    }

    private long initialDurationSeconds(EquipSlot slot, ItemStack item) {
        long existingTotal = persistentDurationSeconds(item, durationTotalKey);
        if (existingTotal > 0L) {
            return existingTotal;
        }
        long fromAncient = persistentDurationSeconds(item, ancientDurationSecondsKey);
        if (fromAncient > 0L) {
            return fromAncient;
        }
        long fromMmoItems = mmoDurationSeconds(slot, item);
        return fromMmoItems > 0 ? fromMmoItems : slot.duration().defaultSeconds();
    }

    private long persistentDurationSeconds(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || key == null || !item.hasItemMeta()) return 0L;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0L;
        try {
            Long longValue = meta.getPersistentDataContainer().get(key, PersistentDataType.LONG);
            if (longValue != null && longValue > 0L) return longValue;
            Integer intValue = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            if (intValue != null && intValue > 0) return intValue.longValue();
            Double doubleValue = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
            if (doubleValue != null && doubleValue > 0D) return Math.max(1L, Math.round(doubleValue));
            String stringValue = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            long parsed = parseDurationSeconds(stringValue, 0L);
            return Math.max(0L, parsed);
        } catch (IllegalArgumentException ignored) {
            return 0L;
        }
    }

    private long mmoDurationSeconds(EquipSlot slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        String statId = normalize(slot.duration().statId());
        if (statId.isBlank()) statId = DURATION_STAT_ID;

        try {
            LiveMMOItem liveItem = new LiveMMOItem(item);
            for (ItemStat stat : liveItem.getStats()) {
                if (!normalize(stat.getId()).equals(statId)) continue;
                StatData data = liveItem.getData(stat);
                if (data instanceof DoubleData doubleData) {
                    return Math.max(0L, Math.round(doubleData.getValue()));
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            NBTItem nbt = NBTItem.get(item);
            for (String key : List.of(statId, "MMOITEMS_" + statId)) {
                double numeric = nbt.getDouble(key);
                if (numeric > 0D) return Math.max(0L, Math.round(numeric));
                long parsed = parseDurationSeconds(nbt.getString(key), 0L);
                if (parsed > 0L) return parsed;
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private long remainingDurationSeconds(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return -1L;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(durationRemainingKey, PersistentDataType.LONG)) {
            return -1L;
        }
        Long remaining = meta.getPersistentDataContainer().get(durationRemainingKey, PersistentDataType.LONG);
        return remaining == null ? -1L : Math.max(0L, remaining);
    }

    private void setRemainingDurationSeconds(ItemStack item, long seconds) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(durationRemainingKey, PersistentDataType.LONG, Math.max(0L, seconds));
        item.setItemMeta(meta);
    }

    private void setRemainingDurationSeconds(EquipSlot slot, ItemStack item, long seconds) {
        setRemainingDurationSeconds(item, seconds);
        syncMmoDurationSeconds(slot, item, seconds);
    }

    private void setTotalDurationSeconds(ItemStack item, long seconds) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(durationTotalKey, PersistentDataType.LONG, Math.max(0L, seconds));
        item.setItemMeta(meta);
    }

    private void ensureTotalDurationSeconds(EquipSlot slot, ItemStack item, long remainingSeconds) {
        if (item == null || item.getType().isAir()) return;
        if (persistentDurationSeconds(item, durationTotalKey) > 0L) return;
        long total = initialDurationSeconds(slot, item);
        if (total <= 0L) {
            total = Math.max(0L, remainingSeconds);
        }
        if (total > 0L) {
            setTotalDurationSeconds(item, Math.max(total, remainingSeconds));
        }
    }

    private boolean handleConsumableDanDuoc(PlayerInteractEvent event) {
        if (!config.getBoolean("consumable.enabled", true)) return false;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;
        if (event.getHand() != EquipmentSlot.HAND) return false;

        ItemStack item = event.getItem();
        if (!isConsumableDanDuoc(item)) return false;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!canUseMmoItem(player, item)) {
            return true;
        }

        Map<String, Double> stats = itemStats(item);
        if (stats.isEmpty()) {
            player.sendMessage(message("consumable-no-stats", "&cDan nay chua co chi so MythicLib de kich hoat."));
            return true;
        }

        long durationSeconds = consumableDurationSeconds(item);
        if (durationSeconds <= 0L) {
            player.sendMessage(message("consumable-invalid-duration", "&cDan nay chua co thoi gian hieu luc."));
            return true;
        }

        applyConsumableStats(player, stats, durationSeconds);
        consumeMainHandItem(player);
        player.sendMessage(applyEquipmentMessagePlaceholders(
                message("consumable-used", "&aDa dung dan duoc. Hieu luc: &e%duration%"),
                "dan_duoc", slotDisplayName("dan_duoc", "Đan dược"),
                displayName(item, "Đan dược"), formatDuration(durationSeconds)));
        return true;
    }

    private void syncMmoDurationSeconds(EquipSlot slot, ItemStack item, long seconds) {
        if (slot == null || !slot.duration().enabled() || item == null || item.getType().isAir()) return;
        String statId = normalizeStatId(slot.duration().statId());
        if (statId.isBlank()) statId = DURATION_STAT_ID;
        try {
            NBTItem nbt = NBTItem.get(item);
            String mmoTag = statId.startsWith("MMOITEMS_") ? statId : "MMOITEMS_" + statId;
            nbt.setDouble(mmoTag, Math.max(0L, seconds));
            if (nbt.hasTag(statId)) {
                nbt.setDouble(statId, Math.max(0L, seconds));
            }
            copyItemData(item, nbt.toItem());
        } catch (Throwable ignored) {
        }
    }

    private void applyConsumableStats(Player player, Map<String, Double> stats, long durationSeconds) {
        String effectId = CONSUMABLE_MOD_PREFIX + (++consumableCounter) + "_";
        try {
            MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());
            if (mmoData == null || mmoData.getStatMap() == null) return;
            StatMap statMap = mmoData.getStatMap();
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                if (entry.getValue() == 0.0D) continue;
                StatInstance instance = statMap.getInstance(entry.getKey());
                if (instance == null) continue;
                instance.addModifier(new StatModifier(effectId + entry.getKey(), entry.getKey(), entry.getValue(), ModifierType.FLAT));
            }
            activeConsumables.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(effectId);
            long delayTicks = Math.max(1L, durationSeconds * 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeConsumableStats(player.getUniqueId(), effectId), delayTicks);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not apply consumable dan duoc stats to " + player.getName() + ": " + throwable.getMessage());
        }
    }

    private void removeConsumableStats(Player player) {
        if (player == null) return;
        removeConsumableStats(player.getUniqueId(), null);
    }

    private void removeConsumableStats(UUID uuid, String effectId) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            if (effectId == null) {
                activeConsumables.remove(uuid);
            } else {
                Set<String> ids = activeConsumables.get(uuid);
                if (ids != null) ids.remove(effectId);
            }
            return;
        }

        try {
            MMOPlayerData mmoData = MMOPlayerData.get(uuid);
            if (mmoData == null || mmoData.getStatMap() == null) return;
            String prefix = effectId == null ? CONSUMABLE_MOD_PREFIX : effectId;
            for (StatInstance instance : mmoData.getStatMap().getInstances()) {
                if (instance != null) {
                    instance.removeIf(key -> key.startsWith(prefix));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (effectId == null) {
                activeConsumables.remove(uuid);
            } else {
                Set<String> ids = activeConsumables.get(uuid);
                if (ids != null) {
                    ids.remove(effectId);
                    if (ids.isEmpty()) activeConsumables.remove(uuid);
                }
            }
        }
    }

    private void copyItemData(ItemStack target, ItemStack source) {
        if (target == null || source == null || source.getType().isAir()) return;
        int amount = target.getAmount();
        target.setType(source.getType());
        target.setItemMeta(source.getItemMeta());
        target.setAmount(amount);
    }

    private Map<String, Double> totalStats(UUID uuid) {
        Map<String, Double> total = new LinkedHashMap<>();
        Map<String, ItemStack> playerItems = equipped.getOrDefault(uuid, Map.of());
        for (EquipSlot slot : slots.values()) {
            if (!playerItems.containsKey(slot.id())) continue;
            itemStats(playerItems.get(slot.id())).forEach((stat, value) -> total.merge(stat, value, Double::sum));
            if (slot.useConfigStats()) {
                slot.stats().forEach((stat, value) -> {
                    if (!isEquipmentInternalStat(stat)) {
                        total.merge(stat, value, Double::sum);
                    }
                });
            }
        }
        return total;
    }

    private Map<String, Double> itemStats(ItemStack item) {
        Map<String, Double> stats = new LinkedHashMap<>();
        if (item == null || item.getType().isAir()) return stats;

        try {
            LiveMMOItem liveItem = new LiveMMOItem(item);
            for (ItemStat stat : liveItem.getStats()) {
                if (!(stat instanceof DoubleStat)) continue;
                String statId = normalize(stat.getId());
                if (isEquipmentInternalStat(statId)) continue;
                StatData data = liveItem.getData(stat);
                if (!(data instanceof DoubleData doubleData)) continue;
                double value = doubleData.getValue();
                if (value != 0D) {
                    stats.merge(statId, value, Double::sum);
                }
            }
            return stats;
        } catch (Throwable ignored) {
        }

        try {
            NBTItem nbt = NBTItem.get(item);
            for (String tag : nbt.getTags()) {
                if (tag == null || !tag.startsWith("MMOITEMS_")) continue;
                if (tag.equals("MMOITEMS_ITEM_TYPE")
                        || tag.equals("MMOITEMS_ITEM_ID")
                        || tag.equals("MMOITEMS_TYPE")
                        || tag.equals("MMOITEMS_ID")) {
                    continue;
                }
                String statId = normalize(tag.substring("MMOITEMS_".length()));
                if (isEquipmentInternalStat(statId)) continue;
                double value = nbt.getDouble(tag);
                if (value != 0D) {
                    stats.merge(statId, value, Double::sum);
                }
            }
        } catch (Throwable ignored) {
        }
        return stats;
    }

    private boolean isConsumableDanDuoc(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String type = mmoType(item);
        if (type == null) return false;
        List<String> acceptedTypes = config.getStringList("consumable.accepted-types");
        if (acceptedTypes.isEmpty()) {
            acceptedTypes = List.of("DAN_DUOC_CONSUMABLE");
        }
        boolean accepted = acceptedTypes.stream().map(this::normalize).anyMatch(type::equals);
        if (accepted) return true;

        String flagStat = normalizeStatId(config.getString("consumable.flag-stat", ""));
        if (flagStat.isBlank()) return false;
        return itemStatValue(item, flagStat) > 0.0D;
    }

    private long consumableDurationSeconds(ItemStack item) {
        String durationStat = normalizeStatId(config.getString("consumable.duration-stat", DURATION_STAT_ID));
        if (durationStat.isBlank()) durationStat = DURATION_STAT_ID;

        double statValue = itemStatValue(item, durationStat);
        if (statValue > 0.0D) {
            return Math.max(1L, Math.round(statValue));
        }

        try {
            NBTItem nbt = NBTItem.get(item);
            for (String key : List.of(durationStat, "MMOITEMS_" + durationStat)) {
                double numeric = nbt.getDouble(key);
                if (numeric > 0.0D) return Math.max(1L, Math.round(numeric));
                long parsed = parseDurationSeconds(nbt.getString(key), 0L);
                if (parsed > 0L) return parsed;
            }
        } catch (Throwable ignored) {
        }

        return parseDurationSeconds(config.get("consumable.default-duration", "5m"), 0L);
    }

    private void consumeMainHandItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;
        int consumeAmount = Math.max(1, config.getInt("consumable.consume-amount", 1));
        int remaining = item.getAmount() - consumeAmount;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            item.setAmount(remaining);
            player.getInventory().setItemInMainHand(item);
        }
        player.updateInventory();
    }

    private double itemStatValue(ItemStack item, String statId) {
        if (item == null || item.getType().isAir()) return 0.0D;
        String targetStat = normalizeStatId(statId);
        if (targetStat.isBlank()) return 0.0D;

        try {
            LiveMMOItem liveItem = new LiveMMOItem(item);
            for (ItemStat stat : liveItem.getStats()) {
                if (!normalizeStatId(stat.getId()).equals(targetStat)) continue;
                StatData data = liveItem.getData(stat);
                if (data instanceof DoubleData doubleData) {
                    return doubleData.getValue();
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            NBTItem nbt = NBTItem.get(item);
            for (String key : List.of(targetStat, "MMOITEMS_" + targetStat)) {
                double numeric = nbt.getDouble(key);
                if (numeric != 0.0D) return numeric;
                String raw = nbt.getString(key);
                if (raw != null && !raw.isBlank()) {
                    try {
                        return Double.parseDouble(raw.trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0.0D;
    }

    private boolean isEquipmentInternalStat(String statId) {
        return isEquipmentDurationStat(statId) || isEquipmentSystemStat(statId);
    }

    private boolean isEquipmentSystemStat(String statId) {
        return SYSTEM_STAT_IDS.contains(normalizeStatId(statId));
    }

    private boolean isEquipmentDurationStat(String statId) {
        String normalized = normalizeStatId(statId);
        if (normalized.equals(DURATION_STAT_ID)) return true;
        for (EquipSlot slot : slots.values()) {
            if (slot.duration().enabled() && normalized.equals(normalizeStatId(slot.duration().statId()))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeStatId(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("MMOITEMS_") ? normalized.substring("MMOITEMS_".length()) : normalized;
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
            slots.put(id, new EquipSlot(id, config.getInt(path + ".slot"), acceptedTypes,
                    config.getBoolean(path + ".use-config-stats", false), stats, loadDurationSettings(path + ".duration")));
        }
    }

    private DurationSettings loadDurationSettings(String path) {
        return new DurationSettings(
                config.getBoolean(path + ".enabled", false),
                normalize(config.getString(path + ".stat", DURATION_STAT_ID)),
                parseDurationSeconds(config.get(path + ".default"), 0L),
                config.getBoolean(path + ".write-to-item-lore", true),
                config.getBoolean(path + ".consume-when-expired", true),
                config.getStringList(path + ".lore")
        );
    }

    private void loadPlayer(UUID uuid) {
        // Fast path: a fully-restored player is cached and never re-scans the YAML. Players whose
        // equipped gear could not be regenerated yet (MMOItems PlayerData not ready) are kept in
        // pendingSlotRegen and fall through to a cheap, slot-scoped retry below.
        if (equipped.containsKey(uuid) && !pendingSlotRegen.contains(uuid)) return;

        Player online = Bukkit.getPlayer(uuid);
        boolean firstLoad = !equipped.containsKey(uuid);
        Map<String, ItemStack> playerItems = equipped.computeIfAbsent(uuid, ignored -> new HashMap<>());
        Map<String, SlotIdentity> identityCache = slotIdentityCache.get(uuid);
        boolean stillPending = false;
        boolean restoredAny = false;

        for (String slotId : slots.keySet()) {
            if (playerItems.containsKey(slotId)) {
                // Already restored on an earlier pass.
                continue;
            }

            EquipSlot slot = slots.get(slotId);

            // --- Priority 1: in-memory identity cache (same-session relog, survives equipped eviction) ---
            // This path never touches YAML or MMOItems PlayerData, so it always succeeds instantly.
            SlotIdentity cached = identityCache != null ? identityCache.get(slotId) : null;
            if (cached != null && online != null) {
                ItemStack fromCache = createMmoItemForSlot(online, cached.type(), cached.id(), slotId);
                if (fromCache != null && !fromCache.getType().isAir()) {
                    fromCache.setAmount(1);
                    if (slot != null && slot.duration().enabled() && cached.hasDuration()) {
                        long total = cached.totalSeconds() > 0 ? cached.totalSeconds() : initialDurationSeconds(slot, fromCache);
                        if (total > 0) setTotalDurationSeconds(fromCache, total);
                        setRemainingDurationSeconds(slot, fromCache, Math.max(0L, cached.remainingSeconds()));
                        updateDurationItemLore(slot, fromCache);
                    }
                    if (slot == null || prepareTimedItemForEquip(slot, fromCache)) {
                        playerItems.put(slotId, fromCache);
                        restoredAny = true;
                        plugin.getLogger().info("[EquipLoad] " + uuid + " slot=" + slotId + " LOADED from cache (" + cached.type() + ":" + cached.id() + ")");
                        continue;
                    }
                }
                // Cache hit but MMOItems not ready yet — retry on a later pass.
                stillPending = true;
                continue;
            }

            // --- Priority 2: raw ItemStack from YAML (first load / server restart) ---
            ItemStack item = firstLoad ? data.getItemStack(uuid + "." + slotId) : null;

            // The raw serialized ItemStack can lose its MMOItems identity (custom NBT) across a
            // server restart. Fall back to regenerating from stored mmo-meta type+id.
            if (item == null || item.getType().isAir() || mmoType(item) == null) {
                if (online != null) {
                    ItemStack regenerated = regenerateSlotItem(online, uuid, slotId, slot);
                    if (regenerated != null) {
                        item = regenerated;
                        restoredAny = true;
                    } else if (hasStoredSlotIdentity(uuid, slotId)) {
                        // Identity exists but MMOItems could not build it yet; retry on a later pass.
                        stillPending = true;
                        continue;
                    }
                } else if (hasStoredSlotIdentity(uuid, slotId)) {
                    stillPending = true;
                    continue;
                }
            }

            if (item != null && item.getType() != Material.AIR) {
                if (slot == null || prepareTimedItemForEquip(slot, item)) {
                    playerItems.put(slotId, item);
                    restoredAny = true;
                    plugin.getLogger().info("[EquipLoad] " + uuid + " slot=" + slotId + " LOADED from YAML");
                } else {
                    plugin.getLogger().info("[EquipLoad] " + uuid + " slot=" + slotId + " REJECTED by prepareTimedItem");
                }
            }
        }

        if (stillPending) {
            pendingSlotRegen.add(uuid);
        } else {
            pendingSlotRegen.remove(uuid);
        }
        // Re-apply stats for any slot restored on a deferred pass so bonuses are not missed.
        if (restoredAny && !firstLoad && online != null && online.isOnline()) {
            applyStats(online);
            refreshOpenEquipment(online);
        }
    }

    private void savePlayer(UUID uuid) {
        Map<String, ItemStack> playerItems = equipped.get(uuid);
        if (playerItems == null) return;
        for (String slotId : slots.keySet()) {
            ItemStack item = playerItems.get(slotId);
            data.set(uuid + "." + slotId, item);
            writeSlotMeta(uuid, slotId, item);
        }
    }

    /**
     * Persists the MMOItems identity (type + id) and remaining/total duration of an equipped item
     * alongside the raw serialized ItemStack. These plain values always survive a YAML round-trip,
     * so {@link #regenerateSlotItem} can rebuild the item if the raw ItemStack loses its MMOItems
     * NBT across a restart.
     * <p>
     * Also updates the in-memory {@link #slotIdentityCache} so that a player who relogs within
     * the same JVM session can have their slots restored immediately without a YAML read.
     */
    private void writeSlotMeta(UUID uuid, String slotId, ItemStack item) {
        String path = uuid + ".mmo-meta." + slotId;
        if (item == null || item.getType().isAir()) {
            data.set(path, null);
            // Clear from in-memory cache too.
            Map<String, SlotIdentity> playerCache = slotIdentityCache.get(uuid);
            if (playerCache != null) playerCache.remove(slotId);
            return;
        }
        String type = mmoType(item);
        String id = mmoId(item);
        if (type == null || id == null || type.isBlank() || id.isBlank()) {
            // Not an MMOItems-backed item; there is nothing to regenerate from later.
            data.set(path, null);
            Map<String, SlotIdentity> playerCache = slotIdentityCache.get(uuid);
            if (playerCache != null) playerCache.remove(slotId);
            return;
        }
        data.set(path + ".type", type);
        data.set(path + ".id", id);
        long remaining = remainingDurationSeconds(item);
        data.set(path + ".remaining-seconds", remaining >= 0L ? remaining : null);
        long total = persistentDurationSeconds(item, durationTotalKey);
        data.set(path + ".total-seconds", total > 0L ? total : null);
        // Update in-memory identity cache — this is the authoritative fast path for same-session
        // relogs so we never depend on YAML ItemStack deserialization or MMOItems timing.
        slotIdentityCache.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(slotId, new SlotIdentity(type, id, remaining >= 0L ? remaining : -1L, total > 0L ? total : 0L));
    }

    /**
     * Rebuilds an equipped item from its stored MMOItems type + id when the raw serialized ItemStack
     * could not be restored. Returns {@code null} when no identity was stored (legacy data) or the
     * MMOItems template no longer exists.
     */
    /**
     * Returns {@code true} when a durable MMOItems identity (type + id) is stored for the slot, so a
     * failed regeneration is worth retrying once MMOItems has finished loading the player's data.
     */
    private boolean hasStoredSlotIdentity(UUID uuid, String slotId) {
        String path = uuid + ".mmo-meta." + slotId;
        String type = data.getString(path + ".type");
        String id = data.getString(path + ".id");
        return type != null && !type.isBlank() && id != null && !id.isBlank();
    }

    private void refreshOpenEquipment(Player player) {
        if (player == null) return;
        for (EquipSlot slot : slots.values()) {
            refreshOpenEquipmentSlot(player, slot);
        }
    }

    private ItemStack regenerateSlotItem(Player player, UUID uuid, String slotId, EquipSlot slot) {
        String path = uuid + ".mmo-meta." + slotId;
        String type = data.getString(path + ".type");
        String id = data.getString(path + ".id");
        if (type == null || id == null || type.isBlank() || id.isBlank()) {
            return null;
        }
        ItemStack regenerated = createMmoItemForSlot(player, type, id, slotId);
        if (regenerated == null || regenerated.getType().isAir()) {
            return null;
        }
        regenerated.setAmount(1);
        long remaining = -1L;
        long total = 0L;
        if (slot != null && slot.duration().enabled() && data.contains(path + ".remaining-seconds")) {
            total = data.getLong(path + ".total-seconds", 0L);
            if (total <= 0L) {
                total = initialDurationSeconds(slot, regenerated);
            }
            if (total > 0L) {
                setTotalDurationSeconds(regenerated, total);
            }
            remaining = Math.max(0L, data.getLong(path + ".remaining-seconds"));
            setRemainingDurationSeconds(slot, regenerated, remaining);
            updateDurationItemLore(slot, regenerated);
        }
        // Populate the in-memory identity cache so subsequent same-session reloads use the fast path.
        slotIdentityCache.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(slotId, new SlotIdentity(type, id, remaining, total));
        return regenerated;
    }

    private EquipSlot slotAt(int guiSlot) {
        for (EquipSlot slot : slots.values()) {
            if (slot.guiSlot() == guiSlot) return slot;
        }
        return null;
    }

    private String resolveSlotId(String slotId) {
        String trimmed = slotId.trim();
        if (slots.containsKey(trimmed)) {
            return trimmed;
        }
        for (String configuredSlotId : slots.keySet()) {
            if (configuredSlotId.equalsIgnoreCase(trimmed)) {
                return configuredSlotId;
            }
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
                    parseMoneyAmount(config.get(path + ".cost"), 0D),
                    config.getInt(path + ".required-level", 0),
                    config.getInt(path + ".required-realm", 0),
                    parseSubRealm(config.getString(path + ".required-sub-realm", "")),
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

    private boolean canUseMmoItem(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) return false;
        try {
            NBTItem nbt = NBTItem.get(item);
            boolean mmoItemsAllowed = PlayerData.get(player).getRPG().canUse(nbt, true);
            boolean realmAllowed = MMOItemsRealmRequirementHook.canUse(realmManager, player, item, mmoItemsAllowed);
            return allEquipmentRequirementsPass(mmoItemsAllowed, realmAllowed);
        } catch (Throwable throwable) {
            player.sendMessage(message("requirement-failed", "&cBan chua du dieu kien de trang bi item nay."));
            return false;
        }
    }

    static boolean allEquipmentRequirementsPass(boolean mmoItemsAllowed, boolean realmAllowed) {
        return mmoItemsAllowed && realmAllowed;
    }

    static int canUseLoreRequirement(String line, String label) {
        return MMOItemsRealmRequirementHook.canUseLoreRequirement(line, label);
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
        Material material = Material.matchMaterial(config.getString("gui.info-item.material", "NETHER_STAR"));
        ItemStack item = named(
                material,
                config.getString("gui.info-item.name", "&6Thông Tin Người Chơi"),
                replaceInfo(config.getStringList("gui.info-item.lore"), player),
                customModelData("gui.info-item"),
                itemModel("gui.info-item")
        );
        if (material == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private List<String> replaceInfo(List<String> lines, Player player) {
        List<String> result = new ArrayList<>();
        Map<String, Double> equipmentStats = totalStats(player.getUniqueId());
        Realm realm = realmManager.getPlayerCurrentRealm(player.getUniqueId());
        String realmText = realmManager.getPlayerRealmDisplay(player.getUniqueId());
        String tuVi = String.valueOf((long) TuTien.getApi().getTuVi(player.getUniqueId()));
        for (String line : lines) {
            String formatted = line
                    .replace("%player%", player.getName())
                    .replace("%realm%", realm == null ? realmText : realmText)
                    .replace("%tuvi%", tuVi);
            Matcher matcher = STAT_PLACEHOLDER.matcher(formatted);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String stat = matcher.group(1);
                double value = mythicStatTotal(player, stat);
                if (Double.isNaN(value)) {
                    value = equipmentStats.getOrDefault(stat, 0D);
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(formatNumber(value)));
            }
            matcher.appendTail(buffer);
            result.add(color(buffer.toString()));
        }
        return result;
    }

    private double mythicStatTotal(Player player, String stat) {
        try {
            MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());
            if (mmoData == null || mmoData.getStatMap() == null) return Double.NaN;
            StatInstance instance = mmoData.getStatMap().getInstance(stat);
            if (instance == null) return Double.NaN;
            Object total = instance.getClass().getMethod("getTotal").invoke(instance);
            return total instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (Throwable ignored) {
            return Double.NaN;
        }
    }

    private ItemStack emptySlotItem(EquipSlot slot) {
        String path = "slots." + slot.id() + ".empty";
        return named(
                Material.matchMaterial(config.getString(path + ".material", "GRAY_STAINED_GLASS_PANE")),
                config.getString(path + ".name", "&7" + slot.id()),
                config.getStringList(path + ".lore"),
                customModelData(path),
                itemModel(path)
        );
    }

    private ItemStack displayEquippedItem(EquipSlot slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return emptySlotItem(slot);
        ItemStack display = item.clone();
        appendDurationLore(slot, display);
        return display;
    }

    private void appendDurationLore(EquipSlot slot, ItemStack item) {
        if (slot == null || !slot.duration().enabled() || item == null || item.getType().isAir()) return;
        long remaining = remainingDurationSeconds(item);
        if (remaining < 0L) {
            remaining = initialDurationSeconds(slot, item);
        }
        if (remaining <= 0L) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        lore = stripDurationLore(slot, lore);
        long duration = initialDurationSeconds(slot, item);
        for (String line : slot.duration().lore()) {
            lore.add(color(replaceDurationPlaceholders(line, remaining, duration)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void updateDurationItemLore(EquipSlot slot, ItemStack item) {
        if (slot == null || !slot.duration().enabled() || !slot.duration().writeToItemLore()) return;
        appendDurationLore(slot, item);
    }

    private List<String> stripDurationLore(EquipSlot slot, List<String> lore) {
        List<String> stripped = new ArrayList<>();
        for (String line : lore) {
            if (isDurationLoreLine(slot, line)) {
                if (!stripped.isEmpty() && isBlankLoreLine(stripped.get(stripped.size() - 1))) {
                    stripped.remove(stripped.size() - 1);
                }
                continue;
            }
            stripped.add(line);
        }
        return stripped;
    }

    private boolean isDurationLoreLine(EquipSlot slot, String line) {
        String normalizedLine = normalizeLoreLine(line);
        if (normalizedLine.isBlank()) return false;
        if (normalizedLine.contains("hieu luc")) {
            return true;
        }
        for (String template : slot.duration().lore()) {
            if (matchesDurationTemplate(normalizedLine, template)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDurationTemplate(String normalizedLine, String template) {
        String normalizedTemplate = normalizeLoreLine(template);
        if (normalizedTemplate.isBlank()) return false;
        String marker = "{duration_value}";
        String withMarker = normalizedTemplate
                .replace("%remaining%", marker)
                .replace("%duration%", marker)
                .replace("%total%", marker);
        if (!withMarker.contains(marker)) {
            return normalizedLine.equals(withMarker);
        }

        int cursor = 0;
        for (String part : withMarker.split(Pattern.quote(marker), -1)) {
            if (part.isBlank()) continue;
            int found = normalizedLine.indexOf(part, cursor);
            if (found < 0) return false;
            cursor = found + part.length();
        }
        return true;
    }

    private String replaceDurationPlaceholders(String line, long remainingSeconds, long totalSeconds) {
        return line
                .replace("%remaining%", formatDuration(remainingSeconds))
                .replace("%duration%", formatDuration(totalSeconds))
                .replace("%total%", formatDuration(totalSeconds));
    }

    private void refreshOpenEquipmentSlot(Player player, EquipSlot slot) {
        if (player == null || slot == null) return;
        if (!player.getOpenInventory().getTitle().equals(color(config.getString("gui.title", "&8Trang Bi Tu Tien")))) {
            return;
        }
        Map<String, ItemStack> playerItems = equipped.getOrDefault(player.getUniqueId(), Map.of());
        ItemStack item = playerItems.get(slot.id());
        player.getOpenInventory().getTopInventory().setItem(slot.guiSlot(), item == null ? emptySlotItem(slot) : displayEquippedItem(slot, item));
        player.updateInventory();
    }

    private ItemStack confirmItem(Player player, ItemStack source, UpgradeRule rule) {
        String fromName = displayName(source, rule.fromType() + ":" + rule.fromId());
        String toName = displayName(createMmoItem(player, rule.toType(), rule.toId()), rule.toType() + ":" + rule.toId());
        ItemStack item = named(
                Material.matchMaterial(config.getString("gui.upgrade-confirm.material", "EMERALD")),
                config.getString("gui.upgrade-confirm.name", "&aTiến Hoá"),
                config.getStringList("gui.upgrade-confirm.lore").stream()
                        .map(line -> replaceUpgradePlaceholders(line, rule, fromName, toName))
                        .toList(),
                customModelData("gui.upgrade-confirm"),
                itemModel("gui.upgrade-confirm")
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "upgrade_confirm");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack previewItem(Player player, UpgradeRule rule) {
        ItemStack result = createMmoItem(player, rule.toType(), rule.toId());
        String toName = displayName(result, rule.toType() + ":" + rule.toId());
        if (result != null && !result.getType().isAir()) {
            ItemStack preview = result.clone();
            preview.setAmount(1);
            appendPreviewLore(preview, rule, toName);
            return preview;
        }
        if (config != null) {
            return named(
                    Material.matchMaterial(config.getString("gui.upgrade-preview.material", "EMERALD")),
                    replaceUpgradePlaceholders(config.getString("gui.upgrade-preview.name", "&e%to_name%"), rule, "", toName),
                    config.getStringList("gui.upgrade-preview.lore").stream()
                            .map(line -> replaceUpgradePlaceholders(line, rule, "", toName))
                            .toList(),
                    customModelData("gui.upgrade-preview"),
                    itemModel("gui.upgrade-preview")
            );
        }
        return named(Material.EMERALD, "&e" + rule.toType() + ":" + rule.toId(), List.of("&7Item nhận qua command cấu hình."));
    }

    private void appendPreviewLore(ItemStack item, UpgradeRule rule, String toName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> appendLines = config.getStringList("gui.upgrade-preview.append-lore");
        if (appendLines.isEmpty()) {
            appendLines = List.of(
                    "",
                    "&7Linh thach: &e%cost%",
                    "&7Cap yeu cau: &a%required_level%",
                    "&7Canh gioi: &b%required_realm%"
            );
        }

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        for (String line : appendLines) {
            lore.add(color(replaceUpgradePlaceholders(line, rule, "", toName)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private String replaceUpgradePlaceholders(String line, UpgradeRule rule, String fromName, String toName) {
        return line
                .replace("%from_type%", rule.fromType())
                .replace("%from_id%", rule.fromId())
                .replace("%from_name%", fromName)
                .replace("%to_type%", rule.toType())
                .replace("%to_id%", rule.toId())
                .replace("%to_name%", toName)
                .replace("%cost%", formatMoney(rule.cost()))
                .replace("%required_level%", rule.requiredLevel() <= 0 ? "Không yêu cầu" : String.valueOf(rule.requiredLevel()))
                .replace("%required_realm%", rule.requiredRealm() <= 0 ? "Không yêu cầu" : formatRealmRequirement(rule))
                .replace("%required_sub_realm%", rule.requiredSubRealm() == null ? "Không yêu cầu" : rule.requiredSubRealm().getDisplayName());
    }

    private String displayName(ItemStack item, String fallback) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return fallback;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return fallback;
        return meta.getDisplayName();
    }

    private void sendEquipmentMessage(Player player, String action, EquipSlot slot, ItemStack item) {
        String fallback = "equipped".equals(action)
                ? "&aDa trang bi &f%slot_display%&a."
                : "&eDa thao &f%slot_display%&e.";
        String template = message(action + "-slot." + slot.id(), message(action, fallback));
        long durationSeconds = equipmentMessageDurationSeconds(slot, item);
        String duration = durationSeconds > 0L ? formatDuration(durationSeconds) : "vĩnh viễn";
        player.sendMessage(applyEquipmentMessagePlaceholders(
                template,
                slot.id(),
                slotDisplayName(slot),
                displayName(item, slotDisplayName(slot)),
                duration
        ));
    }

    private long equipmentMessageDurationSeconds(EquipSlot slot, ItemStack item) {
        if (slot == null || !slot.duration().enabled()) {
            return 0L;
        }

        long remaining = remainingDurationSeconds(item);
        if (remaining > 0L) {
            return remaining;
        }

        long total = persistentDurationSeconds(item, durationTotalKey);
        if (total > 0L) {
            return total;
        }

        return initialDurationSeconds(slot, item);
    }

    private String slotDisplayName(EquipSlot slot) {
        if (slot == null) {
            return "";
        }
        return slotDisplayName(slot.id(), slot.id());
    }

    private String slotDisplayName(String slotId, String fallback) {
        return color(config.getString("slots." + slotId + ".display-name", fallback));
    }

    static String applyEquipmentMessagePlaceholders(String message, String slotId, String slotDisplayName,
                                                   String itemName, String duration) {
        String safeMessage = message == null ? "" : message;
        return safeMessage
                .replace("%slot%", slotId == null ? "" : slotId)
                .replace("%slot_display%", slotDisplayName == null ? "" : slotDisplayName)
                .replace("%item%", itemName == null ? "" : itemName)
                .replace("%duration%", duration == null ? "" : duration);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = named(
                Material.matchMaterial(config.getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE")),
                config.getString("gui.filler.name", " "),
                List.of(),
                customModelData("gui.filler"),
                itemModel("gui.filler")
        );
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private List<String> upgradeFailures(Player player, UpgradeRule rule) {
        List<String> failures = new ArrayList<>();
        if (rule.cost() > 0 && !hasMoney(player, rule.cost())) {
            failures.add(message("failure-money").replace("%cost%", formatMoney(rule.cost())));
        }

        int currentLevel = getTuTienLevel(player);
        if (rule.requiredLevel() > 0) {
            if (currentLevel < 0) {
                failures.add(message("failure-level-unavailable"));
            } else if (currentLevel < rule.requiredLevel()) {
                failures.add(message("failure-level")
                        .replace("%current%", String.valueOf(currentLevel))
                        .replace("%required%", String.valueOf(rule.requiredLevel())));
            }
        }

        if (rule.requiredRealm() > 0) {
            PlayerRealm playerRealm = realmManager.getPlayerRealm(player.getUniqueId());
            int currentRealm = playerRealm == null ? -1 : playerRealm.getRealmId();
            SubRealm currentSubRealm = playerRealm == null ? null : playerRealm.getSubRealm();
            boolean realmOk = currentRealm > rule.requiredRealm()
                    || (currentRealm == rule.requiredRealm() && (rule.requiredSubRealm() == null
                    || (currentSubRealm != null && currentSubRealm.getOrder() >= rule.requiredSubRealm().getOrder())));
            if (!realmOk) {
                failures.add(message("failure-realm")
                        .replace("%current%", formatPlayerRealm(player))
                        .replace("%required%", formatRealmRequirement(rule)));
            }
        }
        return failures;
    }

    private boolean hasMoney(Player player, double amount) {
        Object economy = vaultEconomy();
        if (economy == null) return amount <= 0;
        try {
            Object result = economy.getClass().getMethod("has", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean withdrawMoney(Player player, double amount) {
        if (amount <= 0) return true;
        Object economy = vaultEconomy();
        if (economy == null) return false;
        try {
            Object response = economy.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            Object success = response.getClass().getMethod("transactionSuccess").invoke(response);
            return success instanceof Boolean value && value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Object vaultEconomy() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null) return null;
            return registration.getClass().getMethod("getProvider").invoke(registration);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private int getTuTienLevel(Player player) {
        try {
            Class<?> apiClass = Class.forName("com.turtle.tutienlevel.api.TuTienLevelAPI");
            Object registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) return -1;
            Object api = registration.getClass().getMethod("getProvider").invoke(registration);
            Object level = api.getClass().getMethod("getLevel", UUID.class).invoke(api, player.getUniqueId());
            return level instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private String formatPlayerRealm(Player player) {
        PlayerRealm playerRealm = realmManager.getPlayerRealm(player.getUniqueId());
        if (playerRealm == null) return "N/A";
        return formatRealm(playerRealm.getRealmId(), playerRealm.getSubRealm());
    }

    private String formatRealmRequirement(UpgradeRule rule) {
        return formatRealm(rule.requiredRealm(), rule.requiredSubRealm());
    }

    private String formatRealm(int realmId, SubRealm subRealm) {
        if (realmId <= 0) return "Không yêu cầu";
        Realm realm = realmManager.getRealm(realmId);
        String display = realm == null ? String.valueOf(realmId) : realm.getDisplayNameTranslated();
        if (subRealm != null) {
            display += " " + subRealm.getDisplayName();
        }
        return realmId + " - " + display;
    }

    private SubRealm parseSubRealm(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank() || normalized.equals("0") || normalized.equals("NONE")) return null;
        try {
            return SubRealm.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        return named(material, name, lore, -1, null);
    }

    private ItemStack named(Material material, String name, List<String> lore, int customModelData) {
        return named(material, name, lore, customModelData, null);
    }

    private ItemStack named(Material material, String name, List<String> lore, int customModelData, String itemModel) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(this::color).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            applyItemModel(meta, itemModel);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Read a custom-model-data value from config. Returns -1 when not set (or <= 0),
     * meaning "do not apply custom model data".
     */
    private int customModelData(String path) {
        return config.getInt(path + ".custom-model-data", -1);
    }

    /**
     * Read an item-model value from config. Returns null/blank when not set,
     * meaning "do not apply an item model".
     */
    private String itemModel(String path) {
        return config.getString(path + ".item-model", null);
    }

    /**
     * Apply the {@code item_model} component (Minecraft 1.21.2+) to the meta via reflection,
     * so the plugin still compiles against the 1.21.1 API and degrades gracefully on older servers.
     * The value is a namespaced key such as {@code mypack:trang_bi/info}.
     */
    private void applyItemModel(ItemMeta meta, String itemModel) {
        if (meta == null || itemModel == null || itemModel.isBlank()) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(itemModel.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            plugin.getLogger().warning("Invalid item-model key in equipment-menu.yml: " + itemModel);
            return;
        }
        try {
            ItemMeta.class.getMethod("setItemModel", NamespacedKey.class).invoke(meta, key);
        } catch (NoSuchMethodException ignored) {
            // Server/API older than 1.21.2 — item_model component unavailable, skip silently.
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not apply item-model " + itemModel + ": " + exception.getMessage());
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private String message(String key) {
        return message(key, key);
    }

    private String message(String key, String fallback) {
        return color(config.getString("messages." + key, fallback));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private double parseMoneyAmount(Object value, double fallback) {
        if (value instanceof Number number) {
            return Math.max(0D, number.doubleValue());
        }
        if (value == null) {
            return fallback;
        }

        String raw = String.valueOf(value)
                .trim()
                .replace("_", "")
                .replace(" ", "")
                .replace(",", "");
        if (raw.isBlank()) {
            return fallback;
        }

        double multiplier = 1D;
        char suffix = Character.toLowerCase(raw.charAt(raw.length() - 1));
        if (suffix == 'k' || suffix == 'm' || suffix == 'b' || suffix == 't') {
            raw = raw.substring(0, raw.length() - 1);
            multiplier = switch (suffix) {
                case 'k' -> 1_000D;
                case 'm' -> 1_000_000D;
                case 'b' -> 1_000_000_000D;
                case 't' -> 1_000_000_000_000D;
                default -> 1D;
            };
        }

        try {
            return Math.max(0D, Double.parseDouble(raw) * multiplier);
        } catch (NumberFormatException ignored) {
            plugin.getLogger().warning("Invalid offhand upgrade money cost: " + value);
            return fallback;
        }
    }

    private long parseDurationSeconds(Object value, long fallback) {
        if (value instanceof Number number) {
            return Math.max(0L, Math.round(number.doubleValue()));
        }
        if (value == null) {
            return fallback;
        }

        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT).replace("_", "").replace(",", ".");
        if (raw.isBlank()) {
            return fallback;
        }

        try {
            return Math.max(0L, Math.round(Double.parseDouble(raw)));
        } catch (NumberFormatException ignored) {
        }

        Matcher matcher = DURATION_PART.matcher(raw);
        double seconds = 0D;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            double amount = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2) == null ? "s" : matcher.group(2).toLowerCase(Locale.ROOT);
            seconds += amount * switch (unit) {
                case "d", "day", "days" -> 86_400D;
                case "h", "hour", "hours" -> 3_600D;
                case "m", "min", "mins", "minute", "minutes" -> 60D;
                default -> 1D;
            };
        }
        return matched ? Math.max(0L, Math.round(seconds)) : fallback;
    }

    private String formatDuration(long seconds) {
        long remaining = Math.max(0L, seconds);
        long days = remaining / 86_400L;
        remaining %= 86_400L;
        long hours = remaining / 3_600L;
        remaining %= 3_600L;
        long minutes = remaining / 60L;
        long secs = remaining % 60L;

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (secs > 0 || parts.isEmpty()) parts.add(secs + "s");
        return String.join(" ", parts);
    }

    private String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.2f", value);
    }

    private String formatMoney(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000_000D) {
            return trimDecimal(value / 1_000_000_000_000D) + "T";
        }
        if (absolute >= 1_000_000_000D) {
            return trimDecimal(value / 1_000_000_000D) + "B";
        }
        if (absolute >= 1_000_000D) {
            return trimDecimal(value / 1_000_000D) + "M";
        }
        if (absolute >= 1_000D) {
            return trimDecimal(value / 1_000D) + "K";
        }
        return formatNumber(value);
    }

    private String trimDecimal(double value) {
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static List<String> executableUpgradeCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        List<String> output = new ArrayList<>();
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            output.add(command.trim());
        }
        return output;
    }

    private record EquipSlot(String id, int guiSlot, Set<String> acceptedTypes, boolean useConfigStats, Map<String, Double> stats, DurationSettings duration) {
        boolean accepts(String type) {
            return type != null && acceptedTypes.contains(type);
        }
    }

    public record EquippedMmoItem(String type, String id) {
    }

    private record DurationSettings(
            boolean enabled,
            String statId,
            long defaultSeconds,
            boolean writeToItemLore,
            boolean consumeWhenExpired,
            List<String> lore) {
    }

    private record UpgradeRule(
            String fromType,
            String fromId,
            String toType,
            String toId,
            double cost,
            int requiredLevel,
            int requiredRealm,
            SubRealm requiredSubRealm,
            boolean takeSource,
            List<String> commands) {
    }

    private record BoundOffhandState(String type, String id) {
    }

    /**
     * Lightweight snapshot of what is (or was) equipped in a slot. Stored in
     * {@link #slotIdentityCache} and survives the {@link #equipped} eviction on quit so players
     * who relog within the same JVM session can have their slots rebuilt instantly — no YAML
     * round-trip, no MMOItems PlayerData timing dependency.
     */
    private record SlotIdentity(String type, String id, long remainingSeconds, long totalSeconds) {
        boolean hasDuration() { return remainingSeconds >= 0; }
    }
}
