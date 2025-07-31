package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Registers custom MMOItems stats and keeps them positioned before BLOCK_POWER
 * in the in-game editor. Also re-applies ordering on MMOItems reloads.
 */
public class StatHandler implements Listener {

    // ----- Config -----
    private static final String ANCHOR_ID = "BLOCK_POWER";

    private static final String STAT_ADDITIONAL_ELEM_DMG  = "ADDITIONAL_ELEMENTAL_DAMAGE";
    private static final String STAT_ELEM_CRIT_POWER      = "ELEMENTAL_CRITICAL_STRIKE_POWER";
    private static final String STAT_ELEM_CRIT_CHANCE     = "ELEMENTAL_CRITICAL_STRIKE_CHANCE";

    private final DevDamageHandler plugin;

    // MythicLib runtime modifiers (values managed elsewhere; default 0)
    public final StatModifier elementalDamage =
            new StatModifier("DevDamageHandler", STAT_ADDITIONAL_ELEM_DMG, 0);
    public final StatModifier elementalCriticalPower =
            new StatModifier("DevDamageHandler", STAT_ELEM_CRIT_POWER, 0);
    public final StatModifier elementalCriticalChance =
            new StatModifier("DevDamageHandler", STAT_ELEM_CRIT_CHANCE, 0);

    public StatHandler(DevDamageHandler plugin) {
        this.plugin = plugin;

        // Register custom stats if they don't exist yet (no forced /mi reload)
        registerIfMissing(STAT_ADDITIONAL_ELEM_DMG,
                new DoubleStat(STAT_ADDITIONAL_ELEM_DMG, Material.WIND_CHARGE,
                        "Additional Elemental Damage",
                        new String[]{"Additional Elemental Damage (%)"}));

        registerIfMissing(STAT_ELEM_CRIT_POWER,
                new DoubleStat(STAT_ELEM_CRIT_POWER, Material.END_CRYSTAL,
                        "Elemental Critical Strike Power",
                        new String[]{"Elemental Critical Strike Power (%)"}));

        registerIfMissing(STAT_ELEM_CRIT_CHANCE,
                new DoubleStat(STAT_ELEM_CRIT_CHANCE, Material.END_CRYSTAL,
                        "Elemental Critical Strike Chance",
                        new String[]{"Elemental Critical Strike Chance (%)"}));

        Bukkit.dispatchCommand(plugin.getServer().getConsoleSender(), "mi reload all");
    }

    /* ---------------- Events ---------------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MMOPlayerData data = MMOPlayerData.online(event.getPlayer());
        elementalDamage.register(data);
        elementalCriticalPower.register(data);
        elementalCriticalChance.register(data);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MMOPlayerData data = MMOPlayerData.online(event.getPlayer());
        elementalDamage.unregister(data);
        elementalCriticalPower.unregister(data);
        elementalCriticalChance.unregister(data);
    }


    @EventHandler
    public void onMythicMobsReload(MythicReloadedEvent e) {
        plugin.getTemplateManager().loadAll();
    }


    /**
     * MMOItems reloads rebuild the stat lists; re-apply our ordering one tick later.
     */
    @EventHandler
    public void onMMOItemsReload(MMOItemsReloadEvent event) {
        plugin.getTemplateManager().loadAll();
        Bukkit.getScheduler().runTask(plugin, this::applyOrdering);
    }

    /* ---------------- Core ---------------- */

    private void registerIfMissing(String id, DoubleStat stat) {
        if (!MMOItems.plugin.getStats().has(id)) {
            MMOItems.plugin.getStats().register(stat);
        }
    }

    private void applyOrdering() {
        moveStatBefore(STAT_ADDITIONAL_ELEM_DMG, "MAGIC_DAMAGE");
        moveStatBefore(STAT_ELEM_CRIT_POWER,     ANCHOR_ID);
        moveStatBefore(STAT_ELEM_CRIT_CHANCE,    ANCHOR_ID);
        moveStatBefore("ELEMENT", "ATTACK_SPEED");
    }

    /**
     * For each compatible Type, ensure the given stat appears before the anchor.
     * If the anchor isn't present for that Type, insert the stat at index 0.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void moveStatBefore(String statId, String anchorId) {
        ItemStat<?, ?> stat = MMOItems.plugin.getStats().get(statId);
        if (stat == null) {
            plugin.getLogger().warning("[StatHandler] Stat not found: " + statId);
            return;
        }

        ItemStat<?, ?> anchor = MMOItems.plugin.getStats().get(anchorId); // may be null

        for (Type type : MMOItems.plugin.getTypes().getAll()) {
            // Only consider types where our stat is compatible
            if (!stat.isCompatible(type)) continue;

            // Raw list in some builds; treat as raw to avoid generics issues
            List list = type.getAvailableStats();

            // If the anchor is missing OR not compatible OR not present in this type's list,
            // do nothing — keep the current stat position for this type.
            boolean anchorValid = anchor != null && anchor.isCompatible(type) && list.contains(anchor);
            if (!anchorValid) continue;

            int curIdx = list.indexOf(stat);

            // If stat is not currently in the list for this type, only insert when anchor is valid/present.
            if (curIdx < 0) {
                int anchorIdx = list.indexOf(anchor);
                int target = Math.min(Math.max(anchorIdx, 0), list.size());
                list.add(target, stat);
                continue;
            }

            // If already immediately before anchor, no change needed.
            // (Optional fast-path: skip churn when already in the right spot)
            int anchorIdxBefore = list.indexOf(anchor);
            if (curIdx == anchorIdxBefore - 1) continue;

            // Move: remove from current position, recompute anchor index, then insert before it.
            list.remove(curIdx);
            int anchorIdx = list.indexOf(anchor); // recompute after removal
            int target = Math.min(Math.max(anchorIdx, 0), list.size());
            list.add(target, stat);
        }
    }
}
