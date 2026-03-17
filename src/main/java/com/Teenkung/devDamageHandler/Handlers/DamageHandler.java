package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.API.DamageData;
import com.Teenkung.devDamageHandler.API.Events.*;
import com.Teenkung.devDamageHandler.DevDamageHandler;

import com.Teenkung.devDamageHandler.Indicator.IndicatorLine;
import com.Teenkung.devDamageHandler.Indicator.IndicatorSettings;
import com.Teenkung.devDamageHandler.Util.Msg;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.event.IndicatorDisplayEvent;
import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import io.lumine.mythic.lib.api.player.MMOPlayerData;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

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

    // Fire damage causes (matching MythicLib's DamageReduction)
    private static final Set<EntityDamageEvent.DamageCause> FIRE_DAMAGE_CAUSES = Set.of(
        EntityDamageEvent.DamageCause.FIRE,
        EntityDamageEvent.DamageCause.FIRE_TICK,
        EntityDamageEvent.DamageCause.LAVA,
        EntityDamageEvent.DamageCause.MELTING
    );

    // Context for passing data from DevDamageMechanic (synchronous)
    public static final ThreadLocal<Map<Element, Double>> pendingElements = new ThreadLocal<>();
    public static final ThreadLocal<Set<DamageType>> pendingTypes = new ThreadLocal<>();
    public static final ThreadLocal<org.bukkit.command.CommandSender> debugOverride = new ThreadLocal<>();
    public static final ThreadLocal<Double> pendingMultiplier = new ThreadLocal<>();
    public static final ThreadLocal<Double> pendingFlatDamage = new ThreadLocal<>();
    public static final ThreadLocal<LivingEntity> pendingAttacker = new ThreadLocal<>();

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
       ENVIRONMENTAL DAMAGE REDUCTIONS (fire, fall)
       Replaces MythicLib's DamageReduction for cause-specific stats.
       ====================================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        // Skip combat events (handled by onDamageModify / onEntityDamage)
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof Player player)) return;

        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (data == null) return;

        DamageConfig config = plugin.getDamageConfig();
        double multiplier = 1.0;

        // FIRE_DAMAGE_REDUCTION: applies to FIRE, FIRE_TICK, LAVA, MELTING
        if (FIRE_DAMAGE_CAUSES.contains(event.getCause())) {
            double fireReduction = data.getStatMap().getStat("FIRE_DAMAGE_REDUCTION");
            if (fireReduction > 1e-6) {
                multiplier *= config.calculatePercentDefenseMultiplier(fireReduction);
            }
        }

        // FALL_DAMAGE_REDUCTION: applies to FALL
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double fallReduction = data.getStatMap().getStat("FALL_DAMAGE_REDUCTION");
            if (fallReduction > 1e-6) {
                multiplier *= config.calculatePercentDefenseMultiplier(fallReduction);
            }
        }

        if (Math.abs(multiplier - 1.0) > 1e-6) {
            event.setDamage(event.getDamage() * multiplier);
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
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Determine attacker
        org.bukkit.entity.Entity attackerEntity = null;
        if (event instanceof EntityDamageByEntityEvent edbe) {
            attackerEntity = edbe.getDamager();
            // Skip player attackers UNLESS using /ddh damage command (debugOverride set)
            if (attackerEntity instanceof Player && debugOverride.get() == null) return;
        }

        // Check for pending attacker (noanger/dev-damage override)
        // This allows us to process "noanger" damage (which is generic/null source) as if it came from the caster
        LivingEntity forcedAttacker = pendingAttacker.get();
        if (forcedAttacker != null) {
            attackerEntity = forcedAttacker;
        }
        
        if (!(attackerEntity instanceof LivingEntity attacker)) return;

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
                displayIndicatorsWrapper(target, attacker, dmg, ctx, null, event.getFinalDamage(), true);
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
        StatProvider statProvider = createStatProvider(attacker);

        // 2. Determine Debug Context
        DebugContext debugCtx = resolveDebugContext(attacker, victim);

        // 3. Create DebugReport if debug is enabled
        DebugReport report = debugCtx.enabled ? new DebugReport() : null;

        // 4. Modifiers (MythicMobs - on victim)
        Map<String, Double> victimMmMods = getMythicDamageModifiers(victim);
        Map<String, Double> attackerMmMods = getMythicDamageModifiers(attacker);
        
        // Check overrides
        Map<Element, Double> elemRawOverride = pendingElements.get();
        Set<DamageType> typeOverride = pendingTypes.get();

        // 5. Calculate Logic
        Set<DamageType> activeTypes = typeOverride != null ? typeOverride : dmg.collectTypes();
        if (activeTypes == null) activeTypes = new HashSet<>();
        
        // Elements - collect initial element data
        Map<Element, Double> elemRaw = collectElementDamage(dmg, elemRawOverride);

        // Populate debug report context and raw damage
        if (report != null) {
            String attackerId = getTargetId(attacker);
            String targetId = getTargetId(victim);
            String attackerType = attacker instanceof Player ? "Player" : "Mob";
            String targetType = victim instanceof Player ? "Player" : "Mob";
            report.setContext(attackerId, targetId, attackerType, targetType);

            // Capture raw packets BEFORE any modifications
            List<DebugReport.PacketInfo> rawPkts = new ArrayList<>();
            for (DamagePacket p : dmg.getPackets()) {
                rawPkts.add(new DebugReport.PacketInfo(
                    p.getFinalValue(),
                    p.getTypes().toString(),
                    p.getElement() != null ? p.getElement().getId() : "NONE"
                ));
            }
            report.setRawDamage(originalDamage, rawPkts);

            // Collect attacker stats
            collectAttackerStatsForReport(attacker, elemRaw.keySet(), report);

            // Store MM mods
            report.setMythicMobsMods(attackerMmMods, victimMmMods);
        }

        // Fire Pre-Process Event (allows modification before modifiers)
        DamagePreProcessEvent preProcessEvent = new DamagePreProcessEvent(
            attacker, victim, dmg, elemRaw, activeTypes, isPlayerAttack
        );
        Bukkit.getPluginManager().callEvent(preProcessEvent);
        if (preProcessEvent.isCancelled()) {
            return; // Cancel damage processing
        }

        // Apply pre-process multiplier if changed
        if (Math.abs(preProcessEvent.getDamageMultiplier() - 1.0) > 1e-6) {
            for (DamagePacket packet : dmg.getPackets()) {
                packet.setValue(packet.getValue() * preProcessEvent.getDamageMultiplier());
            }
        }

        // Apply victim player defense stats
        boolean applyMobVsPlayerDefense = !isPlayerAttack && victim instanceof Player && !(attacker instanceof Player);
        boolean applyPvpDefense = isPlayerAttack && victim instanceof Player;
        if (applyMobVsPlayerDefense || applyPvpDefense) {
            Player playerVictim = (Player) victim;
            double defenseBaseDamage = dmg.getPackets().stream()
                .mapToDouble(DamagePacket::getFinalValue)
                .sum();
            PlayerDefenseApplicator.applyDefense(
                playerVictim,
                dmg,
                mobConfig,
                defenseBaseDamage,
                plugin.getDamageConfig(),
                applyPvpDefense,
                report
            );
        }

        DamageModifierApplicator applicator = new DamageModifierApplicator(
            statProvider, 
            dmg,
            victimMmMods, 
            report
        );
        
        // Apply type and stat modifiers
        Map<DamageType, Double> typeMultipliers = applicator.applyTypeModifiers();
        applyStatMultipliers(dmg, applicator.getStatMultipliers(activeTypes));

        // Apply context-based offensive bonuses (PVP/PVE_DAMAGE, UNDEAD_DAMAGE)
        // These were previously handled by MythicLib's LegacyAttackEffects (now unregistered)
        if (isPlayerAttack) {
            applyContextBonuses(dmg, statProvider, victim, report);
        }

        // Merge player victim's element defense stats into modifiers (player attack flow).
        if (!applyMobVsPlayerDefense) {
            mergePlayerElementDefense(victim, elemRaw, victimMmMods, report);
        }

        // Track if attack originally had elemental damage BEFORE modifiers
        boolean originallyHadElements = !elemRaw.isEmpty();
        double originalElementalTotal = elemRaw.values().stream().mapToDouble(Double::doubleValue).sum();

        // Apply Element modifiers
        DamageModifierApplicator.ElementModifierResult elementResult = applicator.applyElementModifiers(elemRaw);

        // Fire immunity event if there are immune elements
        if (!elementResult.immuneElements().isEmpty()) {
            final Map<Element, Double> elemRawForLambda = elemRaw;
            double blockedDamage = elementResult.immuneElements().stream()
                .mapToDouble(el -> elemRawForLambda.getOrDefault(el, 0.0))
                .sum();
            ElementImmunityEvent immunityEvent = new ElementImmunityEvent(
                attacker, victim, dmg, elementResult.immuneElements(), blockedDamage
            );
            Bukkit.getPluginManager().callEvent(immunityEvent);
        }

        // Recalculate elemRaw after element modifiers were applied
        elemRaw = recalculateElementDamage(dmg);

        // Non-Elemental / Carrier packet handling
        NonElementalContext nonElemCtx = processNonElementalDamage(dmg, elemRaw, originallyHadElements, originalElementalTotal);

        // Carrier bonus transfer
        if (report != null && nonElemCtx.carrierBonusTransferred > 0) {
            report.setCarrierTransfer(nonElemCtx.carrierBonusTransferred);
        }

        // Crits - Unified handling for Weapon, Skill, and Elemental crits
        DamageModifierApplicator.CritResult crits = applicator.calculateCrits(elemRaw, nonElemCtx.damage, activeTypes);

        // Fire Critical Hit Events
        fireCritEvents(attacker, victim, dmg, crits);

        // Apply elemental crit multipliers
        applyElementalCrits(dmg, crits, elemRaw);

        // Apply non-elemental modifiers
        double nonElemMul = applicator.applyNoneElementModifier(nonElemCtx.damage, activeTypes);

        // Apply pending LibReforge modifiers
        double nonElemDamage = applyPendingModifiers(dmg, nonElemCtx.damage, report);

        // Apply non-elemental crit multiplier
        applyNonElementalCrit(dmg, crits);

        // Sound effects for critical hits
        playCritSound(crits, attacker);

        // Build and store context
        String targetId = getTargetId(victim);
        HitContext ctx = buildHitContext(elemRaw, elementResult, typeMultipliers, nonElemDamage, nonElemMul,
                                         crits, nonElemCtx.hasElementalDamage, targetId);
        plugin.rememberHit(dmg, ctx);

        // Fire DamageCalculated API Events
        fireDamageCalculatedEvent(attacker, victim, dmg, ctx, elemRaw, elementResult, crits, victimMmMods, isPlayerAttack);

        // Render debug report
        if (report != null) {
            // Capture final packets
            List<DebugReport.PacketInfo> finalPkts = new ArrayList<>();
            double finalTotal = 0;
            for (DamagePacket p : dmg.getPackets()) {
                finalPkts.add(new DebugReport.PacketInfo(
                    p.getFinalValue(),
                    p.getTypes().toString(),
                    p.getElement() != null ? p.getElement().getId() : "NONE"
                ));
                finalTotal += p.getFinalValue();
            }
            report.setFinalDamage(finalTotal, finalPkts);
            report.render(debugCtx.recipient);
        }
    }

    /**
     * Fire critical hit events for the API.
     */
    private void fireCritEvents(LivingEntity attacker, LivingEntity victim, DamageMetadata dmg,
                                DamageModifierApplicator.CritResult crits) {
        // Elemental crits
        if (crits.isElemCrit() && !crits.elementCritMults().isEmpty()) {
            double avgMul = crits.elementCritMults().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(1.0);
            CriticalHitEvent elemCritEvent = new CriticalHitEvent(
                attacker, victim, dmg,
                CriticalHitEvent.CritType.ELEMENTAL,
                crits.elementCritMults().keySet(),
                avgMul
            );
            Bukkit.getPluginManager().callEvent(elemCritEvent);
        }

        // Weapon/Skill crit (non-elemental)
        if (crits.isNonElemCrit()) {
            CriticalHitEvent.CritType critType = crits.triggeredTypes().contains(DamageModifierApplicator.CritType.SKILL)
                ? CriticalHitEvent.CritType.SKILL
                : CriticalHitEvent.CritType.WEAPON;
            CriticalHitEvent weaponCritEvent = new CriticalHitEvent(
                attacker, victim, dmg,
                critType,
                Collections.emptySet(),
                crits.nonElemCritMul()
            );
            Bukkit.getPluginManager().callEvent(weaponCritEvent);
        }
    }

    /**
     * Fire the appropriate DamageCalculatedEvent (Player or Mob).
     */
    private void fireDamageCalculatedEvent(LivingEntity attacker, LivingEntity victim, DamageMetadata dmg,
                                           HitContext ctx, Map<Element, Double> elemRaw,
                                           DamageModifierApplicator.ElementModifierResult elementResult,
                                           DamageModifierApplicator.CritResult crits,
                                           Map<String, Double> victimMmMods, boolean isPlayerAttack) {
        // Build DamageData
        DamageData.Builder builder = new DamageData.Builder()
            .targetId(ctx.targetId())
            .critFlags(crits.isNonElemCrit(), crits.triggeredTypes().contains(DamageModifierApplicator.CritType.SKILL))
            .mythicDamageModifiers(victimMmMods);

        // Build element multiplier maps for fromHitContext
        Map<Element, Double> mobMul = new HashMap<>();
        Map<Element, Double> critMul = new HashMap<>();
        Map<Element, Double> statMul = new HashMap<>();

        for (Element el : elemRaw.keySet()) {
            mobMul.put(el, elementResult.multipliers().getOrDefault(el, 1.0));
            critMul.put(el, crits.elementCritMults().getOrDefault(el, 1.0));
            statMul.put(el, 1.0); // Already applied in element multipliers
        }

        builder.fromHitContext(elemRaw, mobMul, critMul, statMul);
        builder.fromDamageMetadata(dmg, null);

        DamageData damageData = builder.build();

        // Fire the appropriate event
        if (attacker instanceof Player player) {
            PlayerDamageCalculatedEvent event = new PlayerDamageCalculatedEvent(player, victim, damageData, dmg);
            Bukkit.getPluginManager().callEvent(event);
        } else {
            MobDamageCalculatedEvent event = new MobDamageCalculatedEvent(attacker, victim, damageData);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    // --- Helper methods for processDamage ---

    private StatProvider createStatProvider(LivingEntity attacker) {
        if (attacker instanceof Player p) {
            return new PlayerStatProvider(p);
        }
        return new MobStatProvider(attacker);
    }

    private record DebugContext(boolean enabled, org.bukkit.command.CommandSender recipient) {}

    /**
     * Collect relevant attacker stats for the debug report.
     */
    private void collectAttackerStatsForReport(LivingEntity attacker, Set<Element> elements, DebugReport report) {
        if (!(attacker instanceof Player p)) return;
        MMOPlayerData data = MMOPlayerData.get(p.getUniqueId());
        if (data == null) return;

        double critChance = data.getStatMap().getStat("CRITICAL_STRIKE_CHANCE");
        double critPower = data.getStatMap().getStat("CRITICAL_STRIKE_POWER");
        if (critChance > 0 || critPower > 0) {
            report.addAttackerStat("Weapon Crit", DamageDebug.fmt(critChance) + "% chance, " + DamageDebug.fmt(critPower) + "% power");
        }

        double skillCritChance = data.getStatMap().getStat("SKILL_CRITICAL_STRIKE_CHANCE");
        double skillCritPower = data.getStatMap().getStat("SKILL_CRITICAL_STRIKE_POWER");
        if (skillCritChance > 0 || skillCritPower > 0) {
            report.addAttackerStat("Skill Crit", DamageDebug.fmt(skillCritChance) + "% chance, " + DamageDebug.fmt(skillCritPower) + "% power");
        }

        double elemCritChance = data.getStatMap().getStat("ELEMENTAL_CRITICAL_STRIKE_CHANCE");
        double elemCritPower = data.getStatMap().getStat("ELEMENTAL_CRITICAL_STRIKE_POWER");
        if (elemCritChance > 0 || elemCritPower > 0) {
            report.addAttackerStat("Elem Crit", DamageDebug.fmt(elemCritChance) + "% chance, " + DamageDebug.fmt(elemCritPower) + "% power");
        }

        for (Element el : elements) {
            String elId = el.getId().toUpperCase();
            double elemDmg = data.getStatMap().getStat(elId + "_DAMAGE");
            double elemPct = data.getStatMap().getStat(elId + "_DAMAGE_PERCENT");
            double addElem = data.getStatMap().getStat("ADDITIONAL_ELEMENTAL_DAMAGE");
            StringBuilder val = new StringBuilder();
            if (elemDmg > 0) val.append(DamageDebug.fmt(elemDmg)).append(" base ");
            if (elemPct > 0) val.append("+").append(DamageDebug.fmt(elemPct)).append("% ");
            if (addElem > 0) val.append("+").append(DamageDebug.fmt(addElem)).append("% ALL");
            if (!val.isEmpty()) {
                report.addAttackerStat(elId + " Elem", val.toString().trim());
            }
        }
    }

    private DebugContext resolveDebugContext(LivingEntity attacker, LivingEntity victim) {
        if (debugOverride.get() != null) {
            return new DebugContext(true, debugOverride.get());
        }
        if (attacker instanceof Player p && plugin.isDebugging(p)) {
            return new DebugContext(true, p);
        }
        if (victim instanceof Player p && plugin.isDebugging(p)) {
            return new DebugContext(true, p);
        }
        return new DebugContext(false, null);
    }

    private void applyStatMultipliers(DamageMetadata dmg, Map<DamageType, Double> statMultipliers) {
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
            }
        }
    }

    /**
     * Apply context-based offensive bonuses that MythicLib's LegacyAttackEffects used to handle.
     * These are global additive modifiers (apply to all damage packets):
     * - PVP_DAMAGE or PVE_DAMAGE depending on whether victim is a player
     * - UNDEAD_DAMAGE if victim is an undead entity
     */
    private void applyContextBonuses(DamageMetadata dmg, StatProvider statProvider,
                                     LivingEntity victim, DebugReport report) {
        // PVP_DAMAGE or PVE_DAMAGE
        boolean isPvp = victim instanceof Player;
        String contextStat = isPvp ? "PVP_DAMAGE" : "PVE_DAMAGE";
        double contextValue = statProvider.apply(contextStat);
        if (contextValue > 1e-6) {
            dmg.additiveModifier(contextValue / 100.0);
            if (report != null) {
                report.addStatBonus(contextStat, contextValue);
            }
        }

        // UNDEAD_DAMAGE
        if (UtilityMethods.isUndead(victim)) {
            double undeadValue = statProvider.apply("UNDEAD_DAMAGE");
            if (undeadValue > 1e-6) {
                dmg.additiveModifier(undeadValue / 100.0);
                if (report != null) {
                    report.addStatBonus("UNDEAD_DAMAGE", undeadValue);
                }
            }
        }
    }

    private Map<Element, Double> collectElementDamage(DamageMetadata dmg, Map<Element, Double> override) {
        Map<Element, Double> elemRaw = new LinkedHashMap<>();
        if (override != null) {
            elemRaw.putAll(override);
        } else {
            for (DamagePacket packet : dmg.getPackets()) {
                Element elem = packet.getElement();
                if (elem != null) {
                    elemRaw.merge(elem, packet.getFinalValue(), Double::sum);
                }
            }
        }
        return elemRaw;
    }

    private void mergePlayerElementDefense(LivingEntity victim, Map<Element, Double> elemRaw,
                                           Map<String, Double> victimMmMods, DebugReport report) {
        if (!(victim instanceof Player playerVictim) || elemRaw.isEmpty()) return;

        Map<String, Double> playerElemDefense = getPlayerElementDefense(playerVictim, elemRaw);
        if (playerElemDefense.isEmpty()) return;

        for (Map.Entry<String, Double> e : playerElemDefense.entrySet()) {
            victimMmMods.putIfAbsent(e.getKey(), e.getValue());
        }

        if (report != null) {
            for (Map.Entry<String, Double> e : playerElemDefense.entrySet()) {
                String elName = e.getKey().replace("ELEMENT_", "");
                double mul = e.getValue();
                double reductionPct = (1.0 - mul) * 100;
                report.addPlayerElementDefense(elName, reductionPct, mul);
            }
        }
    }

    private Map<Element, Double> recalculateElementDamage(DamageMetadata dmg) {
        Map<Element, Double> elemRaw = new LinkedHashMap<>();
        for (DamagePacket packet : dmg.getPackets()) {
            Element elem = packet.getElement();
            if (elem != null) {
                elemRaw.merge(elem, packet.getFinalValue(), Double::sum);
            }
        }
        return elemRaw;
    }

    private record NonElementalContext(double damage, boolean hasElementalDamage, double carrierBonusTransferred) {}

    private NonElementalContext processNonElementalDamage(DamageMetadata dmg, Map<Element, Double> elemRaw,
                                                          boolean originallyHadElements, double originalElementalTotal) {
        double totalElemental = elemRaw.values().stream().mapToDouble(Double::doubleValue).sum();
        double packetsSum = dmg.getPackets().stream().mapToDouble(DamagePacket::getFinalValue).sum();
        double nonElemRaw = packetsSum - totalElemental;
        
        boolean hasElementalDamage = originallyHadElements && originalElementalTotal > 0;
        double nonElemDamage = hasElementalDamage ? 0 : Math.max(0, nonElemRaw);
        double carrierBonusTransferred = 0;

        // Config options for carrier handling
        boolean shouldIgnoreCarrier = plugin.getDamageConfig().isIgnoreCarrierOnElemental();
        boolean shouldTransfer = plugin.getDamageConfig().isTransferCarrierToElemental();

        // When weapon has elemental damage and we should ignore carrier
        if (hasElementalDamage && shouldIgnoreCarrier) {
            // Transfer carrier packet damage to elemental packets if enabled
            // This preserves enchantment bonuses (Sharpness, Smite, etc.) that are in the carrier
            if (nonElemRaw > 0 && shouldTransfer) {
                carrierBonusTransferred = nonElemRaw;

                // Calculate total elemental damage for proportional distribution
                double totalElemDmg = elemRaw.values().stream().mapToDouble(Double::doubleValue).sum();

                if (totalElemDmg > 0) {
                    // Pre-calculate bonus per element (to avoid issues with multiple packets per element)
                    Map<Element, Double> bonusPerElement = new HashMap<>();
                    for (Map.Entry<Element, Double> entry : elemRaw.entrySet()) {
                        Element elem = entry.getKey();
                        double elemDmg = entry.getValue();
                        double proportion = elemDmg / totalElemDmg;
                        double bonusForThisElement = carrierBonusTransferred * proportion;
                        bonusPerElement.put(elem, bonusForThisElement);
                    }

                    // Track which elements we've already applied bonus to (for multiple packets per element)
                    Set<Element> appliedElements = new HashSet<>();

                    // Apply bonus to packets - only once per element
                    for (DamagePacket packet : dmg.getPackets()) {
                        Element elem = packet.getElement();
                        if (elem != null && bonusPerElement.containsKey(elem) && !appliedElements.contains(elem)) {
                            double bonusForThisElement = bonusPerElement.get(elem);
                            if (bonusForThisElement > 0) {
                                // Direct flat addition to packet value
                                packet.setValue(packet.getValue() + bonusForThisElement);
                                // Update elemRaw to reflect the bonus
                                elemRaw.put(elem, elemRaw.get(elem) + bonusForThisElement);
                                // Mark as applied
                                appliedElements.add(elem);
                            }
                        }
                    }
                }
            }

            // Zero out carrier packets (whether or not we transferred)
            for (DamagePacket packet : dmg.getPackets()) {
                if (packet.getElement() == null && packet.getFinalValue() > 0) {
                    for (DamageType type : packet.getTypes()) {
                        dmg.multiplicativeModifier(0, type);
                    }
                }
            }
        }
        
        return new NonElementalContext(nonElemDamage, hasElementalDamage, carrierBonusTransferred);
    }

    private void applyElementalCrits(DamageMetadata dmg, DamageModifierApplicator.CritResult crits,
                                     Map<Element, Double> elemRaw) {
        crits.elementCritMults().forEach((el, mul) -> {
            dmg.multiplicativeModifier(mul, el);
            elemRaw.computeIfPresent(el, (k, v) -> v * mul);
        });
    }

    private double applyPendingModifiers(DamageMetadata dmg, double nonElemDamage, DebugReport report) {
        nonElemDamage = applyPendingFlatDamage(dmg, nonElemDamage, report);
        applyPendingMultiplier(dmg, report);
        return nonElemDamage;
    }

    private double applyPendingFlatDamage(DamageMetadata dmg, double nonElemDamage, DebugReport report) {
        Double pendingFlat = pendingFlatDamage.get();
        if (pendingFlat == null || Math.abs(pendingFlat) <= 1e-6) {
            return nonElemDamage;
        }

        pendingFlatDamage.remove();
        nonElemDamage += pendingFlat;

        boolean added = false;
        for (DamagePacket packet : dmg.getPackets()) {
            if (packet.getElement() == null) {
                packet.setValue(packet.getValue() + pendingFlat);
                added = true;
                break;
            }
        }
        if (!added && !dmg.getPackets().isEmpty()) {
            dmg.getPackets().get(0).setValue(dmg.getPackets().get(0).getValue() + pendingFlat);
        }

        if (report != null) {
            report.addPendingModifier("LibReforge Flat Damage", "+" + DamageDebug.fmt(pendingFlat));
        }

        return nonElemDamage;
    }

    private void applyPendingMultiplier(DamageMetadata dmg, DebugReport report) {
        Double pendingMul = pendingMultiplier.get();
        if (pendingMul == null || Math.abs(pendingMul - 1.0) <= 1e-6) {
            return;
        }

        pendingMultiplier.remove();

        if (Math.abs(pendingMul) < 1e-6) {
            for (DamagePacket packet : dmg.getPackets()) {
                packet.setValue(0);
            }
        } else {
            for (DamagePacket packet : dmg.getPackets()) {
                packet.setValue(packet.getValue() * pendingMul);
            }
        }

        if (report != null) {
            report.addPendingModifier("LibReforge Multiplier", "×" + DamageDebug.fmt(pendingMul));
        }
    }

    private void applyNonElementalCrit(DamageMetadata dmg, DamageModifierApplicator.CritResult crits) {
        if (crits.nonElemCritMul() <= 1.0) return;

        for (DamagePacket packet : dmg.getPackets()) {
            if (packet.getElement() == null && packet.getFinalValue() > 0) {
                if (!packet.getTypes().isEmpty()) {
                    dmg.multiplicativeModifier(crits.nonElemCritMul(), packet.getTypes().iterator().next());
                } else {
                    dmg.multiplicativeModifier(crits.nonElemCritMul(), DamageType.PHYSICAL);
                }
                break;
            }
        }
    }

    private void playCritSound(DamageModifierApplicator.CritResult crits, LivingEntity attacker) {
        if ((crits.isElemCrit() || crits.isNonElemCrit()) && attacker instanceof Player player) {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.player.attack.crit"));
            player.playSound(attacker, sound, 1, 1);
        }
    }

    private HitContext buildHitContext(Map<Element, Double> elemRaw,
                                       DamageModifierApplicator.ElementModifierResult elementResult,
                                       Map<DamageType, Double> typeMultipliers, double nonElemDamage,
                                       double nonElemMul, DamageModifierApplicator.CritResult crits,
                                       boolean hasElementalDamage, String targetId) {
        return new HitContext(
            new HashMap<>(elemRaw),
            elementResult.multipliers(),
            elementResult.defenseMultipliers(),
            typeMultipliers,
            nonElemDamage,
            nonElemMul,
            crits.elementCritMults().keySet(),
            crits.isNonElemCrit(),
            crits.triggeredTypes().contains(DamageModifierApplicator.CritType.SKILL),
            hasElementalDamage,
            targetId,
            elementResult.immuneElements()
        );
    }

    // Debug output is now handled by DebugReport.render() at the end of processDamage.
    // The old printDebugOutput method is no longer needed.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageShow(PlayerAttackEvent event) {
        DamageMetadata dmg = event.getDamage();
        HitContext ctx = plugin.consumeHit(dmg);
        if (ctx == null) return;

        // Display
        displayIndicatorsWrapper(event.getEntity(), event.getAttacker().getEntity(), dmg, ctx, null, null, false);
    }
    
    /* ======================================================================
       GLOBAL DAMAGE LISTENER (VANILLA FALLBACK)
       ====================================================================== */

    // Tracks when an indicator was last shown for an entity to prevent duplicates
    private final Map<org.bukkit.entity.Entity, Long> lastIndicatorTick = 
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlobalDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        
        // Check if we already showed an indicator this tick (handled by DDH core)
        long currentTick = target.getWorld().getFullTime();
        Long lastTick = lastIndicatorTick.get(target);
        
        if (lastTick != null && lastTick == currentTick) {
            return;
        }
        
        // Skip if damage is zero or negative
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0.001) return;

        // Construct a simple vanilla context
        HitContext vanillaCtx = new HitContext(
            Collections.emptyMap(), // No elements
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            finalDamage,            // All damage is non-elemental
            1.0,                    // Multiplier
            Collections.emptySet(), // No elem crits
            false,                  // No non-elem crit
            false,                  // No skill crit
            false,                  // Not pure elemental
            "VANILLA_" + event.getCause().name(),
            Collections.emptySet()
        );
        
        // Create dummy metadata for the scaler
        DamageMetadata dummyMeta = new DamageMetadata(finalDamage, DamageType.PHYSICAL);
        
        // Display
        displayIndicatorsWrapper(target, null, dummyMeta, vanillaCtx, Collections.emptySet(), finalDamage, true);
    }

    private void displayIndicatorsWrapper(LivingEntity target, LivingEntity attacker, DamageMetadata dmg, HitContext ctx,
                                          Set<DamageType> typeOverride, Double bukkitFinalDamage, boolean forceBukkitScaling) {
        // Mark that we handled this entity this tick
        lastIndicatorTick.put(target, target.getWorld().getFullTime());

        IndicatorSettings settings = plugin.getIndicatorSettings();
        if (settings == null || !settings.enabled) return;
        
        // Build lines
        Set<DamageType> baseTypes = (typeOverride != null) ? typeOverride : dmg.collectTypes();
        
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
        double packetDamage = dmg.getPackets().stream().mapToDouble(DamagePacket::getFinalValue).sum();
        
        // Safety fallback for vanilla contexts or empty packets
        if (dmg.getPackets().isEmpty()) {
            packetDamage = ctx.nonElemDamage();
        }

        double effectiveBukkitDamage = (bukkitFinalDamage != null) ? bukkitFinalDamage : packetDamage;
        double scaleTarget = forceBukkitScaling
            ? effectiveBukkitDamage
            : IndicatorBuilder.getScalingTarget(settings, dmg, lines, effectiveBukkitDamage);
        lines = IndicatorBuilder.scaleLines(lines, scaleTarget);

        // Fire indicator display event
        DamageIndicatorDisplayEvent indicatorEvent = new DamageIndicatorDisplayEvent(
            target, attacker, lines, effectiveBukkitDamage
        );
        Bukkit.getPluginManager().callEvent(indicatorEvent);

        if (indicatorEvent.isCancelled()) {
            return; // Don't show indicators
        }

        // Use potentially modified lines from the event
        lines = indicatorEvent.getIndicatorLines();

        // Indicator debug lines are now part of the DebugReport, printed in §7.
        // We keep IndicatorBuilder.printDebugLines for standalone indicator debugging.
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
