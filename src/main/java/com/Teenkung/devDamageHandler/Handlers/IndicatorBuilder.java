package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.Indicator.IndicatorLine;
import com.Teenkung.devDamageHandler.Indicator.IndicatorSettings;
import com.Teenkung.devDamageHandler.Util.Msg;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;


import java.util.*;

import static com.Teenkung.devDamageHandler.Handlers.DamageDebug.fmt;

/**
 * Handles building and scaling indicator lines for display.
 */
public class IndicatorBuilder {
    
    private static final double EPS = 1e-6;
    
    /**
     * Build indicator lines from hit context.
     */
    public static List<IndicatorLine> buildLines(DamageHandler.HitContext ctx, java.util.function.Function<String, Double> statProvider, 
                                                 Set<DamageType> baseTypes, IndicatorSettings settings) {
        List<IndicatorLine> lines = new ArrayList<>();
        
        // 1) Element lines
        for (Element el : ctx.elementDamage().keySet()) {
            double base = ctx.elementDamage().get(el);
            double mul = ctx.elementMultipliers().getOrDefault(el, 1.0);
            double defMul = ctx.elementDefenseMultipliers().getOrDefault(el, 1.0); // Use defense-only for indicator arrow
            boolean immune = Math.abs(mul) < EPS;
            boolean crit = ctx.elementCrits().contains(el);
            double value = immune ? 0.0 : base * mul;
            
            // Skip zero-damage lines unless they are immune
            if (!immune && value < 0.01) continue;
            
            // Apply elemental crit to display value
            if (crit && !immune) {
                double critPower = statProvider.apply("ELEMENTAL_CRITICAL_STRIKE_POWER");
                value *= (1.0 + critPower / 100.0);
            }
            
            // We pass ctx.skillCrit() to element lines too IF they stacked?
            // "Stacking" merges powers, but logically the crit IS a skill crit too.
            lines.add(new IndicatorLine(value, immune, crit, ctx.skillCrit(), el, baseTypes, defMul, null));
        }

        // 2) NONE element line (if not pure elemental)
        if (!ctx.isPureElemental() && ctx.nonElemDamage() > EPS) {
            double mul = ctx.nonElemMul();
            boolean immune = Math.abs(mul) < EPS;
            boolean crit = ctx.nonElemCrit();
            double value = immune ? 0.0 : ctx.nonElemDamage() * mul;
            
            // Apply non-elem crit to display value
            if (crit && !immune) {
                double critPower = statProvider.apply("CRITICAL_STRIKE_POWER");
                value *= (1.0 + critPower / 100.0);
            }
            
            Set<DamageType> types = baseTypes;
            if (!ctx.elementDamage().isEmpty() && settings.stripPhysicalWhenElement) {
                types = EnumSet.copyOf(types);
                types.remove(DamageType.PHYSICAL);
            }
            
            // null element = NONE
            lines.add(new IndicatorLine(value, immune, crit, ctx.skillCrit(), null, types, mul, null));
        }
        
        return lines;
    }
    
    /**
     * Scale indicator lines to match target damage value.
     */
    public static List<IndicatorLine> scaleLines(List<IndicatorLine> lines, double target) {
        double sumDisplay = lines.stream()
            .filter(l -> !l.immune && l.value > EPS)
            .mapToDouble(l -> l.value).sum();
        
        if (sumDisplay > EPS && Math.abs(target - sumDisplay) > EPS) {
            double scale = target / sumDisplay;
            List<IndicatorLine> scaled = new ArrayList<>(lines.size());
            for (IndicatorLine l : lines) {
                if (!l.immune && l.value > EPS) {
                    scaled.add(new IndicatorLine(l.value * scale, false, l.crit, l.skillCrit,
                        l.element, l.types, l.mobMul, l.iconOverride));
                } else {
                    scaled.add(l);
                }
            }
            return scaled;
        }
        
        return lines;
    }
    
    /**
     * Get the target value for scaling based on settings.
     */
    public static double getScalingTarget(IndicatorSettings settings, DamageMetadata dmg, 
                                         List<IndicatorLine> lines, double bukkitDamage) {
        double packetsSum = dmg.getPackets().stream()
            .mapToDouble(DamagePacket::getFinalValue).sum();
        
        double sumDisplay = lines.stream()
            .filter(l -> !l.immune && l.value > EPS)
            .mapToDouble(l -> l.value).sum();
        
        return switch (settings.scaleTarget) {
            case META -> dmg.getDamage();
            case PACKETS -> packetsSum;
            case NONE -> sumDisplay;
            case BUKKIT -> bukkitDamage;
        };
    }
    
    /**
     * Print debug info about indicator lines.
     */
    public static void printDebugLines(org.bukkit.entity.Player player, List<IndicatorLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("<yellow>Indicator Lines (</yellow><white>").append(lines.size()).append("</white><yellow>):</yellow>\n");
        for (IndicatorLine l : lines) {
            sb.append("  <gray>elem=</gray><white>").append(l.element != null ? l.element.getId() : "NONE")
              .append("</white> <gray>value=</gray><white>").append(fmt(l.value))
              .append("</white> <gray>immune=</gray><white>").append(l.immune)
              .append("</white> <gray>crit=</gray><white>").append(l.crit).append("</white>\n");
        }
        Msg.send(player, sb.toString());
    }
}
