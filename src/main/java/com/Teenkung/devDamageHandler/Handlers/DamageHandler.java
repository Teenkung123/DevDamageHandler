package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.Config.CombineMode;
import com.Teenkung.devDamageHandler.Config.ElementResolver;
import com.Teenkung.devDamageHandler.Config.MultiplierUtil;
import com.Teenkung.devDamageHandler.Config.TypeResolver;
import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Indicator.IndicatorLine;
import com.Teenkung.devDamageHandler.Indicator.IndicatorSettings;
import com.Teenkung.devDamageHandler.Util.Msg;
import com.Teenkung.devDamageHandler.Util.PacketAccess;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.lib.api.event.IndicatorDisplayEvent;
import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main damage pipeline and indicator assembly for DevDamageHandler.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Pre‑process MythicLib damage (flat tweaks when any elemental damage is present).</li>
 *   <li>Apply per-type modifiers (from plugin config) using a configurable combine mode.</li>
 *   <li>Apply per-element modifiers from three sources (multiplied together):
 *       <ol>
 *         <li>Element Templates (by mob type) – if configured via TemplateManager</li>
 *         <li>Per-mob plugin config overrides</li>
 *         <li>MythicMobs DamageModifiers (keys: {@code ELEMENT_<ID>} / {@code <ID>})</li>
 *       </ol>
 *   </li>
 *   <li>Assemble damage indicator lines (elements + non-element), with optional scaling target.</li>
 *   <li>Emit debug dumps when player debug mode is enabled.</li>
 * </ul>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>Physical crit suppression was removed; we no longer tamper with crit effects.</li>
 *   <li>This handler supports MythicMobs, players, and vanilla entities via a unified {@code targetKey}.</li>
 * </ul>
 */
public class DamageHandler implements Listener {

    private static final double EPS = 1e-6;

    private final DevDamageHandler plugin;

    public DamageHandler(DevDamageHandler plugin) {
        this.plugin = plugin;
    }

    /* ======================================================================
       EVENT FILTERS
       ====================================================================== */

    /**
     * Cancels MythicLib holograms which would display as zero after custom font decoding.
     */
    @EventHandler
    public void onIndicatorDisplay(IndicatorDisplayEvent event) {
        String number = plugin.getFontCodec().decodeNumbers(event.getMessage());
        if ("0".equals(number) || ".0".equals(number)) {
            event.setCancelled(true);
        }
    }

    /* ======================================================================
       MAIN DAMAGE PIPELINE
       ====================================================================== */

    /**
     * Mutates MythicLib damage according to our stacking/config, then stores a per-hit context
     * for the MONITOR phase to render indicators.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageModify(PlayerAttackEvent event) {
        String targetId = targetKey(event.getEntity());
        DamageMetadata dmg = event.getDamage();
        Map<Element, Double> elemRaw = dmg.mapElementalDamage();

        boolean debug = plugin.getPlayerDebugMode(event.getAttacker().getPlayer());
        DebugPrinter dbg = debug ? new DebugPrinter(targetId, dmg, elemRaw) : null;

        // Small up-front adjustments when elemental damage is present
        applyFlatTweaks(dmg, elemRaw);

        // Resolvers & config
        TypeResolver    typeResolver = plugin.getTypeResolver(targetId);
        ElementResolver elemResolver = plugin.getElementResolver(targetId);
        Map<DamageType, Double> typeMods = plugin.getConfigLoader().getDamageTypeModifiers(targetId);

        // Merge element modifiers: template -> per-mob plugin overrides
        Map<Element, Double> tplMods = (plugin.getTemplateManager() != null)
                ? plugin.getTemplateManager().getForMob(targetId)
                : Collections.emptyMap();
        Map<Element, Double> cfgMods = plugin.getConfigLoader().getElementalModifiers(targetId);
        Map<Element, Double> mergedElemMods = mergeElementMods(tplMods, cfgMods);

        if (debug) {
            String tplName = (plugin.getTemplateManager() != null)
                    ? plugin.getTemplateManager().getTemplateForMob(targetId) : null;
            StringBuilder sb = new StringBuilder("<yellow>Element config sources (pre-MythicMobs):</yellow>\n");
            sb.append("<gray>Template:</gray> <white>").append(tplName == null ? "(none)" : tplName).append("</white>\n");
            if (tplMods.isEmpty()) sb.append("  <dark_gray>(template empty)</dark_gray>\n");
            else tplMods.forEach((el, v) -> sb.append("  TPL ").append(el.getId()).append(": ").append(fmt(v)).append("\n"));

            if (cfgMods == null || cfgMods.isEmpty()) sb.append("<gray>Plugin per-mob:</gray> <dark_gray>(none)</dark_gray>\n");
            else {
                sb.append("<gray>Plugin per-mob:</gray>\n");
                cfgMods.forEach((el, v) -> sb.append("  CFG ").append(el.getId()).append(": ").append(fmt(v)).append("\n"));
            }

            if (mergedElemMods.isEmpty()) sb.append("<gray>Merged:</gray> <dark_gray>(none)</dark_gray>\n");
            else {
                sb.append("<gray>Merged:</gray>\n");
                mergedElemMods.forEach((el, v) -> sb.append("  MRG ").append(el.getId()).append(": ").append(fmt(v)).append("\n"));
            }
            Msg.send(event.getAttacker().getPlayer(), sb.toString());
        }

        // Type stage
        applyTypeStage(dmg, typeResolver, typeMods, dbg);

        // Element stage (includes MythicMobs ELEMENT_* combination)
        ElementStageResult elemRes = applyElementStage(dmg, elemRaw, mergedElemMods, elemResolver, event, dbg);

        if (debug) dbg.printFinal(dmg, event.getAttacker().getPlayer());

        // Persist per-hit context for MONITOR stage (indicators)
        HitContext ctx = new HitContext(
                new HashMap<>(elemRaw),
                elemRes.mobMul(),
                elemRes.critMul(),
                elemRes.statMul(),
                dmg.isWeaponCriticalStrike(),
                dmg.isSkillCriticalStrike(),
                targetId
        );
        plugin.rememberHit(dmg, ctx);
    }

    /**
     * Builds and displays indicators after damage is finalized by other listeners.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageShow(PlayerAttackEvent event) {
        DamageMetadata dmg = event.getDamage();
        HitContext ctx = plugin.consumeHit(dmg);
        if (ctx != null) {
            pushIndicatorsFinal(event, dmg, ctx);
        }
    }

    /* ======================================================================
       STAGES
       ====================================================================== */

    /**
     * Tiny up-front adjustments to Physical/Magic when any elemental damage is present.
     */
    private void applyFlatTweaks(DamageMetadata dmg, Map<Element, Double> elem) {
        double sum = elem.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) return;

        if (dmg.getDamage(DamageType.PHYSICAL) > 0) {
            dmg.additiveModifier(-2.115, DamageType.PHYSICAL);
            if (dmg.getDamage(DamageType.PHYSICAL) < 0.1) dmg.multiplicativeModifier(0, DamageType.PHYSICAL);
        }
        if (dmg.getDamage(DamageType.MAGIC) > 0) {
            double amt = dmg.isWeaponCriticalStrike() ? -3.375 : -1.5;
            dmg.additiveModifier(amt, DamageType.MAGIC);
            if (dmg.getDamage(DamageType.MAGIC) < 0.1) dmg.multiplicativeModifier(0, DamageType.MAGIC);
        }
    }

    /**
     * Apply global per-type modifiers per packet, following the resolver's primary/flags and combine mode.
     */
    private TypeStageResult applyTypeStage(DamageMetadata dmg,
                                           TypeResolver resolver,
                                           Map<DamageType, Double> mods,
                                           DebugPrinter dbg) {
        if (mods == null || mods.isEmpty()) return new TypeStageResult(Collections.emptyMap());

        Map<DamageType, List<Double>> perTypeMul = new EnumMap<>(DamageType.class);
        Map<DamagePacket, Double> orphanMul = new IdentityHashMap<>();
        Map<DamagePacket, DebugPrinter.TypeStep> debugMap = (dbg == null) ? null : new IdentityHashMap<>();

        Set<DamageType> fallback = dmg.collectTypes().isEmpty()
                ? EnumSet.noneOf(DamageType.class)
                : EnumSet.copyOf(dmg.collectTypes());

        for (DamagePacket pkt : dmg.getPackets()) {
            DamageType[] arr = pkt.getTypes();
            Set<DamageType> types = (arr.length == 0) ? fallback : EnumSet.copyOf(Arrays.asList(arr));
            if (types.isEmpty()) continue;

            DamageType primary = resolver.pickPrimary(types);
            LinkedHashMap<DamageType, Double> used = new LinkedHashMap<>();
            if (primary != null) addIfPresent(mods, used, primary);
            if (resolver.isFlagsAffectPrimary()) {
                for (DamageType dt : types) if (dt != primary && resolver.isFlag(dt)) addIfPresent(mods, used, dt);
            }

            double mul = used.isEmpty() ? 1.0 : MultiplierUtil.combine(used.values(), resolver.getCombineMode());

            if (mul != 1.0) {
                if (arr.length == 0) {
                    orphanMul.put(pkt, mul);
                } else {
                    for (DamageType dt : types) {
                        if (!resolver.isFlag(dt)) perTypeMul.computeIfAbsent(dt, k -> new ArrayList<>()).add(mul);
                    }
                }
            }

            if (dbg != null) debugMap.put(pkt, new DebugPrinter.TypeStep(primary, used, mul, arr.length == 0));
        }

        for (Map.Entry<DamageType, List<Double>> e : perTypeMul.entrySet()) {
            double f = MultiplierUtil.combine(e.getValue(), resolver.getCombineMode());
            if (f != 1.0) {
                dmg.multiplicativeModifier(f, e.getKey());
                if (dbg != null) dbg.globalTypeMul(e.getKey(), f);
            }
        }
        for (Map.Entry<DamagePacket, Double> e : orphanMul.entrySet()) {
            PacketAccess.multiplyValue(e.getKey(), e.getValue());
            if (dbg != null) dbg.orphanMul(e.getValue());
        }

        return new TypeStageResult(debugMap);
    }

    /**
     * Apply per-element mob/crit/stat multipliers and mark elemental crits.
     * Final per-element multiplier = (template × plugin per-mob) × MythicMobs ELEMENT_*.
     *
     * Also supports MythicMobs type-like keys for non-element damage:
     *   - ELEMENT_PHYSICAL / PHYSICAL  -> applies to DamageType.PHYSICAL
     *   - ELEMENT_MAGIC    / MAGIC     -> applies to DamageType.MAGIC   (optional, included for symmetry)
     *
     * Examples in MythicMobs:
     *   DamageModifiers:
     *     - ELEMENT_PHYSICAL: 0    # full immunity to physical (non-element) damage
     *     - PHYSICAL: 0.5          # 50% physical taken
     */
    private ElementStageResult applyElementStage(DamageMetadata dmg,
                                                 Map<Element, Double> raw,
                                                 Map<Element, Double> mergedMods,
                                                 ElementResolver resolver,
                                                 PlayerAttackEvent event,
                                                 DebugPrinter dbg) {
        Map<Element, Double> critMul = new HashMap<>();
        Map<Element, Double> mobMul  = new HashMap<>();
        Map<Element, Double> statMul = new HashMap<>();

        // MythicMobs per-mob DamageModifiers (normalized to UPPER + '_' for '-')
        Map<String, Double> mmMods = getMythicDamageModifiersNormalized(event.getEntity());

        boolean debug = plugin.getPlayerDebugMode(event.getAttacker().getPlayer());
        StringBuilder localDbg = debug ? new StringBuilder("<gray>MythicMobs DamageModifiers (normalized):</gray>\n") : null;
        if (debug) {
            if (mmMods.isEmpty()) {
                localDbg.append("  <dark_gray>(none or not a MythicMob)</dark_gray>\n");
            } else {
                mmMods.forEach((k, v) -> localDbg.append("  <white>").append(k).append("</white>: <white>").append(fmt(v)).append("</white>\n"));
            }
        }

        // ----- Per-element processing -----
        for (Element el : raw.keySet()) {
            String keyUpper = normalizeKey(el.getId() == null ? "" : el.getId());

            // Config (template -> per-mob override)
            double confMul = 1.0;
            if (mergedMods != null) {
                Double m = mergedMods.get(el);
                if (m != null) confMul = m;
            }

            // MythicMobs ELEMENT_<ID> or <ID>
            double mmMul = lookupMythicElementMul(mmMods, keyUpper);

            // Combined mob multiplier for this element
            double finalMobMul = confMul * mmMul;
            if (finalMobMul == 0.0) {
                dmg.multiplicativeModifier(0, el);
            } else if (Math.abs(finalMobMul - 1.0) > EPS) {
                dmg.multiplicativeModifier(finalMobMul, el);
            }
            mobMul.put(el, finalMobMul);

            // Elemental crit (independent of weapon/skill crit)
            double cMul = 1.0;
            if (resolver.rollElementalCrit(event.getAttacker())) {
                cMul = resolver.critPowerMultiplier(event.getAttacker());
                if (Math.abs(cMul - 1.0) > EPS) {
                    dmg.multiplicativeModifier(cMul, el);
                    dmg.registerElementalCriticalStrike(el);
                }
            }
            critMul.put(el, cMul);

            // Global elemental damage stat (ADDITIONAL_ELEMENTAL_DAMAGE %)
            double sMul = 1.0 + resolver.statMultiplier(event.getAttacker());
            if (Math.abs(sMul - 1.0) > EPS) {
                dmg.multiplicativeModifier(sMul, el);
            }
            statMul.put(el, sMul);

            if (debug) {
                localDbg.append("  <white>").append(el.getId()).append("</white>: ")
                        .append("<gray>confMul=</gray><white>").append(fmt(confMul)).append("</white> ")
                        .append("<gray>mmMul=</gray><white>").append(fmt(mmMul)).append("</white> ")
                        .append("<gray>finalMobMul=</gray><white>").append(fmt(finalMobMul)).append("</white> ")
                        .append("<gray>critMul=</gray><white>").append(fmt(cMul)).append("</white> ")
                        .append("<gray>statMul=</gray><white>").append(fmt(sMul)).append("</white>\n");
            }
        }

        // ----- NEW: non-element type modifiers via MythicMobs keys -----
        // ELEMENT_PHYSICAL / PHYSICAL -> DamageType.PHYSICAL
        double physMul = lookupMythicElementMul(mmMods, "PHYSICAL");
        if (Math.abs(physMul - 1.0) > EPS) {
            if (physMul == 0.0) {
                dmg.multiplicativeModifier(0, DamageType.PHYSICAL);
            } else {
                dmg.multiplicativeModifier(physMul, DamageType.PHYSICAL);
            }
            if (debug) {
                localDbg.append("<gray>Applied non-element:</gray> <white>PHYSICAL</white> x <white>")
                        .append(fmt(physMul)).append("</white>\n");
            }
        }

        // (Optional, included for symmetry) ELEMENT_MAGIC / MAGIC -> DamageType.MAGIC
        double magicMul = lookupMythicElementMul(mmMods, "MAGIC");
        if (Math.abs(magicMul - 1.0) > EPS) {
            if (magicMul == 0.0) {
                dmg.multiplicativeModifier(0, DamageType.MAGIC);
            } else {
                dmg.multiplicativeModifier(magicMul, DamageType.MAGIC);
            }
            if (debug) {
                localDbg.append("<gray>Applied non-element:</gray> <white>MAGIC</white> x <white>")
                        .append(fmt(magicMul)).append("</white>\n");
            }
        }
        // ----- end new -----

        if (debug) Msg.send(event.getAttacker().getPlayer(), localDbg.toString());
        if (dbg != null) dbg.elementStage(raw, critMul, mobMul, statMul);
        return new ElementStageResult(critMul, mobMul, statMul);
    }

    /* ======================================================================
       INDICATORS
       ====================================================================== */

    /**
     * Compose element + non-element indicator lines, optionally scale to a target total, and display.
     * Now also shows IMMUNE for non-element types (PHYSICAL/MAGIC) when MythicMobs declares
     * ELEMENT_PHYSICAL / PHYSICAL (or ELEMENT_MAGIC / MAGIC) as 0 and that type was part of the hit.
     */
    private void pushIndicatorsFinal(PlayerAttackEvent event, DamageMetadata dmg, HitContext ctx) {
        IndicatorSettings settings = plugin.getIndicatorSettings();
        if (settings == null || !settings.enabled) return;

        boolean debug = plugin.getPlayerDebugMode(event.getAttacker().getPlayer());
        StringBuilder debugOut = debug ? new StringBuilder() : null;

        // Check MM modifiers so we can show non-element IMMUNE lines when appropriate
        Map<String, Double> mmMods = getMythicDamageModifiersNormalized(event.getEntity());
        boolean physImmune  = lookupMythicElementMul(mmMods, "PHYSICAL") == 0.0;
        boolean magicImmune = lookupMythicElementMul(mmMods, "MAGIC")    == 0.0;

        List<IndicatorLine> lines = new ArrayList<>();
        double elementFinalTotal = 0.0;
        Set<DamageType> baseTypes = dmg.collectTypes();

        // 1) Per-element lines (final values)
        for (Element el : ctx.elemBase().keySet()) {
            double base = ctx.elemBase().get(el);
            double mMul = ctx.elemMobMul().getOrDefault(el, 1.0);
            double cMul = ctx.elemCritMul().getOrDefault(el, 1.0);
            double sMul = ctx.elemStatMul().getOrDefault(el, 1.0);

            boolean immune = Math.abs(mMul) < EPS;
            boolean crit = Math.abs(cMul - 1.0) > EPS;
            double value = immune ? 0.0 : base * mMul * cMul * sMul;

            elementFinalTotal += value;
            lines.add(new IndicatorLine(value, immune, crit, el, baseTypes, mMul, null));
        }

        // 2) Non-element line (and IMMUNE handling for PHYSICAL/MAGIC)
        boolean hasElements = !ctx.elemBase().isEmpty();
        DamagePacket carrier = guessCarrierPacket(dmg, hasElements);
        double packetsSum = dmg.getPackets().stream().mapToDouble(DamagePacket::getFinalValue).sum();
        double carrierVal = (carrier == null) ? 0.0 : carrier.getFinalValue();

        double nonElem = packetsSum - carrierVal - elementFinalTotal;
        if (nonElem < 0) nonElem = 0;

        // Add normal non-element line if any remains
        if (nonElem > EPS) {
            boolean crit = ctx.wasWeaponCrit() || ctx.wasSkillCrit();
            Set<DamageType> types = baseTypes;
            if (hasElements && settings.stripPhysicalWhenElement) {
                types = EnumSet.copyOf(types);
                types.remove(DamageType.PHYSICAL);
            }
            lines.add(new IndicatorLine(nonElem, false, crit, null, types, 1.0, null));
        } else {
            // nonElem == 0: if the hit contained PHYSICAL or MAGIC and the mob is immune to that type,
            // show explicit IMMUNE lines so the player knows why nothing landed for that portion.
            // We only add for types that were actually part of this hit.
            if (baseTypes.contains(DamageType.PHYSICAL) && physImmune) {
                Set<DamageType> t = EnumSet.of(DamageType.PHYSICAL);
                String icon = settings.iconFor(t, false);
                lines.add(new IndicatorLine(0.0, true, false, null, t, 0.0, icon));
            }
            if (baseTypes.contains(DamageType.MAGIC) && magicImmune) {
                Set<DamageType> t = EnumSet.of(DamageType.MAGIC);
                String icon = settings.iconFor(t, false);
                lines.add(new IndicatorLine(0.0, true, false, null, t, 0.0, icon));
            }
        }

        // 2.5) Killed by type modifiers?
        boolean killedByType = (packetsSum < EPS) || (dmg.getDamage() < EPS);
        if (killedByType) {
            if (!lines.isEmpty()) {
                // turn existing lines into IMMUNE unless they already represent immunity
                lines = lines.stream().map(orig -> {
                    if (orig.immune) return orig;
                    String typeIcon = settings.iconFor(orig.types, false);
                    return new IndicatorLine(0.0, true, false, null, orig.types, orig.mobMul, typeIcon);
                }).collect(Collectors.toList());
            } else {
                // No lines yet (e.g., pure non-element PHYSICAL blocked to 0) – synthesize an IMMUNE line.
                // Prefer specific PHYSICAL/MAGIC immunity if we can detect it; otherwise use baseTypes.
                if (baseTypes.contains(DamageType.PHYSICAL) && physImmune) {
                    Set<DamageType> t = EnumSet.of(DamageType.PHYSICAL);
                    String icon = settings.iconFor(t, false);
                    lines.add(new IndicatorLine(0.0, true, false, null, t, 0.0, icon));
                } else if (baseTypes.contains(DamageType.MAGIC) && magicImmune) {
                    Set<DamageType> t = EnumSet.of(DamageType.MAGIC);
                    String icon = settings.iconFor(t, false);
                    lines.add(new IndicatorLine(0.0, true, false, null, t, 0.0, icon));
                } else if (!baseTypes.isEmpty()) {
                    // Fallback: show a generic immune indicator for the hit's base types.
                    String icon = settings.iconFor(baseTypes, false);
                    lines.add(new IndicatorLine(0.0, true, false, null, baseTypes, 0.0, icon));
                }
            }
        }

        // 3) Scaling (skip if killedByType)
        double scale = 1.0;
        if (!killedByType) {
            double sumDisplay = lines.stream()
                    .filter(l -> !l.immune && l.value > EPS)
                    .mapToDouble(l -> l.value)
                    .sum();

            double target = switch (settings.scaleTarget) {
                case META    -> dmg.getDamage();
                case PACKETS -> packetsSum;
                case NONE    -> sumDisplay;
                case BUKKIT  -> event.toBukkit().getFinalDamage();
            };

            if (sumDisplay > EPS && Math.abs(target - sumDisplay) > EPS) {
                scale = target / sumDisplay;
                List<IndicatorLine> scaled = new ArrayList<>(lines.size());
                for (IndicatorLine l : lines) {
                    if (!l.immune && l.value > EPS) {
                        scaled.add(new IndicatorLine(l.value * scale, false, l.crit, l.element, l.types, l.mobMul, l.iconOverride));
                    } else {
                        scaled.add(l);
                    }
                }
                lines = scaled;
            }
        }

        // 4) Debug
        if (debug) {
            debugOut.append("<yellow>Indicator Stage Debug:</yellow>\n")
                    .append("<gray>scale-target:</gray> <white>").append(settings.scaleTarget).append("</white>\n")
                    .append("<gray>metaDamageSum (dmg.getDamage()):</gray> <white>").append(fmt(dmg.getDamage())).append("</white>\n")
                    .append("<gray>packetsSum (Σ packet.final):</gray> <white>").append(fmt(packetsSum)).append("</white>\n")
                    .append("<gray>Bukkit final:</gray> <white>").append(fmt(event.toBukkit().getFinalDamage())).append("</white>\n")
                    .append("<gray>carrierVal:</gray> <white>").append(fmt(carrierVal)).append("</white>\n")
                    .append("<gray>elementFinalTotal (we show):</gray> <white>").append(fmt(elementFinalTotal)).append("</white>\n")
                    .append("<gray>nonElem (pre-scale):</gray> <white>").append(fmt(nonElem)).append("</white>\n")
                    .append("<gray>physImmune:</gray> <white>").append(physImmune).append("</white> ")
                    .append("<gray>magicImmune:</gray> <white>").append(magicImmune).append("</white>\n");

            double sumDisplayDbg = lines.stream()
                    .filter(l -> !l.immune && l.value > EPS)
                    .mapToDouble(l -> l.value)
                    .sum();

            double targetDbg = killedByType ? 0.0 : switch (settings.scaleTarget) {
                case META    -> dmg.getDamage();
                case PACKETS -> packetsSum;
                case NONE    -> sumDisplayDbg;
                case BUKKIT  -> event.toBukkit().getFinalDamage();
            };

            debugOut.append("<gray>sumDisplay (pre-scale):</gray> <white>").append(fmt(sumDisplayDbg)).append("</white>\n")
                    .append("<gray>targetTotal:</gray> <white>").append(fmt(targetDbg)).append("</white>\n")
                    .append("<gray>scale:</gray> <white>").append(fmt(scale)).append("</white>\n")
                    .append("<gray>killedByType:</gray> <white>").append(killedByType).append("</white>\n")
                    .append("<gray>Lines:</gray>\n");

            int i = 1;
            for (IndicatorLine l : lines) {
                debugOut.append("  #").append(i++).append(" val=").append(fmt(l.value))
                        .append(" immune=").append(l.immune)
                        .append(" crit=").append(l.crit)
                        .append(" elem=").append(l.element == null ? "null" : l.element.getId())
                        .append(" types=").append(l.types == null ? "[]" : l.types)
                        .append("\n");
            }
            Msg.send(event.getAttacker().getPlayer(), debugOut.toString());
        }

        // 5) Display
        plugin.getIndicators().displayLines(event.getEntity(), lines);
    }


    /* ======================================================================
       HELPERS / TYPES
       ====================================================================== */

    /**
     * Prefer MythicMob ID; otherwise distinguish players and vanilla entities.
     */
    private String targetKey(LivingEntity e) {
        if (MythicBukkit.inst().getMobManager().isMythicMob(e)) {
            return MythicBukkit.inst().getMobManager().getMythicMobInstance(e).getMobType();
        }
        if (e instanceof Player) {
            return "PLAYER";
        }
        return "VANILLA_" + e.getType().name();
    }

    /**
     * If elements are present, MythicLib often inserts a tiny typed packet first.
     */
    private DamagePacket guessCarrierPacket(DamageMetadata dmg, boolean hasElements) {
        if (!hasElements) return null;
        List<DamagePacket> list = dmg.getPackets();
        if (list.isEmpty()) return null;
        DamagePacket first = list.get(0);
        return (first.getTypes().length > 0) ? first : null;
    }

    /**
     * Merge element maps in order; later sources override earlier ones.
     */
    @SafeVarargs
    private final Map<Element, Double> mergeElementMods(Map<Element, Double>... sources) {
        Map<Element, Double> out = new HashMap<>();
        if (sources == null) return out;
        for (Map<Element, Double> src : sources) {
            if (src == null) continue;
            for (Map.Entry<Element, Double> e : src.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * Returns MythicMobs DamageModifiers for this mob as a normalized map:
     * keys are uppercased with '-' replaced by '_'. Non-MythicMob or empty map -> empty result.
     */
    private Map<String, Double> getMythicDamageModifiersNormalized(LivingEntity target) {
        Map<String, Double> out = new LinkedHashMap<>();
        try {
            if (!MythicBukkit.inst().getMobManager().isMythicMob(target)) return out;
            var inst = MythicBukkit.inst().getMobManager().getMythicMobInstance(target);
            if (inst == null) return out;

            Map<String, Double> raw = inst.getType().getDamageModifiers();
            if (raw == null || raw.isEmpty()) return out;

            for (Map.Entry<String, Double> e : raw.entrySet()) {
                if (e.getKey() == null) continue;
                out.put(normalizeKey(e.getKey()), e.getValue());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[DevDamageHandler] getMythicDamageModifiersNormalized failed: " + t.getMessage());
        }
        return out;
    }

    /**
     * Look up MythicMobs element multiplier by accepting aliases:
     * {@code ELEMENT_<ID>}, {@code ELEMENT-<ID>} (normalized), or {@code <ID>}.
     * Returns {@code 1.0} if not found.
     */
    private double lookupMythicElementMul(Map<String, Double> mmMods, String upperId) {
        if (mmMods.isEmpty() || upperId.isEmpty()) return 1.0;
        String k1 = "ELEMENT_" + upperId;
        String k2 = "ELEMENT-" + upperId;
        String k3 = upperId;

        Double v = mmMods.get(k1);
        if (v == null) v = mmMods.get(normalizeKey(k2));
        if (v == null) v = mmMods.get(k3);
        return (v != null) ? v : 1.0;
    }

    private String normalizeKey(String s) {
        return s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private void addIfPresent(Map<DamageType, Double> src, Map<DamageType, Double> dst, DamageType key) {
        Double v = src.get(key);
        if (v != null) dst.put(key, v);
    }

    private static String fmt(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) return String.valueOf((long) Math.rint(d));
        return String.format(Locale.US, "%.3f", d);
    }

    /* ======================================================================
       DATA HOLDERS
       ====================================================================== */

    private record TypeStageResult(Map<DamagePacket, DebugPrinter.TypeStep> debugMap) {}

    private record ElementStageResult(Map<Element, Double> critMul,
                                      Map<Element, Double> mobMul,
                                      Map<Element, Double> statMul) {
    }

    /**
     * Context persisted between modify and show phases for an individual hit.
     */
    public record HitContext(
            Map<Element, Double> elemBase,
            Map<Element, Double> elemMobMul,
            Map<Element, Double> elemCritMul,
            Map<Element, Double> elemStatMul,
            boolean wasWeaponCrit,
            boolean wasSkillCrit,
            String mobId
    ) {}

    /**
     * Pretty printer for deep debug dumps sent to the attacker.
     */
    private static class DebugPrinter {
        private final StringBuilder sb = new StringBuilder();
        private final String mobId;
        private final DamageMetadata dmg;
        private final Map<Element, Double> elemRaw;

        DebugPrinter(String mobId, DamageMetadata dmg, Map<Element, Double> elemRaw) {
            this.mobId = mobId;
            this.dmg = dmg;
            this.elemRaw = elemRaw;
            header();
        }

        void globalTypeMul(DamageType dt, double mul) {
            sb.append("<gray>Applied Global Type Mul: </gray><white>").append(dt)
                    .append("</white><gray> = </gray><white>").append(fmt(mul)).append("</white>\n");
        }

        void orphanMul(double mul) {
            sb.append("<gray>Applied Orphan Packet Mul: </gray><white>").append(fmt(mul)).append("</white>\n");
        }

        record TypeStep(DamageType primary, LinkedHashMap<DamageType, Double> applied, double result, boolean orphan) {}

        void elementStage(Map<Element, Double> base, Map<Element, Double> crit, Map<Element, Double> mob, Map<Element, Double> stat) {
            if (base.isEmpty()) return;
            sb.append("<yellow>Element Calculations:</yellow>\n");
            for (Element el : base.keySet()) {
                double c = crit.getOrDefault(el, 1.0);
                double m = mob.getOrDefault(el, 1.0);
                double s = stat.getOrDefault(el, 1.0);
                double f = c * m * s;
                sb.append("  <white>").append(el.getId()).append("</white>: ")
                        .append("<gray>base=</gray><white>").append(fmt(base.get(el))).append("</white> ")
                        .append("<gray>mobMul=</gray><white>").append(fmt(m)).append("</white> ")
                        .append("<gray>critMul=</gray><white>").append(fmt(c)).append("</white> ")
                        .append("<gray>statMul=</gray><white>").append(fmt(s)).append("</white> ")
                        .append("<gray>finalMul=</gray><white>").append(fmt(f)).append("</white>\n");
            }
        }

        void printFinal(DamageMetadata dmg, Player player) {
            sb.append("<yellow>Packet Type Calculations:</yellow>\n");
            int i = 1;
            for (DamagePacket pkt : dmg.getPackets()) {
                sb.append("  <gray>#").append(i++).append("</gray> ")
                        .append("<white>").append(fmt(pkt.getFinalValue())).append("</white>")
                        .append(" <gray>types=</gray><white>")
                        .append(Arrays.toString(pkt.getTypes())).append("</white>\n");
            }

            sb.append("<yellow>Final Packets:</yellow>\n");
            double sum = 0;
            int j = 1;
            for (DamagePacket p : dmg.getPackets()) {
                sb.append("  <gray>#").append(j++).append("</gray> <white>")
                        .append(fmt(p.getFinalValue()))
                        .append("</white> <gray>types=</gray><white>")
                        .append(Arrays.toString(p.getTypes()))
                        .append("</white>\n");
                sum += p.getFinalValue();
            }
            sb.append("<gray>Total Final Damage:</gray> <white>").append(fmt(sum)).append("</white>\n");
            sb.append("<gray>====================================</gray>");

            Msg.send(player, sb.toString());
        }

        private void header() {
            sb.append("<gray>========= <gold>Damage Debug <gray>=========\n")
                    .append("<yellow>Target:</yellow> <white>").append(mobId).append("</white>\n")
                    .append("<yellow>Raw Packets:</yellow>\n");
            int i = 1;
            for (DamagePacket p : dmg.getPackets()) {
                sb.append("  <gray>#").append(i++).append("</gray> <white>")
                        .append(fmt(p.getFinalValue()))
                        .append("</white> <gray>types=</gray><white>")
                        .append(Arrays.toString(p.getTypes()))
                        .append("</white>\n");
            }
            if (!elemRaw.isEmpty()) {
                sb.append("<yellow>Elements Raw:</yellow>\n");
                elemRaw.forEach((el, val) ->
                        sb.append("  <white>").append(el.getId()).append("</white>: <white>").append(fmt(val)).append("</white>\n")
                );
            } else {
                sb.append("<yellow>Elements Raw:</yellow> <dark_gray>(none)</dark_gray>\n");
            }
        }
    }
}
