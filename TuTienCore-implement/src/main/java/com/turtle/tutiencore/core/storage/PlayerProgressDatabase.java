package com.turtle.tutiencore.core.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

public class PlayerProgressDatabase {
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

    private final DataSource dataSource;

    public PlayerProgressDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_SQL);
        }
    }

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
}
