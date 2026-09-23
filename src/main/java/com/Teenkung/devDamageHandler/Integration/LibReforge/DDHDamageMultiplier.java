package com.Teenkung.devDamageHandler.Integration.LibReforge;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Handlers.DamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import com.willfp.eco.core.config.interfaces.Config;
import com.willfp.libreforge.ConfigArguments;
import com.willfp.libreforge.ConfigArgumentsKt;
import com.willfp.libreforge.NoCompileData;
import com.willfp.libreforge.effects.Effect;
import com.willfp.libreforge.triggers.TriggerData;
import com.willfp.libreforge.SeparatorAmbivalentConfigKt;
import com.willfp.libreforge.triggers.TriggerParameter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class DDHDamageMultiplier extends Effect<NoCompileData> {

    private final DevDamageHandler plugin;

    public DDHDamageMultiplier(DevDamageHandler plugin) {
        super("ddh_damage_multiplier");
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public ConfigArguments getArguments() {
        return ConfigArgumentsKt.arguments(builder -> {
            builder.require("multiplier", "You must specify the multiplier amount!");
            return kotlin.Unit.INSTANCE;
        });
    }

    @NotNull
    @Override
    public Set<TriggerParameter> getParameters() {
        return Set.of(TriggerParameter.PLAYER);
    }

    @Override
    protected boolean onTrigger(@NotNull Config config, @NotNull TriggerData data, @NotNull NoCompileData compileData) {
        // We expect this to run just before DamageHandler processes the event.
        // We'll set a ThreadLocal in DamageHandler with the multiplier.
        
        // Supports expression for multiplier
        double multiplier = SeparatorAmbivalentConfigKt.getDoubleFromExpression(config, "multiplier", data);

        // Set the pending multiplier for the current thread
        DamageHandler.pendingMultiplier.set(multiplier);

        if (data.getPlayer() != null && plugin.getLibReforgeDebugMode(data.getPlayer())) {
            String src = data.getHolder().getHolder().getId().toString();
            Msg.send(data.getPlayer(), "<gray>[LR]</gray> <aqua>" + src + "</aqua> <gray>→ mult</gray> <white>×" + multiplier + "</white>");
        }

        return true;
    }
}
