package com.Teenkung.devDamageHandler.API.Events;

import com.Teenkung.devDamageHandler.API.DamageData;
import io.lumine.mythic.lib.damage.DamageMetadata;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player attacker's damage has been fully calculated.
 * This event is called after all damage modifiers, elements, and crits have been applied.
 *
 * <p>This event is fired at the END of damage calculation, before indicators are shown.
 * Use {@link DamagePreProcessEvent} if you want to modify damage BEFORE calculations.</p>
 *
 * <p>The DamageData contains:</p>
 * <ul>
 *   <li>Elemental damage breakdown (base, mob modifiers, crit multipliers, stat multipliers, final)</li>
 *   <li>Damage type totals (PHYSICAL, MAGIC, SKILL, etc.)</li>
 *   <li>Packet breakdown from MythicLib</li>
 *   <li>Crit flags (weapon crit, skill crit)</li>
 *   <li>Source modifiers (MythicMobs damage modifiers)</li>
 * </ul>
 */
public class PlayerDamageCalculatedEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Player attacker;
    private final Entity victim;
    private final DamageData damageData;
    private final DamageMetadata damageMetadata;

    public PlayerDamageCalculatedEvent(@NotNull Player attacker,
                                       @NotNull Entity victim,
                                       @NotNull DamageData damageData,
                                       @NotNull DamageMetadata damageMetadata) {
        this.attacker = attacker;
        this.victim = victim;
        this.damageData = damageData;
        this.damageMetadata = damageMetadata;
    }

    /**
     * Gets the player who dealt the damage.
     * @return The attacking player
     */
    public @NotNull Player getAttacker() { return attacker; }

    /**
     * Gets the entity that received the damage.
     * @return The victim entity
     */
    public @NotNull Entity getVictim() { return victim; }

    /**
     * Gets the calculated damage data snapshot.
     * @return The damage data containing all computed values
     */
    public @NotNull DamageData getDamageData() { return damageData; }

    /**
     * Gets the underlying MythicLib damage metadata.
     * @return The damage metadata
     */
    public @NotNull DamageMetadata getDamageMetadata() { return damageMetadata; }

    /**
     * Convenience method to get the total final damage.
     * @return The total damage dealt
     */
    public double getFinalDamage() { return damageData.getPacketsSum(); }

    /**
     * Checks if this attack was a critical hit.
     * @return true if either weapon or skill crit occurred
     */
    public boolean isCriticalHit() { return damageData.isWeaponCrit() || damageData.isSkillCrit(); }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
