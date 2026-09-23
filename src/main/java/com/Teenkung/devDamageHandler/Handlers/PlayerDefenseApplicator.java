package com.Teenkung.devDamageHandler.Handlers;


import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamageType;

import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;



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
            boolean isPvp, Set<DamageType> activeTypes, DebugReport report) {

        MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
        if (data == null) {
            return DefenseResult.NONE;
        }

        double baseDamage = originalDamage;
        if (baseDamage <= EPS) {
            return DefenseResult.NONE;
        }

        // Gather all defense stats
        DefenseStats stats = gatherDefenseStats(player, data, mobConfig, isPvp, config, activeTypes, baseDamage);
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
        double typeReduction,
        double armorEnchantReduction,  // % from Protection/Projectile Protection enchantments (EPF*4)
        double vanillaArmorMultiplier  // direct multiplier from vanilla armor formula (1.0 = no reduction)
    ) {}

    static record MultiplierResult(
        double weakness,
        double defense,
        double reduction,
        double total
    ) {}

    private static DefenseStats gatherDefenseStats(Player player, MMOPlayerData data,
            MobElementConfig mobConfig, boolean isPvp, DamageConfig config, Set<DamageType> activeTypes,
            double baseDamage) {
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

        // Armor enchantment protection (routes through DDH's percent-defense formula)
        double armorEnchantReduction = 0;
        if (config != null && config.isVanillaArmorEnchantmentsEnabled()) {
            boolean isProjectile = activeTypes != null && activeTypes.contains(DamageType.PROJECTILE);
            armorEnchantReduction = calculateArmorEnchantProtection(player, isProjectile);
        }

        // Vanilla armor points — replicate the vanilla formula exactly
        double vanillaArmorMultiplier = 1.0;
        if (config != null && config.isVanillaArmorPointsEnabled()) {
            vanillaArmorMultiplier = calculateVanillaArmorMultiplier(player, baseDamage);
        }

        return new DefenseStats(defense, damageReduction, contextReductionId, contextReduction,
                               elementId, elementDefense, elementDefensePercent, elementWeakness,
                               typeId, typeReduction, armorEnchantReduction, vanillaArmorMultiplier);
    }

    /**
     * Replicates vanilla's armor damage reduction formula.
     * effective_armor = max(armor/5, armor - damage/(2 + toughness/4))
     * reduction = min(20, effective_armor) / 25   (max 80%)
     *
     * @param player        The defending player
     * @param incomingDamage Damage before DDH defense (post-offensive-modifiers)
     * @return Damage multiplier (0.2 to 1.0; 1.0 = no armor)
     */
    public static double calculateVanillaArmorMultiplier(Player player, double incomingDamage) {
        var armorAttr = player.getAttribute(Attribute.GENERIC_ARMOR);
        var toughnessAttr = player.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS);
        if (armorAttr == null || toughnessAttr == null) return 1.0;

        double armor = armorAttr.getValue();
        double toughness = toughnessAttr.getValue();
        if (armor <= EPS) return 1.0;

        double effectiveArmor = Math.max(armor / 5.0, armor - incomingDamage / (2.0 + toughness / 4.0));
        double cappedArmor = Math.min(20.0, Math.max(0.0, effectiveArmor));
        return 1.0 - cappedArmor / 25.0;
    }

    /**
     * Calculate protection % from armor enchantments using vanilla's EPF formula.
     * Protection: 1 EPF/level. Projectile Protection: 2 EPF/level (only when isProjectile=true).
     * Total EPF capped at 20. Reduction = EPF * 4% (e.g. Prot IV × 4 pieces = 64%).
     * Broken armor pieces (vanilla damage >= max_damage) are skipped.
     */
    public static double calculateArmorEnchantProtection(Player player, boolean isProjectile) {
        int totalEPF = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null) continue;
            if (isItemBroken(piece)) continue;
            totalEPF += piece.getEnchantmentLevel(Enchantment.PROTECTION);
            if (isProjectile) {
                totalEPF += piece.getEnchantmentLevel(Enchantment.PROJECTILE_PROTECTION) * 2;
            }
        }
        totalEPF = Math.min(totalEPF, 20);  // vanilla cap
        return totalEPF * 4.0;              // EPF/25*100 = EPF*4%
    }

    /**
     * Returns true if the item is broken (vanilla damage >= max durability).
     * Unbreakable items and non-damageable items always return false.
     */
    private static boolean isItemBroken(ItemStack item) {
        if (item.getType().getMaxDurability() <= 0) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        if (damageable.isUnbreakable()) return false;
        return damageable.getDamage() >= item.getType().getMaxDurability();
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
        if (stats.armorEnchantReduction > EPS) {
            multiplier *= config.calculatePercentDefenseMultiplier(stats.armorEnchantReduction);
        }
        if (stats.vanillaArmorMultiplier < 1.0 - EPS) {
            multiplier *= stats.vanillaArmorMultiplier;
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
