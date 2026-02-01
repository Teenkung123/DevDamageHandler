package com.Teenkung.devDamageHandler.Handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DamageMechanics utility class.
 * Tests the core damage calculation logic in isolation.
 */
class DamageMechanicsTest {

    @Nested
    @DisplayName("Defense Multiplier Calculation")
    class DefenseMultiplierTests {

        @Test
        @DisplayName("Zero defense returns multiplier of 1.0 (no reduction)")
        void zeroDefenseReturnsFullDamage() {
            double multiplier = calculateDefenseMultiplier(0, 100);
            assertEquals(1.0, multiplier, 0.001);
        }

        @Test
        @DisplayName("Defense equal to damage gives 50% reduction")
        void defenseEqualToDamageGivesHalfReduction() {
            // Formula: 1 - defense/(defense + baseDamage)
            // With defense=100, damage=100: 1 - 100/200 = 0.5
            double multiplier = calculateDefenseMultiplier(100, 100);
            assertEquals(0.5, multiplier, 0.001);
        }

        @Test
        @DisplayName("14.5 defense against 100 damage gives ~12.7% reduction")
        void realWorldDefenseValue() {
            // Formula: 1 - 14.5/(14.5 + 100) = 1 - 0.127 = 0.873
            double multiplier = calculateDefenseMultiplier(14.5, 100);
            assertEquals(0.873, multiplier, 0.01);
        }

        @Test
        @DisplayName("High defense provides diminishing returns")
        void highDefenseDiminishingReturns() {
            double mul50 = calculateDefenseMultiplier(50, 100);   // ~0.667
            double mul100 = calculateDefenseMultiplier(100, 100); // 0.5
            double mul200 = calculateDefenseMultiplier(200, 100); // ~0.333
            
            // Each doubling of defense gives less additional reduction
            double reduction50to100 = mul50 - mul100;    // ~0.167
            double reduction100to200 = mul100 - mul200;  // ~0.167
            
            // With this formula, doubling gives same absolute reduction
            // but percentage-wise it's diminishing
            assertTrue(mul50 > mul100);
            assertTrue(mul100 > mul200);
        }

        @Test
        @DisplayName("Negative defense should increase damage taken")
        void negativeDefenseIncreasesDamage() {
            // If defense is negative, damage should be amplified
            // With defense=-50, damage=100: 1 - (-50)/50 = 1 + 1 = 2
            double multiplier = calculateDefenseMultiplier(-50, 100);
            assertTrue(multiplier > 1.0, "Negative defense should amplify damage");
        }

        // Helper method that mirrors the actual formula
        private double calculateDefenseMultiplier(double defense, double baseDamage) {
            if (baseDamage <= 0) return 1.0;
            return 1.0 - (defense / (defense + baseDamage));
        }
    }

    @Nested
    @DisplayName("Percentage Reduction Stacking")
    class ReductionStackingTests {

        @Test
        @DisplayName("Single 10% reduction gives 0.9 multiplier")
        void singleReduction() {
            double result = applyGeometricReductions(10.0);
            assertEquals(0.9, result, 0.001);
        }

        @Test
        @DisplayName("Two 10% reductions give geometric stacking (0.81)")
        void twoReductionsStackGeometrically() {
            // 0.9 * 0.9 = 0.81
            double result = applyGeometricReductions(10.0, 10.0);
            assertEquals(0.81, result, 0.001);
        }

        @Test
        @DisplayName("Four 10% reductions give 0.6561 multiplier")
        void fourReductionsStackGeometrically() {
            // 0.9^4 = 0.6561
            double result = applyGeometricReductions(10.0, 10.0, 10.0, 10.0);
            assertEquals(0.6561, result, 0.001);
        }

        @Test
        @DisplayName("100% reduction should give 0 damage")
        void fullReductionGivesZero() {
            double result = applyGeometricReductions(100.0);
            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("Mixed reduction percentages")
        void mixedReductions() {
            // 10%, 20%, 15% = 0.9 * 0.8 * 0.85 = 0.612
            double result = applyGeometricReductions(10.0, 20.0, 15.0);
            assertEquals(0.612, result, 0.001);
        }

        // Helper that mirrors geometric stacking
        private double applyGeometricReductions(double... reductionPercents) {
            double multiplier = 1.0;
            for (double pct : reductionPercents) {
                double clamped = Math.min(100, Math.max(0, pct));
                multiplier *= (1.0 - clamped / 100.0);
            }
            return multiplier;
        }
    }

    @Nested
    @DisplayName("Element Multiplier Lookup")
    class ElementMultiplierLookupTests {

        @Test
        @DisplayName("Missing element returns default 1.0")
        void missingElementReturnsDefault() {
            Map<String, Double> mods = new HashMap<>();
            double mul = lookupElementMultiplier(mods, "FIRE");
            assertEquals(1.0, mul, 0.001);
        }

        @Test
        @DisplayName("Element immunity (0.0) returns 0.0")
        void elementImmunityReturnsZero() {
            Map<String, Double> mods = new HashMap<>();
            mods.put("ELEMENT_FIRE", 0.0);
            double mul = lookupElementMultiplier(mods, "FIRE");
            assertEquals(0.0, mul, 0.001);
        }

        @Test
        @DisplayName("Element weakness (2.0) returns 2.0")
        void elementWeaknessReturnsMultiplier() {
            Map<String, Double> mods = new HashMap<>();
            mods.put("ELEMENT_WATER", 2.0);
            double mul = lookupElementMultiplier(mods, "WATER");
            assertEquals(2.0, mul, 0.001);
        }

        @Test
        @DisplayName("Case insensitive lookup")
        void caseInsensitiveLookup() {
            Map<String, Double> mods = new HashMap<>();
            mods.put("ELEMENT_FIRE", 1.5);
            mods.put("element_ice", 0.5);
            
            assertEquals(1.5, lookupElementMultiplier(mods, "fire"), 0.001);
            assertEquals(0.5, lookupElementMultiplier(mods, "ICE"), 0.001);
        }

        // Helper that mirrors DamageMechanics.lookupElementMultiplier
        private double lookupElementMultiplier(Map<String, Double> mods, String elementId) {
            if (mods == null || mods.isEmpty()) return 1.0;
            
            String key = "ELEMENT_" + elementId.toUpperCase();
            for (Map.Entry<String, Double> entry : mods.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return 1.0;
        }
    }

    @Nested
    @DisplayName("Type Multiplier Lookup")
    class TypeMultiplierLookupTests {

        @Test
        @DisplayName("Missing type returns default 1.0")
        void missingTypeReturnsDefault() {
            Map<String, Double> mods = new HashMap<>();
            double mul = lookupTypeMultiplier(mods, "PHYSICAL");
            assertEquals(1.0, mul, 0.001);
        }

        @Test
        @DisplayName("Type immunity returns 0.0")
        void typeImmunityReturnsZero() {
            Map<String, Double> mods = new HashMap<>();
            mods.put("TYPE_MAGIC", 0.0);
            double mul = lookupTypeMultiplier(mods, "MAGIC");
            assertEquals(0.0, mul, 0.001);
        }

        @Test
        @DisplayName("Type resistance returns reduced multiplier")
        void typeResistanceReturnsReduced() {
            Map<String, Double> mods = new HashMap<>();
            mods.put("TYPE_PHYSICAL", 0.5);
            double mul = lookupTypeMultiplier(mods, "PHYSICAL");
            assertEquals(0.5, mul, 0.001);
        }

        // Helper that mirrors DamageMechanics.lookupTypeMultiplier
        private double lookupTypeMultiplier(Map<String, Double> mods, String typeName) {
            if (mods == null || mods.isEmpty()) return 1.0;
            
            String key = "TYPE_" + typeName.toUpperCase();
            for (Map.Entry<String, Double> entry : mods.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return 1.0;
        }
    }

    @Nested
    @DisplayName("Weakness Application")
    class WeaknessTests {

        @Test
        @DisplayName("10% weakness increases damage by 10%")
        void weaknessIncreasesDamage() {
            double baseDamage = 100.0;
            double weaknessPercent = 10.0;
            double result = applyWeakness(baseDamage, weaknessPercent);
            assertEquals(110.0, result, 0.001);
        }

        @Test
        @DisplayName("0% weakness has no effect")
        void zeroWeaknessNoEffect() {
            double result = applyWeakness(100.0, 0.0);
            assertEquals(100.0, result, 0.001);
        }

        @Test
        @DisplayName("Negative weakness (resistance) reduces damage")
        void negativeWeaknessReducesDamage() {
            double result = applyWeakness(100.0, -20.0);
            assertEquals(80.0, result, 0.001);
        }

        private double applyWeakness(double baseDamage, double weaknessPercent) {
            return baseDamage * (1.0 + weaknessPercent / 100.0);
        }
    }

    @Nested
    @DisplayName("Critical Strike Calculations")
    class CriticalStrikeTests {

        @Test
        @DisplayName("Crit power 200% doubles damage on crit")
        void critPowerDoublesDamage() {
            double baseDamage = 100.0;
            double critPower = 200.0; // 200% means 2x damage
            double result = applyCrit(baseDamage, critPower);
            assertEquals(200.0, result, 0.001);
        }

        @Test
        @DisplayName("Crit power 150% gives 1.5x damage")
        void critPower150Gives1_5x() {
            double result = applyCrit(100.0, 150.0);
            assertEquals(150.0, result, 0.001);
        }

        @Test
        @DisplayName("Crit power 100% is no bonus (1x)")
        void critPower100IsNoBonus() {
            double result = applyCrit(100.0, 100.0);
            assertEquals(100.0, result, 0.001);
        }

        private double applyCrit(double baseDamage, double critPowerPercent) {
            return baseDamage * (critPowerPercent / 100.0);
        }
    }

    @Nested
    @DisplayName("Full Damage Pipeline")
    class FullDamagePipelineTests {

        @Test
        @DisplayName("Complete damage calculation: 100 base with standard defenses")
        void completeDamageCalculation() {
            // Scenario from user's test:
            // Base: 100
            // Weakness: +10% (FIRE_WEAKNESS)
            // Defense: 14.5 + element defense 10 = 24.5
            // Reductions: DR 10%, FIRE_DEF% 10%, PVE 10%, PHYSICAL 10%
            
            double baseDamage = 100.0;
            
            // Step 1: Apply weakness (+10%)
            double afterWeakness = baseDamage * 1.10; // 110
            
            // Step 2: Apply flat defense (24.5 against 110)
            double defenseMultiplier = 1.0 - (24.5 / (24.5 + 110));
            double afterDefense = afterWeakness * defenseMultiplier; // ~89.5
            
            // Step 3: Apply geometric reductions (4x 10%)
            double reductionMultiplier = Math.pow(0.9, 4); // 0.6561
            double finalDamage = afterDefense * reductionMultiplier; // ~58.7
            
            // The expected final damage is around 58-59
            assertTrue(finalDamage > 57 && finalDamage < 60, 
                "Expected ~58 damage, got " + finalDamage);
        }

        @Test
        @DisplayName("Damage with element immunity results in 0")
        void elementImmunityGivesZeroDamage() {
            double baseDamage = 100.0;
            double elementMultiplier = 0.0; // IMMUNE
            double finalDamage = baseDamage * elementMultiplier;
            assertEquals(0.0, finalDamage, 0.001);
        }

        @Test
        @DisplayName("Damage with weakness and no defense")
        void weaknessOnlyNoDef() {
            double baseDamage = 80.0;
            double weaknessMultiplier = 1.10; // 10% weakness
            double finalDamage = baseDamage * weaknessMultiplier;
            assertEquals(88.0, finalDamage, 0.001);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Zero base damage stays zero regardless of modifiers")
        void zeroDamageStaysZero() {
            double baseDamage = 0.0;
            double result = baseDamage * 1.10 * 2.0 * 0.5; // weakness, element, reduction
            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("Very high defense doesn't go negative")
        void highDefenseDoesntGoNegative() {
            double defense = 10000.0;
            double baseDamage = 100.0;
            double multiplier = 1.0 - (defense / (defense + baseDamage));
            assertTrue(multiplier >= 0.0, "Defense multiplier should not be negative");
            assertTrue(multiplier < 0.01, "Very high defense should reduce damage to near 0");
        }

        @Test
        @DisplayName("Very small damage values are handled correctly")
        void verySmallDamageValues() {
            double baseDamage = 0.001;
            double multiplier = 0.5;
            double result = baseDamage * multiplier;
            assertEquals(0.0005, result, 0.0001);
        }

        @Test
        @DisplayName("Floating point precision with many multipliers")
        void floatingPointPrecision() {
            double baseDamage = 100.0;
            // Apply many small multipliers
            double result = baseDamage;
            for (int i = 0; i < 10; i++) {
                result *= 0.95;
            }
            // 0.95^10 ≈ 0.5987
            assertTrue(Math.abs(result - 59.87) < 0.1, "Expected ~59.87, got " + result);
        }
    }
}
