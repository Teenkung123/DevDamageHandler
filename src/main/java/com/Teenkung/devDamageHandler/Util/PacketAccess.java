package com.Teenkung.devDamageHandler.Util;

import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.listener.option.GameIndicators;

import java.lang.reflect.Field;

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

    public static void test() {
        Field f = null;
        try {
            f = GameIndicators.class.getDeclaredField("displayIndicator");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
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
