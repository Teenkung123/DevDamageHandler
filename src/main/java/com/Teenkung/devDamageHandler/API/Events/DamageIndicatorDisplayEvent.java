package com.Teenkung.devDamageHandler.API.Events;

import com.Teenkung.devDamageHandler.Indicator.IndicatorLine;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fired before damage indicators are displayed.
 * This event allows modification or cancellation of indicator display.
 */
public class DamageIndicatorDisplayEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final LivingEntity victim;
    private final LivingEntity attacker;
    private List<IndicatorLine> indicatorLines;
    private final double totalDamage;

    private boolean cancelled = false;

    public DamageIndicatorDisplayEvent(@NotNull LivingEntity victim,
                                       @Nullable LivingEntity attacker,
                                       @NotNull List<IndicatorLine> indicatorLines,
                                       double totalDamage) {
        this.victim = victim;
        this.attacker = attacker;
        this.indicatorLines = indicatorLines;
        this.totalDamage = totalDamage;
    }

    /**
     * Gets the victim entity (where the indicator will appear).
     * @return The victim entity
     */
    public @NotNull LivingEntity getVictim() { return victim; }

    /**
     * Gets the attacker entity (may be null for environmental damage).
     * @return The attacker, or null
     */
    public @Nullable LivingEntity getAttacker() { return attacker; }

    /**
     * Gets the indicator lines to be displayed.
     * @return List of indicator lines
     */
    public @NotNull List<IndicatorLine> getIndicatorLines() { return indicatorLines; }

    /**
     * Sets the indicator lines to display.
     * @param lines The new indicator lines
     */
    public void setIndicatorLines(@NotNull List<IndicatorLine> lines) { this.indicatorLines = lines; }

    /**
     * Gets the total damage dealt.
     * @return The total damage
     */
    public double getTotalDamage() { return totalDamage; }

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


