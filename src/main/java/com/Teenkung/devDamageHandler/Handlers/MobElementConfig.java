package com.Teenkung.devDamageHandler.Handlers;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.entity.LivingEntity;

import java.util.Map;

/**
 * Resolves the primary element and damage type for a mob attacker.
 * 
 * Priority:
 * 1. MythicMobs DamageModifiers (if mob has "ELEMENT_X" or "TYPE_X" key)
 * 2. Default: PHYSICAL type, no element (NONE)
 */
public class MobElementConfig {
    
    private final Element primaryElement;  // null = NONE
    private final DamageType primaryType;  // default PHYSICAL
    
    public static final MobElementConfig DEFAULT = new MobElementConfig(null, DamageType.PHYSICAL);
    
    public MobElementConfig(Element primaryElement, DamageType primaryType) {
        this.primaryElement = primaryElement;
        this.primaryType = primaryType != null ? primaryType : DamageType.PHYSICAL;
    }
    
    public Element getPrimaryElement() {
        return primaryElement;
    }
    
    public DamageType getPrimaryType() {
        return primaryType;
    }
    
    /**
     * Resolve element config for any living entity.
     * Works for MythicMobs (including vanilla overrides like "ZOMBIE:") and plain vanilla mobs.
     */
    public static MobElementConfig forEntity(LivingEntity entity) {
        // Check if this is a MythicMob (or vanilla override)
        if (MythicBukkit.inst().getMobManager().isMythicMob(entity)) {
            ActiveMob mob = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
            if (mob != null) {
                return fromMythicMob(mob);
            }
        }
        
        // Plain vanilla mob - no MythicMobs config
        return DEFAULT;
    }
    
    /**
     * Extract element/type from MythicMob's DamageModifiers config.
     * 
     * MythicMobs DamageModifiers format example:
     *   DamageModifiers:
     *     ELEMENT_FIRE: 0.5      # Takes 50% fire damage (defense)
     *     TYPE_MAGIC: 1.5        # Takes 150% magic damage
     *   
     * We look for keys starting with "ELEMENT_" or "TYPE_" to determine
     * the mob's PRIMARY offensive element/type (for when IT attacks).
     * 
     * Convention: The first ELEMENT_X with value 0.0 (immune) = mob's primary element
     *             Otherwise, use ELEMENT_X with lowest value (strongest resist)
     */
    private static MobElementConfig fromMythicMob(ActiveMob mob) {
        Map<String, Double> rawMods = mob.getType().getDamageModifiers();
        if (rawMods == null || rawMods.isEmpty()) {
            return DEFAULT;
        }
        
        Element foundElement = findPrimaryElement(rawMods);
        DamageType foundType = findPrimaryType(rawMods);

        return new MobElementConfig(foundElement, foundType);
    }

    private static Element findPrimaryElement(Map<String, Double> rawMods) {
        Element foundElement = null;
        double lowestElementValue = Double.MAX_VALUE;
        
        for (Map.Entry<String, Double> entry : rawMods.entrySet()) {
            String key = DamageMechanics.normalizeKey(entry.getKey());
            double value = entry.getValue();
            
            if (!key.startsWith("ELEMENT_")) continue;

            String elemName = key.substring("ELEMENT_".length());
            if (elemName.equalsIgnoreCase("NONE")) continue;

            // Mob is IMMUNE (0.0) or has strong resist = likely its element
            if (value < lowestElementValue) {
                lowestElementValue = value;
                Element elem = findElement(elemName);
                if (elem != null) {
                    foundElement = elem;
                }
            }
        }

        return foundElement;
    }

    private static DamageType findPrimaryType(Map<String, Double> rawMods) {
        for (Map.Entry<String, Double> entry : rawMods.entrySet()) {
            String key = DamageMechanics.normalizeKey(entry.getKey());
            double value = entry.getValue();

            if (!key.startsWith("TYPE_")) continue;

            String typeName = key.substring("TYPE_".length());
            // Immunity or strong resist suggests this is the mob's type
            if (value <= 0.5) {
                try {
                    return DamageType.valueOf(typeName.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        return DamageType.PHYSICAL;
    }
    
    /**
     * Find MythicLib Element by ID.
     */
    private static Element findElement(String id) {
        for (Element el : MythicLib.plugin.getElements().getAll()) {
            if (el.getId().equalsIgnoreCase(id)) {
                return el;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "MobElementConfig{element=" + (primaryElement != null ? primaryElement.getId() : "NONE") 
             + ", type=" + primaryType + "}";
    }
}
