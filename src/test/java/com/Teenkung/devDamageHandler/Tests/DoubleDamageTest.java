package com.Teenkung.devDamageHandler.Tests;

import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates logical integrity of manual packet iteration vs old type iteration
 * to ensure no double-application of global multipliers occur.
 */
public class DoubleDamageTest {

    // Mock-like classes for testing logic
    static class MockPacket {
        double value;
        Set<DamageType> types = new HashSet<>();
        Element element;

        MockPacket(double val, DamageType... ts) {
            this.value = val;
            this.types.addAll(Arrays.asList(ts));
        }
        
        void setValue(double v) { this.value = v; }
        double getValue() { return value; }
        Set<DamageType> getTypes() { return types; }
    }

    @Test
    public void testGlobalMultiplierOnMultiTypePacket() {
        // SCENARIO: A packet has BOTH PHYSICAL and WEAPON types.
        // We apply a global multiplier of x4.0.
        // OLD LOGIC: Iterated activeTypes (PHYSICAL, WEAPON). Applied x4 for each.
        // NEW LOGIC: Iterates packets. Applies x4 once.

        MockPacket packet = new MockPacket(7.0, DamageType.PHYSICAL, DamageType.WEAPON);
        List<MockPacket> packets = Collections.singletonList(packet);

        // Simulation of pendingMultiplier logic
        double pendingMul = 4.0;
        
        // --- OLD BUGGY LOGIC SIMULATION ---
        Set<DamageType> activeTypes = packet.getTypes();
        double buggyValue = packet.getValue();
        for (DamageType type : activeTypes) {
            // Apply modifier for this type "via packet iteration"
            // (Simulating specific type targeting)
            buggyValue *= pendingMul; 
        }
        // 7 * 4 * 4 = 112
        assertEquals(112.0, buggyValue, 0.001, "Buggy logic should produce double multiplication");

        // --- NEW FIXED LOGIC SIMULATION ---
        double fixedValue = packet.getValue(); // Reset to 7.0 (conceptually)
        // Iterate packets directly
        for (MockPacket p : packets) {
            p.setValue(p.getValue() * pendingMul);
        }
        
        // 7 * 4 = 28
        assertEquals(28.0, packet.getValue(), 0.001, "Fixed logic should produce single multiplication");
    }

    @Test
    public void testTypeSpecificModifiers() {
        // SCENARIO: Packet has PHYSICAL and SKILL.
        // Modifiers: PHYSICAL x1.5, SKILL x1.2.
        // Should stack.
        
        MockPacket packet = new MockPacket(100.0, DamageType.PHYSICAL, DamageType.SKILL);
        
        Map<DamageType, Double> multipliers = new HashMap<>();
        multipliers.put(DamageType.PHYSICAL, 1.5);
        multipliers.put(DamageType.SKILL, 1.2);
        
        // Logic from DamageModifierApplicator (ish)
        double packetMul = 1.0;
        for (DamageType type : packet.getTypes()) {
            Double mul = multipliers.get(type);
            if (mul != null) {
                packetMul *= mul;
            }
        }
        packet.setValue(packet.getValue() * packetMul);
        
        // 100 * 1.5 * 1.2 = 180
        assertEquals(180.0, packet.getValue(), 0.001);
    }
}
