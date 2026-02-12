package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.Util.Msg;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamageType;
import org.bukkit.command.CommandSender;
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
     * @param debug Whether to print debug info
     * @param debugRecipient Who to send debug info to
     * @return DefenseResult with details of what was applied
     */
    public static DefenseResult applyDefense(Player player, DamageMetadata dmg, 
            MobElementConfig mobConfig, double originalDamage, boolean debug, CommandSender debugRecipient) {
        
        MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
        if (data == null) {
            return DefenseResult.NONE;
        }
        
        double baseDamage = originalDamage;
        if (baseDamage <= EPS) {
            return DefenseResult.NONE;
        }

        // Gather all defense stats
        DefenseStats stats = gatherDefenseStats(data, mobConfig);

        // Calculate multipliers
        MultiplierResult multipliers = calculateMultipliers(stats);

        // Apply to damage metadata
        if (Math.abs(multipliers.total - 1.0) > EPS) {
            dmg.multiplicativeModifier(multipliers.total);
        }

        double finalDamage = baseDamage * multipliers.total;

        // Debug output
        if (debug && debugRecipient != null) {
            printDefenseDebug(debugRecipient, stats, multipliers, baseDamage, finalDamage);
        }

        return new DefenseResult(multipliers.total, baseDamage, finalDamage);
    }

    // --- Helper records and methods ---

    private record DefenseStats(
        double defense,
        double damageReduction,
        double pveReduction,
        String elementId,
        double elementDefense,
        double elementDefensePercent,
        double elementWeakness,
        String typeId,
        double typeReduction
    ) {}

    private record MultiplierResult(
        double weakness,
        double defense,
        double reduction,
        double total
    ) {}

    private static DefenseStats gatherDefenseStats(MMOPlayerData data, MobElementConfig mobConfig) {
        double defense = data.getStatMap().getStat("DEFENSE");
        double damageReduction = data.getStatMap().getStat("DAMAGE_REDUCTION");
        double pveReduction = data.getStatMap().getStat("PVE_DAMAGE_REDUCTION");
        
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

        return new DefenseStats(defense, damageReduction, pveReduction,
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

    private static MultiplierResult calculateMultipliers(DefenseStats stats) {
        // 1. Apply WEAKNESS first (increases damage taken)
        double weaknessMultiplier = 1.0;
        if (stats.elementWeakness > EPS) {
            weaknessMultiplier = 1.0 + (stats.elementWeakness / 100.0);
        }
        
        // 2. Apply DEFENSE formula: damage * (1 - (defense / (defense + 100)))
        double totalDefense = stats.defense + stats.elementDefense;
        double defenseMultiplier = 1.0;
        if (totalDefense > EPS) {
            defenseMultiplier = 1.0 - (totalDefense / (totalDefense + 100.0));
            defenseMultiplier = Math.max(0.0, Math.min(1.0, defenseMultiplier));
        }
        
        // 3. Apply percentage-based reductions (geometric stacking)
        double reductionMultiplier = calculateReductionMultiplier(stats);

        // Combined multiplier
        double totalMultiplier = weaknessMultiplier * defenseMultiplier * reductionMultiplier;
        totalMultiplier = Math.max(0.01, totalMultiplier); // Minimum 1% damage
        
        return new MultiplierResult(weaknessMultiplier, defenseMultiplier, reductionMultiplier, totalMultiplier);
    }

    private static double calculateReductionMultiplier(DefenseStats stats) {
        double multiplier = 1.0;

        if (stats.damageReduction > EPS) {
            multiplier *= (1.0 - Math.min(stats.damageReduction, 99.0) / 100.0);
        }
        if (stats.elementDefensePercent > EPS) {
            multiplier *= (1.0 - Math.min(stats.elementDefensePercent, 99.0) / 100.0);
        }
        if (stats.pveReduction > EPS) {
            multiplier *= (1.0 - Math.min(stats.pveReduction, 99.0) / 100.0);
        }
        if (stats.typeReduction > EPS) {
            multiplier *= (1.0 - Math.min(stats.typeReduction, 99.0) / 100.0);
        }

        return multiplier;
    }

    private static void printDefenseDebug(CommandSender recipient, DefenseStats stats,
                                          MultiplierResult multipliers, double baseDamage, double finalDamage) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n<yellow>Player Defense Stats:</yellow>\n");
        
        // Weakness (if any)
        if (stats.elementWeakness > EPS) {
            sb.append("  <red>").append(stats.elementId).append("_WEAKNESS:</red> <white>+")
              .append(fmt(stats.elementWeakness)).append("%</white>");
            sb.append(" <gray>→ mul:</gray> <red>").append(fmt(multipliers.weakness * 100)).append("%</red>\n");
        }
        
        // Defense (flat)
        sb.append("  <gray>DEFENSE:</gray> <white>").append(fmt(stats.defense)).append("</white>");
        if (stats.elementDefense > EPS) {
            sb.append(" <gray>+ ").append(stats.elementId).append("_DEFENSE:</gray> <white>")
              .append(fmt(stats.elementDefense)).append("</white>");
        }
        sb.append(" <gray>→ mul:</gray> <aqua>").append(fmt(multipliers.defense * 100)).append("%</aqua>\n");

        // Reductions (percent)
        appendReductionsDebug(sb, stats, multipliers);

        // Final
        sb.append("  <gray>Combined:</gray> <aqua>").append(fmt(multipliers.total * 100)).append("%</aqua>\n");
        sb.append("  <gray>Damage:</gray> <white>").append(fmt(baseDamage)).append("</white> <gray>→</gray> <green>")
          .append(fmt(finalDamage)).append("</green>");

        Msg.send(recipient, sb.toString());
    }

    private static void appendReductionsDebug(StringBuilder sb, DefenseStats stats, MultiplierResult multipliers) {
        sb.append("  <gray>Reductions:</gray>");
        boolean hasReductions = false;
        
        if (stats.damageReduction > EPS) {
            sb.append(" <white>DR:").append(fmt(stats.damageReduction)).append("%</white>");
            hasReductions = true;
        }
        if (stats.elementDefensePercent > EPS) {
            sb.append(" <white>").append(stats.elementId).append("_DEF%:")
              .append(fmt(stats.elementDefensePercent)).append("%</white>");
            hasReductions = true;
        }
        if (stats.pveReduction > EPS) {
            sb.append(" <white>PVE:").append(fmt(stats.pveReduction)).append("%</white>");
            hasReductions = true;
        }
        if (stats.typeReduction > EPS) {
            sb.append(" <white>").append(stats.typeId).append(":").append(fmt(stats.typeReduction)).append("%</white>");
            hasReductions = true;
        }
        
        if (!hasReductions) {
            sb.append(" <gray>none</gray>");
        }
        sb.append(" <gray>→ mul:</gray> <aqua>").append(fmt(multipliers.reduction * 100)).append("%</aqua>\n");
    }
    
    private static String fmt(double d) {
        return DamageDebug.fmt(d);
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
