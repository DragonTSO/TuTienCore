package net.Indyuce.mmocore.api.player;

import java.util.UUID;

public class PlayerData {
    public static PlayerData get(org.bukkit.entity.Player player) { return new PlayerData(); }
    public static PlayerData get(UUID uuid) { return new PlayerData(); }
    public PlayerClass getProfess() { return new PlayerClass(); }
    public boolean isOnline() { return false; }
    public int getLevel() { return 0; }
}
