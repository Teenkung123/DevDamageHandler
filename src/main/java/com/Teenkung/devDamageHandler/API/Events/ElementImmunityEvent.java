package com.Teenkung.devDamageHandler.API.Events;

import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Fired when an entity is immune to an element.
 * This event is called for each immune element in an attack.
 */
public class ElementImmunityEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final LivingEntity attacker;
    private final Entity victim;
    private final DamageMetadata damageMetadata;
    private final Set<Element> immuneElements;
    private final double blockedDamage;

    public ElementImmunityEvent(@NotNull LivingEntity attacker,
                                @NotNull Entity victim,
                                @NotNull DamageMetadata damageMetadata,
                                @NotNull Set<Element> immuneElements,
                                double blockedDamage) {
        this.attacker = attacker;
        this.victim = victim;
        this.damageMetadata = damageMetadata;
        this.immuneElements = immuneElements;
        this.blockedDamage = blockedDamage;
    }

    /**
     * Gets the attacker entity.
     * @return The entity dealing the damage
     */
    public @NotNull LivingEntity getAttacker() { return attacker; }

    /**
     * Gets the victim entity.
     * @return The entity with the immunity
     */
    public @NotNull Entity getVictim() { return victim; }

    /**
     * Gets the damage metadata.
     * @return The damage metadata
     */
    public @NotNull DamageMetadata getDamageMetadata() { return damageMetadata; }

    /**
     * Gets the elements the victim is immune to.
     * @return Set of immune elements
     */
    public @NotNull Set<Element> getImmuneElements() { return immuneElements; }

    /**
     * Gets the amount of damage blocked by the immunity.
     * @return The blocked damage amount
     */
    public double getBlockedDamage() { return blockedDamage; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}

