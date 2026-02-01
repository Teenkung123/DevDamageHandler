package com.Teenkung.devDamageHandler.Integration.LibReforge;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import com.willfp.eco.core.config.interfaces.Config;
import com.willfp.libreforge.ConfigArguments;
import com.willfp.libreforge.Dispatcher;
import com.willfp.libreforge.NoCompileData;
import com.willfp.libreforge.ProvidedHolder;
import com.willfp.libreforge.effects.Effect;
import com.willfp.libreforge.effects.Identifiers;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DDHStat extends Effect<NoCompileData> {
    // Per player+effect-instance tracking so we don't overwrite fields.
    private final ConcurrentHashMap<UUID, StatModifier> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> activeStatName = new ConcurrentHashMap<>();
    private final DevDamageHandler plugin;

    public DDHStat(DevDamageHandler plugin) {
        super("ddh_stat");
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public ConfigArguments getArguments() {
        return com.willfp.libreforge.ConfigArgumentsKt.arguments(builder -> {
            builder.require("stat", "You must specify the stat to add!");
            builder.require("amount", "You must specify the amount to add!");
            builder.require("type", "You must specify the type of modifier to add! (FLAT, RELATIVE)");
            builder.require("identifier", "You must specify an identifier for this effect!");
            return kotlin.Unit.INSTANCE;
        });
    }

    @Override
    public boolean getShouldReload() {
        return false;
    }


    private UUID key(Player player, Identifiers identifiers) {
        // Stable key per-player/per-effect-instance. Prevents collisions across holders.
        return new UUID(
                player.getUniqueId().getMostSignificantBits() ^ identifiers.getUuid().getMostSignificantBits(),
                player.getUniqueId().getLeastSignificantBits() ^ identifiers.getUuid().getLeastSignificantBits()
        );
    }

    @Override
    public void onEnable(
            @NotNull Dispatcher<?> dispatcher,
            @NotNull Config config,
            @NotNull Identifiers identifiers,
            @NotNull ProvidedHolder holder,
            @NotNull NoCompileData compileData
    ) {
        if (!(dispatcher.getDispatcher() instanceof Player player)) return;

        String statName = config.getString("stat");
        String type = config.getString("type");
        String identifier = config.getString("identifier");
        double amount = config.getDoubleFromExpression("amount", player);

        MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());

        UUID k = key(player, identifiers);

        // If this effect is enabled again before disable, remove the old modifier first.
        StatModifier old = active.remove(k);
        String oldStat = activeStatName.remove(k);
        if (old != null && oldStat != null) {
            StatInstance oldInstance = mmoData.getStatMap().getInstance(oldStat);
            // Safe removal: only remove if present.
            if (oldInstance.getModifier(old.getUniqueId()) != null) {
                oldInstance.removeModifier(old.getUniqueId());
            }
            StatTracking.removeStat(mmoData, old.getUniqueId());
        }

        StatModifier mod = new StatModifier(
                "ecoenchants_" + identifier + "_" + identifiers.getUuid(),
                statName,
                amount,
                type.equalsIgnoreCase("RELATIVE") ? ModifierType.RELATIVE : ModifierType.FLAT
        );

        // Track first (your bookkeeping)
        StatTracking.addStat(mmoData, statName, mod);

        // Apply to MythicLib
        StatInstance instance = mmoData.getStatMap().getInstance(statName);

        // If somehow already exists, clean it (idempotent).
        if (instance.getModifier(mod.getUniqueId()) != null) {
            instance.removeModifier(mod.getUniqueId());
        }
        instance.registerModifier(mod);

        // Save for disable
        active.put(k, mod);
        activeStatName.put(k, statName);

        if (plugin.getLibReforgeDebugMode(player)) {
            Msg.send(player, "Adding Mythiclib Stat modifier: <cyan>" + statName + " <gray>(" + amount + " " + type + ")");
        }

    }

    @Override
    public void onDisable(
            @NotNull Dispatcher<?> dispatcher,
            @NotNull Identifiers identifiers,
            @NotNull ProvidedHolder holder
    ) {
        if (!(dispatcher.getDispatcher() instanceof Player player)) return;

        MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());

        UUID k = key(player, identifiers);

        StatModifier mod = active.remove(k);
        String statName = activeStatName.remove(k);

        if (mod == null || statName == null) return;

        StatInstance instance = mmoData.getStatMap().getInstance(statName);
        // Idempotent: remove only if present (prevents double-remove issues).
        if (instance.getModifier(mod.getUniqueId()) != null) {
            instance.removeModifier(mod.getUniqueId());
        }

        StatTracking.removeStat(mmoData, mod.getUniqueId());

        if (plugin.getLibReforgeDebugMode(player)) {
            Msg.send(player, "Removing Mythiclib Stat modifier: <cyan>" + statName);
        }
    }
}
