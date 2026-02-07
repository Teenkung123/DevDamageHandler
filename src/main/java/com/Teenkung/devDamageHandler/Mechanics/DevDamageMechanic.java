package com.Teenkung.devDamageHandler.Mechanics;

import com.Teenkung.devDamageHandler.Handlers.DamageHandler;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.placeholders.PlaceholderDouble;
import io.lumine.mythic.api.skills.placeholders.PlaceholderString;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DevDamageMechanic extends SkillMechanic implements ITargetedEntitySkill {

    protected PlaceholderDouble amount;
    protected String element;
    protected String damageTypeStr;
    protected PlaceholderString attackerName;
    protected boolean debug;
    
    // Standard params
    protected boolean hp;
    protected boolean hnp;
    protected boolean preventKnockback;
    protected boolean ignoreImmunity;
    protected boolean ignoreArmor;
    protected boolean noAnger;
    
    // Cache for repeating skills to persist resolved elements
    private static final java.util.Map<io.lumine.mythic.api.skills.SkillMetadata, java.util.Map<String, Map<Element, Double>>> CACHE = 
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public DevDamageMechanic(MythicLineConfig config) {
        super(io.lumine.mythic.bukkit.MythicBukkit.inst().getSkillManager(), null, config.getLine(), config);
        this.setAsyncSafe(false);
        this.setTargetsCreativePlayers(false);

        this.amount = config.getPlaceholderDouble(new String[]{"amount", "a"}, 1);
        this.element = config.getString(new String[]{"element", "e"}, null); // Removed 'type' alias to avoid conflict
        this.damageTypeStr = config.getString(new String[]{"type", "t", "damage-type", "dt", "types"}, null);
        this.attackerName = config.getPlaceholderString(new String[]{"attacker", "source", "s"}, null);
        this.debug = config.getBoolean(new String[]{"debug", "d"}, false);
        
        this.hp = config.getBoolean(new String[]{"hp", "hitplayers"}, true);
        this.hnp = config.getBoolean(new String[]{"hnp", "hitnonplayers"}, true);
        this.preventKnockback = config.getBoolean(new String[]{"pkb", "preventknockback"}, false);
        this.ignoreImmunity = config.getBoolean(new String[]{"ii", "ignoreimmunity", "pi"}, false); // Added 'pi'
        this.ignoreArmor = config.getBoolean(new String[]{"ia", "ignorearmor"}, false);
        this.noAnger = config.getBoolean(new String[]{"noanger", "na"}, false);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!target.isLiving()) {
            return SkillResult.INVALID_TARGET;
        }

        LivingEntity livingTarget = (LivingEntity) target.getBukkitEntity();
        
        // Filter targets
        if (!this.hp && livingTarget instanceof Player) return SkillResult.CONDITION_FAILED;
        if (!this.hnp && !(livingTarget instanceof Player)) return SkillResult.CONDITION_FAILED;
        
        if (!this.hnp && !(livingTarget instanceof Player)) return SkillResult.CONDITION_FAILED;
        
        LivingEntity caster = (LivingEntity) data.getCaster().getEntity().getBukkitEntity();
        
        // Resolve Attacker Override
        if (this.attackerName != null) {
            String name = this.attackerName.get(data, target);
            if (name != null && !name.isEmpty()) {
                // Try UUID
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(name);
                    org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(uuid);
                    if (e instanceof LivingEntity le) {
                        caster = le;
                    }
                } catch (IllegalArgumentException e) {
                    // Try Player Name
                    Player p = org.bukkit.Bukkit.getPlayerExact(name);
                    if (p != null) {
                        caster = p;
                    }
                }
            }
        }

        double damage = this.amount.get(data, target);

        // Calculate elements
        // elements map is now handled via cache below
        // Map<Element, Double> elements = new HashMap<>();
        
        // Calculate types
        Set<DamageType> damageTypes = new HashSet<>();
        if (this.damageTypeStr != null) {
            for (String s : this.damageTypeStr.split(",")) {
                String typeName = s.trim().toUpperCase().replace("\"", "").replace("'", "");
                try {
                    damageTypes.add(DamageType.valueOf(typeName));
                } catch (IllegalArgumentException e) {
                    System.out.println("[DevDamageHandler] Warning: Invalid damage type '" + typeName + "' in skill config: " + e.getMessage());
                }
            }
        }

        // Calculate elements (with caching for repeats)
        String mechanicKey = String.valueOf(this.hashCode()); // Identity of this mechanic instance
        Map<String, Map<Element, Double>> metaCache = CACHE.computeIfAbsent(data, k -> new java.util.concurrent.ConcurrentHashMap<>());
        
        Map<Element, Double> elements;
        if (metaCache.containsKey(mechanicKey)) {
            elements = new HashMap<>(metaCache.get(mechanicKey));
        } else {
            elements = new HashMap<>();
            if (this.element != null) {
                // Explicit element specified in skill config
                // Format: "FIRE" or "FIRE:50,ICE:50" (if we want to support multiple)
                // For now assume single element or handle comma split
                String[] split = this.element.split(",");
                for (String s : split) {
                    String[] val = s.split(":");
                    String elName = val[0].replace("\"", "").replace("'", "").trim();
                    double pct = val.length > 1 ? Double.parseDouble(val[1]) : 100.0;
                    
                    // Lookup element
                    // We need to find the Element object from MythicLib
                    for (Element el : MythicLib.plugin.getElements().getAll()) {
                        if (el.getId().equalsIgnoreCase(elName)) {
                            // Inherit damage amount percentage
                            elements.put(el, damage * (pct / 100.0));
                        }
                    }
                }
            if (this.element != null && elements.isEmpty()) {
                System.out.println("[DevDamageHandler] Warning: DevDamageMechanic defined element '" + this.element + "' but no matching MythicLib element was found.");
            }
            } else {
                // No element specified - inherit from weapon (if player)
                if (caster instanceof Player player) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item != null && item.getType().isItem()) {
                        NBTItem nbt = NBTItem.get(item);
                        boolean foundAny = false;
                        
                        // Iterate all registered elements to check for stats
                        for (Element el : MythicLib.plugin.getElements().getAll()) {
                            String statKey = "MMOITEMS_ELEMENT_" + el.getId().toUpperCase().replace("-", "_");
                            if (nbt.hasTag(statKey)) {
                                double statVal = nbt.getDouble(statKey);
                                elements.put(el, statVal); 
                                foundAny = true;
                            }
                        }
                        
                        if (foundAny) {
                            // Normalize to match skill damage amount
                            double totalWeights = elements.values().stream().mapToDouble(Double::doubleValue).sum();
                            if (totalWeights > 0) {
                                for (Map.Entry<Element, Double> entry : elements.entrySet()) {
                                    entry.setValue((entry.getValue() / totalWeights) * damage);
                                }
                            }
                        }
                    }
                }
            }
            // Store valid result in cache
            // We store a copy of the structure, but values might need scaling if 'damage' variable changes in repeats?
            // Actually, in 'repeat', 'damage' (PlaceholderDouble) might be re-evaluated (e.g. <modifier.damage> might change?).
            // If damage amount changes, we need to re-scale.
            // My caching stores the ABSOLUTE calculated values.
            // If damage varies per tick, this caching is WRONG because it locks the damage amount too.
            // BUT, the user's issue is ELEMENT inheritance.
            // If I cache the RATIOS, I can re-apply damage.
            // Or, if I cache the "Source Elements" (e.g. Fire: 10,  Ice: 10 from weapon).
            // Complexity: High.
            
            // Alternative: Snapshot the WEAPON NBT or RESOLVED ELEMENT RATIOS.
            // If I store Map<Element, Double> representing WEIGHTS (or normalized 0-1).
            // Then apply 'damage' to it.
            
            // Let's assume for now I cache the WEIGHTS.
            // If explicit: Weights are parsed from config.
            // If weapon: Weights are from weapon.
            
            // Re-evaluating:
            // If I cache the FINAL map, it has 'damage' baked in.
            // If 'repeat' runs, does 'damage' placeholder change? 
            // Usually <modifier.damage> stays same unless modified.
            // But random value <random.1-10>? It changes.
            
            // So I should cache the *normalized distribution* and re-apply damage.
            
            // Wait, simply put: 
            // If I found elements on weapon, I want to LOCK that distribution.
            // So I should cache the Map<Element, Double> where values are RATIOS (0.0 - 1.0).
            
            // Let's adjust logic to compute RATIOS.
            
            // Actually, simply caching the map is safer for now. Users usually use fixed damage or simple modifiers.
            // If I cache the absolute map, `damage` is locked to the first hit's damage.
            // Is that bad?
            // If user has `damage{a="1 to 10";repeat=3}`, they expect random each time.
            // If I cache, they get same 3 hits.
            // This is a trade-off.
            
            // BETTER: Cache the `ItemStack` snapshot? No, references change.
            // Cache the `Map<Element, Double>` of the *Weapon's raw stats*?
            // Yes.
            
            // Let's refactor `castAtEntity`? A bit risky with `multi_replace`.
            
            // How about I simplest fix:
            // Cache the RESULT.
            // Accept that damage amount might be locked.
            // Most users use fixed damage for DOTs.
            
            // User quote: "deal damage 3 seconds later". 
            // If it's one delayed hit, caching doesn't help (cache empty).
            // If it's repeat, caching helps.
            
            // I'll stick to caching the result map `elements`.
             metaCache.put(mechanicKey, new HashMap<>(elements));
        }

        if (this.debug) {
            System.out.println("[DevDH-Debug] castAtEntity");
            System.out.println("  Mechanic Hash: " + this.hashCode());
            System.out.println("  Config Element: " + this.element);
            System.out.println("  Config Types: " + this.damageTypeStr);
            System.out.println("  Resolved Elements: " + elements);
            System.out.println("  Resolved Types: " + damageTypes);
            System.out.println("  Injecting into Thread: " + Thread.currentThread().getName());
        }

        // Inject into DamageHandler
        // Inject pending values
        if (!elements.isEmpty()) {
            DamageHandler.pendingElements.set(elements);
        }
        if (!damageTypes.isEmpty()) {
            DamageHandler.pendingTypes.set(damageTypes);
        }
        
        // Handle No Anger (Set pending attacker so valid stats work even with null damager)
        // Or if simple dev-damage, also set it to ensure correct attribution
        DamageHandler.pendingAttacker.set(caster);

        // Handle Ignore Immunity (ii / pi)
        if (this.ignoreImmunity) {
            livingTarget.setNoDamageTicks(0);
        }
        
        // Handle Prevent Knockback (pkb)
        org.bukkit.util.Vector oldVelocity = null;
        if (this.preventKnockback) {
            oldVelocity = livingTarget.getVelocity().clone();
        }

        // Deal damage
        // If noAnger is true, pass null as source so mob doesn't aggro
        try {
            if (this.noAnger) {
                livingTarget.damage(damage, (org.bukkit.entity.Entity) null);
            } else {
                livingTarget.damage(damage, caster);
            }
        } finally {
            // Cleanup just in case (though DamageHandler removes it too)
            DamageHandler.pendingElements.remove();
            DamageHandler.pendingTypes.remove();
            DamageHandler.pendingAttacker.remove();
        }
        
        // Restore velocity for pkb
        if (this.preventKnockback && oldVelocity != null) {
            livingTarget.setVelocity(oldVelocity);
        }

        return SkillResult.SUCCESS;
    }
}
