package com.Teenkung.devDamageHandler.Util;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Indicator.CustomIndicators;
import com.Teenkung.devDamageHandler.Indicator.IndicatorLine;
import io.lumine.mythic.lib.MythicLib;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;

/**
 * Utility for displaying custom indicators.
 * This now uses our own CustomIndicators instead of the removed DamageIndicators.
 */
public final class IndicatorUtil {

    private IndicatorUtil() {}

    /**
     * Show a custom indicator line.
     * Falls back to logging if the plugin instance is not available.
     */
    public static void show(Entity target, String message) {
        DevDamageHandler plugin = JavaPlugin.getPlugin(DevDamageHandler.class);
        if (plugin == null) {
            MythicLib.plugin.getLogger().warning("Could not find DevDamageHandler plugin instance.");
            return;
        }
        
        CustomIndicators indicators = plugin.getIndicators();
        if (indicators == null) {
            MythicLib.plugin.getLogger().warning("CustomIndicators not initialized.");
            return;
        }
        
        // Create a simple indicator line for the message
        // Using null element and no types for a generic message display
        IndicatorLine line = new IndicatorLine(0, false, false, false, null, null, 1.0, message);
        indicators.displayLines(target, Collections.singletonList(line));
    }

    /**
     * Show a numeric damage indicator with specific value.
     */
    public static void showDamage(Entity target, double value, boolean crit) {
        DevDamageHandler plugin = JavaPlugin.getPlugin(DevDamageHandler.class);
        if (plugin == null) return;
        
        CustomIndicators indicators = plugin.getIndicators();
        if (indicators == null) return;
        
        IndicatorLine line = new IndicatorLine(value, false, crit, false, null, null, 1.0, null);
        indicators.displayLines(target, Collections.singletonList(line));
    }
}
