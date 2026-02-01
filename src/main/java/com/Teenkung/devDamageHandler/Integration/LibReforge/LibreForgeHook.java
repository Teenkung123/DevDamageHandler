package com.Teenkung.devDamageHandler.Integration.LibReforge;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.willfp.libreforge.effects.Effects;

public class LibreForgeHook {

    public static void register(DevDamageHandler plugin) {
        Effects.INSTANCE.register(new DDHStat(plugin));
        Effects.INSTANCE.register(new DDHTempStat(plugin));
        Effects.INSTANCE.register(new DDHDamageMultiplier(plugin));
        Effects.INSTANCE.register(new DDHAddDamage(plugin));
    }


}
