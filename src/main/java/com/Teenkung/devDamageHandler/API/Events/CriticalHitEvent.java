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
 * Fired when a critical hit occurs.
 * This event provides information about what type of crit occurred and allows
 * modification of the crit multiplier.
 */
public class CriticalHitEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    public enum CritType {
        /** Normal weapon critical strike */
        WEAPON,
        /** Skill-based critical strike */
        SKILL,
        /** Elemental critical strike */
        ELEMENTAL
    }

    private final LivingEntity attacker;
    private final Entity victim;
    private final DamageMetadata damageMetadata;
    private final CritType critType;
    private final Set<Element> critElements;

    private double critMultiplier;

    public CriticalHitEvent(@NotNull LivingEntity attacker,
                           @NotNull Entity victim,
                           @NotNull DamageMetadata damageMetadata,
                           @NotNull CritType critType,
                           @NotNull Set<Element> critElements,
                           double critMultiplier) {
        this.attacker = attacker;
        this.victim = victim;
        this.damageMetadata = damageMetadata;
        this.critType = critType;
        this.critElements = critElements;
        this.critMultiplier = critMultiplier;
    }

    /**
     * Gets the attacker entity.
     * @return The entity dealing the critical hit
     */
    public @NotNull LivingEntity getAttacker() { return attacker; }

    /**
     * Gets the victim entity.
     * @return The entity receiving the critical hit
     */
    public @NotNull Entity getVictim() { return victim; }

    /**
     * Gets the damage metadata.
     * @return The damage metadata
     */
    public @NotNull DamageMetadata getDamageMetadata() { return damageMetadata; }

    /**
     * Gets the type of critical hit.
     * @return The crit type
     */
    public @NotNull CritType getCritType() { return critType; }

    /**
     * Gets the elements that are critting (for ELEMENTAL type).
     * @return Set of elements with critical hits
     */
    public @NotNull Set<Element> getCritElements() { return critElements; }

    /**
     * Gets the critical hit multiplier.
     * @return The crit damage multiplier
     */
    public double getCritMultiplier() { return critMultiplier; }

    /**
     * Sets the critical hit multiplier.
     * @param multiplier The new multiplier
     */
    public void setCritMultiplier(double multiplier) { this.critMultiplier = multiplier; }

    /**
     * Checks if this is an elemental crit.
     * @return true if this is an elemental crit
     */
    public boolean isElementalCrit() { return critType == CritType.ELEMENTAL; }

    /**
     * Checks if this is a weapon crit.
     * @return true if this is a weapon crit
     */
    public boolean isWeaponCrit() { return critType == CritType.WEAPON; }

    /**
     * Checks if this is a skill crit.
     * @return true if this is a skill crit
     */
    public boolean isSkillCrit() { return critType == CritType.SKILL; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}

