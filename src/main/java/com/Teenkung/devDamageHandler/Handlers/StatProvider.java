package com.Teenkung.devDamageHandler.Handlers;

import org.bukkit.entity.LivingEntity;

import java.util.function.Function;

/**
 * Interface for retrieving stat values for damage calculation.
 * Abstracts the difference between Players (MMOItems/MythicLib) and Mobs (MythicMobs config).
 */
public interface StatProvider extends Function<String, Double> {
    
    /**
     * Get a stat value by its ID.
     * @param statId The ID of the stat (e.g., "CRITICAL_STRIKE_CHANCE")
     * @return The value of the stat, or 0.0 if not found/default.
     */
    @Override
    Double apply(String statId);

    /**
     * Get the entity associated with this provider.
     */
    LivingEntity getEntity();
}
