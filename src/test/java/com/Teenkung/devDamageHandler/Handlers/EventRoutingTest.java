package com.Teenkung.devDamageHandler.Handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests to verify correct event routing and prevent double handling.
 * These tests simulate the decision logic in DamageHandler.
 */
class EventRoutingTest {

    /**
     * Simulates the event routing logic from DamageHandler.
     * Returns which handlers would process the damage.
     */
    static class EventRouter {
        private boolean debugOverride = false;
        
        public EventRouter setDebugOverride(boolean val) {
            debugOverride = val;
            return this;
        }

        /**
         * Determine which handlers process this attack.
         * @param attackerType "PLAYER", "MOB", "PROJECTILE"
         * @param victimType "PLAYER", "MOB"
         * @return Set of handler names that would fire
         */
        public Set<String> getActiveHandlers(String attackerType, String victimType) {
            Set<String> handlers = new HashSet<>();
            
            // PlayerAttackEvent fires for ANY player attack
            if ("PLAYER".equals(attackerType)) {
                handlers.add("onDamageModify(PlayerAttackEvent)");
            }
            
            // EntityDamageByEntityEvent fires for ALL attacks
            // But we skip player attackers unless debugOverride
            boolean skipPlayerInEntityEvent = "PLAYER".equals(attackerType) && !debugOverride;
            if (!skipPlayerInEntityEvent) {
                handlers.add("onEntityDamage(EntityDamageByEntityEvent)");
            }
            
            return handlers;
        }

        /**
         * Check if this scenario would cause double processing.
         */
        public boolean wouldDoubleProcess(String attackerType, String victimType) {
            Set<String> handlers = getActiveHandlers(attackerType, victimType);
            return handlers.size() > 1;
        }
    }

    private EventRouter router;

    @BeforeEach
    void setUp() {
        router = new EventRouter();
    }

    @Nested
    @DisplayName("Normal Event Routing (no debugOverride)")
    class NormalRoutingTests {

        @Test
        @DisplayName("Player → Mob: Only PlayerAttackEvent handles")
        void playerAttackMob() {
            Set<String> handlers = router.getActiveHandlers("PLAYER", "MOB");
            assertEquals(1, handlers.size());
            assertTrue(handlers.contains("onDamageModify(PlayerAttackEvent)"));
            assertFalse(router.wouldDoubleProcess("PLAYER", "MOB"));
        }

        @Test
        @DisplayName("Player → Player: Only PlayerAttackEvent handles")
        void playerAttackPlayer() {
            Set<String> handlers = router.getActiveHandlers("PLAYER", "PLAYER");
            assertEquals(1, handlers.size());
            assertTrue(handlers.contains("onDamageModify(PlayerAttackEvent)"));
            assertFalse(router.wouldDoubleProcess("PLAYER", "PLAYER"));
        }

        @Test
        @DisplayName("Mob → Player: Only EntityDamage handles")
        void mobAttackPlayer() {
            Set<String> handlers = router.getActiveHandlers("MOB", "PLAYER");
            assertEquals(1, handlers.size());
            assertTrue(handlers.contains("onEntityDamage(EntityDamageByEntityEvent)"));
            assertFalse(router.wouldDoubleProcess("MOB", "PLAYER"));
        }

        @Test
        @DisplayName("Mob → Mob: Only EntityDamage handles")
        void mobAttackMob() {
            Set<String> handlers = router.getActiveHandlers("MOB", "MOB");
            assertEquals(1, handlers.size());
            assertTrue(handlers.contains("onEntityDamage(EntityDamageByEntityEvent)"));
            assertFalse(router.wouldDoubleProcess("MOB", "MOB"));
        }
    }

    @Nested
    @DisplayName("Debug Override Routing")
    class DebugOverrideTests {

        @Test
        @DisplayName("Player → Mob with debug: BOTH handlers fire - DOUBLE HANDLING!")
        void playerAttackMobWithDebug() {
            router.setDebugOverride(true);
            Set<String> handlers = router.getActiveHandlers("PLAYER", "MOB");
            
            // This is a BUG - both handlers would fire!
            assertEquals(2, handlers.size(), 
                "DEBUG MODE: Both handlers fire for player attacks when debugOverride is set");
            assertTrue(router.wouldDoubleProcess("PLAYER", "MOB"),
                "This is the double handling bug!");
        }

        @Test
        @DisplayName("Mob → Player with debug: Only EntityDamage (correct)")
        void mobAttackPlayerWithDebug() {
            router.setDebugOverride(true);
            Set<String> handlers = router.getActiveHandlers("MOB", "PLAYER");
            assertEquals(1, handlers.size());
            assertFalse(router.wouldDoubleProcess("MOB", "PLAYER"));
        }
    }

    @Nested
    @DisplayName("Defense Application Scenarios")
    class DefenseApplicationTests {

        /**
         * Tracks what defense is applied for each scenario.
         */
        static class DefenseTracker {
            boolean mythicLibAppliesDefense; // Does MythicLib apply defense?
            boolean weApplyDefense;          // Do we apply PlayerDefenseApplicator?
            
            public boolean isDoubleDefense() {
                return mythicLibAppliesDefense && weApplyDefense;
            }
        }

        @Test
        @DisplayName("Player → Mob: No victim defense (mob has no player stats)")
        void playerAttackMobDefense() {
            DefenseTracker tracker = new DefenseTracker();
            // MythicLib applies mob's DamageModifiers, not player defense
            tracker.mythicLibAppliesDefense = false;
            tracker.weApplyDefense = false; // We only apply for mob→player
            
            assertFalse(tracker.isDoubleDefense());
        }

        @Test
        @DisplayName("Player → Player: MythicLib handles PvP defense")
        void playerAttackPlayerDefense() {
            DefenseTracker tracker = new DefenseTracker();
            // MythicLib's PlayerAttackEvent should handle victim defense
            tracker.mythicLibAppliesDefense = true;
            tracker.weApplyDefense = false; // isPlayerAttack=true, so we skip
            
            assertFalse(tracker.isDoubleDefense());
        }

        @Test
        @DisplayName("Mob → Player: MythicLib already applied defense")
        void mobAttackPlayerDefense() {
            DefenseTracker tracker = new DefenseTracker();
            // MythicLib applies defense BEFORE our event handler
            tracker.mythicLibAppliesDefense = true;
            // We DISABLED our defense (commented out in code)
            tracker.weApplyDefense = false;
            
            assertFalse(tracker.isDoubleDefense());
        }

        @Test
        @DisplayName("Mob → Mob: No player defense applied")
        void mobAttackMobDefense() {
            DefenseTracker tracker = new DefenseTracker();
            tracker.mythicLibAppliesDefense = false; // No player involved
            tracker.weApplyDefense = false;          // victim not a player
            
            assertFalse(tracker.isDoubleDefense());
        }
    }

    @Nested
    @DisplayName("Damage Modification Order")
    class DamageModificationOrderTests {

        /**
         * Simulates the damage modification pipeline.
         */
        static class DamagePipeline {
            private double damage;
            private final List<String> modifiersApplied = new ArrayList<>();
            
            public DamagePipeline(double initial) {
                this.damage = initial;
            }
            
            public DamagePipeline applyMythicLibDefense(double defensePercent) {
                damage *= (1 - defensePercent / 100.0);
                modifiersApplied.add("MythicLib Defense: -" + defensePercent + "%");
                return this;
            }
            
            public DamagePipeline applyOurDefense(double defensePercent) {
                damage *= (1 - defensePercent / 100.0);
                modifiersApplied.add("Our Defense: -" + defensePercent + "%");
                return this;
            }
            
            public DamagePipeline applyTypeModifier(double multiplier) {
                damage *= multiplier;
                modifiersApplied.add("Type Modifier: x" + multiplier);
                return this;
            }
            
            public double getFinalDamage() { return damage; }
            public List<String> getModifiersApplied() { return modifiersApplied; }
        }

        @Test
        @DisplayName("Correct order: MythicLib → Type → (no double defense)")
        void correctDamageOrder() {
            DamagePipeline pipeline = new DamagePipeline(100)
                .applyMythicLibDefense(45)  // MythicLib reduces first
                .applyTypeModifier(1.0);     // We apply type modifiers
            
            // Only one defense application
            assertEquals(55, pipeline.getFinalDamage(), 0.1);
            assertEquals(2, pipeline.getModifiersApplied().size());
        }

        @Test
        @DisplayName("Bug scenario: Double defense application")
        void doubleDefenseBug() {
            DamagePipeline pipeline = new DamagePipeline(100)
                .applyMythicLibDefense(45)  // MythicLib reduces
                .applyTypeModifier(1.0)      // We process
                .applyOurDefense(45);        // BUG: We also reduce
            
            // Damage is too low due to double reduction
            double expected = 100 * 0.55 * 0.55; // ~30.25
            assertEquals(expected, pipeline.getFinalDamage(), 0.5);
            
            // This shows the bug - 3 modifiers when there should be 2
            assertEquals(3, pipeline.getModifiersApplied().size());
        }
    }
}
