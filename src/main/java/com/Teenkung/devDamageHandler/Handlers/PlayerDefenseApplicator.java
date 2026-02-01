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
        
        // Use original damage (passed in) rather than reading packets which may already be modified
        double baseDamage = originalDamage;
        
        if (baseDamage <= EPS) {
            return DefenseResult.NONE;
        }

        
        // Get base stats
        double defense = data.getStatMap().getStat("DEFENSE");
        double damageReduction = data.getStatMap().getStat("DAMAGE_REDUCTION");
        
        // PVE reduction (mob → player is always PvE)
        double pveReduction = data.getStatMap().getStat("PVE_DAMAGE_REDUCTION");
        
        // Get element-specific stats
        double elementDefense = 0;
        double elementDefensePercent = 0;
        double elementWeakness = 0;
        String elementId = null;
        
        if (mobConfig != null && mobConfig.getPrimaryElement() != null) {
            elementId = mobConfig.getPrimaryElement().getId().toUpperCase();
            elementDefense = data.getStatMap().getStat(elementId + "_DEFENSE");
            elementDefensePercent = data.getStatMap().getStat(elementId + "_DEFENSE_PERCENT");
            elementWeakness = data.getStatMap().getStat(elementId + "_WEAKNESS");
        }
        
        // Get type-specific reductions based on mob's damage type
        double typeReduction = 0;
        String typeId = null;
        
        if (mobConfig != null) {
            DamageType primaryType = mobConfig.getPrimaryType();
            if (primaryType != null) {
                typeId = primaryType.name();
                // Map DamageType to reduction stat
                typeReduction = switch (primaryType) {
                    case PHYSICAL -> data.getStatMap().getStat("PHYSICAL_DAMAGE_REDUCTION");
                    case MAGIC -> data.getStatMap().getStat("MAGIC_DAMAGE_REDUCTION");
                    case PROJECTILE -> data.getStatMap().getStat("PROJECTILE_DAMAGE_REDUCTION");
                    default -> 0;
                };
            }
        }
        
        // 1. Apply WEAKNESS first (increases damage taken)
        double weaknessMultiplier = 1.0;
        if (elementWeakness > EPS) {
            weaknessMultiplier = 1.0 + (elementWeakness / 100.0);
        }
        
        // 2. Apply DEFENSE formula: damage * (1 - (defense / (defense + 100)))
        double totalDefense = defense + elementDefense;
        double defenseMultiplier = 1.0;
        if (totalDefense > EPS) {
            defenseMultiplier = 1.0 - (totalDefense / (totalDefense + 100.0));
            defenseMultiplier = Math.max(0.0, Math.min(1.0, defenseMultiplier));
        }
        
        // 3. Apply percentage-based reductions (geometric stacking)
        // Order: DAMAGE_REDUCTION, {ELEMENT}_DEFENSE_PERCENT, PVE_DAMAGE_REDUCTION, {TYPE}_DAMAGE_REDUCTION
        double reductionMultiplier = 1.0;
        
        if (damageReduction > EPS) {
            reductionMultiplier *= (1.0 - Math.min(damageReduction, 99.0) / 100.0);
        }
        if (elementDefensePercent > EPS) {
            reductionMultiplier *= (1.0 - Math.min(elementDefensePercent, 99.0) / 100.0);
        }
        if (pveReduction > EPS) {
            reductionMultiplier *= (1.0 - Math.min(pveReduction, 99.0) / 100.0);
        }
        if (typeReduction > EPS) {
            reductionMultiplier *= (1.0 - Math.min(typeReduction, 99.0) / 100.0);
        }
        
        // Combined multiplier
        double totalMultiplier = weaknessMultiplier * defenseMultiplier * reductionMultiplier;
        totalMultiplier = Math.max(0.01, totalMultiplier); // Minimum 1% damage
        
        // Apply to damage metadata
        if (Math.abs(totalMultiplier - 1.0) > EPS) {
            dmg.multiplicativeModifier(totalMultiplier);
        }
        
        double finalDamage = baseDamage * totalMultiplier;
        
        // Debug output
        if (debug && debugRecipient != null) {
            printDefenseDebug(debugRecipient, 
                defense, damageReduction, pveReduction,
                elementId, elementDefense, elementDefensePercent, elementWeakness,
                typeId, typeReduction,
                weaknessMultiplier, defenseMultiplier, reductionMultiplier, totalMultiplier,
                baseDamage, finalDamage);
        }
        
        return new DefenseResult(totalMultiplier, baseDamage, finalDamage);
    }
    
    private static void printDefenseDebug(CommandSender recipient,
            double defense, double damageReduction, double pveReduction,
            String elementId, double elementDefense, double elementDefensePercent, double elementWeakness,
            String typeId, double typeReduction,
            double weaknessMultiplier, double defenseMultiplier, double reductionMultiplier, double totalMultiplier,
            double baseDamage, double finalDamage) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n<yellow>Player Defense Stats:</yellow>\n");
        
        // Weakness (if any)
        if (elementWeakness > EPS) {
            sb.append("  <red>").append(elementId).append("_WEAKNESS:</red> <white>+").append(fmt(elementWeakness)).append("%</white>");
            sb.append(" <gray>→ mul:</gray> <red>").append(fmt(weaknessMultiplier * 100)).append("%</red>\n");
        }
        
        // Defense (flat)
        sb.append("  <gray>DEFENSE:</gray> <white>").append(fmt(defense)).append("</white>");
        if (elementDefense > EPS) {
            sb.append(" <gray>+ ").append(elementId).append("_DEFENSE:</gray> <white>").append(fmt(elementDefense)).append("</white>");
        }
        sb.append(" <gray>→ mul:</gray> <aqua>").append(fmt(defenseMultiplier * 100)).append("%</aqua>\n");
        
        // Reductions (percent) - show all that are active
        sb.append("  <gray>Reductions:</gray>");
        boolean hasReductions = false;
        
        if (damageReduction > EPS) {
            sb.append(" <white>DR:").append(fmt(damageReduction)).append("%</white>");
            hasReductions = true;
        }
        if (elementDefensePercent > EPS) {
            sb.append(" <white>").append(elementId).append("_DEF%:").append(fmt(elementDefensePercent)).append("%</white>");
            hasReductions = true;
        }
        if (pveReduction > EPS) {
            sb.append(" <white>PVE:").append(fmt(pveReduction)).append("%</white>");
            hasReductions = true;
        }
        if (typeReduction > EPS) {
            sb.append(" <white>").append(typeId).append(":").append(fmt(typeReduction)).append("%</white>");
            hasReductions = true;
        }
        
        if (!hasReductions) {
            sb.append(" <gray>none</gray>");
        }
        sb.append(" <gray>→ mul:</gray> <aqua>").append(fmt(reductionMultiplier * 100)).append("%</aqua>\n");
        
        // Final
        sb.append("  <gray>Combined:</gray> <aqua>").append(fmt(totalMultiplier * 100)).append("%</aqua>\n");
        sb.append("  <gray>Damage:</gray> <white>").append(fmt(baseDamage)).append("</white> <gray>→</gray> <green>").append(fmt(finalDamage)).append("</green>");
        
        Msg.send(recipient, sb.toString());
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
