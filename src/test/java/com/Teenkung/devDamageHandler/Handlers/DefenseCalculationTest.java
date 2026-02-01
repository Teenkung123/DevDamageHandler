package com.Teenkung.devDamageHandler.Handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for defense calculation logic.
 * Tests defense formulas and stat interactions in isolation.
 */
class DefenseCalculationTest {

    private static final double EPS = 1e-6;

    /**
     * Simulates the defense calculation formula from PlayerDefenseApplicator.
     * This allows us to test the formula without needing Bukkit/MythicLib dependencies.
     */
    static class DefenseCalculator {
        private double defense = 0;
        private double damageReduction = 0;
        private double pveReduction = 0;
        private double physicalReduction = 0;
        private double magicReduction = 0;
        private double projectileReduction = 0;
        private double fireDefense = 0;
        private double fireDefensePercent = 0;
        private double fireWeakness = 0;
        
        public DefenseCalculator setDefense(double val) { defense = val; return this; }
        public DefenseCalculator setDamageReduction(double val) { damageReduction = val; return this; }
        public DefenseCalculator setPveReduction(double val) { pveReduction = val; return this; }
        public DefenseCalculator setPhysicalReduction(double val) { physicalReduction = val; return this; }
        public DefenseCalculator setMagicReduction(double val) { magicReduction = val; return this; }
        public DefenseCalculator setProjectileReduction(double val) { projectileReduction = val; return this; }
        public DefenseCalculator setFireDefense(double val) { fireDefense = val; return this; }
        public DefenseCalculator setFireDefensePercent(double val) { fireDefensePercent = val; return this; }
        public DefenseCalculator setFireWeakness(double val) { fireWeakness = val; return this; }

        /**
         * Calculate damage after applying all defense stats.
         * @param baseDamage The incoming damage
         * @param isFireElement Whether damage has FIRE element
         * @param isPhysical Whether damage is PHYSICAL type
         * @return The final damage after defenses
         */
        public double calculate(double baseDamage, boolean isFireElement, boolean isPhysical) {
            double damage = baseDamage;
            
            // 1. Apply weakness (increases damage)
            if (isFireElement && fireWeakness > 0) {
                damage *= (1.0 + fireWeakness / 100.0);
            }
            
            // 2. Apply flat defense
            double totalFlatDefense = defense;
            if (isFireElement) {
                totalFlatDefense += fireDefense;
            }
            
            if (totalFlatDefense > 0 && damage > 0) {
                damage *= (1.0 - totalFlatDefense / (totalFlatDefense + damage));
            }
            
            // 3. Apply percentage reductions (geometric stacking)
            List<Double> reductions = new ArrayList<>();
            if (damageReduction > 0) reductions.add(damageReduction);
            if (pveReduction > 0) reductions.add(pveReduction);
            if (isFireElement && fireDefensePercent > 0) reductions.add(fireDefensePercent);
            if (isPhysical && physicalReduction > 0) reductions.add(physicalReduction);
            
            for (double reduction : reductions) {
                double clamped = Math.min(100, Math.max(0, reduction));
                damage *= (1.0 - clamped / 100.0);
            }
            
            return Math.max(0, damage);
        }
    }

    @Nested
    @DisplayName("Defense Formula Tests")
    class DefenseFormulaTests {

        @Test
        @DisplayName("No defense = full damage")
        void noDefenseFullDamage() {
            DefenseCalculator calc = new DefenseCalculator();
            double result = calc.calculate(100, false, true);
            assertEquals(100, result, 0.01);
        }

        @Test
        @DisplayName("14.5 defense reduces 100 damage correctly")
        void standardDefense() {
            DefenseCalculator calc = new DefenseCalculator().setDefense(14.5);
            double result = calc.calculate(100, false, true);
            // 100 * (1 - 14.5/114.5) = 100 * 0.873 = 87.3
            assertEquals(87.3, result, 0.5);
        }

        @Test
        @DisplayName("Defense + element defense stack additively for flat portion")
        void defenseAndElementDefenseStack() {
            DefenseCalculator calc = new DefenseCalculator()
                .setDefense(14.5)
                .setFireDefense(10);
            double result = calc.calculate(100, true, true);
            // Total flat defense = 24.5
            // 100 * (1 - 24.5/124.5) = 100 * 0.803 = 80.3
            assertEquals(80.3, result, 0.5);
        }
    }

    @Nested
    @DisplayName("Weakness Tests")
    class WeaknessFormulaTests {

        @Test
        @DisplayName("10% weakness increases damage by 10%")
        void weaknessIncreasesDamage() {
            DefenseCalculator calc = new DefenseCalculator().setFireWeakness(10);
            double result = calc.calculate(100, true, true);
            assertEquals(110, result, 0.01);
        }

        @Test
        @DisplayName("Weakness is applied before defense")
        void weaknessAppliedBeforeDefense() {
            DefenseCalculator calc = new DefenseCalculator()
                .setFireWeakness(10)
                .setDefense(14.5);
            double result = calc.calculate(100, true, true);
            // 100 * 1.1 = 110 (after weakness)
            // 110 * (1 - 14.5/124.5) = 110 * 0.8835 = 97.2
            assertEquals(97.2, result, 0.5);
        }

        @Test
        @DisplayName("No weakness for non-matching element")
        void noWeaknessForWrongElement() {
            DefenseCalculator calc = new DefenseCalculator().setFireWeakness(10);
            double result = calc.calculate(100, false, true); // Not fire element
            assertEquals(100, result, 0.01);
        }
    }

    @Nested
    @DisplayName("Percentage Reduction Tests")
    class PercentageReductionTests {

        @Test
        @DisplayName("Single 10% damage reduction")
        void singleReduction() {
            DefenseCalculator calc = new DefenseCalculator().setDamageReduction(10);
            double result = calc.calculate(100, false, true);
            assertEquals(90, result, 0.01);
        }

        @Test
        @DisplayName("Multiple reductions stack geometrically")
        void multipleReductionsStack() {
            DefenseCalculator calc = new DefenseCalculator()
                .setDamageReduction(10)
                .setPveReduction(10)
                .setPhysicalReduction(10);
            double result = calc.calculate(100, false, true);
            // 0.9 * 0.9 * 0.9 = 0.729
            assertEquals(72.9, result, 0.1);
        }

        @Test
        @DisplayName("Four 10% reductions (user scenario)")
        void fourReductionsUserScenario() {
            DefenseCalculator calc = new DefenseCalculator()
                .setDamageReduction(10)
                .setPveReduction(10)
                .setFireDefensePercent(10)
                .setPhysicalReduction(10);
            double result = calc.calculate(100, true, true);
            // 0.9^4 = 0.6561
            assertEquals(65.61, result, 0.1);
        }

        @Test
        @DisplayName("Non-matching type reduction not applied")
        void nonMatchingTypeNotApplied() {
            DefenseCalculator calc = new DefenseCalculator()
                .setMagicReduction(50);  // Magic reduction
            double result = calc.calculate(100, false, true); // Physical damage
            assertEquals(100, result, 0.01); // No reduction
        }
    }

    @Nested
    @DisplayName("Full Defense Pipeline Tests")
    class FullPipelineTests {

        @Test
        @DisplayName("User's exact scenario: 100 damage with all defenses")
        void userExactScenario() {
            // Exact user stats:
            // DEFENSE: 14.5
            // DAMAGE_REDUCTION: 10%
            // FIRE_DEFENSE: 10
            // FIRE_DEFENSE_PERCENT: 10%
            // PVE_DAMAGE_REDUCTION: 10%
            // PHYSICAL_DAMAGE_REDUCTION: 10%
            // FIRE_WEAKNESS: 10%
            
            DefenseCalculator calc = new DefenseCalculator()
                .setDefense(14.5)
                .setDamageReduction(10)
                .setFireDefense(10)
                .setFireDefensePercent(10)
                .setPveReduction(10)
                .setPhysicalReduction(10)
                .setFireWeakness(10);
            
            double result = calc.calculate(100, true, true);
            
            // Manual calculation:
            // 1. Weakness: 100 * 1.1 = 110
            // 2. Flat defense (24.5): 110 * (1 - 24.5/134.5) = 110 * 0.818 = 90.0
            // 3. Reductions (4x 10%): 90 * 0.6561 = 59.0
            
            assertTrue(result > 55 && result < 65, 
                "Expected ~58-60 damage, got " + result);
        }

        @Test
        @DisplayName("High defense low base damage")
        void highDefenseLowDamage() {
            DefenseCalculator calc = new DefenseCalculator().setDefense(100);
            double result = calc.calculate(10, false, true);
            // 10 * (1 - 100/110) = 10 * 0.091 = 0.91
            assertTrue(result < 2, "Expected <2 damage, got " + result);
        }

        @Test
        @DisplayName("No element-specific reduction for non-element damage")
        void noElementReductionForNonElementDamage() {
            DefenseCalculator calc = new DefenseCalculator()
                .setFireDefense(50)
                .setFireDefensePercent(50)
                .setFireWeakness(100);
            double result = calc.calculate(100, false, true); // Not fire
            assertEquals(100, result, 0.01); // No modifiers applied
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Zero damage stays zero")
        void zeroDamageStaysZero() {
            DefenseCalculator calc = new DefenseCalculator()
                .setDefense(100)
                .setDamageReduction(50);
            double result = calc.calculate(0, true, true);
            assertEquals(0, result, EPS);
        }

        @Test
        @DisplayName("Negative defense is handled (could amplify damage)")
        void negativeDefense() {
            DefenseCalculator calc = new DefenseCalculator().setDefense(-10);
            double result = calc.calculate(100, false, true);
            // With negative defense, formula would give > 1.0 multiplier
            // We might want to clamp this in production
            // For now, just ensure it doesn't crash
            assertNotNull(result);
        }

        @Test
        @DisplayName("100% reduction gives zero damage")
        void hundredPercentReduction() {
            DefenseCalculator calc = new DefenseCalculator().setDamageReduction(100);
            double result = calc.calculate(100, false, true);
            assertEquals(0, result, EPS);
        }

        @Test
        @DisplayName("Over 100% reduction clamped to 100%")
        void overHundredPercentClamped() {
            DefenseCalculator calc = new DefenseCalculator().setDamageReduction(150);
            double result = calc.calculate(100, false, true);
            assertEquals(0, result, EPS); // Clamped to 100%
        }
    }
}
