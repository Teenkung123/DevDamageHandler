package com.Teenkung.devDamageHandler.Integration.LibReforge;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import com.willfp.eco.core.config.interfaces.Config;
import com.willfp.libreforge.NoCompileData;
import com.willfp.libreforge.effects.Effect;
import com.willfp.libreforge.SeparatorAmbivalentConfigKt;
import com.willfp.libreforge.triggers.TriggerData;
import com.willfp.libreforge.triggers.TriggerParameter;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.modifier.TemporaryStatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DDHTempStat extends Effect<NoCompileData> {

    /**
     * We keep the modifier OBJECT so we can safely unregister to refresh duration.
     * Key is per: player + holderId + identifier + statName
     */
    private final ConcurrentHashMap<UUID, TemporaryStatModifier> active = new ConcurrentHashMap<>();
    private final DevDamageHandler plugin;

    public DDHTempStat(DevDamageHandler plugin) {
        super("ddh_tempstat");
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public Set<TriggerParameter> getParameters() {
        return Set.of(TriggerParameter.PLAYER);
    }

    private UUID key(Player player, TriggerData data, String identifier, String statName) {
        // holder id is stable for the enchant/holder definition
        String holderId = data.getHolder().getHolder().getId().toString();

        String raw = player.getUniqueId() + "|" + holderId + "|" + identifier + "|" + statName;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected boolean onTrigger(@NotNull Config config, @NotNull TriggerData data, @NotNull NoCompileData compileData) {
        Player player = data.getPlayer();
        if (player == null) return false;

        MMOPlayerData mmoData = MMOPlayerData.get(player.getUniqueId());

        String statName = config.getString("stat");
        String type = config.getString("type");
        String identifier = config.getString("identifier");

        // Trigger-aware expressions (amount/duration can use placeholders from TriggerData)
        double amount = SeparatorAmbivalentConfigKt.getDoubleFromExpression(config, "amount", data);
        long durationSeconds = (long) SeparatorAmbivalentConfigKt.getDoubleFromExpression(config, "duration", data);
        long durationTicks = Math.max(1L, durationSeconds * 20L);

        UUID k = key(player, data, identifier, statName);

        // Refresh behavior: unregister old object if present
        TemporaryStatModifier old = active.remove(k);
        if (old != null) {
            try {
                old.unregister(mmoData);
            } catch (IllegalArgumentException ignored) {
                // already expired / already removed
            }
        }

        // Make modifier key stable (doesn't need identifiers.getUuid() here)
        String modifierKey = "ecoenchants_" + identifier + "_" + data.getHolder().getHolder().getId();

        TemporaryStatModifier mod = new TemporaryStatModifier(
                modifierKey,
                statName,
                amount,
                type.equalsIgnoreCase("RELATIVE") ? ModifierType.RELATIVE : ModifierType.FLAT,
                EquipmentSlot.OTHER,
                ModifierSource.ACCESSORY
        );

        mod.register(mmoData, durationTicks);

        active.put(k, mod);

        // Optional tracking (recommended so you can bulk-cleanup on quit/disable)
        StatTracking.addTempStat(mmoData, statName, mod);

        if (plugin.getLibReforgeDebugMode(player)) {
            Msg.send(player, "Adding Mythiclib Temp Stat modifier: <cyan>" + statName + " <gray>(" + amount + " " + type + " " + durationTicks + "t) <white>with identifier: <yellow>" + identifier);
        }

        return true;
    }
}
