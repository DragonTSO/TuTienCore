package com.turtle.tutiencore.core.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Database storage for player equipment (/trangbi) and bound offhand items.
 * Stores equipment slots as serialized ItemStack blobs + MMOItems metadata.
 */
public class EquipmentDatabase {
    
    // Equipment slots table
    private static final String CREATE_EQUIPMENT_TABLE = 
        "CREATE TABLE IF NOT EXISTS tutien_equipment ("
        + "uuid VARCHAR(36) NOT NULL, "
        + "slot_id VARCHAR(32) NOT NULL, "
        + "item_data BLOB, "
        + "mmo_type VARCHAR(64), "
        + "mmo_id VARCHAR(64), "
        + "remaining_seconds BIGINT, "
        + "total_seconds BIGINT, "
        + "PRIMARY KEY (uuid, slot_id), "
        + "INDEX idx_uuid (uuid))";

    // Bound offhand table
    private static final String CREATE_OFFHAND_TABLE = 
        "CREATE TABLE IF NOT EXISTS tutien_bound_offhand ("
        + "uuid VARCHAR(36) PRIMARY KEY, "
        + "slot_index INT NOT NULL, "
        + "item_data BLOB, "
        + "mmo_type VARCHAR(64), "
        + "mmo_id VARCHAR(64))";

    private final DataSource dataSource;

    public EquipmentDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_EQUIPMENT_TABLE);
            stmt.executeUpdate(CREATE_OFFHAND_TABLE);
        }
    }

    // ==========================================
    // EQUIPMENT SLOTS
    // ==========================================

    /**
     * Save a single equipment slot for a player.
     */
    public void saveEquipmentSlot(UUID uuid, String slotId, ItemStack item, 
                                   String mmoType, String mmoId, 
                                   long remainingSeconds, long totalSeconds) throws SQLException {
        String sql = "INSERT INTO tutien_equipment "
                + "(uuid, slot_id, item_data, mmo_type, mmo_id, remaining_seconds, total_seconds) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "item_data = VALUES(item_data), "
                + "mmo_type = VALUES(mmo_type), "
                + "mmo_id = VALUES(mmo_id), "
                + "remaining_seconds = VALUES(remaining_seconds), "
                + "total_seconds = VALUES(total_seconds)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, slotId);
            stmt.setBytes(3, item != null ? serializeItem(item) : null);
            stmt.setString(4, mmoType);
            stmt.setString(5, mmoId);
            stmt.setLong(6, remainingSeconds);
            stmt.setLong(7, totalSeconds);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete an equipment slot (when unequipped).
     */
    public void deleteEquipmentSlot(UUID uuid, String slotId) throws SQLException {
        String sql = "DELETE FROM tutien_equipment WHERE uuid = ? AND slot_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, slotId);
            stmt.executeUpdate();
        }
    }

    /**
     * Load all equipment slots for a player.
     * Returns a map: slotId -> EquipmentSlotData
     */
    public Map<String, EquipmentSlotData> loadEquipment(UUID uuid) throws SQLException {
        String sql = "SELECT slot_id, item_data, mmo_type, mmo_id, remaining_seconds, total_seconds "
                + "FROM tutien_equipment WHERE uuid = ?";
        
        Map<String, EquipmentSlotData> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String slotId = rs.getString("slot_id");
                    byte[] itemData = rs.getBytes("item_data");
                    ItemStack item = itemData != null ? deserializeItem(itemData) : null;
                    String mmoType = rs.getString("mmo_type");
                    String mmoId = rs.getString("mmo_id");
                    long remainingSeconds = rs.getLong("remaining_seconds");
                    long totalSeconds = rs.getLong("total_seconds");
                    
                    result.put(slotId, new EquipmentSlotData(item, mmoType, mmoId, remainingSeconds, totalSeconds));
                }
            }
        }
        return result;
    }

    /**
     * Delete all equipment slots for a player.
     */
    public void deleteAllEquipment(UUID uuid) throws SQLException {
        String sql = "DELETE FROM tutien_equipment WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        }
    }

    // ==========================================
    // BOUND OFFHAND
    // ==========================================

    /**
     * Save bound offhand state for a player.
     */
    public void saveBoundOffhand(UUID uuid, int slotIndex, ItemStack item, 
                                  String mmoType, String mmoId) throws SQLException {
        String sql = "INSERT INTO tutien_bound_offhand "
                + "(uuid, slot_index, item_data, mmo_type, mmo_id) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "slot_index = VALUES(slot_index), "
                + "item_data = VALUES(item_data), "
                + "mmo_type = VALUES(mmo_type), "
                + "mmo_id = VALUES(mmo_id)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, slotIndex);
            stmt.setBytes(3, item != null ? serializeItem(item) : null);
            stmt.setString(4, mmoType);
            stmt.setString(5, mmoId);
            stmt.executeUpdate();
        }
    }

    /**
     * Load bound offhand state for a player.
     */
    public BoundOffhandData loadBoundOffhand(UUID uuid) throws SQLException {
        String sql = "SELECT slot_index, item_data, mmo_type, mmo_id FROM tutien_bound_offhand WHERE uuid = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int slotIndex = rs.getInt("slot_index");
                    byte[] itemData = rs.getBytes("item_data");
                    ItemStack item = itemData != null ? deserializeItem(itemData) : null;
                    String mmoType = rs.getString("mmo_type");
                    String mmoId = rs.getString("mmo_id");
                    return new BoundOffhandData(slotIndex, item, mmoType, mmoId);
                }
            }
        }
        return null;
    }

    /**
     * Delete bound offhand for a player.
     */
    public void deleteBoundOffhand(UUID uuid) throws SQLException {
        String sql = "DELETE FROM tutien_bound_offhand WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        }
    }

    // ==========================================
    // SERIALIZATION
    // ==========================================

    private byte[] serializeItem(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(item);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize ItemStack", e);
        }
    }

    private ItemStack deserializeItem(byte[] data) {
        if (data == null) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize ItemStack", e);
        }
    }

    // ==========================================
    // DATA CLASSES
    // ==========================================

    public record EquipmentSlotData(
        ItemStack item,
        String mmoType,
        String mmoId,
        long remainingSeconds,
        long totalSeconds
    ) {}

    public record BoundOffhandData(
        int slotIndex,
        ItemStack item,
        String mmoType,
        String mmoId
    ) {}
}
