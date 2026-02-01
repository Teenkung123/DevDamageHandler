package com.Teenkung.devDamageHandler.API.Events;

import com.Teenkung.devDamageHandler.API.DamageData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerDamageCalculatedEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Player attacker;
    private final Entity victim;
    private final DamageData damageData;

    public PlayerDamageCalculatedEvent(@NotNull Player attacker,
                                       @NotNull Entity victim,
                                       @NotNull DamageData damageData) {
        this.attacker = attacker;
        this.victim = victim;
        this.damageData = damageData;
    }

    public @NotNull Player getAttacker() { return attacker; }
    public @NotNull Entity getVictim() { return victim; }
    public @NotNull DamageData getDamageData() { return damageData; }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
