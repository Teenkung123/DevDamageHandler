package com.Teenkung.devDamageHandler.Handlers;


import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamageType;

import org.bukkit.entity.Player;



/**
 * Applies player defense stats to incoming damage.
 * Used when mobs attack players (since MythicLib's defense only works for PlayerAttackEvent).
 * 
 * Element Stats:
 * - {ELEMENT}_DEFENSE: flat defense (uses formula)
 * - {ELEMENT}_DEFENSE_PERCENT: percentage reduction
 * - {ELEMENT}_WEAKNESS: percentage INCREASED damage taken
 * 
 * Generic Stats:
 * - DEFENSE: flat defense formula
 * - DAMAGE_REDUCTION: percentage reduction
 * 
 * Context Stats:
 * - PVE_DAMAGE_REDUCTION: reduces mob damage
 * 
 * Type Stats:
 * - PHYSICAL_DAMAGE_REDUCTION, MAGIC_DAMAGE_REDUCTION, PROJECTILE_DAMAGE_REDUCTION
 */
public class PlayerDefenseApplicator {
    
    private static final double EPS = 1e-6;
    
    /**
     * Apply player defense stats to the damage.
     * Modifies the DamageMetadata in place.
     * 
     * @param player The player being attacked
     * @param dmg The damage metadata to modify
     * @param mobConfig The mob's element config (for element/type-specific defense)
     * @param originalDamage The original damage BEFORE any other modifiers were applied
     * @param config The defense formula config
     * @param isPvp Whether the attacker is a player (PVP context)
     * @param report DebugReport to collect data into (null if debug disabled)
     * @return DefenseResult with details of what was applied
     */
    public static DefenseResult applyDefense(Player player, DamageMetadata dmg, 
            MobElementConfig mobConfig, double originalDamage, DamageConfig config,
            boolean isPvp, DebugReport report) {
        
        MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
        if (data == null) {
            return DefenseResult.NONE;
        }
        
        double baseDamage = originalDamage;
        if (baseDamage <= EPS) {
            return DefenseResult.NONE;
        }

        // Gather all defense stats
        DefenseStats stats = gatherDefenseStats(data, mobConfig, isPvp);
        DamageConfig defenseConfig = config != null ? config : new DamageConfig();

        // Calculate multipliers
        MultiplierResult multipliers = calculateMultipliers(stats, baseDamage, defenseConfig);

        // Apply to damage metadata
        if (Math.abs(multipliers.total - 1.0) > EPS) {
            dmg.multiplicativeModifier(multipliers.total);
        }

        double finalDamage = baseDamage * multipliers.total;

        // Collect debug data
        if (report != null) {
            report.setPlayerDefense(stats, multipliers, baseDamage, finalDamage);
        }

        return new DefenseResult(multipliers.total, baseDamage, finalDamage);
    }

    // --- Helper records and methods ---

    static record DefenseStats(
        double defense,
        double damageReduction,
        String contextReductionId,
        double contextReduction,
        String elementId,
        double elementDefense,
        double elementDefensePercent,
        double elementWeakness,
        String typeId,
        double typeReduction
    ) {}

    static record MultiplierResult(
        double weakness,
        double defense,
        double reduction,
        double total
    ) {}

    private static DefenseStats gatherDefenseStats(MMOPlayerData data, MobElementConfig mobConfig, boolean isPvp) {
        double defense = data.getStatMap().getStat("DEFENSE");
        double damageReduction = data.getStatMap().getStat("DAMAGE_REDUCTION");
        String contextReductionId = isPvp ? "PVP_DAMAGE_REDUCTION" : "PVE_DAMAGE_REDUCTION";
        double contextReduction = data.getStatMap().getStat(contextReductionId);
        
        // Element-specific stats
        String elementId = null;
        double elementDefense = 0;
        double elementDefensePercent = 0;
        double elementWeakness = 0;

        if (mobConfig != null && mobConfig.getPrimaryElement() != null) {
            elementId = mobConfig.getPrimaryElement().getId().toUpperCase();
            elementDefense = data.getStatMap().getStat(elementId + "_DEFENSE");
            elementDefensePercent = data.getStatMap().getStat(elementId + "_DEFENSE_PERCENT");
            elementWeakness = data.getStatMap().getStat(elementId + "_WEAKNESS");
        }
        
        // Type-specific reductions
        String typeId = null;
        double typeReduction = 0;

        if (mobConfig != null && mobConfig.getPrimaryType() != null) {
            DamageType primaryType = mobConfig.getPrimaryType();
            typeId = primaryType.name();
            typeReduction = getTypeReduction(data, primaryType);
        }

        return new DefenseStats(defense, damageReduction, contextReductionId, contextReduction,
                               elementId, elementDefense, elementDefensePercent, elementWeakness,
                               typeId, typeReduction);
    }

    private static double getTypeReduction(MMOPlayerData data, DamageType type) {
        return switch (type) {
            case PHYSICAL -> data.getStatMap().getStat("PHYSICAL_DAMAGE_REDUCTION");
            case MAGIC -> data.getStatMap().getStat("MAGIC_DAMAGE_REDUCTION");
            case PROJECTILE -> data.getStatMap().getStat("PROJECTILE_DAMAGE_REDUCTION");
            default -> 0;
        };
    }

    private static MultiplierResult calculateMultipliers(DefenseStats stats, double baseDamage, DamageConfig config) {
        // 1. Apply WEAKNESS first (increases damage taken)
        double weaknessMultiplier = 1.0;
        if (stats.elementWeakness > EPS) {
            weaknessMultiplier = config.calculateWeaknessMultiplier(stats.elementWeakness);
        }
        
        // 2. Apply flat defense formula from config to (DEFENSE + {ELEMENT}_DEFENSE)
        double totalDefense = stats.defense + stats.elementDefense;
        double defenseMultiplier = 1.0;
        double afterWeakness = baseDamage * weaknessMultiplier;
        if (totalDefense > EPS && afterWeakness > EPS) {
            double afterFlatDefense = config.applyFlatDefense(afterWeakness, totalDefense);
            defenseMultiplier = afterFlatDefense / afterWeakness;
            defenseMultiplier = Math.max(0.0, Math.min(1.0, defenseMultiplier));
        }
        
        // 3. Apply percentage-based reductions (geometric stacking)
        double reductionMultiplier = calculateReductionMultiplier(stats, config);

        // Combined multiplier
        double totalMultiplier = weaknessMultiplier * defenseMultiplier * reductionMultiplier;
        totalMultiplier = Math.max(0.01, totalMultiplier); // Minimum 1% damage
        
        return new MultiplierResult(weaknessMultiplier, defenseMultiplier, reductionMultiplier, totalMultiplier);
    }

    private static double calculateReductionMultiplier(DefenseStats stats, DamageConfig config) {
        double multiplier = 1.0;

        if (stats.damageReduction > EPS) {
            multiplier *= config.calculatePercentDefenseMultiplier(stats.damageReduction);
        }
        if (stats.elementDefensePercent > EPS) {
            multiplier *= config.calculatePercentDefenseMultiplier(stats.elementDefensePercent);
        }
        if (stats.contextReduction > EPS) {
            multiplier *= config.calculatePercentDefenseMultiplier(stats.contextReduction);
        }
        if (stats.typeReduction > EPS) {
            multiplier *= config.calculatePercentDefenseMultiplier(stats.typeReduction);
        }

        return multiplier;
    }



    
    /**
     * Result of defense application for tracking/display.
     */
    public record DefenseResult(
        double totalMultiplier,
        double baseDamage,
        double finalDamage
    ) {
        public static final DefenseResult NONE = new DefenseResult(1.0, 0, 0);
    }
}
