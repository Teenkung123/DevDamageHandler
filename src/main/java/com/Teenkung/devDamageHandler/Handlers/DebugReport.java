package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.Teenkung.devDamageHandler.Handlers.DamageDebug.fmt;

/**
 * Collects debug data throughout the damage pipeline and renders it all at once
 * in a clean, organized order. This replaces the old scattered inline Msg.send() calls.
 */
public class DebugReport {

    // ── §1 Context ──
    private String attackerName;
    private String targetName;
    private String attackerType; // "Player", "MythicMob", "Vanilla"
    private String targetType;

    // ── §2 Raw Damage ──
    private double rawDamageTotal;
    private final List<PacketInfo> rawPackets = new ArrayList<>();

    // ── §3 Attacker Stats ──
    private final List<StatEntry> attackerStats = new ArrayList<>();

    // ── §4 Modifiers ──
    private final List<ModifierEntry> typeModifiers = new ArrayList<>();
    private final List<ModifierEntry> statBonuses = new ArrayList<>();
    private final List<ElementModEntry> elementModifiers = new ArrayList<>();
    private NoneElementEntry noneElement;
    private double carrierTransferAmount;
    private final Map<String, Double> victimMmMods = new LinkedHashMap<>();
    private final Map<String, Double> attackerMmMods = new LinkedHashMap<>();
    private final List<PendingModEntry> pendingModifiers = new ArrayList<>();
    private final List<String> playerElementDefense = new ArrayList<>();

    // ── §5 Crits ──
    private final List<CritEntry> critResults = new ArrayList<>();
    private StackedCritEntry stackedCrit;
    private double lrPowerBonus = 0.0;   // pendingMul - 1, pooled additively with crit
    private double lrPooledFinalMul = 0.0;

    // ── §6 Defense ──
    private DefenseEntry defenseEntry;

    // ── §7 Final Result ──
    private final List<PacketInfo> finalPackets = new ArrayList<>();
    private double finalDamageTotal;
    private final List<IndicatorEntry> indicatorLines = new ArrayList<>();

    // ═══════════════════════════════════════════════
    //  Data collection methods (called during pipeline)
    // ═══════════════════════════════════════════════

    public void setContext(String attackerName, String targetName, String attackerType, String targetType) {
        this.attackerName = attackerName;
        this.targetName = targetName;
        this.attackerType = attackerType;
        this.targetType = targetType;
    }

    public void setRawDamage(double total, List<PacketInfo> packets) {
        this.rawDamageTotal = total;
        this.rawPackets.addAll(packets);
    }

    public void addAttackerStat(String label, String value) {
        attackerStats.add(new StatEntry(label, value));
    }

    public void addTypeModifier(String type, double multiplier) {
        typeModifiers.add(new ModifierEntry(type, multiplier));
    }

    public void addStatBonus(String type, double percentValue) {
        statBonuses.add(new ModifierEntry(type, percentValue));
    }

    public void addElementModifier(String elementId, double baseDmg, double defMul,
                                   double offensePercent, double bonusElem, double finalDmg) {
        elementModifiers.add(new ElementModEntry(elementId, baseDmg, defMul, offensePercent, bonusElem, finalDmg));
    }

    public void setNoneElement(double baseDmg, double mul) {
        this.noneElement = new NoneElementEntry(baseDmg, mul);
    }

    public void setCarrierTransfer(double amount) {
        this.carrierTransferAmount = amount;
    }

    public void setMythicMobsMods(Map<String, Double> attackerMods, Map<String, Double> victimMods) {
        if (attackerMods != null) this.attackerMmMods.putAll(attackerMods);
        if (victimMods != null) this.victimMmMods.putAll(victimMods);
    }

    public void addPendingModifier(String name, String displayValue) {
        pendingModifiers.add(new PendingModEntry(name, displayValue));
    }

    public void addPlayerElementDefense(String element, double reductionPct, double multiplier) {
        String color = multiplier < 1.0 ? "<green>" : "<red>";
        String colorEnd = multiplier < 1.0 ? "</green>" : "</red>";
        playerElementDefense.add("    " + color + element + ": -" + fmt(reductionPct) + "% (×" + fmt(multiplier) + ")" + colorEnd);
    }

    public void addCritResult(String type, String symbol, double power) {
        critResults.add(new CritEntry(type, symbol, power));
    }

    public void setStackedCrit(double totalPower, double finalMul) {
        this.stackedCrit = new StackedCritEntry(totalPower, finalMul);
    }

    public void setLrPowerBonus(double extra, double combinedMul) {
        this.lrPowerBonus = extra;
        this.lrPooledFinalMul = combinedMul;
    }

    public void setPlayerDefense(PlayerDefenseApplicator.DefenseStats stats,
                                 PlayerDefenseApplicator.MultiplierResult multipliers,
                                 double baseDmg, double finalDmg) {
        this.defenseEntry = new DefenseEntry(stats, multipliers, baseDmg, finalDmg);
    }

    public void setFinalDamage(double total, List<PacketInfo> packets) {
        this.finalDamageTotal = total;
        this.finalPackets.addAll(packets);
    }

    public void addIndicatorLine(String element, double value, boolean immune, boolean crit) {
        indicatorLines.add(new IndicatorEntry(element, value, immune, crit));
    }

    // ═══════════════════════════════════════════════
    //  Render the full report
    // ═══════════════════════════════════════════════

    public void render(CommandSender recipient) {
        StringBuilder sb = new StringBuilder();

        renderHeader(sb);
        renderRawDamage(sb);
        renderAttackerStats(sb);
        renderModifiers(sb);
        renderCrits(sb);
        renderDefense(sb);
        renderFinalResult(sb);
        renderConfig(sb);

        Msg.send(recipient, sb.toString());
    }

    // ── §1 CONTEXT ──
    private void renderHeader(StringBuilder sb) {
        sb.append("<gold>■ DDH DAMAGE REPORT</gold>\n");
        sb.append("  <yellow>Atk:</yellow> <white>").append(safe(attackerName)).append("</white>");
        if (attackerType != null) sb.append("<dark_gray>(").append(attackerType).append(")</dark_gray>");
        sb.append("  <yellow>Tgt:</yellow> <white>").append(safe(targetName)).append("</white>");
        if (targetType != null) sb.append("<dark_gray>(").append(targetType).append(")</dark_gray>");
        sb.append("\n");
    }

    // ── §2 RAW DAMAGE ──
    private void renderRawDamage(StringBuilder sb) {
        sb.append("\n<gray>▸</gray> <yellow>#2 RAW DAMAGE</yellow>\n");
        sb.append("  <yellow>Base Damage:</yellow> <white>").append(fmt(rawDamageTotal)).append("</white>\n");
        if (!rawPackets.isEmpty()) {
            sb.append("  <yellow>Packets:</yellow>\n");
            for (int i = 0; i < rawPackets.size(); i++) {
                PacketInfo p = rawPackets.get(i);
                sb.append("    <gray>#").append(i + 1).append("</gray> <white>").append(fmt(p.value))
                  .append("</white>  <gray>[</gray><white>").append(p.types).append("</white><gray>]</gray>")
                  .append("  <gray>elem=</gray><white>").append(safe(p.element)).append("</white>\n");
            }
        }
    }

    // ── §3 ATTACKER STATS ──
    private void renderAttackerStats(StringBuilder sb) {
        sb.append("\n<gray>▸</gray> <yellow>#3 ATTACKER STATS</yellow>\n");
        if (attackerStats.isEmpty()) {
            sb.append("  <dark_gray>(none)</dark_gray>\n");
        } else {
            for (StatEntry stat : attackerStats) {
                sb.append("  <yellow>").append(stat.label).append(":</yellow> <white>").append(stat.value).append("</white>\n");
            }
        }
    }

    // ── §4 MODIFIERS ──
    private void renderModifiers(StringBuilder sb) {
        sb.append("\n<gray>▸</gray> <yellow>#4 MODIFIERS</yellow>\n");

        // Type multipliers
        if (!typeModifiers.isEmpty()) {
            sb.append("  <aqua>Type Multipliers:</aqua>\n");
            for (ModifierEntry m : typeModifiers) {
                String color = colorForMul(m.value);
                String colorEnd = colorEndForMul(m.value);
                String display = m.value < 0.01 ? "IMMUNE" : "×" + fmt(m.value);
                sb.append("    <gray>TYPE_</gray><white>").append(m.name).append("</white><gray>:</gray> ")
                  .append(color).append(display).append(colorEnd).append("\n");
            }
        }

        // Stat bonuses
        if (!statBonuses.isEmpty()) {
            sb.append("  <aqua>Stat Bonuses:</aqua>\n");
            for (ModifierEntry m : statBonuses) {
                sb.append("    <white>").append(m.name).append("</white><gray>:</gray> <green>+")
                  .append(fmt(m.value)).append("%</green>\n");
            }
        }

        // Element modifiers
        if (!elementModifiers.isEmpty()) {
            sb.append("  <aqua>Element Modifiers:</aqua>\n");
            for (ElementModEntry e : elementModifiers) {
                sb.append("    <aqua>").append(e.elementId).append("</aqua>");
                sb.append(" <gray>base=</gray><white>").append(fmt(e.baseDmg)).append("</white>");

                String defColor = colorForMul(e.defMul);
                String defColorEnd = colorEndForMul(e.defMul);
                sb.append(" <gray>def=</gray>").append(defColor).append("×").append(fmt(e.defMul)).append(defColorEnd);

                if (e.offensePercent > 0.001 || e.bonusElem > 0.001) {
                    sb.append(" <gray>offense=</gray><white>");
                    if (e.offensePercent > 0.001) sb.append("+").append(fmt(e.offensePercent)).append("% ");
                    if (e.bonusElem > 0.001) sb.append("+").append(fmt(e.bonusElem)).append("% ALL");
                    sb.append("</white>");
                }

                sb.append(" <gray>→</gray> <green>").append(fmt(e.finalDmg)).append("</green>\n");
            }
        }

        // ELEMENT_NONE
        if (noneElement != null) {
            sb.append("  <aqua>Element NONE:</aqua> <gray>base=</gray><white>").append(fmt(noneElement.baseDmg))
              .append("</white> <gray>mul=</gray><white>×").append(fmt(noneElement.mul)).append("</white>\n");
        }

        // Carrier transfer
        if (carrierTransferAmount > 0.001) {
            sb.append("  <green>Carrier → Element Transfer:</green> <white>+").append(fmt(carrierTransferAmount))
              .append("</white> <dark_gray>(enchantments applied to elemental)</dark_gray>\n");
        }

        // Pending LibReforge modifiers
        for (PendingModEntry pm : pendingModifiers) {
            sb.append("  <gray>").append(pm.name).append(":</gray> <aqua>").append(pm.displayValue).append("</aqua>\n");
        }

        // Player element defense
        if (!playerElementDefense.isEmpty()) {
            sb.append("  <aqua>Player Element Defense:</aqua>\n");
            for (String line : playerElementDefense) {
                sb.append(line).append("\n");
            }
        }

        // MythicMobs mods
        if (!attackerMmMods.isEmpty()) {
            sb.append("  <aqua>Attacker MM Mods:</aqua>\n");
            for (Map.Entry<String, Double> e : attackerMmMods.entrySet()) {
                sb.append("    <gray>").append(e.getKey()).append(":</gray> <white>").append(fmt(e.getValue())).append("</white>\n");
            }
        }
        if (!victimMmMods.isEmpty()) {
            sb.append("  <aqua>Victim MM Mods:</aqua>\n");
            for (Map.Entry<String, Double> e : victimMmMods.entrySet()) {
                String color = e.getValue() < 1.0 ? "<green>" : (e.getValue() > 1.0 ? "<red>" : "<white>");
                String colorEnd = e.getValue() < 1.0 ? "</green>" : (e.getValue() > 1.0 ? "</red>" : "</white>");
                sb.append("    <gray>").append(e.getKey()).append(":</gray> ").append(color).append(fmt(e.getValue())).append(colorEnd).append("\n");
            }
        }
    }

    // ── §5 CRITS ──
    private void renderCrits(StringBuilder sb) {
        sb.append("\n<gray>▸</gray> <yellow>#5 CRITS</yellow>\n");

        boolean hasCrit = !critResults.isEmpty();
        boolean hasLr   = lrPowerBonus > 1e-6;

        if (!hasCrit && !hasLr) {
            sb.append("  <dark_gray>(none)</dark_gray>\n");
            return;
        }

        for (CritEntry c : critResults) {
            sb.append("  <gold>").append(c.symbol).append(" ").append(c.type).append(" CRIT!</gold> <gray>+</gray><white>")
              .append(fmt(c.power)).append("% power</white>\n");
        }

        if (hasLr) {
            sb.append("  <aqua>⬆ LibReforge:</aqua> <white>+").append(fmt(lrPowerBonus * 100)).append("% power</white>\n");
        }

        if (hasCrit && hasLr) {
            // Both present — show the additive pool total
            double critPct  = critResults.stream().mapToDouble(c -> c.power).sum();
            double totalPct = critPct + lrPowerBonus * 100.0;
            sb.append("  <aqua>⚡ POOLED</aqua> <white>+").append(fmt(totalPct))
              .append("% = ×").append(fmt(lrPooledFinalMul)).append("</white>\n");
        } else if (hasCrit && stackedCrit != null) {
            // Multiple crit types, no LR bonus
            sb.append("  <aqua>⚡ STACKED!</aqua> <white>Total: ").append(fmt(stackedCrit.totalPower))
              .append("% = ×").append(fmt(stackedCrit.finalMul)).append("</white>\n");
        } else if (!hasCrit && hasLr) {
            // Only LR, no crit — show its effective multiplier
            sb.append("  <dark_gray>→ ×").append(fmt(1.0 + lrPowerBonus)).append("</dark_gray>\n");
        }
    }

    // ── §6 DEFENSE ──
    private void renderDefense(StringBuilder sb) {
        sb.append("\n<gray>▸</gray> <yellow>#6 DEFENSE</yellow>\n");

        boolean hasDefenseInfo = false;

        // Player defense stats (from PlayerDefenseApplicator)
        if (defenseEntry != null) {
            hasDefenseInfo = true;
            PlayerDefenseApplicator.DefenseStats stats = defenseEntry.stats;
            PlayerDefenseApplicator.MultiplierResult muls = defenseEntry.multipliers;

            if (stats.elementWeakness() > 0.001) {
                sb.append("  <red>").append(stats.elementId()).append("_WEAKNESS:</red> <white>+")
                  .append(fmt(stats.elementWeakness())).append("%</white> <gray>→ mul:</gray> <red>")
                  .append(fmt(muls.weakness() * 100)).append("%</red>\n");
            }

            sb.append("  <gray>DEFENSE:</gray> <white>").append(fmt(stats.defense())).append("</white>");
            if (stats.elementDefense() > 0.001) {
                sb.append(" <gray>+ ").append(stats.elementId()).append("_DEF:</gray> <white>")
                  .append(fmt(stats.elementDefense())).append("</white>");
            }
            sb.append(" <gray>→ mul:</gray> <aqua>").append(fmt(muls.defense() * 100)).append("%</aqua>\n");

            // Reductions
            sb.append("  <gray>Reductions:</gray>");
            boolean hasRed = false;
            if (stats.damageReduction() > 0.001) {
                sb.append(" <white>DR:").append(fmt(stats.damageReduction())).append("%</white>");
                hasRed = true;
            }
            if (stats.elementDefensePercent() > 0.001) {
                sb.append(" <white>").append(stats.elementId()).append("_DEF%:").append(fmt(stats.elementDefensePercent())).append("%</white>");
                hasRed = true;
            }
            if (stats.contextReduction() > 0.001) {
                sb.append(" <white>").append(stats.contextReductionId().replace("_DAMAGE_REDUCTION", ""))
                  .append(":").append(fmt(stats.contextReduction())).append("%</white>");
                hasRed = true;
            }
            if (stats.typeReduction() > 0.001) {
                sb.append(" <white>").append(stats.typeId()).append(":").append(fmt(stats.typeReduction())).append("%</white>");
                hasRed = true;
            }
            if (stats.armorEnchantReduction() > 0.001) {
                sb.append(" <white>Armor Prot:").append(fmt(stats.armorEnchantReduction())).append("%</white>");
                hasRed = true;
            }
            if (stats.vanillaArmorMultiplier() < 1.0 - 0.001) {
                double armorReductionPct = (1.0 - stats.vanillaArmorMultiplier()) * 100;
                sb.append(" <white>Armor:").append(fmt(armorReductionPct)).append("%(×")
                  .append(fmt(stats.vanillaArmorMultiplier())).append(")</white>");
                hasRed = true;
            }
            if (!hasRed) sb.append(" <gray>none</gray>");
            sb.append(" <gray>→ mul:</gray> <aqua>").append(fmt(muls.reduction() * 100)).append("%</aqua>\n");

            sb.append("  <gray>Combined:</gray> <aqua>").append(fmt(muls.total() * 100)).append("%</aqua>\n");
            sb.append("  <gray>Damage:</gray> <white>").append(fmt(defenseEntry.baseDmg)).append("</white>")
              .append(" <gray>→</gray> <green>").append(fmt(defenseEntry.finalDmg)).append("</green>\n");
        }

        // Mob defense via MythicMobs DamageModifiers (negative modifiers = defense)
        if (!victimMmMods.isEmpty()) {
            boolean hasMobDefense = victimMmMods.values().stream().anyMatch(v -> v < 1.0);
            if (hasMobDefense) {
                hasDefenseInfo = true;
                sb.append("  <aqua>Victim Resistances (MM Mods):</aqua>\n");
                for (Map.Entry<String, Double> e : victimMmMods.entrySet()) {
                    if (e.getValue() < 1.0) {
                        double reductionPct = (1.0 - e.getValue()) * 100;
                        sb.append("    <green>").append(e.getKey()).append(": -").append(fmt(reductionPct))
                          .append("% (×").append(fmt(e.getValue())).append(")</green>\n");
                    }
                }
            }
        }

        if (!hasDefenseInfo) {
            sb.append("  <dark_gray>(no defense modifiers)</dark_gray>\n");
        }
    }

    // ── §7 FINAL RESULT ──
    private void renderFinalResult(StringBuilder sb) {
        sb.append("\n<gray>▸</gray> <yellow>#7 FINAL RESULT</yellow>\n");
        if (!finalPackets.isEmpty()) {
            sb.append("  <yellow>Final Packets:</yellow>\n");
            for (int i = 0; i < finalPackets.size(); i++) {
                PacketInfo p = finalPackets.get(i);
                String valColor = p.value <= 0 ? "<red>" : "<white>";
                String valColorEnd = p.value <= 0 ? "</red>" : "</white>";
                sb.append("    <gray>#").append(i + 1).append("</gray> ").append(valColor).append(fmt(p.value))
                  .append(valColorEnd).append("  <gray>[</gray><white>").append(p.types).append("</white><gray>]</gray>");
                if (p.element != null && !p.element.equals("NONE")) {
                    sb.append("  <gray>elem=</gray><white>").append(p.element).append("</white>");
                }
                sb.append("\n");
            }
        }
        sb.append("  <yellow>Total:</yellow> <green>").append(fmt(finalDamageTotal)).append("</green>\n");

        // Indicator lines
        if (!indicatorLines.isEmpty()) {
            sb.append("\n  <yellow>Indicator Lines (").append(indicatorLines.size()).append("):</yellow>\n");
            for (IndicatorEntry il : indicatorLines) {
                sb.append("    <white>").append(safe(il.element)).append("</white>  <white>").append(fmt(il.value)).append("</white>");
                if (il.immune) sb.append("  <red>IMMUNE</red>");
                if (il.crit) sb.append("  <gold>CRIT</gold>");
                sb.append("\n");
            }
        }
    }

    // ── Config (footer) ──
    private void renderConfig(StringBuilder sb) {
        DevDamageHandler plugin = (DevDamageHandler) Bukkit.getPluginManager().getPlugin("DevDamageHandler");
        if (plugin == null) return;
        DamageConfig config = plugin.getDamageConfig();
        if (config == null) return;

        sb.append("\n<gray>▸</gray> <yellow>Config</yellow>\n");
        sb.append("  <yellow>Formula:</yellow> <white>").append(config.getFlatDefenseType().name()).append("</white> <dark_gray>(");
        switch (config.getFlatDefenseType()) {
            case DIMINISHING -> sb.append("dmg × (1 - def/(def+").append(fmt(config.getFlatDefenseBase())).append("))");
            case LINEAR -> sb.append("dmg × (1 - def/100)");
            case FLAT -> sb.append("dmg - def, min 1");
        }
        sb.append(")</dark_gray>\n");
        sb.append("  <yellow>% Cap:</yellow> <white>").append(fmt(config.getPercentDefenseCap())).append("%</white>");
        sb.append("  <gray>|</gray>  <yellow>% Formula:</yellow> <white>").append(config.getPercentDefenseType().name()).append("</white> <dark_gray>(");
        switch (config.getPercentDefenseType()) {
            case DIMINISHING -> sb.append("1 - pct/(pct+").append(fmt(config.getPercentDefenseBase())).append(")");
            case LINEAR -> sb.append("1 - min(pct,cap)/100");
            default -> sb.append("linear");
        }
        sb.append(")</dark_gray>\n");
        sb.append("  <yellow>Weakness Cap:</yellow> <white>").append(fmt(config.getWeaknessCap())).append("%</white>\n");
        sb.append("  <yellow>Carrier Ignored:</yellow> <white>").append(config.isIgnoreCarrierOnElemental() ? "Yes" : "No").append("</white>");
    }

    // ═══════════════════════════════════════════════
    //  Data records
    // ═══════════════════════════════════════════════

    public record PacketInfo(double value, String types, String element) {}

    private record StatEntry(String label, String value) {}
    private record ModifierEntry(String name, double value) {}
    private record ElementModEntry(String elementId, double baseDmg, double defMul,
                                   double offensePercent, double bonusElem, double finalDmg) {}
    private record NoneElementEntry(double baseDmg, double mul) {}
    private record PendingModEntry(String name, String displayValue) {}
    private record CritEntry(String type, String symbol, double power) {}
    private record StackedCritEntry(double totalPower, double finalMul) {}
    private record IndicatorEntry(String element, double value, boolean immune, boolean crit) {}

    private record DefenseEntry(PlayerDefenseApplicator.DefenseStats stats,
                                PlayerDefenseApplicator.MultiplierResult multipliers,
                                double baseDmg, double finalDmg) {}

    // ═══════════════════════════════════════════════
    //  Utilities
    // ═══════════════════════════════════════════════

    private static String safe(String s) {
        return s != null ? s : "NONE";
    }

    private static String colorForMul(double mul) {
        return mul < 1.0 ? "<green>" : (mul > 1.0 ? "<red>" : "<white>");
    }

    private static String colorEndForMul(double mul) {
        return mul < 1.0 ? "</green>" : (mul > 1.0 ? "</red>" : "</white>");
    }
}
