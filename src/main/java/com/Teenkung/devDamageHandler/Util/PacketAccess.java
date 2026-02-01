package com.Teenkung.devDamageHandler.Util;

import io.lumine.mythic.lib.damage.DamagePacket;

import java.lang.reflect.Field;

/**
 * Utility for directly accessing DamagePacket's private value field.
 * This is a workaround for when the public API doesn't expose certain modifiers.
 */
public final class PacketAccess {
    private static final Field VALUE_FIELD;

    static {
        Field f = null;
        try {
            f = DamagePacket.class.getDeclaredField("value");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        VALUE_FIELD = f;
    }

    private PacketAccess() {}

    public static void multiplyValue(DamagePacket pkt, double mul) {
        if (VALUE_FIELD == null) return;
        try {
            double now = (double) VALUE_FIELD.get(pkt);
            VALUE_FIELD.set(pkt, now * mul);
        } catch (IllegalAccessException ignored) { }
    }
}
