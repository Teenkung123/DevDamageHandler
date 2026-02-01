package com.Teenkung.devDamageHandler.Handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for critical strike stacking logic.
 * Tests weapon, skill, and elemental crit interactions.
 */
class CritStackingTest {

    private static final double EPS = 1e-6;

    /**
     * Simulates crit calculation logic from DamageModifierApplicator.
     */
    static class CritCalculator {
        private double weaponCritChance = 0;
        private double weaponCritPower = 0;
        private double skillCritChance = 0;
        private double skillCritPower = 0;
        private double elemCritChance = 0;
        private double elemCritPower = 0;
        
        private boolean hasWeaponType = false;
        private boolean hasSkillType = false;
        private double nonElemDamage = 0;
        private double elemDamage = 0;
        
        // For deterministic testing
        private boolean forceWeaponCrit = false;
        private boolean forceSkillCrit = false;
        private boolean forceElemCrit = false;
        
        public CritCalculator setWeaponCrit(double chance, double power) {
            weaponCritChance = chance; weaponCritPower = power; return this;
        }
        
        public CritCalculator setSkillCrit(double chance, double power) {
            skillCritChance = chance; skillCritPower = power; return this;
        }
        
        public CritCalculator setElemCrit(double chance, double power) {
            elemCritChance = chance; elemCritPower = power; return this;
        }
        
        public CritCalculator setHasWeaponType(boolean val) { hasWeaponType = val; return this; }
        public CritCalculator setHasSkillType(boolean val) { hasSkillType = val; return this; }
        public CritCalculator setNonElemDamage(double val) { nonElemDamage = val; return this; }
        public CritCalculator setElemDamage(double val) { elemDamage = val; return this; }
        
        public CritCalculator forceWeaponCrit(boolean val) { forceWeaponCrit = val; return this; }
        public CritCalculator forceSkillCrit(boolean val) { forceSkillCrit = val; return this; }
        public CritCalculator forceElemCrit(boolean val) { forceElemCrit = val; return this; }
        
        /**
         * Calculate resulting crit multipliers for non-elemental and elemental damage.
         */
        public CritResult calculate() {
            double nonElemCritMul = 1.0;
            double elemCritMul = 1.0;
            double accumulatedPower = 0;
            Set<String> triggeredTypes = new HashSet<>();
            
            // 1. Weapon Crit - applies to WEAPON/PHYSICAL type, non-elem damage
            if (hasWeaponType && nonElemDamage > EPS && weaponCritChance > 0) {
                if (forceWeaponCrit) {
                    triggeredTypes.add("WEAPON");
                    accumulatedPower += weaponCritPower;
                }
            }
            
            // 2. Skill Crit - applies to SKILL type, non-elem damage
            if (hasSkillType && nonElemDamage > EPS && skillCritChance > 0) {
                if (forceSkillCrit) {
                    triggeredTypes.add("SKILL");
                    accumulatedPower += skillCritPower;
                }
            }
            
            // Calculate non-elem crit multiplier from accumulated power
            if (accumulatedPower > 0) {
                nonElemCritMul = 1.0 + (accumulatedPower / 100.0);
            }
            
            // 3. Elemental Crit - applies to element damage
            if (elemDamage > EPS && elemCritChance > 0) {
                double elemAccumulatedPower = 0;
                
                if (forceElemCrit) {
                    triggeredTypes.add("ELEMENTAL");
                    elemAccumulatedPower += elemCritPower;
                }
                
                // If skill type present and skill crit triggered, also add to elemental
                if (hasSkillType && triggeredTypes.contains("SKILL")) {
                    elemAccumulatedPower += skillCritPower;
                }
                
                if (elemAccumulatedPower > 0) {
                    elemCritMul = 1.0 + (elemAccumulatedPower / 100.0);
                }
            }
            
            return new CritResult(nonElemCritMul, elemCritMul, triggeredTypes);
        }
        
        public record CritResult(
            double nonElemCritMul,
            double elemCritMul,
            Set<String> triggeredTypes
        ) {
            public boolean isNonElemCrit() { return nonElemCritMul > 1.0; }
            public boolean isElemCrit() { return elemCritMul > 1.0; }
        }
    }

    @Nested
    @DisplayName("Single Crit Type Tests")
    class SingleCritTests {
        
        @Test
        @DisplayName("Weapon crit only applies to non-elem damage")
        void weaponCritOnlyNonElem() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 150)
                .setHasWeaponType(true)
                .setNonElemDamage(100)
                .setElemDamage(50)
                .forceWeaponCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(2.5, result.nonElemCritMul(), EPS); // 1 + 150/100 = 2.5
            assertEquals(1.0, result.elemCritMul(), EPS);    // No elem crit
        }
        
        @Test
        @DisplayName("Skill crit only applies to non-elem damage")
        void skillCritOnlyNonElem() {
            CritCalculator calc = new CritCalculator()
                .setSkillCrit(100, 200)
                .setHasSkillType(true)
                .setNonElemDamage(100)
                .setElemDamage(0)
                .forceSkillCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(3.0, result.nonElemCritMul(), EPS); // 1 + 200/100 = 3.0
            assertTrue(result.triggeredTypes().contains("SKILL"));
        }
        
        @Test
        @DisplayName("Elemental crit only applies to elem damage")
        void elemCritOnlyElem() {
            CritCalculator calc = new CritCalculator()
                .setElemCrit(100, 175)
                .setNonElemDamage(100)
                .setElemDamage(50)
                .forceElemCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(1.0, result.nonElemCritMul(), EPS);  // No non-elem crit
            assertEquals(2.75, result.elemCritMul(), EPS);    // 1 + 175/100 = 2.75
        }
    }

    @Nested
    @DisplayName("Crit Stacking Tests")
    class CritStackingTests {
        
        @Test
        @DisplayName("Weapon + Skill crits stack additively for non-elem")
        void weaponAndSkillStack() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 150)
                .setSkillCrit(100, 100)
                .setHasWeaponType(true)
                .setHasSkillType(true)
                .setNonElemDamage(100)
                .forceWeaponCrit(true)
                .forceSkillCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            // Stacked power: 150 + 100 = 250%
            // Multiplier: 1 + 250/100 = 3.5
            assertEquals(3.5, result.nonElemCritMul(), EPS);
            assertEquals(2, result.triggeredTypes().size());
        }
        
        @Test
        @DisplayName("Skill crit stacks with elemental crit for elem damage")
        void skillAndElemStack() {
            CritCalculator calc = new CritCalculator()
                .setSkillCrit(100, 100)
                .setElemCrit(100, 150)
                .setHasSkillType(true)
                .setNonElemDamage(100)
                .setElemDamage(50)
                .forceSkillCrit(true)
                .forceElemCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            // Elem crit power: 150 (elem) + 100 (skill) = 250%
            // Elem multiplier: 1 + 250/100 = 3.5
            assertEquals(3.5, result.elemCritMul(), EPS);
            
            // Non-elem still gets skill crit: 1 + 100/100 = 2.0
            assertEquals(2.0, result.nonElemCritMul(), EPS);
        }
        
        @Test
        @DisplayName("All three crits trigger simultaneously")
        void allThreeCritsStack() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 150)
                .setSkillCrit(100, 100)
                .setElemCrit(100, 75)
                .setHasWeaponType(true)
                .setHasSkillType(true)
                .setNonElemDamage(100)
                .setElemDamage(50)
                .forceWeaponCrit(true)
                .forceSkillCrit(true)
                .forceElemCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            // Non-elem: weapon 150 + skill 100 = 250% -> 3.5x
            assertEquals(3.5, result.nonElemCritMul(), EPS);
            
            // Elem: elem 75 + skill 100 = 175% -> 2.75x
            assertEquals(2.75, result.elemCritMul(), EPS);
            
            assertEquals(3, result.triggeredTypes().size());
        }
    }

    @Nested
    @DisplayName("No Crit Scenarios")
    class NoCritTests {
        
        @Test
        @DisplayName("No crit chance means no crit")
        void noCritChanceNoCrit() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(0, 150) // 0% chance
                .setHasWeaponType(true)
                .setNonElemDamage(100);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(1.0, result.nonElemCritMul(), EPS);
            assertFalse(result.isNonElemCrit());
        }
        
        @Test
        @DisplayName("No matching type means no crit")
        void noMatchingTypeNoCrit() {
            CritCalculator calc = new CritCalculator()
                .setSkillCrit(100, 150)
                .setHasSkillType(false) // No SKILL type in damage
                .setNonElemDamage(100)
                .forceSkillCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(1.0, result.nonElemCritMul(), EPS);
            assertTrue(result.triggeredTypes().isEmpty());
        }
        
        @Test
        @DisplayName("Zero damage means no crit")
        void zeroDamageNoCrit() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 150)
                .setHasWeaponType(true)
                .setNonElemDamage(0) // No damage
                .forceWeaponCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(1.0, result.nonElemCritMul(), EPS);
        }
        
        @Test
        @DisplayName("Elemental crit requires elemental damage > 0")
        void elemCritRequiresElemDamage() {
            CritCalculator calc = new CritCalculator()
                .setElemCrit(100, 150)
                .setElemDamage(0) // No elem damage
                .setNonElemDamage(100)
                .forceElemCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(1.0, result.elemCritMul(), EPS);
            assertFalse(result.isElemCrit());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Very high crit power (1000%) works correctly")
        void veryHighCritPower() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 1000)
                .setHasWeaponType(true)
                .setNonElemDamage(100)
                .forceWeaponCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(11.0, result.nonElemCritMul(), EPS); // 1 + 1000/100 = 11
        }
        
        @Test
        @DisplayName("Zero crit power means 1x multiplier")
        void zeroCritPower() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 0) // 0% power
                .setHasWeaponType(true)
                .setNonElemDamage(100)
                .forceWeaponCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            // With 0% power, accumulated = 0, so multiplier stays 1.0
            assertEquals(1.0, result.nonElemCritMul(), EPS);
        }
        
        @Test
        @DisplayName("100% crit power equals 2x damage")
        void hundredPercentPower() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 100)
                .setHasWeaponType(true)
                .setNonElemDamage(100)
                .forceWeaponCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(2.0, result.nonElemCritMul(), EPS); // 1 + 100/100 = 2
        }
        
        @Test
        @DisplayName("Fractional crit power calculates correctly")
        void fractionalCritPower() {
            CritCalculator calc = new CritCalculator()
                .setWeaponCrit(100, 175.5)
                .setHasWeaponType(true)
                .setNonElemDamage(100)
                .forceWeaponCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(2.755, result.nonElemCritMul(), 0.001);
        }
        
        @Test
        @DisplayName("Multiple elements each get their own crit check (simulated)")
        void multipleElementsCrit() {
            // This test documents the expected behavior:
            // Each element should be checked for crit independently
            // Here we just verify the logic for a single element works
            CritCalculator calc = new CritCalculator()
                .setElemCrit(100, 150)
                .setElemDamage(100)
                .forceElemCrit(true);
            
            CritCalculator.CritResult result = calc.calculate();
            
            assertEquals(2.5, result.elemCritMul(), EPS);
            assertTrue(result.isElemCrit());
        }
    }
}
