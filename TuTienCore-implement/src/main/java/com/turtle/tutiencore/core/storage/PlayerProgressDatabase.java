package com.turtle.tutiencore.core.storage;

import com.turtle.tutiencore.core.infusion.OwnedInfusion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

/**
 * Primary database layer for all player data stored in the main TuTienCore database.
 *
 * Tables managed:
 *   tutien_player_progress  — tu vi, realm_id, sub_realm (legacy / cross-server sync)
 *   tutien_player_data      — tu vi, tu luyen total seconds, player name
 *   tutien_realm_data       — realm_id, sub_realm, breakthrough_count, breakthrough_cooldown
 *   tutien_infusion         — infusion inventory rows (one row per owned infusion)
 *   tutien_fly_sword        — fly sword level
 *   tutien_offline_tuluyen  — pending_tuvi, last_offline_start, last_earned_seconds,
 *                             last_real_offline_seconds, last_earned_multiplier
 */
public class PlayerProgressDatabase {

    // ── legacy table (kept for cross-server dashboard reads) ──────────────────
    static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS tutien_player_progress ("
            + "uuid VARCHAR(36) PRIMARY KEY, "
            + "player_name VARCHAR(16) NOT NULL, "
            + "tuvi DOUBLE NOT NULL, "
            + "realm_id INT NOT NULL, "
            + "sub_realm VARCHAR(32) NOT NULL)";
    static final String UPSERT_SQL = "INSERT INTO tutien_player_progress "
            + "(uuid, player_name, tuvi, realm_id, sub_realm) VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), "
            + "tuvi = VALUES(tuvi), realm_id = VALUES(realm_id), sub_realm = VALUES(sub_realm)";

    // ── tutien_player_data ────────────────────────────────────────────────────
    private static final String CREATE_PLAYER_DATA = "CREATE TABLE IF NOT EXISTS tutien_player_data ("
            + "uuid VARCHAR(36) PRIMARY KEY, "
            + "player_name VARCHAR(64) NOT NULL DEFAULT '', "
            + "tuvi DOUBLE NOT NULL DEFAULT 0, "
            + "tuluyen_total_seconds BIGINT NOT NULL DEFAULT 0, "
            + "equipped_infusion_id VARCHAR(64) DEFAULT NULL)";

    private static final String UPSERT_PLAYER_DATA =
            "INSERT INTO tutien_player_data (uuid, player_name, tuvi, tuluyen_total_seconds, equipped_infusion_id) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), "
            + "tuvi = VALUES(tuvi), tuluyen_total_seconds = VALUES(tuluyen_total_seconds), "
            + "equipped_infusion_id = VALUES(equipped_infusion_id)";

    // ── tutien_realm_data ─────────────────────────────────────────────────────
    private static final String CREATE_REALM_DATA = "CREATE TABLE IF NOT EXISTS tutien_realm_data ("
            + "uuid VARCHAR(36) PRIMARY KEY, "
            + "realm_id INT NOT NULL DEFAULT 1, "
            + "sub_realm VARCHAR(32) NOT NULL DEFAULT 'SO_KY', "
            + "breakthrough_count INT NOT NULL DEFAULT 0, "
            + "breakthrough_cooldown BIGINT NOT NULL DEFAULT 0)";

    private static final String UPSERT_REALM_DATA =
            "INSERT INTO tutien_realm_data (uuid, realm_id, sub_realm, breakthrough_count, breakthrough_cooldown) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE realm_id = VALUES(realm_id), sub_realm = VALUES(sub_realm), "
            + "breakthrough_count = VALUES(breakthrough_count), breakthrough_cooldown = VALUES(breakthrough_cooldown)";

    // ── tutien_infusion ───────────────────────────────────────────────────────
    private static final String CREATE_INFUSION = "CREATE TABLE IF NOT EXISTS tutien_infusion ("
            + "uuid VARCHAR(36) NOT NULL, "
            + "infusion_id VARCHAR(64) NOT NULL, "
            + "type_id VARCHAR(64) NOT NULL, "
            + "rarity_id VARCHAR(64) NOT NULL, "
            + "created_at BIGINT NOT NULL DEFAULT 0, "
            + "PRIMARY KEY (uuid, infusion_id), "
            + "INDEX idx_infusion_uuid (uuid))";

    // ── tutien_fly_sword ──────────────────────────────────────────────────────
    private static final String CREATE_FLY_SWORD = "CREATE TABLE IF NOT EXISTS tutien_fly_sword ("
            + "uuid VARCHAR(36) PRIMARY KEY, "
            + "level INT NOT NULL DEFAULT 1)";

    // ── tutien_offline_tuluyen ────────────────────────────────────────────────
    private static final String CREATE_OFFLINE_TULUYEN = "CREATE TABLE IF NOT EXISTS tutien_offline_tuluyen ("
            + "uuid VARCHAR(36) PRIMARY KEY, "
            + "pending_tuvi DOUBLE NOT NULL DEFAULT 0, "
            + "last_offline_start BIGINT NOT NULL DEFAULT 0, "
            + "last_earned_seconds BIGINT NOT NULL DEFAULT 0, "
            + "last_real_offline_seconds BIGINT NOT NULL DEFAULT 0, "
            + "last_earned_multiplier DOUBLE NOT NULL DEFAULT 1.0)";

    private final DataSource dataSource;

    public PlayerProgressDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INITIALIZATION
    // ─────────────────────────────────────────────────────────────────────────

    public void initialize() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_SQL);
            statement.executeUpdate(CREATE_PLAYER_DATA);
            statement.executeUpdate(CREATE_REALM_DATA);
            statement.executeUpdate(CREATE_INFUSION);
            statement.executeUpdate(CREATE_FLY_SWORD);
            statement.executeUpdate(CREATE_OFFLINE_TULUYEN);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEGACY: tutien_player_progress (cross-server sync)
    // ─────────────────────────────────────────────────────────────────────────

    public void saveProgress(UUID uuid, String playerName, double tuvi, int realmId, String subRealm) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setDouble(3, tuvi);
            statement.setInt(4, realmId);
            statement.setString(5, subRealm);
            statement.executeUpdate();
        }
    }

    public PlayerProgress loadProgress(UUID uuid) throws SQLException {
        String sql = "SELECT player_name, tuvi, realm_id, sub_realm FROM tutien_player_progress WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new PlayerProgress(
                        uuid,
                        rs.getString("player_name"),
                        rs.getDouble("tuvi"),
                        rs.getInt("realm_id"),
                        rs.getString("sub_realm")
                    );
                }
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tutien_player_data
    // ─────────────────────────────────────────────────────────────────────────

    public void savePlayerData(UUID uuid, String playerName, double tuvi, long tuLuyenTotalSeconds,
                               String equippedInfusionId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_PLAYER_DATA)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, playerName != null ? playerName : "");
            stmt.setDouble(3, tuvi);
            stmt.setLong(4, tuLuyenTotalSeconds);
            stmt.setString(5, equippedInfusionId); // nullable
            stmt.executeUpdate();
        }
    }

    /** Returns null if the player has no row yet. */
    public PlayerData loadPlayerData(UUID uuid) throws SQLException {
        String sql = "SELECT player_name, tuvi, tuluyen_total_seconds, equipped_infusion_id "
                + "FROM tutien_player_data WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerData(
                        uuid,
                        rs.getString("player_name"),
                        rs.getDouble("tuvi"),
                        rs.getLong("tuluyen_total_seconds"),
                        rs.getString("equipped_infusion_id")
                    );
                }
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tutien_realm_data
    // ─────────────────────────────────────────────────────────────────────────

    public void saveRealmData(UUID uuid, int realmId, String subRealm,
                              int breakthroughCount, long breakthroughCooldown) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_REALM_DATA)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, realmId);
            stmt.setString(3, subRealm);
            stmt.setInt(4, breakthroughCount);
            stmt.setLong(5, breakthroughCooldown);
            stmt.executeUpdate();
        }
    }

    /** Returns null if the player has no row yet. */
    public RealmData loadRealmData(UUID uuid) throws SQLException {
        String sql = "SELECT realm_id, sub_realm, breakthrough_count, breakthrough_cooldown "
                + "FROM tutien_realm_data WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new RealmData(
                        uuid,
                        rs.getInt("realm_id"),
                        rs.getString("sub_realm"),
                        rs.getInt("breakthrough_count"),
                        rs.getLong("breakthrough_cooldown")
                    );
                }
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tutien_infusion
    // ─────────────────────────────────────────────────────────────────────────

    /** Replaces all infusion rows for a player with the given list (delete + insert). */
    public void saveInfusions(UUID uuid, List<OwnedInfusion> inventory) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete existing rows for this player
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM tutien_infusion WHERE uuid = ?")) {
                    del.setString(1, uuid.toString());
                    del.executeUpdate();
                }
                // Re-insert
                if (!inventory.isEmpty()) {
                    String insertSql = "INSERT INTO tutien_infusion "
                            + "(uuid, infusion_id, type_id, rarity_id, created_at) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                        for (OwnedInfusion infusion : inventory) {
                            if (infusion == null) continue;
                            ins.setString(1, uuid.toString());
                            ins.setString(2, infusion.id());
                            ins.setString(3, infusion.typeId());
                            ins.setString(4, infusion.rarityId());
                            ins.setLong(5, infusion.createdAt());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<OwnedInfusion> loadInfusions(UUID uuid) throws SQLException {
        String sql = "SELECT infusion_id, type_id, rarity_id, created_at "
                + "FROM tutien_infusion WHERE uuid = ? ORDER BY created_at ASC";
        List<OwnedInfusion> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new OwnedInfusion(
                        rs.getString("infusion_id"),
                        rs.getString("type_id"),
                        rs.getString("rarity_id"),
                        rs.getLong("created_at")
                    ));
                }
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tutien_fly_sword
    // ─────────────────────────────────────────────────────────────────────────

    public void saveFlySwordLevel(UUID uuid, int level) throws SQLException {
        String sql = "INSERT INTO tutien_fly_sword (uuid, level) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE level = VALUES(level)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, level);
            stmt.executeUpdate();
        }
    }

    /** Returns -1 if no row exists for the player (caller should use default). */
    public int loadFlySwordLevel(UUID uuid) throws SQLException {
        String sql = "SELECT level FROM tutien_fly_sword WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("level");
            }
        }
        return -1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tutien_offline_tuluyen
    // ─────────────────────────────────────────────────────────────────────────

    public void saveOfflineTuLuyen(UUID uuid, double pendingTuVi, long lastOfflineStart,
                                   long lastEarnedSeconds, long lastRealOfflineSeconds,
                                   double lastEarnedMultiplier) throws SQLException {
        String sql = "INSERT INTO tutien_offline_tuluyen "
                + "(uuid, pending_tuvi, last_offline_start, last_earned_seconds, "
                + "last_real_offline_seconds, last_earned_multiplier) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "pending_tuvi = VALUES(pending_tuvi), "
                + "last_offline_start = VALUES(last_offline_start), "
                + "last_earned_seconds = VALUES(last_earned_seconds), "
                + "last_real_offline_seconds = VALUES(last_real_offline_seconds), "
                + "last_earned_multiplier = VALUES(last_earned_multiplier)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, pendingTuVi);
            stmt.setLong(3, lastOfflineStart);
            stmt.setLong(4, lastEarnedSeconds);
            stmt.setLong(5, lastRealOfflineSeconds);
            stmt.setDouble(6, lastEarnedMultiplier);
            stmt.executeUpdate();
        }
    }

    /** Returns null if no row exists for this player. */
    public OfflineTuLuyenData loadOfflineTuLuyen(UUID uuid) throws SQLException {
        String sql = "SELECT pending_tuvi, last_offline_start, last_earned_seconds, "
                + "last_real_offline_seconds, last_earned_multiplier "
                + "FROM tutien_offline_tuluyen WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new OfflineTuLuyenData(
                        uuid,
                        rs.getDouble("pending_tuvi"),
                        rs.getLong("last_offline_start"),
                        rs.getLong("last_earned_seconds"),
                        rs.getLong("last_real_offline_seconds"),
                        rs.getDouble("last_earned_multiplier")
                    );
                }
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DATA RECORDS
    // ─────────────────────────────────────────────────────────────────────────

    public record PlayerProgress(UUID uuid, String playerName, double tuvi, int realmId, String subRealm) {}

    public record PlayerData(UUID uuid, String playerName, double tuvi,
                             long tuLuyenTotalSeconds, String equippedInfusionId) {}

    public record RealmData(UUID uuid, int realmId, String subRealm,
                            int breakthroughCount, long breakthroughCooldown) {}

    public record OfflineTuLuyenData(UUID uuid, double pendingTuVi, long lastOfflineStart,
                                     long lastEarnedSeconds, long lastRealOfflineSeconds,
                                     double lastEarnedMultiplier) {}
}
