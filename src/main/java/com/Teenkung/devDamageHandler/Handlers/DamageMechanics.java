package com.Teenkung.devDamageHandler.Handlers;

import io.lumine.mythic.lib.damage.DamageType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DamageMechanics {

    public static double lookupElementMultiplier(Map<String, Double> mods, String elementId) {
        if (mods.isEmpty() || elementId == null) return 1.0;

        String id = normalizeKey(elementId);

        // Try ELEMENT_<ID>
        Double v = mods.get("ELEMENT_" + id);
        if (v != null) return v;

        // Try just <ID>
        v = mods.get(id);
        if (v != null) return v;

        return 1.0;
    }

    public static double lookupTypeMultiplier(Map<String, Double> mods, DamageType type) {
        if (mods.isEmpty() || type == null) return 1.0;

        String typeName = type.name();

        // Try TYPE_<NAME>
        Double v = mods.get("TYPE_" + typeName);
        if (v != null) return v;

        // Try just <NAME>
        v = mods.get(typeName);
        if (v != null) return v;

        return 1.0;
    }

    public static boolean rollCrit(double chance) {
        if (chance <= 0) return false;
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    public static String normalizeKey(String s) {
        return s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
