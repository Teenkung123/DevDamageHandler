package com.Teenkung.devDamageHandler.API.Events;

import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Fired BEFORE damage modifiers are applied.
 * This event allows modification of the damage before multipliers and crits are calculated.
 *
 * <p>Use this event to:</p>
 * <ul>
 *   <li>Add/remove elements from the attack</li>
 *   <li>Add/remove damage types</li>
 *   <li>Modify base damage values</li>
 *   <li>Cancel the damage processing entirely</li>
 * </ul>
 */
public class DamagePreProcessEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final LivingEntity attacker;
    private final Entity victim;
    private final DamageMetadata damageMetadata;
    private final Map<Element, Double> elementDamage;
    private final Set<DamageType> damageTypes;
    private final boolean isPlayerAttack;

    private boolean cancelled = false;
    private double damageMultiplier = 1.0;

    public DamagePreProcessEvent(@NotNull LivingEntity attacker,
                                 @NotNull Entity victim,
                                 @NotNull DamageMetadata damageMetadata,
                                 @NotNull Map<Element, Double> elementDamage,
                                 @NotNull Set<DamageType> damageTypes,
                                 boolean isPlayerAttack) {
        this.attacker = attacker;
        this.victim = victim;
        this.damageMetadata = damageMetadata;
        this.elementDamage = elementDamage;
        this.damageTypes = damageTypes;
        this.isPlayerAttack = isPlayerAttack;
    }

    /**
     * Gets the attacker entity.
     * @return The entity dealing the damage
     */
    public @NotNull LivingEntity getAttacker() { return attacker; }

    /**
     * Gets the victim entity.
     * @return The entity receiving the damage
     */
    public @NotNull Entity getVictim() { return victim; }

    /**
     * Gets the underlying MythicLib damage metadata.
     * @return The damage metadata
     */
    public @NotNull DamageMetadata getDamageMetadata() { return damageMetadata; }

    /**
     * Gets the modifiable map of elemental damage.
     * Changes to this map will affect the final damage.
     * @return Map of elements to their damage values
     */
    public @NotNull Map<Element, Double> getElementDamage() { return elementDamage; }

    /**
     * Gets the modifiable set of damage types.
     * Changes to this set will affect which modifiers are applied.
     * @return Set of active damage types
     */
    public @NotNull Set<DamageType> getDamageTypes() { return damageTypes; }

    /**
     * Checks if the attacker is a player.
     * @return true if the attacker is a player
     */
    public boolean isPlayerAttack() { return isPlayerAttack; }

    /**
     * Gets the damage multiplier to apply.
     * @return The multiplier (default 1.0)
     */
    public double getDamageMultiplier() { return damageMultiplier; }

    /**
     * Sets a multiplier for all damage.
     * @param multiplier The multiplier to apply
     */
    public void setDamageMultiplier(double multiplier) { this.damageMultiplier = multiplier; }

    /**
     * Adds elemental damage of a specific element.
     * @param element The element to add
     * @param damage The damage value
     */
    public void addElementDamage(@NotNull Element element, double damage) {
        elementDamage.merge(element, damage, Double::sum);
    }

    /**
     * Removes elemental damage of a specific element.
     * @param element The element to remove
     */
    public void removeElementDamage(@NotNull Element element) {
        elementDamage.remove(element);
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}

