package com.Teenkung.devDamageHandler.Integration.LibReforge;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.api.stat.modifier.TemporaryStatModifier;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class StatTracking {

    private StatTracking() {}

    public enum Kind {
        NORMAL,
        TEMPORARY
    }

    /**
     * Track per player UUID to avoid issues if MMOPlayerData object identity changes.
     *
     * ConcurrentHashMap for safe access from different threads (libreforge tasks etc).
     * We still synchronize on each player's list when mutating it.
     */
    private static final Map<UUID, List<TrackedStat>> tracked = new ConcurrentHashMap<>();

    /**
     * Stores what we need to remove later.
     */
    private record TrackedStat(String statName, UUID modifierId, Kind kind) {}

    // ----- Add helpers -----

    public static void addStat(MMOPlayerData player, String statName, StatModifier modifier) {
        add(player, statName, modifier.getUniqueId(), Kind.NORMAL);
    }

    public static void addTempStat(MMOPlayerData player, String statName, TemporaryStatModifier modifier) {
        add(player, statName, modifier.getUniqueId(), Kind.TEMPORARY);
    }

    public static void add(MMOPlayerData player, String statName, UUID modifierId, Kind kind) {
        UUID uuid = player.getUniqueId();
        List<TrackedStat> list = tracked.computeIfAbsent(uuid, __ -> Collections.synchronizedList(new ArrayList<>()));

        // Optional: prevent duplicate entries (same stat + same id)
        synchronized (list) {
            for (TrackedStat ts : list) {
                if (ts.modifierId.equals(modifierId)) return;
            }
            list.add(new TrackedStat(statName, modifierId, kind));
        }
    }

    // ----- Remove helpers -----

    /**
     * Remove tracking entry by modifier UUID (useful for onDisable).
     */
    public static void removeStat(MMOPlayerData player, UUID modifierId) {
        UUID uuid = player.getUniqueId();
        List<TrackedStat> list = tracked.get(uuid);
        if (list == null) return;

        synchronized (list) {
            list.removeIf(ts -> ts.modifierId().equals(modifierId));
            if (list.isEmpty()) tracked.remove(uuid);
        }
    }

    /**
     * Works like calling "cleanup" for ALL tracked stats of this player:
     * - remove modifier from the correct stat instance (ONLY if still present)
     * - clear tracking
     *
     * This supports both NORMAL and TEMPORARY because removal is based on UUID in the StatInstance.
     * (Temporary unregister should be handled in the effect when you still have the modifier object.)
     */
    public static void removeAll(MMOPlayerData player) {
        UUID uuid = player.getUniqueId();
        List<TrackedStat> list = tracked.get(uuid);
        if (list == null || list.isEmpty()) return;

        // Snapshot to avoid concurrent modification while removing
        List<TrackedStat> snapshot;
        synchronized (list) {
            snapshot = new ArrayList<>(list);
        }

        for (TrackedStat ts : snapshot) {
            StatInstance instance = player.getStatMap().getInstance(ts.statName());
            if (instance == null) continue;

            // Idempotent: remove only if still present.
            if (instance.getModifier(ts.modifierId()) == null) continue;

            try {
                instance.removeModifier(ts.modifierId());
            } catch (IllegalArgumentException ignored) {
                // Covers edge cases like "modifier is not active" or already-removed races.
            } catch (Exception ignored) {
                // Don't ever crash cleanup.
            }
        }

        tracked.remove(uuid);
    }

    public static void removeAllPlayers() {
        // Iterate over a copy to avoid ConcurrentModification issues
        List<UUID> players = new ArrayList<>(tracked.keySet());
        for (UUID uuid : players) {
            MMOPlayerData pd = MMOPlayerData.get(uuid);
            if (pd != null) {
                removeAll(pd);
            } else {
                // If MMOPlayerData isn't available anymore, at least clear tracking
                tracked.remove(uuid);
            }
        }
    }

    // ----- Debug -----

    public static List<String> debugList(MMOPlayerData player) {
        List<TrackedStat> list = tracked.getOrDefault(player.getUniqueId(), Collections.emptyList());
        List<String> out;

        if (list instanceof RandomAccess) {
            out = new ArrayList<>(list.size());
        } else {
            out = new ArrayList<>();
        }

        if (list instanceof ObjectLists.SynchronizedList) {
            synchronized (list) {
                for (TrackedStat ts : list) {
                    out.add(ts.kind() + " | " + ts.statName() + " -> " + ts.modifierId());
                }
            }
        } else {
            for (TrackedStat ts : list) {
                out.add(ts.kind() + " | " + ts.statName() + " -> " + ts.modifierId());
            }
        }

        return out;
    }
}
