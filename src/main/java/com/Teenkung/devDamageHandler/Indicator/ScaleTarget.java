package com.Teenkung.devDamageHandler.Indicator;

import java.util.Locale;

public enum ScaleTarget {
    BUKKIT, META, PACKETS, NONE;

    public static ScaleTarget fromString(String raw, ScaleTarget def) {
        if (raw == null) return def;
        try {
            return ScaleTarget.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
