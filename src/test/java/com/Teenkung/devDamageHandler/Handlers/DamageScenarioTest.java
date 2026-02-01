package com.Teenkung.devDamageHandler.Handlers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests for complete damage scenarios.
 * Tests end-to-end damage calculation with multiple modifiers.
 */
class DamageScenarioTest {

    private static final double EPS = 1e-6;

    /**
     * Complete damage pipeline simulator.
     */
    static class DamageScenario {
        // Base damage
        private double baseDamage = 100;
        private String damageElement = null; // null = non-elemental
        private Set<String> damageTypes = new HashSet<>();
        
        // Attacker stats
        private double weaponCritChance = 0;
        private double weaponCritPower = 100;
        private double skillCritChance = 0;
        private double skillCritPower = 100;
        private double elemCritChance = 0;
        private double elemCritPower = 100;
        
        // Victim modifiers
        private Map<String, Double> victimMods = new HashMap<>();
        
        // Results
        private boolean critTriggered = false;
        
        public DamageScenario setBaseDamage(double val) { baseDamage = val; return this; }
        public DamageScenario setElement(String el) { damageElement = el; return this; }
        public DamageScenario addType(String type) { damageTypes.add(type); return this; }
        
        public DamageScenario setWeaponCrit(double chance, double power) {
            weaponCritChance = chance; weaponCritPower = power; return this;
        }
        public DamageScenario setSkillCrit(double chance, double power) {
            skillCritChance = chance; skillCritPower = power; return this;
        }
        public DamageScenario setElemCrit(double chance, double power) {
            elemCritChance = chance; elemCritPower = power; return this;
        }
        
        public DamageScenario setVictimMod(String key, double value) {
            victimMods.put(key, value); return this;
        }
        
        public DamageScenario forceCrit(boolean val) { critTriggered = val; return this; }
        
        /**
         * Calculate final damage.
         */
        public DamageResult calculate() {
            double damage = baseDamage;
            List<String> steps = new ArrayList<>();
            boolean isCrit = false;
            
            // 1. Apply element multiplier
            if (damageElement != null) {
                String key = "ELEMENT_" + damageElement.toUpperCase();
                double elemMul = victimMods.getOrDefault(key, 1.0);
                if (Math.abs(elemMul) < EPS) {
                    return new DamageResult(0, true, false, List.of("IMMUNE to " + damageElement));
                }
                damage *= elemMul;
                steps.add("Element " + damageElement + ": x" + elemMul);
            }
            
            // 2. Apply type multipliers
            for (String type : damageTypes) {
                String key = "TYPE_" + type.toUpperCase();
                double typeMul = victimMods.getOrDefault(key, 1.0);
                if (Math.abs(typeMul) < EPS) {
                    return new DamageResult(0, true, false, List.of("IMMUNE to " + type));
                }
                if (Math.abs(typeMul - 1.0) > EPS) {
                    damage *= typeMul;
                    steps.add("Type " + type + ": x" + typeMul);
                }
            }
            
            // 3. Apply crit if forced
            if (critTriggered) {
                double critPower = 0;
                if (damageElement != null && elemCritChance > 0) {
                    critPower = Math.max(critPower, elemCritPower);
                    isCrit = true;
                }
                if (damageTypes.contains("WEAPON") || damageTypes.contains("PHYSICAL")) {
                    if (weaponCritChance > 0) {
                        critPower = Math.max(critPower, weaponCritPower);
                        isCrit = true;
                    }
                }
                if (damageTypes.contains("SKILL")) {
                    if (skillCritChance > 0) {
                        critPower = Math.max(critPower, skillCritPower);
                        isCrit = true;
                    }
                }
                
                if (isCrit && critPower > 0) {
                    double critMul = 1.0 + (critPower / 100.0);
                    damage *= critMul;
                    steps.add("Crit: x" + critMul + " (power: " + critPower + "%)");
                }
            }
            
            return new DamageResult(damage, false, isCrit, steps);
        }
        
        public record DamageResult(
            double finalDamage,
            boolean immune,
            boolean crit,
            List<String> steps
        ) {}
    }

    @Nested
    @DisplayName("Basic Damage Scenarios")
    class BasicScenarios {
        
        @Test
        @DisplayName("Pure physical damage with no modifiers")
        void purePhysicalNoMods() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .addType("PHYSICAL");
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(100, result.finalDamage(), EPS);
            assertFalse(result.immune());
            assertFalse(result.crit());
        }
        
        @Test
        @DisplayName("Fire elemental damage with no modifiers")
        void fireElementalNoMods() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(80)
                .setElement("FIRE")
                .addType("MAGIC");
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(80, result.finalDamage(), EPS);
        }
        
        @Test
        @DisplayName("Weapon projectile damage")
        void weaponProjectile() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(50)
                .addType("WEAPON")
                .addType("PROJECTILE")
                .addType("PHYSICAL");
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(50, result.finalDamage(), EPS);
        }
    }

    @Nested
    @DisplayName("Element Immunity Scenarios")
    class ImmunityScenarios {
        
        @Test
        @DisplayName("Fire immunity blocks all fire damage")
        void fireImmunity() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .setElement("FIRE")
                .setVictimMod("ELEMENT_FIRE", 0.0);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(0, result.finalDamage(), EPS);
            assertTrue(result.immune());
        }
        
        @Test
        @DisplayName("Magic type immunity")
        void magicTypeImmunity() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .addType("MAGIC")
                .setVictimMod("TYPE_MAGIC", 0.0);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(0, result.finalDamage(), EPS);
            assertTrue(result.immune());
        }
    }

    @Nested
    @DisplayName("Resistance/Weakness Scenarios")
    class ResistanceWeaknessScenarios {
        
        @Test
        @DisplayName("50% fire resistance halves damage")
        void fireResistance() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .setElement("FIRE")
                .setVictimMod("ELEMENT_FIRE", 0.5);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(50, result.finalDamage(), EPS);
        }
        
        @Test
        @DisplayName("200% fire weakness doubles damage")
        void fireWeakness() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .setElement("FIRE")
                .setVictimMod("ELEMENT_FIRE", 2.0);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(200, result.finalDamage(), EPS);
        }
        
        @Test
        @DisplayName("Physical resistance with multiple types")
        void physicalResistance() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .addType("PHYSICAL")
                .addType("WEAPON")
                .setVictimMod("TYPE_PHYSICAL", 0.7);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(70, result.finalDamage(), EPS);
        }
    }

    @Nested
    @DisplayName("Critical Strike Scenarios")
    class CritScenarios {
        
        @Test
        @DisplayName("Weapon crit with 150% power")
        void weaponCrit() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .addType("WEAPON")
                .addType("PHYSICAL")
                .setWeaponCrit(100, 150)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(250, result.finalDamage(), EPS); // 100 * 2.5
            assertTrue(result.crit());
        }
        
        @Test
        @DisplayName("Skill crit with 200% power")
        void skillCrit() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .addType("SKILL")
                .setSkillCrit(100, 200)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(300, result.finalDamage(), EPS); // 100 * 3.0
            assertTrue(result.crit());
        }
        
        @Test
        @DisplayName("Elemental crit on fire damage")
        void elementalCrit() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .setElement("FIRE")
                .setElemCrit(100, 175)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(275, result.finalDamage(), EPS); // 100 * 2.75
            assertTrue(result.crit());
        }
    }

    @Nested
    @DisplayName("Combined Modifier Scenarios")
    class CombinedScenarios {
        
        @Test
        @DisplayName("Fire damage with resistance and crit")
        void fireResistanceAndCrit() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .setElement("FIRE")
                .setVictimMod("ELEMENT_FIRE", 0.5)
                .setElemCrit(100, 100)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            // 100 * 0.5 (resistance) * 2.0 (crit) = 100
            assertEquals(100, result.finalDamage(), EPS);
            assertTrue(result.crit());
        }
        
        @Test
        @DisplayName("Physical weapon with type resistance")
        void physicalWeaponResistance() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .addType("WEAPON")
                .addType("PHYSICAL")
                .setVictimMod("TYPE_PHYSICAL", 0.8)
                .setWeaponCrit(100, 50)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            // 100 * 0.8 (resistance) * 1.5 (crit) = 120
            assertEquals(120, result.finalDamage(), EPS);
        }
        
        @Test
        @DisplayName("Ice elemental with weakness and crit")
        void iceWeaknessAndCrit() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(50)
                .setElement("ICE")
                .setVictimMod("ELEMENT_ICE", 1.5) // 50% weakness
                .setElemCrit(100, 100)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            // 50 * 1.5 (weakness) * 2.0 (crit) = 150
            assertEquals(150, result.finalDamage(), EPS);
        }
    }

    @Nested
    @DisplayName("Edge Case Scenarios")
    class EdgeCaseScenarios {
        
        @Test
        @DisplayName("Zero base damage stays zero")
        void zeroDamage() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(0)
                .addType("PHYSICAL")
                .setWeaponCrit(100, 200)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertEquals(0, result.finalDamage(), EPS);
        }
        
        @Test
        @DisplayName("Very small damage values preserved")
        void smallDamage() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(0.001)
                .addType("PHYSICAL");
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            assertTrue(result.finalDamage() > 0);
            assertEquals(0.001, result.finalDamage(), 0.0001);
        }
        
        @Test
        @DisplayName("Very high damage values handled")
        void highDamage() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(10000000)
                .setElement("FIRE")
                .setVictimMod("ELEMENT_FIRE", 2.0)
                .setElemCrit(100, 500)
                .forceCrit(true);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            // 10M * 2.0 * 6.0 = 120M
            assertEquals(120000000, result.finalDamage(), 1);
        }
        
        @Test
        @DisplayName("Multiple type modifiers stack")
        void multipleTypeMods() {
            DamageScenario scenario = new DamageScenario()
                .setBaseDamage(100)
                .addType("PHYSICAL")
                .addType("WEAPON")
                .setVictimMod("TYPE_PHYSICAL", 0.8)
                .setVictimMod("TYPE_WEAPON", 0.5);
            
            DamageScenario.DamageResult result = scenario.calculate();
            
            // 100 * 0.8 * 0.5 = 40
            assertEquals(40, result.finalDamage(), EPS);
        }
    }
}
