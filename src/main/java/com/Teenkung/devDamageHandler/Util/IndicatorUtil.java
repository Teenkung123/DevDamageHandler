package com.Teenkung.devDamageHandler.Util;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.event.IndicatorDisplayEvent;
import io.lumine.mythic.lib.listener.option.DamageIndicators;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class IndicatorUtil {

    private static DamageIndicators cached;

    private IndicatorUtil() {}

    /** Try to find MythicLib's DamageIndicators listener once. */
    public static DamageIndicators get() {
        if (cached != null) return cached;
        for (RegisteredListener rl : HandlerList.getRegisteredListeners(MythicLib.plugin)) {
            if (rl.getListener() instanceof DamageIndicators di) {
                cached = di;
                break;
            }
        }
        return cached;
    }

    /** Show a custom indicator line (MiniMessage/legacy supported by MythicLib.parseColors). */
    public static void show(Entity target, String message) {
        DamageIndicators di = get();
        if (di == null) {
            // Fallback: just log
            MythicLib.plugin.getLogger().warning("Could not find DamageIndicators listener.");
            return;
        }
        // Any direction vector you like; this mimics MythicLib default
        Vector dir = randomDir(target);
        di.displayIndicator(target, message, dir, IndicatorDisplayEvent.IndicatorType.DAMAGE);
    }

    private static Vector randomDir(Entity e) {
        double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        return new Vector(Math.cos(a), 0, Math.sin(a));
    }
}
