package com.Teenkung.devDamageHandler.Indicator;

import io.lumine.mythic.lib.damage.DamageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for indicator display logic.
 * Tests icon selection, priority, crit icons, arrow indicators, and edge cases.
 */
class IndicatorLogicTest {

    private static final double EPS = 1e-6;

    /**
     * Simulates IndicatorSettings.iconListFor logic for testing.
     */
    static class IconSelector {
        private Map<DamageType, String[]> typeIcons = new EnumMap<>(DamageType.class);
        private boolean multiTypeIcons = true;
        private int maxTypeIcons = 0;
        private String defaultNormal = "";
        private String defaultCrit = "";
        
        // Priority list (Specific -> Generic)
        private static final List<DamageType> TYPE_PRIORITY = Arrays.asList(
            DamageType.SKILL, DamageType.MAGIC, DamageType.WEAPON,
            DamageType.PROJECTILE, DamageType.UNARMED, DamageType.PHYSICAL
        );
        
        public IconSelector setTypeIcon(DamageType type, String normal, String crit) {
            typeIcons.put(type, new String[]{normal, crit});
            return this;
        }
        
        public IconSelector setMultiTypeIcons(boolean val) { multiTypeIcons = val; return this; }
        public IconSelector setMaxTypeIcons(int val) { maxTypeIcons = val; return this; }
        public IconSelector setDefaultNormal(String val) { defaultNormal = val; return this; }
        public IconSelector setDefaultCrit(String val) { defaultCrit = val; return this; }
        
        public List<String> iconListFor(Collection<DamageType> types, boolean crit) {
            if (types == null || types.isEmpty()) return Collections.emptyList();
            
            // Sort by priority
            List<DamageType> sorted = new ArrayList<>(types);
            sorted.sort(Comparator.comparingInt(dt -> {
                int idx = TYPE_PRIORITY.indexOf(dt);
                return idx == -1 ? 999 : idx;
            }));
            
            List<String> out = new ArrayList<>();
            for (DamageType dt : sorted) {
                String[] icons = typeIcons.get(dt);
                if (icons == null) continue;
                out.add(crit ? icons[1] : icons[0]);
                if (!multiTypeIcons && !out.isEmpty()) break;
                if (maxTypeIcons > 0 && out.size() >= maxTypeIcons) break;
            }
            return out;
        }
        
        public String getDefault(boolean crit) {
            return crit ? defaultCrit : defaultNormal;
        }
    }

    @Nested
    @DisplayName("Icon Priority Tests")
    class IconPriorityTests {
        
        private IconSelector selector;
        
        @BeforeEach
        void setUp() {
            selector = new IconSelector()
                .setTypeIcon(DamageType.PHYSICAL, "⚔", "⚔!")
                .setTypeIcon(DamageType.WEAPON, "🗡", "🗡!")
                .setTypeIcon(DamageType.PROJECTILE, "🏹", "🏹!")
                .setTypeIcon(DamageType.SKILL, "★", "★!")
                .setTypeIcon(DamageType.MAGIC, "✦", "✦!");
        }
        
        @Test
        @DisplayName("SKILL has highest priority")
        void skillHasHighestPriority() {
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.SKILL, DamageType.WEAPON);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals("★", icons.get(0), "SKILL should be first");
        }
        
        @Test
        @DisplayName("MAGIC has second highest priority")
        void magicHasSecondPriority() {
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.MAGIC, DamageType.WEAPON);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals("✦", icons.get(0), "MAGIC should be first");
        }
        
        @Test
        @DisplayName("WEAPON comes before PROJECTILE")
        void weaponBeforeProjectile() {
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.PROJECTILE, DamageType.WEAPON);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals("🗡", icons.get(0), "WEAPON should be first");
        }
        
        @Test
        @DisplayName("PROJECTILE comes before PHYSICAL")
        void projectileBeforePhysical() {
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.PROJECTILE);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals("🏹", icons.get(0), "PROJECTILE should be first");
        }
        
        @Test
        @DisplayName("PHYSICAL is last priority")
        void physicalIsLastPriority() {
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals("⚔", icons.get(0), "PHYSICAL icon when only type");
        }
        
        @Test
        @DisplayName("Full priority order: SKILL > MAGIC > WEAPON > PROJECTILE > PHYSICAL")
        void fullPriorityOrder() {
            Set<DamageType> types = EnumSet.of(
                DamageType.PHYSICAL, DamageType.PROJECTILE, DamageType.WEAPON,
                DamageType.MAGIC, DamageType.SKILL
            );
            List<String> icons = selector.iconListFor(types, false);
            
            assertEquals(5, icons.size());
            assertEquals("★", icons.get(0), "SKILL first");
            assertEquals("✦", icons.get(1), "MAGIC second");
            assertEquals("🗡", icons.get(2), "WEAPON third");
            assertEquals("🏹", icons.get(3), "PROJECTILE fourth");
            assertEquals("⚔", icons.get(4), "PHYSICAL last");
        }
    }

    @Nested
    @DisplayName("Max Type Icons Tests")
    class MaxTypeIconsTests {
        
        private IconSelector selector;
        
        @BeforeEach
        void setUp() {
            selector = new IconSelector()
                .setTypeIcon(DamageType.PHYSICAL, "A", "A!")
                .setTypeIcon(DamageType.WEAPON, "B", "B!")
                .setTypeIcon(DamageType.PROJECTILE, "C", "C!")
                .setMultiTypeIcons(true);
        }
        
        @Test
        @DisplayName("maxTypeIcons=0 means no limit")
        void maxZeroNoLimit() {
            selector.setMaxTypeIcons(0);
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.WEAPON, DamageType.PROJECTILE);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals(3, icons.size());
        }
        
        @Test
        @DisplayName("maxTypeIcons=1 returns only highest priority icon")
        void maxOneSingleIcon() {
            selector.setMaxTypeIcons(1);
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.WEAPON, DamageType.PROJECTILE);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals(1, icons.size());
            assertEquals("B", icons.get(0), "WEAPON has highest priority");
        }
        
        @Test
        @DisplayName("maxTypeIcons=2 returns top 2 priority icons")
        void maxTwoIcons() {
            selector.setMaxTypeIcons(2);
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.WEAPON, DamageType.PROJECTILE);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals(2, icons.size());
            assertEquals("B", icons.get(0), "WEAPON first");
            assertEquals("C", icons.get(1), "PROJECTILE second");
        }
        
        @Test
        @DisplayName("multiTypeIcons=false returns only first icon")
        void multiTypeFalse() {
            selector.setMultiTypeIcons(false);
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL, DamageType.WEAPON);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals(1, icons.size());
        }
    }

    @Nested
    @DisplayName("Crit Icon Tests")
    class CritIconTests {
        
        private IconSelector selector;
        
        @BeforeEach
        void setUp() {
            selector = new IconSelector()
                .setTypeIcon(DamageType.PHYSICAL, "⚔", "⚔‼")
                .setTypeIcon(DamageType.SKILL, "★", "★‼")
                .setDefaultNormal("•")
                .setDefaultCrit("•!");
        }
        
        @Test
        @DisplayName("Crit=true uses crit icon variant")
        void critUsesCritIcon() {
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL);
            List<String> icons = selector.iconListFor(types, true);
            assertEquals("⚔‼", icons.get(0), "Should use crit variant");
        }
        
        @Test
        @DisplayName("Crit=false uses normal icon variant")
        void nonCritUsesNormalIcon() {
            Set<DamageType> types = EnumSet.of(DamageType.PHYSICAL);
            List<String> icons = selector.iconListFor(types, false);
            assertEquals("⚔", icons.get(0), "Should use normal variant");
        }
        
        @Test
        @DisplayName("Default crit icon when no types match")
        void defaultCritIcon() {
            String def = selector.getDefault(true);
            assertEquals("•!", def);
        }
        
        @Test
        @DisplayName("Default normal icon when no types match")
        void defaultNormalIcon() {
            String def = selector.getDefault(false);
            assertEquals("•", def);
        }
    }

    @Nested
    @DisplayName("Arrow Indicator Tests")
    class ArrowIndicatorTests {
        
        /**
         * Simulates arrow selection based on mob multiplier.
         */
        static class ArrowSelector {
            private String arrowUp = "▲";
            private String arrowDown = "▼";
            
            public String selectArrow(Double mobMul) {
                double mul = (mobMul == null) ? 1.0 : mobMul;
                if (mul > 1.0001) return arrowUp;      // Weakness
                if (mul < 0.9999) return arrowDown;    // Resistance
                return "";                              // Neutral
            }
        }
        
        private ArrowSelector selector;
        
        @BeforeEach
        void setUp() {
            selector = new ArrowSelector();
        }
        
        @Test
        @DisplayName("Multiplier > 1.0 shows up arrow (weakness)")
        void weaknessShowsUpArrow() {
            assertEquals("▲", selector.selectArrow(1.5));
            assertEquals("▲", selector.selectArrow(2.0));
            assertEquals("▲", selector.selectArrow(1.01));
        }
        
        @Test
        @DisplayName("Multiplier < 1.0 shows down arrow (resistance)")
        void resistanceShowsDownArrow() {
            assertEquals("▼", selector.selectArrow(0.5));
            assertEquals("▼", selector.selectArrow(0.0));
            assertEquals("▼", selector.selectArrow(0.99));
        }
        
        @Test
        @DisplayName("Multiplier = 1.0 shows no arrow (neutral)")
        void neutralShowsNoArrow() {
            assertEquals("", selector.selectArrow(1.0));
            assertEquals("", selector.selectArrow(1.0001)); // Within tolerance
            assertEquals("", selector.selectArrow(0.9999)); // Within tolerance
        }
        
        @Test
        @DisplayName("Null multiplier treated as 1.0 (neutral)")
        void nullMultiplierIsNeutral() {
            assertEquals("", selector.selectArrow(null));
        }
    }

    @Nested
    @DisplayName("Empty/Null Type Handling")
    class EmptyTypeHandlingTests {
        
        private IconSelector selector;
        
        @BeforeEach
        void setUp() {
            selector = new IconSelector()
                .setTypeIcon(DamageType.PHYSICAL, "⚔", "⚔!")
                .setDefaultNormal("•")
                .setDefaultCrit("•!");
        }
        
        @Test
        @DisplayName("Empty type set returns empty list")
        void emptyTypesReturnsEmpty() {
            List<String> icons = selector.iconListFor(Collections.emptySet(), false);
            assertTrue(icons.isEmpty());
        }
        
        @Test
        @DisplayName("Null type set returns empty list")
        void nullTypesReturnsEmpty() {
            List<String> icons = selector.iconListFor(null, false);
            assertTrue(icons.isEmpty());
        }
        
        @Test
        @DisplayName("Types with no configured icons returns empty")
        void unconfiguredTypesReturnsEmpty() {
            Set<DamageType> types = EnumSet.of(DamageType.UNARMED); // Not configured
            List<String> icons = selector.iconListFor(types, false);
            assertTrue(icons.isEmpty());
        }
    }

    @Nested
    @DisplayName("Indicator Line Value Tests")
    class IndicatorLineValueTests {
        
        /**
         * Simulates IndicatorLine value calculation.
         */
        static class LineValueCalculator {
            
            public double calculateDisplayValue(double baseDamage, double multiplier, 
                                                boolean crit, double critPower, boolean immune) {
                if (immune) return 0.0;
                double value = baseDamage * multiplier;
                if (crit) {
                    value *= (1.0 + critPower / 100.0);
                }
                return value;
            }
            
            public boolean shouldSkipLine(double value, boolean immune) {
                return !immune && value < 0.01;
            }
        }
        
        private LineValueCalculator calc;
        
        @BeforeEach
        void setUp() {
            calc = new LineValueCalculator();
        }
        
        @Test
        @DisplayName("Immune line shows 0 value")
        void immuneShowsZero() {
            double val = calc.calculateDisplayValue(100, 0.0, false, 0, true);
            assertEquals(0.0, val, EPS);
        }
        
        @Test
        @DisplayName("Crit multiplies by crit power")
        void critMultipliesByPower() {
            double val = calc.calculateDisplayValue(100, 1.0, true, 150, false);
            assertEquals(250.0, val, EPS); // 100 * (1 + 150/100) = 250
        }
        
        @Test
        @DisplayName("Non-crit doesn't apply crit power")
        void nonCritNoPower() {
            double val = calc.calculateDisplayValue(100, 1.0, false, 150, false);
            assertEquals(100.0, val, EPS);
        }
        
        @Test
        @DisplayName("Zero damage should be skipped (unless immune)")
        void zeroDamageSkipped() {
            assertTrue(calc.shouldSkipLine(0.0, false));
            assertTrue(calc.shouldSkipLine(0.005, false));
        }
        
        @Test
        @DisplayName("Immune lines are NOT skipped")
        void immuneNotSkipped() {
            assertFalse(calc.shouldSkipLine(0.0, true));
        }
        
        @Test
        @DisplayName("Resistance reduces display value")
        void resistanceReducesValue() {
            double val = calc.calculateDisplayValue(100, 0.5, false, 0, false);
            assertEquals(50.0, val, EPS);
        }
        
        @Test
        @DisplayName("Weakness increases display value")
        void weaknessIncreasesValue() {
            double val = calc.calculateDisplayValue(100, 1.5, false, 0, false);
            assertEquals(150.0, val, EPS);
        }
    }

    @Nested
    @DisplayName("Skill Crit Icon Injection Tests")
    class SkillCritIconTests {
        
        /**
         * Simulates skill crit icon injection logic.
         */
        static String injectSkillCritIcon(List<String> typeIcons, boolean skillCrit, String skillCritIcon) {
            if (!skillCrit || skillCritIcon == null || skillCritIcon.isEmpty()) {
                return String.join("", typeIcons);
            }
            
            List<String> result = new ArrayList<>(typeIcons);
            if (!result.isEmpty()) {
                result.set(0, skillCritIcon + result.get(0));
            } else {
                result.add(skillCritIcon);
            }
            return String.join("", result);
        }
        
        @Test
        @DisplayName("Skill crit prepends icon to first type icon")
        void skillCritPrependsToFirst() {
            List<String> icons = List.of("⚔", "🏹");
            String result = injectSkillCritIcon(icons, true, "★");
            assertEquals("★⚔🏹", result);
        }
        
        @Test
        @DisplayName("No skill crit = no change")
        void noSkillCritNoChange() {
            List<String> icons = List.of("⚔", "🏹");
            String result = injectSkillCritIcon(icons, false, "★");
            assertEquals("⚔🏹", result);
        }
        
        @Test
        @DisplayName("Empty skill crit icon = no change")
        void emptySkillCritIconNoChange() {
            List<String> icons = List.of("⚔", "🏹");
            String result = injectSkillCritIcon(icons, true, "");
            assertEquals("⚔🏹", result);
        }
        
        @Test
        @DisplayName("Skill crit with empty type icons adds standalone")
        void skillCritEmptyTypesStandalone() {
            List<String> icons = new ArrayList<>();
            String result = injectSkillCritIcon(icons, true, "★");
            assertEquals("★", result);
        }
    }
}
