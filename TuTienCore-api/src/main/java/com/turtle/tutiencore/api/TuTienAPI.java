package com.turtle.tutiencore.api;

import java.util.UUID;
import java.util.List;
import java.util.Map;

public interface TuTienAPI {
    
    /**
     * Get the cultivation points (Tu Vi) of a player.
     * @param uuid Player's UUID
     * @return Amount of Tu Vi
     */
    double getTuVi(UUID uuid);

    /**
     * Set the cultivation points (Tu Vi) of a player.
     * @param uuid Player's UUID
     * @param amount New Tu Vi amount
     */
    void setTuVi(UUID uuid, double amount);

    /**
     * Add cultivation points (Tu Vi) to a player.
     * @param uuid Player's UUID
     * @param amount Amount to add
     */
    void addTuVi(UUID uuid, double amount);

    /**
     * Take cultivation points (Tu Vi) from a player.
     * @param uuid Player's UUID
     * @param amount Amount to remove
     */
    void takeTuVi(UUID uuid, double amount);

    /**
     * Get the top players by Tu Vi.
     * @return List of entries mapping Player Name to Tu Vi amount
     */
    List<Map.Entry<String, Double>> getTopTuVi();
}
