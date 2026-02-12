package com.Teenkung.devDamageHandler.API.Events;

import com.Teenkung.devDamageHandler.API.DamageData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a mob (non-player) attacker's damage has been fully calculated.
 * This event is called after all damage modifiers, elements, and crits have been applied.
 */
public class MobDamageCalculatedEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final LivingEntity attacker;
    private final Entity victim;
    private final DamageData damageData;

    public MobDamageCalculatedEvent(@NotNull LivingEntity attacker,
                                    @NotNull Entity victim,
                                    @NotNull DamageData damageData) {
        this.attacker = attacker;
        this.victim = victim;
        this.damageData = damageData;
    }

    /**
     * Gets the mob attacker.
     * @return The mob (non-player) that dealt the damage
     */
    public @NotNull LivingEntity getAttacker() { return attacker; }

    /**
     * Gets the victim entity.
     * @return The entity that received the damage
     */
    public @NotNull Entity getVictim() { return victim; }

    /**
     * Gets the calculated damage data.
     * @return The damage data containing all computed values
     */
    public @NotNull DamageData getDamageData() { return damageData; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}

