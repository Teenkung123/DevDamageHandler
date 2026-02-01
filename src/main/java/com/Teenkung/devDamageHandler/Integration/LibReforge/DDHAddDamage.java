package com.Teenkung.devDamageHandler.Integration.LibReforge;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Handlers.DamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import com.willfp.eco.core.config.interfaces.Config;
import com.willfp.libreforge.ConfigArguments;
import com.willfp.libreforge.NoCompileData;
import com.willfp.libreforge.effects.Effect;
import com.willfp.libreforge.triggers.TriggerData;
import com.willfp.libreforge.SeparatorAmbivalentConfigKt;
import com.willfp.libreforge.triggers.TriggerParameter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class DDHAddDamage extends Effect<NoCompileData> {

    private final DevDamageHandler plugin;

    public DDHAddDamage(DevDamageHandler plugin) {
        super("ddh_add_damage");
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public ConfigArguments getArguments() {
        return com.willfp.libreforge.ConfigArgumentsKt.arguments(builder -> {
            builder.require("amount", "You must specify the damage amount to add!");
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
        // We'll set a ThreadLocal in DamageHandler with the flat damage amount.
        
        // Supports expression for amount
        double amount = SeparatorAmbivalentConfigKt.getDoubleFromExpression(config, "amount", data);

        // Set the pending flat damage for the current thread
        DamageHandler.pendingFlatDamage.set(amount);

        if (data.getPlayer() != null && plugin.getLibReforgeDebugMode(data.getPlayer())) {
            Msg.send(data.getPlayer(), "Set Flat Damage Addition: +" + amount);
        }

        return true;
    }
}
