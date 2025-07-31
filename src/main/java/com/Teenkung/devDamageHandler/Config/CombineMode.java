package com.Teenkung.devDamageHandler.Config;

public enum CombineMode {
    PRODUCT, MIN, MAX, AVERAGE, SOFT_ADD, RESIST_ADD;

    public static CombineMode fromString(String s, CombineMode def) {
        if (s == null) return def;
        try {
            return CombineMode.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
