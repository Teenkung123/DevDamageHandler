package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.Util.Msg;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;

import java.util.*;

import static com.Teenkung.devDamageHandler.Handlers.DamageDebug.fmt;

/**
 * Handles the calculation of damage modifiers.
 * Now pure calculation for elements/crits (no longer modifies DamageMetadata directly for those).
 */
public class DamageModifierApplicator {
    
    private static final double EPS = 1e-6;
    
    private final boolean debug;
    private final java.util.function.Function<String, Double> statProvider;
    private final org.bukkit.command.CommandSender debugSender;
    private final DamageMetadata dmg;
    private final Map<String, Double> mmMods;
    
    public DamageModifierApplicator(java.util.function.Function<String, Double> statProvider, 
                                   org.bukkit.command.CommandSender debugSender,
                                   DamageMetadata dmg, 
                                   Map<String, Double> mmMods, boolean debug) {
        this.statProvider = statProvider;
        this.debugSender = debugSender;
        this.dmg = dmg;
        this.mmMods = mmMods;
        this.debug = debug;
    }
    
    /**
     * Apply TYPE_* modifiers to damage packets.
     * Iterates packets directly to ensure correct application.
     * @return Map of type multipliers that were applied
     */
    public Map<DamageType, Double> applyTypeModifiers() {
        Map<DamageType, Double> typeMultipliers = new EnumMap<>(DamageType.class);
        Set<DamageType> allTypes = dmg.collectTypes();
        
        // Pre-calculate multipliers for all present types
        for (DamageType type : allTypes) {
            double typeMul = DamageMechanics.lookupTypeMultiplier(mmMods, type);
            typeMultipliers.put(type, typeMul);
            
            if (debug) {
                String color = typeMul < 1.0 ? "<green>" : (typeMul > 1.0 ? "<red>" : "<white>");
                String colorEnd = typeMul < 1.0 ? "</green>" : (typeMul > 1.0 ? "</red>" : "</white>");
                String result = typeMul < 0.01 ? "IMMUNE" : "×" + fmt(typeMul);
                Msg.send(debugSender, 
                    "<gray>TYPE_</gray><white>" + type + "</white><gray>:</gray> " + color + result + colorEnd);
            }
        }
        
        // Apply to packets directly
        for (DamagePacket packet : dmg.getPackets()) {
            double packetMul = 1.0;
            boolean modified = false;
            
            for (DamageType type : packet.getTypes()) {
                Double mul = typeMultipliers.get(type);
                if (mul != null) {
                    if (Math.abs(mul) < EPS) {
                        packetMul = 0;
                        modified = true;
                        break; // Immune
                    }
                    if (Math.abs(mul - 1.0) > EPS) {
                        packetMul *= mul;
                        modified = true;
                    }
                }
            }
            
            if (modified) {
                packet.setValue(packet.getValue() * packetMul);
            }
        }
        
        return typeMultipliers;
    }
    
    /**
     * Apply ELEMENT_* modifiers to elemental damage.
     * 
     * MMOItems Element Offense Stats (from attacker):
     * - {ELEMENT}_DAMAGE: flat damage bonus for that element
     * - {ELEMENT}_DAMAGE_PERCENT: percentage multiplier for that element's damage
     * - ADDITIONAL_ELEMENTAL_DAMAGE: generic bonus to all elemental damage
     * 
     * @return Map of element multipliers and set of critted elements
     */
    public ElementModifierResult applyElementModifiers(Map<Element, Double> elemRaw) {
        Map<Element, Double> totalMultipliers = new HashMap<>();
        Map<Element, Double> defenseMultipliers = new HashMap<>();
        Set<Element> elementCrits = new HashSet<>();
        Set<Element> immuneElements = new HashSet<>();
        
        // 1. Calculate Multipliers per Element
        for (Element el : elemRaw.keySet()) {
            String elId = el.getId().toUpperCase();
            
            // Get element-specific multiplier from MythicMob DamageModifiers (victim's defense)
            double elemMul = DamageMechanics.lookupElementMultiplier(mmMods, elId);
            defenseMultipliers.put(el, elemMul);
            
            // Handle immunity
            if (Math.abs(elemMul) < EPS) {
                immuneElements.add(el);
            }
            
            // Get attacker's offense stats for this element
            // {ELEMENT}_DAMAGE_PERCENT: percentage multiplier bonus
            double elemDamagePercent = statProvider.apply(elId + "_DAMAGE_PERCENT");
            
            // ADDITIONAL_ELEMENTAL_DAMAGE: generic bonus to all elements
            double bonusElem = statProvider.apply("ADDITIONAL_ELEMENTAL_DAMAGE");
            
            // Combine percentage bonuses only
            double percentMul = 1.0 + (elemDamagePercent / 100.0);
            double bonusMul = 1.0 + (bonusElem / 100.0);
            
            double combinedOffenseMul = percentMul * bonusMul;
            double finalMul = elemMul * combinedOffenseMul;
            
            totalMultipliers.put(el, finalMul);

            if (debug) {
                StringBuilder sb = new StringBuilder();
                sb.append("<gray>┌─ </gray><aqua>").append(elId).append("</aqua><gray> ─┐</gray>\n");
                sb.append("<gray>│</gray> <yellow>Base:</yellow> <white>").append(fmt(elemRaw.get(el))).append("</white>\n");
                
                // Show defense multiplier with color
                String defColor = elemMul < 1.0 ? "<green>" : (elemMul > 1.0 ? "<red>" : "<white>");
                String defEnd = elemMul < 1.0 ? "</green>" : (elemMul > 1.0 ? "</red>" : "</white>");
                double defReduction = (1.0 - elemMul) * 100;
                sb.append("<gray>│</gray> <yellow>Def Mul:</yellow> ").append(defColor).append("×").append(fmt(elemMul));
                if (Math.abs(defReduction) > EPS) {
                    sb.append(" (").append(defReduction > 0 ? "-" : "+").append(fmt(Math.abs(defReduction))).append("%)");
                }
                sb.append(defEnd).append("\n");
                
                // Show attacker offense bonuses (only percentage-based stats)
                if (elemDamagePercent > EPS || bonusElem > EPS) {
                    sb.append("<gray>│</gray> <yellow>Offense:</yellow>");
                    if (elemDamagePercent > EPS) {
                        sb.append(" <white>+").append(fmt(elemDamagePercent)).append("% ").append(elId).append("</white>");
                    }
                    if (bonusElem > EPS) {
                        sb.append(" <white>+").append(fmt(bonusElem)).append("% ALL</white>");
                    }
                    sb.append("\n");
                }
                
                // Show final calculated damage
                double finalElemDmg = elemRaw.get(el) * elemMul; // Note: indicator raw shows Defense effect
                sb.append("<gray>│</gray> <yellow>Final:</yellow> <green>").append(fmt(finalElemDmg)).append("</green>\n");
                sb.append("<gray>└────────────────────┘</gray>");
                
                Msg.send(debugSender, sb.toString());
            }
        }
        
        // 2. Apply multipliers to packets directly
        for (DamagePacket packet : dmg.getPackets()) {
            Element el = packet.getElement();
            if (el != null) {
                Double mul = totalMultipliers.get(el);
                if (mul != null) {
                     if (Math.abs(mul) < EPS) {
                         packet.setValue(0);
                     } else if (Math.abs(mul - 1.0) > EPS) {
                         packet.setValue(packet.getValue() * mul);
                     }
                }
            }
        }
        
        return new ElementModifierResult(totalMultipliers, defenseMultipliers, elementCrits, immuneElements);
    }

    /**
     * Get Attacker's Stat Modifiers for specific Damage Types.
     */
    public Map<DamageType, Double> getStatMultipliers(Set<DamageType> activeTypes) {
        Map<DamageType, Double> statMults = new EnumMap<>(DamageType.class);
        for (DamageType type : activeTypes) {
             // Stat format: SKILL_DAMAGE, PHYSICAL_DAMAGE (as per user report)
             // We also check ADDITIONAL_SKILL_DAMAGE as legacy fallback or for consistency with Element stats
             double val = statProvider.apply(type.name() + "_DAMAGE");
             if (val <= EPS) {
                 val = statProvider.apply("ADDITIONAL_" + type.name() + "_DAMAGE");
             }
             
             if (val > EPS) {
                 statMults.put(type, 1.0 + (val / 100.0));
                 if (debug) {
                     Msg.send(debugSender, "<gray>Stat Bonus <white>" + type + "</white>: +" + fmt(val) + "%</gray>");
                 }
             }
        }
        return statMults;
    }
    

    /**
     * Apply ELEMENT_NONE modifier to non-elemental damage.
     */
    public double applyNoneElementModifier(double nonElemDamage, Set<DamageType> allTypes) {
        double nonElemMul = 1.0;
        if (nonElemDamage > EPS) {
            nonElemMul = DamageMechanics.lookupElementMultiplier(mmMods, "NONE");
            
            if (debug) {
                Msg.send(debugSender, 
                    "<gray>ELEMENT_NONE: base=<white>" + fmt(nonElemDamage) + 
                    "</white> mul=<white>" + fmt(nonElemMul) + "</white>");
            }
        }
        return nonElemMul;
    }
    
    /**
     * Result holder for comprehensive crit calculation.
     */
    public record CritResult(
        Map<Element, Double> elementCritMults,
        double nonElemCritMul,
        Set<CritType> triggeredTypes,
        double totalCritPower,
        boolean isElemCrit,
        boolean isNonElemCrit
    ) {}
    
    /**
     * Crit types for tracking which crits triggered.
     */
    public enum CritType {
        WEAPON,         // CRITICAL_STRIKE_*
        SKILL,          // SKILL_CRITICAL_STRIKE_*
        ELEMENTAL       // ELEMENTAL_CRITICAL_STRIKE_*
    }
    
    /**
     * Calculate all critical strikes.
     * - Weapon crits: apply to non-elemental damage (WEAPON type attacks)
     * - Skill crits: apply to non-elemental damage (SKILL type attacks)
     * - Elemental crits: apply to elemental damage
     * 
     * When multiple crit types trigger, their powers ADD together.
     */
    public CritResult calculateCrits(Map<Element, Double> elemRaw, double nonElemDamage, Set<DamageType> allTypes) {
        Map<Element, Double> elemCritMults = new HashMap<>();
        double nonElemCritMul = 1.0;
        Set<CritType> triggered = EnumSet.noneOf(CritType.class);
        boolean isElemCrit = false;
        boolean isNonElemCrit = false;
        
        // Collect stats
        double weaponCritChance = statProvider.apply("CRITICAL_STRIKE_CHANCE");
        double weaponCritPower = statProvider.apply("CRITICAL_STRIKE_POWER");
        
        double skillCritChance = statProvider.apply("SKILL_CRITICAL_STRIKE_CHANCE");
        double skillCritPower = statProvider.apply("SKILL_CRITICAL_STRIKE_POWER");
        
        double elemCritChance = statProvider.apply("ELEMENTAL_CRITICAL_STRIKE_CHANCE");
        double elemCritPower = statProvider.apply("ELEMENTAL_CRITICAL_STRIKE_POWER");
        
        // Track total accumulated power for non-elemental
        double accumulatedPower = 0;
        
        // 1. WEAPON CRIT - applies to WEAPON/PHYSICAL type attacks
        boolean hasWeaponType = allTypes.contains(DamageType.WEAPON) || allTypes.contains(DamageType.PHYSICAL);
        if (hasWeaponType && nonElemDamage > EPS && weaponCritChance > 0) {
            if (DamageMechanics.rollCrit(weaponCritChance)) {
                triggered.add(CritType.WEAPON);
                accumulatedPower += weaponCritPower;
                isNonElemCrit = true;
                
                if (debug) {
                    Msg.send(debugSender, 
                        "<gold>⚔ WEAPON CRIT!</gold> <gray>+</gray><white>" + fmt(weaponCritPower) + "% power</white>");
                }
            }
        }
        
        // 2. SKILL CRIT - applies to SKILL type attacks
        boolean hasSkillType = allTypes.contains(DamageType.SKILL);
        if (hasSkillType && nonElemDamage > EPS && skillCritChance > 0) {
            if (DamageMechanics.rollCrit(skillCritChance)) {
                triggered.add(CritType.SKILL);
                accumulatedPower += skillCritPower;
                isNonElemCrit = true;
                
                if (debug) {
                    Msg.send(debugSender, 
                        "<gold>★ SKILL CRIT!</gold> <gray>+</gray><white>" + fmt(skillCritPower) + "% power</white>");
                }
            }
        }
        
        // Calculate non-elemental crit multiplier from accumulated power
        if (accumulatedPower > 0) {
            nonElemCritMul = 1.0 + (accumulatedPower / 100.0);
            
            if (debug && triggered.size() > 1) {
                Msg.send(debugSender, 
                    "<aqua>⚡ STACKED CRIT!</aqua> <white>Total power: " + fmt(accumulatedPower) + 
                    "% = x" + fmt(nonElemCritMul) + "</white>");
            }
        }
        
        // 3. ELEMENTAL CRIT - applies to each element separately
        // Also stacks with skill crit power if skill type is present
        for (Element el : elemRaw.keySet()) {
            double elemAccumulatedPower = 0;
            Set<CritType> elemTriggered = EnumSet.noneOf(CritType.class);
            
            // Roll elemental crit (only if dealing actual damage)
            if (elemRaw.get(el) > EPS && elemCritChance > 0 && DamageMechanics.rollCrit(elemCritChance)) {
                elemTriggered.add(CritType.ELEMENTAL);
                elemAccumulatedPower += elemCritPower;
                triggered.add(CritType.ELEMENTAL);
                isElemCrit = true;
                
                if (debug) {
                    Msg.send(debugSender, 
                        "<gold>✦ ELEMENTAL CRIT!</gold> <aqua>" + el.getId() + 
                        "</aqua> <gray>+</gray><white>" + fmt(elemCritPower) + "% power</white>");
                }
            }
            
            // If skill type is present and skill crit triggered, add skill power to elemental too
            if (hasSkillType && triggered.contains(CritType.SKILL)) {
                elemAccumulatedPower += skillCritPower;
                elemTriggered.add(CritType.SKILL);
                
                if (debug && elemTriggered.contains(CritType.ELEMENTAL)) {
                    Msg.send(debugSender, 
                        "<aqua>⚡ STACKED!</aqua> <white>SKILL + ELEMENTAL: +" + 
                        fmt(skillCritPower) + "% added</white>");
                }
            }
            
            if (elemAccumulatedPower > 0) {
                double elemCritMul = 1.0 + (elemAccumulatedPower / 100.0);
                elemCritMults.put(el, elemCritMul);
                
                if (debug && elemTriggered.size() > 1) {
                    Msg.send(debugSender, 
                        "<aqua>" + el.getId() + " Total:</aqua> <white>x" + fmt(elemCritMul) + "</white>");
                }
            }
        }
        
        // Calculate total power for result
        double totalPower = 0;
        if (isNonElemCrit) totalPower = accumulatedPower;
        // For elemental, average power if multiple elements critted
        if (!elemCritMults.isEmpty()) {
            double avgElemPower = elemCritMults.values().stream()
                .mapToDouble(m -> (m - 1.0) * 100)
                .average()
                .orElse(0);
            totalPower = Math.max(totalPower, avgElemPower);
        }
        
        return new CritResult(elemCritMults, nonElemCritMul, triggered, totalPower, isElemCrit, isNonElemCrit);
    }
    
    /**
     * Legacy method for elemental crits - delegates to new system.
     */
    public Map<Element, Double> applyElementalCrits(Map<Element, Double> elemRaw) {
        CritResult result = calculateCrits(elemRaw, 0, EnumSet.noneOf(DamageType.class));
        return result.elementCritMults();
    }
    
    /**
     * Legacy method for non-elemental crits - delegates to new system.
     */
    public double applyNonElementalCrit(double nonElemDamage, double nonElemMul, Set<DamageType> allTypes) {
        CritResult result = calculateCrits(Map.of(), nonElemDamage, allTypes);
        return result.nonElemCritMul();
    }

    public record ElementModifierResult(
        Map<Element, Double> totalMultipliers,
        Map<Element, Double> defenseMultipliers,
        Set<Element> crittedElements,
        Set<Element> immuneElements
    ) {
        public Map<Element, Double> multipliers() { return totalMultipliers; }
        public boolean hasImmunity() { return !immuneElements.isEmpty(); }
    }
}
