package com.turtle.tutiencore.core.storage;

import org.bukkit.configuration.file.FileConfiguration;

public record DatabaseSettings(boolean enabled, String type, String host, int port, String database,
                               String username, String password, boolean useSsl) {

    public static DatabaseSettings from(FileConfiguration config) {
        return new DatabaseSettings(
                config.getBoolean("database.enabled", false),
                config.getString("database.type", "mysql"),
                config.getString("database.mysql.host", "localhost"),
                config.getInt("database.mysql.port", 3306),
                config.getString("database.mysql.database", "tutiencore"),
                config.getString("database.mysql.username", "root"),
                config.getString("database.mysql.password", ""),
                config.getBoolean("database.mysql.use-ssl", false)
        );
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl + "&characterEncoding=utf8&useUnicode=true";
    }
}
