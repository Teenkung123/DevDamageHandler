package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.DevDamageHandler;

import com.Teenkung.devDamageHandler.Indicator.IndicatorLine;
import com.Teenkung.devDamageHandler.Indicator.IndicatorSettings;
import com.Teenkung.devDamageHandler.Util.Msg;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.lib.api.event.IndicatorDisplayEvent;
import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import io.lumine.mythic.lib.api.player.MMOPlayerData;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * DevDamageHandler
 * 
 * Features:
 * - Element modifiers: ELEMENT_FIRE, ELEMENT_WATER, ELEMENT_NONE, etc.
 * - Type modifiers: TYPE_PHYSICAL, TYPE_MAGIC, TYPE_SKILL, etc.
 * - Custom crit system:
 *   - Non-elemental: CRITICAL_STRIKE_CHANCE/POWER
 *   - Elemental: ELEMENTAL_CRITICAL_STRIKE_CHANCE/POWER
 */
public class DamageHandler implements Listener {

    private final DevDamageHandler plugin;

    public DamageHandler(DevDamageHandler plugin) {
        this.plugin = plugin;
    }

    // Context for passing data from DevDamageMechanic (synchronous)
    public static final ThreadLocal<Map<Element, Double>> pendingElements = new ThreadLocal<>();
    public static final ThreadLocal<Set<DamageType>> pendingTypes = new ThreadLocal<>();
    public static final ThreadLocal<org.bukkit.command.CommandSender> debugOverride = new ThreadLocal<>();
    public static final ThreadLocal<Double> pendingMultiplier = new ThreadLocal<>();
    public static final ThreadLocal<Double> pendingFlatDamage = new ThreadLocal<>();

    /* ======================================================================
       EVENT FILTERS
       ====================================================================== */

    @EventHandler
    public void onIndicatorDisplay(IndicatorDisplayEvent event) {
        if (plugin.getFontCodec() == null) return;
        String number = plugin.getFontCodec().decodeNumbers(event.getMessage());
        if ("0".equals(number) || ".0".equals(number)) {
            event.setCancelled(true);
        }
    }

    /* ======================================================================
       MAIN DAMAGE PIPELINE
       ====================================================================== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageModify(PlayerAttackEvent event) {
        processDamage(
            event.getDamage(),
            event.getEntity(),
            event.getAttacker().getEntity(),
            true,
            null  // Player attack, no mob config
        );
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        // Skip player attackers UNLESS using /ddh damage command (debugOverride set)
        if (event.getDamager() instanceof Player && debugOverride.get() == null) return;
        if (!(event.getDamager() instanceof LivingEntity attacker) || !(event.getEntity() instanceof LivingEntity target)) return;

        try {
            // 1. Check skill overrides first (from DevDamageMechanic)
            Map<Element, Double> elemOverride = pendingElements.get();
            Set<DamageType> typeOverride = pendingTypes.get();
            
            // 2. Resolve mob's element config
            MobElementConfig mobConfig = MobElementConfig.forEntity(attacker);
            
            // 3. Determine final element and type
            Element element = null;
            DamageType type = mobConfig.getPrimaryType();
            
            if (elemOverride != null && !elemOverride.isEmpty()) {
                // Skill override takes precedence
                element = elemOverride.keySet().iterator().next();
            } else if (mobConfig.getPrimaryElement() != null) {
                element = mobConfig.getPrimaryElement();
            }
            
            if (typeOverride != null && !typeOverride.isEmpty()) {
                type = typeOverride.iterator().next();
            }
            
            // 4. Build DamageMetadata with resolved element/type
            DamageMetadata dmg;
            if (element != null) {
                dmg = new DamageMetadata(event.getDamage(), element, type);
            } else {
                dmg = new DamageMetadata(event.getDamage(), type);
            }
            
            // 5. Process damage (modifiers, crits, etc.)
            processDamage(dmg, target, attacker, false, mobConfig);
            
            event.setDamage(dmg.getPackets().stream().mapToDouble(DamagePacket::getFinalValue).sum());
            
            HitContext ctx = plugin.consumeHit(dmg);
            if (ctx != null) {
                displayIndicatorsWrapper(target, attacker, dmg, ctx);
            }
            
        } catch (Exception e) {
             plugin.getLogger().warning("[DevDamageHandler] Error in onEntityDamage: " + e.getMessage());
             e.printStackTrace();
        }
    }

    /**
     * Process damage for any LivingEntity attacker.
     * @param mobConfig Optional mob element config (for mob attackers), null for player attacks
     */
    public void processDamage(DamageMetadata dmg, org.bukkit.entity.Entity victimEntity, org.bukkit.entity.Entity attackerEntity, boolean isPlayerAttack, MobElementConfig mobConfig) {
        if (!(attackerEntity instanceof LivingEntity attacker) || !(victimEntity instanceof LivingEntity victim)) return;

        // Store original damage BEFORE any modifiers
        double originalDamage = dmg.getPackets().stream()
            .mapToDouble(DamagePacket::getFinalValue)
            .sum();

        // 1. Determine Stat Provider
        StatProvider statProvider;
        if (attacker instanceof Player p) {
            statProvider = new PlayerStatProvider(p);
        } else {
            statProvider = new MobStatProvider(attacker);
        }

        // 2. Determine Debug Recipient
        org.bukkit.command.CommandSender debugRecipient = null;
        boolean debug = false;
        
        if (debugOverride.get() != null) {
            debugRecipient = debugOverride.get();
            debug = true;
        } else if (attacker instanceof Player p && plugin.isDebugging(p)) {
            debug = true;
            debugRecipient = p;
        } else if (victim instanceof Player p && plugin.isDebugging(p)) {
            debug = true;
            debugRecipient = p;
        }

        if (!debug || debugRecipient == null) {
            debug = false;
        }

        // 3. Modifiers (MythicMobs - on victim)
        Map<String, Double> victimMmMods = getMythicDamageModifiers(victim);
        
        // Get attacker's modifiers too (for mob debug)
        Map<String, Double> attackerMmMods = getMythicDamageModifiers(attacker);
        
        // Check overrides
        Map<Element, Double> elemRawOverride = pendingElements.get();
        Set<DamageType> typeOverride = pendingTypes.get();

        // 4. Calculate Logic
        Set<DamageType> activeTypes = typeOverride != null ? typeOverride : dmg.collectTypes();
        if (activeTypes == null) activeTypes = new HashSet<>();
        
        DamageModifierApplicator applicator = new DamageModifierApplicator(
            statProvider, 
            debugRecipient, 
            dmg, 
            victimMmMods, 
            debug
        );
        
        // Type Modifiers
        // Now applies directly to packets
        Map<DamageType, Double> typeMultipliers = applicator.applyTypeModifiers();
        
        // Stat Bonuses
        // This calculates bonuses but does NOT apply them yet! 
        // We need to verify where these are applied.
        // Wait, getStatMultipliers just calculates them.
        Map<DamageType, Double> statMultipliers = applicator.getStatMultipliers(activeTypes);
        // We need to apply these to packets too!
        // Using same safe logic:
        for (DamagePacket packet : dmg.getPackets()) {
             double packetMul = 1.0;
             boolean modified = false;
             for (DamageType type : packet.getTypes()) {
                 Double mul = statMultipliers.get(type);
                 if (mul != null) {
                     packetMul *= mul;
                     modified = true;
                 }
             }
             if (modified) {
                 packet.setValue(packet.getValue() * packetMul);
                 // We don't use dmg.multiplicativeModifier because it might iterate packets again 
                 // and if we have overlapping types it might double apply?
                 // Wait, dmg.multiplicativeModifier(mul, type) iterates packets.
                 // If packet has TYPE1, TYPE2. 
                 // And we call modifier(mul1, TYPE1). Packet *= mul1.
                 // We call modifier(mul2, TYPE2). Packet *= mul2.
                 // This is correct behavior for stats (stacking bonuses).
                 // The issue with double-apply was for the SAME multiplier (LibreForge global mult).
                 
                 // However, for consistency and safety, let's keep packet iteration manual if desired.
                 // But manual loop here is safer.
             }
        }

        // Elements
        Map<Element, Double> elemRaw = new LinkedHashMap<>();
        if (elemRawOverride != null) {
            elemRaw.putAll(elemRawOverride);
        } else {
             for (DamagePacket packet : dmg.getPackets()) {
                Element elem = packet.getElement();
                if (elem != null) {
                    elemRaw.merge(elem, packet.getFinalValue(), Double::sum);
                }
            }
        }
        
        // Merge player victim's element defense stats into modifiers
        // This allows the applicator to apply FIRE_DEFENSE, FIRE_WEAKNESS, etc.
        if (victim instanceof Player playerVictim && !elemRaw.isEmpty()) {
            Map<String, Double> playerElemDefense = getPlayerElementDefense(playerVictim, elemRaw);
            if (!playerElemDefense.isEmpty()) {
                // Merge with MythicMob modifiers (player stats take precedence for ELEMENT_* keys)
                for (Map.Entry<String, Double> e : playerElemDefense.entrySet()) {
                    // Only add if not already set by MythicMob config
                    victimMmMods.putIfAbsent(e.getKey(), e.getValue());
                }
                
                if (debug) {
                    for (Map.Entry<String, Double> e : playerElemDefense.entrySet()) {
                        String elName = e.getKey().replace("ELEMENT_", "");
                        double mul = e.getValue();
                        double reductionPct = (1.0 - mul) * 100;
                        String color = mul < 1.0 ? "<green>" : "<red>";
                        com.Teenkung.devDamageHandler.Util.Msg.send(debugRecipient, 
                            "<gray>Player " + elName + " defense:</gray> " + color + "-" + DamageDebug.fmt(reductionPct) + "%" + (mul < 1.0 ? "</green>" : "</red>") + " <gray>(x" + DamageDebug.fmt(mul) + ")</gray>");
                    }
                }
            }
        }
        
        // Track if attack originally had elemental damage BEFORE modifiers
        // This determines if we should ignore the carrier packet
        boolean originallyHadElements = !elemRaw.isEmpty();
        double originalElementalTotal = elemRaw.values().stream().mapToDouble(Double::doubleValue).sum();
        
        // Apply Element modifiers
        DamageModifierApplicator.ElementModifierResult elementResult = applicator.applyElementModifiers(elemRaw);
        
        // Recalculate elemRaw after element modifiers were applied
        // This ensures indicators show the correct (post-multiplier) damage values
        elemRaw.clear();
        for (DamagePacket packet : dmg.getPackets()) {
            Element elem = packet.getElement();
            if (elem != null) {
                elemRaw.merge(elem, packet.getFinalValue(), Double::sum);
            }
        }
        
        // Non-Elemental / Carrier packet handling
        // If the weapon originally had elemental damage, ignore the carrier packet
        double totalElemental = elemRaw.values().stream().mapToDouble(Double::doubleValue).sum();
        double packetsSum = dmg.getPackets().stream().mapToDouble(DamagePacket::getFinalValue).sum();
        double nonElemRaw = packetsSum - totalElemental;
        
        // hasElementalDamage is true if the weapon ORIGINALLY had elements (even if now 0 due to immunity)
        boolean hasElementalDamage = originallyHadElements && originalElementalTotal > 0;
        double nonElemDamage = hasElementalDamage ? 0 : Math.max(0, nonElemRaw);
        
        // Zero out carrier (non-elemental) packets when weapon has elemental damage
        // This prevents the carrier packet from counting as damage
        if (hasElementalDamage) {
            for (DamagePacket packet : dmg.getPackets()) {
                if (packet.getElement() == null && packet.getFinalValue() > 0) {
                    // This is a carrier packet with no element - zero it out
                    for (DamageType type : packet.getTypes()) {
                        dmg.multiplicativeModifier(0, type);
                    }
                }
            }
        }
        
        // Crits - Unified handling for Weapon, Skill, and Elemental crits
        DamageModifierApplicator.CritResult crits = applicator.calculateCrits(elemRaw, nonElemDamage, activeTypes);
        
        // Apply elemental crit multipliers
        crits.elementCritMults().forEach((el, mul) -> {
            dmg.multiplicativeModifier(mul, el);
            // Update elemRaw for indicators/context so they show the post-crit value
            elemRaw.computeIfPresent(el, (k, v) -> v * mul);
        });

        // Apply non-elemental modifiers
        double nonElemMul = applicator.applyNoneElementModifier(nonElemDamage, activeTypes);
        
        // Apply LibReforge/pending flat damage addition
        // We add this to the non-elemental damage part (or distribute it? for now add to first available packet or create new?)
        // MythicLib structures damage in packets. We can increase the value of existing packets.
        // Or if we want to be safe, we can add it to the nonElemDamage variable which is used for crit calc,
        // BUT we also need to actually modify the dmg object.
        Double pendingFlat = pendingFlatDamage.get();
        if (pendingFlat != null) {
            pendingFlatDamage.remove();
            if (Math.abs(pendingFlat) > 1e-6) {
                // We need to add this damage.
                // Strategy: Find a PHYSICAL packet and add execution, or just append a modifier?
                // Modifier is multiplicative usually.
                // We can't easily "add" base damage via modifier API unless we find a packet and set value.
                // But we are in "processDamage".
                // Let's modify the damage directly by registering a wrapper modifier? No, that's multiplicative.
                // We should add it to 'nonElemDamage' for crit calculation logic
                nonElemDamage += pendingFlat;
                
                // AND we need to make sure the final damage corresponds.
                // Since 'dmg' object holds the packets, we need to inject this damage into a packet.
                // If there are no packets, we might need to create one? (Unlikely in attack event)
                // Existing packets might be elemental.
                // Valid strategy: Find first packet and add value.
                boolean added = false;
                for (DamagePacket packet : dmg.getPackets()) {
                    // Try to find a non-elemental packet first
                    if (packet.getElement() == null) {
                         packet.setValue(packet.getValue() + pendingFlat);
                         added = true;
                         break;
                    }
                }
                if (!added && !dmg.getPackets().isEmpty()) {
                     // No non-elemental packet, add to first one
                     dmg.getPackets().get(0).setValue(dmg.getPackets().get(0).getValue() + pendingFlat);
                }
                
                if (debug) {
                    Msg.send(debugRecipient, "<gray>LibReforge Flat Damage:</gray> <white>+" + DamageDebug.fmt(pendingFlat) + "</white>");
                }
            }
        }
        
        // Apply LibReforge/pending damage multiplier
        // This is applied to ALL active types to scale total damage
        Double pendingMul = pendingMultiplier.get();
        if (pendingMul != null) {
            pendingMultiplier.remove();
            
            if (Math.abs(pendingMul - 1.0) > 1e-6) {
                 // specific check for 0
                 if (Math.abs(pendingMul) < 1e-6) {
                     // 0 multiplier - Zero out all packets
                     for (DamagePacket packet : dmg.getPackets()) {
                         // We can't set value to 0 directly if we want to rely on modifiers API,
                         // but "multiplicativeModifier" applies to types.
                         // Safest way to zero out is to multiply values by 0 directly.
                         packet.setValue(0);
                     }
                 } else {
                     // Apply multiplier to all packets directly
                     // This avoids double application if a packet has multiple types
                     for (DamagePacket packet : dmg.getPackets()) {
                         packet.setValue(packet.getValue() * pendingMul);
                     }
                 }
                 
                 if (debug) {
                     Msg.send(debugRecipient, "<gray>LibReforge Multiplier:</gray> <aqua>x" + DamageDebug.fmt(pendingMul) + "</aqua>");
                 }
            }
        }

        // Apply non-elemental crit multiplier (stacks Weapon + Skill power)
        if (crits.nonElemCritMul() > 1.0) {
            // Apply as a multiplier to the entire damage context?
            // Since we don't have a specific "NON_ELEMENTAL" element type modifier,
            // we apply it to the non-elemental damage simply by multiplying generic packets.
            // But MythicLib API is usually type/element based.
            // If we have non-elemental damage, it likely has types (PHYSICAL, etc.)
            // Or no types?
            
            // Just apply to any packet with no element
             for (DamagePacket packet : dmg.getPackets()) {
                if (packet.getElement() == null && packet.getFinalValue() > 0) {
                     // For each type in the packet, apply modifier?
                     // Or just apply to the packet via modifier on one of its types?
                     if (!packet.getTypes().isEmpty()) {
                         // Apply to first type to avoid double application
                         dmg.multiplicativeModifier(crits.nonElemCritMul(), packet.getTypes().iterator().next());
                     } else {
                         // No types? Usually has at least one. If not, can't apply modifier easily via API?
                         // MythicLib supports type-less modifiers? Unsure.
                         // Fallback: Apply to PHYSICAL if present, or WEAPON
                         dmg.multiplicativeModifier(crits.nonElemCritMul(), DamageType.PHYSICAL); 
                     }
                     // Only apply once per packet group? The modifier applies to the TYPE globally.
                     // So applying to PHYSICAL applies to ALL packets with PHYSICAL.
                     // We need to be careful not to apply multiple times.
                     break; // Apply once to a representative type
                }
            }
        }
        
        // NOTE: MythicLib already applies player defense stats before we receive the event
        // (defense, damage reduction, etc. are handled at a lower priority)
        // So we do NOT apply PlayerDefenseApplicator to avoid double reduction.
        PlayerDefenseApplicator.DefenseResult defenseResult = null;
        /* DISABLED - MythicLib handles defense for mob→player attacks
        if (!isPlayerAttack && victim instanceof Player playerVictim) {
            defenseResult = PlayerDefenseApplicator.applyDefense(
                playerVictim, dmg, mobConfig, originalDamage, debug, debugRecipient
            );
        }
        */

        // Sound effects for critical hits (if any)
        if ((crits.isElemCrit() || crits.isNonElemCrit()) && attacker instanceof Player player) {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.player.attack.crit"));
            player.playSound(attacker, sound, 1, 1);
        }
        
        
        // Context
        String targetId = getTargetId(victim);
        
        HitContext ctx = new HitContext(
            new HashMap<>(elemRaw),
            elementResult.multipliers(),
            elementResult.defenseMultipliers(),
            typeMultipliers,
            nonElemDamage,
            nonElemMul,
            crits.elementCritMults().keySet(),
            crits.isNonElemCrit(), // Used to be isNonElemCrit(), checking mul for safety or just pass boolean
            crits.triggeredTypes().contains(DamageModifierApplicator.CritType.SKILL),
            hasElementalDamage,
            targetId,
            elementResult.immuneElements()
        );
        plugin.rememberHit(dmg, ctx);

        if (debug) {
            // Use different debug output based on attack type
            if (!isPlayerAttack && mobConfig != null && victim instanceof Player) {
                // Mob → Player: Use enhanced debug
                DamageDebug.printMobAttackDebug(debugRecipient, attacker, victim, mobConfig, dmg, attackerMmMods, victimMmMods);
            } else {
                // Player → anything or no mobConfig: Use standard debug
                DamageDebug.printDebugHeader(debugRecipient, victim, dmg, elemRaw, victimMmMods, targetId);
                DamageDebug.printDebugFooter(debugRecipient, dmg);
            }
            
            // Show relevant stats for both attacker and victim
            DamageDebug.printRelevantStats(debugRecipient, attacker, victim, elemRaw.keySet());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageShow(PlayerAttackEvent event) {
        DamageMetadata dmg = event.getDamage();
        HitContext ctx = plugin.consumeHit(dmg);
        if (ctx == null) return;

        // Display
        displayIndicatorsWrapper(event.getEntity(), event.getAttacker().getEntity(), dmg, ctx);
    }
    
    private void displayIndicatorsWrapper(LivingEntity target, LivingEntity attacker, DamageMetadata dmg, HitContext ctx) {
        IndicatorSettings settings = plugin.getIndicatorSettings();
        if (settings == null || !settings.enabled) return;
        
        // Build lines
        Set<DamageType> baseTypes = dmg.collectTypes();
        
        // Stat provider for crit power lookups
        java.util.function.Function<String, Double> statProvider;
        if (attacker instanceof Player p) {
             MMOPlayerData data = MMOPlayerData.get(p.getUniqueId());
             statProvider = (data != null) ? (s) -> data.getStatMap().getStat(s) : (s) -> 0.0;
        } else {
             statProvider = (s) -> 0.0;
        }
        
        List<IndicatorLine> lines = IndicatorBuilder.buildLines(ctx, statProvider, baseTypes, settings);
        
        // Scale
        double finalDamage = dmg.getPackets().stream().mapToDouble(DamagePacket::getFinalValue).sum();
        
        double scaleTarget = IndicatorBuilder.getScalingTarget(settings, dmg, lines, finalDamage);
        lines = IndicatorBuilder.scaleLines(lines, scaleTarget);

        // Debug (only if player attacker for now? or check debug recipient?)
        if (attacker instanceof Player p && plugin.isDebugging(p)) {
            IndicatorBuilder.printDebugLines(p, lines);
        }
        
        // Display
        if (plugin.isUsingHologramLib() && plugin.getHologramLibIndicators() != null) {
            plugin.getHologramLibIndicators().displayLines(target, lines);
        } else if (plugin.getIndicators() != null) {
            plugin.getIndicators().displayLines(target, lines);
        }
    }

    /* ======================================================================
       HELPERS
       ====================================================================== */

    private Map<String, Double> getMythicDamageModifiers(LivingEntity target) {
        Map<String, Double> out = new LinkedHashMap<>();
        try {
            if (!MythicBukkit.inst().getMobManager().isMythicMob(target)) {
                return out;
            }
            var inst = MythicBukkit.inst().getMobManager().getMythicMobInstance(target);
            if (inst == null) return out;

            Map<String, Double> raw = inst.getType().getDamageModifiers();
            if (raw == null || raw.isEmpty()) return out;

            for (Map.Entry<String, Double> e : raw.entrySet()) {
                if (e.getKey() == null) continue;
                out.put(DamageMechanics.normalizeKey(e.getKey()), e.getValue());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[DevDamageHandler] getMythicDamageModifiers failed: " + t.getMessage());
        }
        return out;
    }

    private String getTargetId(LivingEntity e) {
        if (MythicBukkit.inst().getMobManager().isMythicMob(e)) {
            return MythicBukkit.inst().getMobManager().getMythicMobInstance(e).getMobType();
        }
        if (e instanceof Player) {
            return "PLAYER";
        }
        return "VANILLA_" + e.getType().name();
    }
    
    /**
     * Calculate player victim's element defense stats as a modifier map.
     * Returns the combined multiplier for each element based on:
     * - {ELEMENT}_DEFENSE: flat defense (uses configured formula)
     * - {ELEMENT}_DEFENSE_PERCENT: percentage reduction
     * - {ELEMENT}_WEAKNESS: percentage INCREASED damage taken
     * 
     * @param player The player victim
     * @param elemRaw Map of element to raw damage value (for FLAT formula calculation)
     * @return Map of "ELEMENT_X" -> combined multiplier
     */
    private Map<String, Double> getPlayerElementDefense(Player player, Map<Element, Double> elemRaw) {
        Map<String, Double> mods = new LinkedHashMap<>();
        
        MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
        if (data == null || elemRaw.isEmpty()) return mods;
        
        DamageConfig config = plugin.getDamageConfig();
        
        for (Map.Entry<Element, Double> entry : elemRaw.entrySet()) {
            Element el = entry.getKey();
            double elementDamage = entry.getValue();  // Per-element raw damage
            String elId = el.getId().toUpperCase();
            
            // {ELEMENT}_DEFENSE: flat defense (uses formula from config)
            double flatDefense = data.getStatMap().getStat(elId + "_DEFENSE");
            
            // {ELEMENT}_DEFENSE_PERCENT: percentage reduction
            double percentDefense = data.getStatMap().getStat(elId + "_DEFENSE_PERCENT");
            
            // {ELEMENT}_WEAKNESS: percentage INCREASED damage taken
            double weakness = data.getStatMap().getStat(elId + "_WEAKNESS");
            
            // Calculate combined multiplier
            double multiplier = 1.0;
            
            // 1. Apply weakness first (increases damage taken)
            if (weakness > 1e-6) {
                multiplier *= config.calculateWeaknessMultiplier(weakness);
            }
            
            // 2. Apply global ELEMENTAL_DEFENSE (percent reduction) - Affects ALL elements
            double globalElemDefense = data.getStatMap().getStat("ELEMENTAL_DEFENSE");
            if (globalElemDefense > 1e-6) {
                multiplier *= Math.max(0.0, 1.0 - (globalElemDefense / 100.0));
            }
            
            // 3. Apply flat defense formula (uses config's formula type: DIMINISHING/LINEAR/FLAT)
            if (flatDefense > 1e-6) {
                switch (config.getFlatDefenseType()) {
                    case FLAT:
                        // For FLAT: dmg - def, converted to multiplier
                        // multiplier = max(1, damage - defense) / damage
                        if (elementDamage > 1e-6) {
                            double resultDamage = Math.max(1.0, elementDamage - flatDefense);
                            multiplier *= (resultDamage / elementDamage);
                        }
                        break;
                    case LINEAR:
                        // LINEAR: 1 - def/100
                        multiplier *= Math.max(0.0, 1.0 - (flatDefense / 100.0));
                        break;
                    case DIMINISHING:
                    default:
                        // DIMINISHING: 1 - def/(def+base)
                        double base = config.getFlatDefenseBase();
                        multiplier *= 1.0 - (flatDefense / (flatDefense + base));
                        break;
                }
            }
            
            // 3. Apply percentage reduction (with cap from config)
            if (percentDefense > 1e-6) {
                multiplier *= config.calculatePercentDefenseMultiplier(percentDefense);
            }
            
            // Clamp multiplier
            multiplier = Math.max(0.0, Math.min(2.0, multiplier));
            
            // Only add if different from 1.0
            if (Math.abs(multiplier - 1.0) > 1e-6) {
                mods.put("ELEMENT_" + elId, multiplier);
            }
        }
        
        return mods;
    }

    /* ======================================================================
       DATA HOLDERS
       ====================================================================== */

    public record HitContext(
        Map<Element, Double> elementDamage,
        Map<Element, Double> elementMultipliers, // Total multipliers (Def * Stat)
        Map<Element, Double> elementDefenseMultipliers, // Def only (for indicator)
        Map<DamageType, Double> typeMultipliers,
        double nonElemDamage,
        double nonElemMul,
        Set<Element> elementCrits,
        boolean nonElemCrit,
        boolean skillCrit,
        boolean isPureElemental,
        String targetId,
        Set<Element> immuneElements
    ) {
        public boolean hasImmunity() { return !immuneElements.isEmpty(); }
    }
}
