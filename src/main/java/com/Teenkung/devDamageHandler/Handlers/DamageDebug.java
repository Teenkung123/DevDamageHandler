package com.Teenkung.devDamageHandler.Handlers;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public class DamageDebug {

    /**
     * Print config settings at the start of debug output.
     */
    public static void printConfigSettings(org.bukkit.command.CommandSender recipient) {
        DevDamageHandler plugin = (DevDamageHandler) Bukkit.getPluginManager().getPlugin("DevDamageHandler");
        if (plugin == null) return;
        
        DamageConfig config = plugin.getDamageConfig();
        if (config == null) return;
        
        StringBuilder sb = new StringBuilder();
        sb.append("<gray>┌─────── <gold>Active Config</gold> ───────┐</gray>\n");
        sb.append("<gray>│</gray> <yellow>Formula:</yellow> <white>").append(config.getFlatDefenseType().name()).append("</white>\n");
        
        switch (config.getFlatDefenseType()) {
            case DIMINISHING:
                sb.append("<gray>│</gray> <yellow>Base:</yellow> <white>").append(fmt(config.getFlatDefenseBase())).append("</white>\n");
                sb.append("<gray>│</gray> <dark_gray>dmg × (1 - def/(def+base))</dark_gray>\n");
                break;
            case LINEAR:
                sb.append("<gray>│</gray> <dark_gray>dmg × (1 - def/100)</dark_gray>\n");
                break;
            case FLAT:
                sb.append("<gray>│</gray> <dark_gray>dmg - def (min 1)</dark_gray>\n");
                break;
        }
        
        sb.append("<gray>│</gray> <yellow>% Cap:</yellow> <white>").append(fmt(config.getPercentDefenseCap())).append("%</white>\n");
        sb.append("<gray>│</gray> <yellow>Weakness Cap:</yellow> <white>").append(fmt(config.getWeaknessCap())).append("%</white>\n");
        sb.append("<gray>│</gray> <yellow>Carrier Ignored:</yellow> <white>").append(config.isIgnoreCarrierOnElemental() ? "Yes" : "No").append("</white>\n");
        sb.append("<gray>└────────────────────────────┘</gray>");
        
        Msg.send(recipient, sb.toString());
    }

    public static void printDebugHeader(org.bukkit.command.CommandSender recipient, LivingEntity target, DamageMetadata dmg,
                                        Map<Element, Double> elemRaw, Map<String, Double> mmMods, String targetId) {
        // Print config first
        printConfigSettings(recipient);
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n<gray>========= <gold>Damage Debug</gold> =========</gray>\n");
        sb.append("<yellow>Target:</yellow> <white>").append(targetId).append("</white>\n");

        sb.append("<yellow>Raw Packets:</yellow>\n");
        int i = 1;
        for (DamagePacket p : dmg.getPackets()) {
            sb.append("  <gray>#").append(i++).append("</gray> <white>")
                    .append(fmt(p.getFinalValue()))
                    .append("</white> <gray>types=</gray><white>")
                    .append(p.getTypes().toString())
                    .append("</white> <gray>elem=</gray><white>")
                    .append(p.getElement() != null ? p.getElement().getId() : "null")
                    .append("</white>\n");
        }

        if (!elemRaw.isEmpty()) {
            sb.append("<yellow>Elements Raw:</yellow>\n");
            elemRaw.forEach((el, val) ->
                    sb.append("  <white>").append(el.getId()).append("</white>: <white>").append(fmt(val)).append("</white>\n")
            );
        } else {
            sb.append("<yellow>Elements Raw:</yellow> <dark_gray>(none - all NONE element)</dark_gray>\n");
        }

        sb.append("<yellow>MythicMobs DamageModifiers:</yellow>\n");
        if (mmMods.isEmpty()) {
            sb.append("  <dark_gray>(none or not a MythicMob)</dark_gray>\n");
        } else {
            mmMods.forEach((k, v) ->
                    sb.append("  <white>").append(k).append("</white>: <white>").append(fmt(v)).append("</white>\n")
            );
        }

        Msg.send(recipient, sb.toString());
    }

    public static void printDebugFooter(org.bukkit.command.CommandSender recipient, DamageMetadata dmg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<yellow>Final Packets:</yellow>\n");
        double sum = 0;
        int j = 1;
        for (DamagePacket p : dmg.getPackets()) {
            String valueColor = p.getFinalValue() <= 0 ? "<red>" : "<white>";
            sb.append("  <gray>#").append(j++).append("</gray> ").append(valueColor)
                    .append(fmt(p.getFinalValue()))
                    .append("</").append(p.getFinalValue() <= 0 ? "red" : "white").append("> <gray>types=</gray><white>")
                    .append(p.getTypes().toString())
                    .append("</white>\n");
            sum += p.getFinalValue();
        }
        sb.append("<gray>Total Final Damage:</gray> <green>").append(fmt(sum)).append("</green>\n");
        sb.append("<gray>====================================</gray>");

        Msg.send(recipient, sb.toString());
    }

    /**
     * Print defense calculation breakdown for a single element.
     */
    public static void printDefenseCalculation(org.bukkit.command.CommandSender recipient,
            String elementName, double incomingDamage, 
            double flatDef, double pctDef, double weakness,
            double finalMultiplier, double finalDamage) {
        
        DevDamageHandler plugin = (DevDamageHandler) Bukkit.getPluginManager().getPlugin("DevDamageHandler");
        DamageConfig config = plugin != null ? plugin.getDamageConfig() : null;
        
        StringBuilder sb = new StringBuilder();
        sb.append("<gray>┌─ <aqua>").append(elementName).append(" Defense Calc</aqua> ─┐</gray>\n");
        sb.append("<gray>│</gray> <yellow>Incoming:</yellow> <white>").append(fmt(incomingDamage)).append("</white>\n");
        
        if (weakness > 0) {
            double weakMul = config != null ? config.calculateWeaknessMultiplier(weakness) : 1.0 + weakness/100.0;
            sb.append("<gray>│</gray> <red>Weakness:</red> <white>+").append(fmt(weakness)).append("%</white> <gray>(×").append(fmt(weakMul)).append(")</gray>\n");
        }
        
        if (flatDef > 0) {
            String formulaType = config != null ? config.getFlatDefenseType().name() : "UNKNOWN";
            double flatMul = config != null ? config.calculateFlatDefenseMultiplier(flatDef) : 1.0;
            sb.append("<gray>│</gray> <green>Flat Def:</green> <white>").append(fmt(flatDef)).append("</white>\n");
            sb.append("<gray>│</gray>   <dark_gray>").append(formulaType).append(" → ×").append(fmt(flatMul)).append("</dark_gray>\n");
        }
        
        if (pctDef > 0) {
            double cappedPct = config != null ? Math.min(pctDef, config.getPercentDefenseCap()) : pctDef;
            double pctMul = 1.0 - (cappedPct / 100.0);
            sb.append("<gray>│</gray> <green>% Def:</green> <white>-").append(fmt(cappedPct)).append("%</white> <gray>(×").append(fmt(pctMul)).append(")</gray>\n");
        }
        
        sb.append("<gray>│</gray> <yellow>Final:</yellow> <white>").append(fmt(incomingDamage)).append("</white> → <green>").append(fmt(finalDamage)).append("</green>\n");
        sb.append("<gray>│</gray>   <dark_gray>Total ×").append(fmt(finalMultiplier)).append("</dark_gray>\n");
        sb.append("<gray>└────────────────────────────┘</gray>");
        
        Msg.send(recipient, sb.toString());
    }

    /**
     * Print enhanced debug for mob → player attacks.
     * Shows attacker info, mob element/type, player defense, and damage calculation.
     */
    public static void printMobAttackDebug(org.bukkit.command.CommandSender recipient, 
            LivingEntity attacker, LivingEntity victim,
            MobElementConfig mobConfig, DamageMetadata dmg,
            Map<String, Double> attackerMmMods, Map<String, Double> victimMmMods) {
        
        // Print config first
        printConfigSettings(recipient);
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n<gray>========= <gold>Mob → Player Debug</gold> =========</gray>\n");
        
        // Attacker info
        String attackerId = getEntityId(attacker);
        sb.append("<yellow>Attacker:</yellow> <white>").append(attackerId).append("</white>\n");
        sb.append("<yellow>Base Damage:</yellow> <white>").append(fmt(dmg.getDamage())).append("</white>\n");
        
        // Mob element config
        if (mobConfig != null) {
            sb.append("<yellow>Primary Element:</yellow> <white>")
              .append(mobConfig.getPrimaryElement() != null ? mobConfig.getPrimaryElement().getId() : "NONE")
              .append("</white>\n");
            sb.append("<yellow>Primary Type:</yellow> <white>")
              .append(mobConfig.getPrimaryType())
              .append("</white>\n");
        } else {
            sb.append("<yellow>Mob Config:</yellow> <dark_gray>(not configured)</dark_gray>\n");
        }
        
        // Attacker's DamageModifiers (how mob is configured)
        if (!attackerMmMods.isEmpty()) {
            sb.append("\n<yellow>Attacker DamageModifiers:</yellow>\n");
            attackerMmMods.forEach((k, v) ->
                sb.append("  <gray>").append(k).append(":</gray> <white>").append(fmt(v)).append("</white>\n")
            );
        }
        
        // Victim (player) defense stats
        if (victim instanceof Player player) {
            sb.append("\n<yellow>Player Defense Stats:</yellow>\n");
            io.lumine.mythic.lib.api.player.MMOPlayerData data = 
                io.lumine.mythic.lib.api.player.MMOPlayerData.get(player.getUniqueId());
            if (data != null) {
                double defense = data.getStatMap().getStat("DEFENSE");
                double dmgRed = data.getStatMap().getStat("DAMAGE_REDUCTION");
                sb.append("  <gray>DEFENSE:</gray> <white>").append(fmt(defense)).append("</white>\n");
                sb.append("  <gray>DAMAGE_REDUCTION:</gray> <white>").append(fmt(dmgRed)).append("%</white>\n");
                
                // Element-specific defense if mob has an element
                if (mobConfig != null && mobConfig.getPrimaryElement() != null) {
                    String elemId = mobConfig.getPrimaryElement().getId().toUpperCase();
                    double elemDef = data.getStatMap().getStat(elemId + "_DEFENSE");
                    double elemPctDef = data.getStatMap().getStat(elemId + "_DEFENSE_PERCENT");
                    double elemWeak = data.getStatMap().getStat(elemId + "_WEAKNESS");
                    
                    if (elemDef > 0 || elemPctDef > 0 || elemWeak > 0) {
                        sb.append("\n  <aqua>").append(elemId).append(" Element Stats:</aqua>\n");
                        if (elemDef > 0) {
                            sb.append("    <gray>").append(elemId).append("_DEFENSE:</gray> <white>").append(fmt(elemDef)).append("</white>\n");
                        }
                        if (elemPctDef > 0) {
                            sb.append("    <gray>").append(elemId).append("_DEFENSE_PERCENT:</gray> <white>").append(fmt(elemPctDef)).append("%</white>\n");
                        }
                        if (elemWeak > 0) {
                            sb.append("    <gray>").append(elemId).append("_WEAKNESS:</gray> <red>+").append(fmt(elemWeak)).append("%</red>\n");
                        }
                    }
                }
            }
        }
        
        // Victim MythicMob modifiers (if victim is also a MythicMob)
        if (!victimMmMods.isEmpty()) {
            sb.append("\n<yellow>Victim DamageModifiers:</yellow>\n");
            victimMmMods.forEach((k, v) -> {
                String color = v < 1.0 ? "<green>" : (v > 1.0 ? "<red>" : "<white>");
                String colorEnd = v < 1.0 ? "</green>" : (v > 1.0 ? "</red>" : "</white>");
                sb.append("  <gray>").append(k).append(":</gray> ").append(color).append(fmt(v)).append(colorEnd).append("\n");
            });
        }
        
        // Damage packets
        sb.append("\n<yellow>Damage Packets:</yellow>\n");
        int i = 1;
        double total = 0;
        for (DamagePacket p : dmg.getPackets()) {
            String valueColor = p.getFinalValue() <= 0 ? "<red>" : "<white>";
            sb.append("  <gray>#").append(i++).append("</gray> ").append(valueColor)
              .append(fmt(p.getFinalValue()))
              .append(p.getFinalValue() <= 0 ? "</red>" : "</white>")
              .append(" <gray>types=</gray><white>")
              .append(p.getTypes().toString())
              .append("</white> <gray>elem=</gray><white>")
              .append(p.getElement() != null ? p.getElement().getId() : "NONE")
              .append("</white>\n");
            total += p.getFinalValue();
        }
        sb.append("\n<yellow>Final Damage:</yellow> <green>").append(fmt(total)).append("</green>\n");
        sb.append("<gray>====================================</gray>");
        
        Msg.send(recipient, sb.toString());
    }
    
    /**
     * Get entity identifier for debug display.
     */
    private static String getEntityId(LivingEntity entity) {
        if (io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().isMythicMob(entity)) {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager()
                .getMythicMobInstance(entity).getMobType();
        }
        if (entity instanceof Player p) {
            return "PLAYER:" + p.getName();
        }
        return "VANILLA_" + entity.getType().name();
    }

    /**
     * Print relevant stats for attacker and victim.
     * Only shows stats that are relevant to the damage calculation.
     */
    public static void printRelevantStats(org.bukkit.command.CommandSender recipient,
            LivingEntity attacker, LivingEntity victim, java.util.Set<Element> elements) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n<gray>╔═════════════════════════════╗</gray>\n");
        sb.append("<gray>║</gray>              <gold>RELEVANT STATS</gold>                   <gray>║</gray>\n");
        sb.append("<gray>╚═════════════════════════════╝</gray>\n");
        
        // Attacker stats (if player)
        if (attacker instanceof Player attackerPlayer) {
            io.lumine.mythic.lib.api.player.MMOPlayerData attackerData = 
                io.lumine.mythic.lib.api.player.MMOPlayerData.get(attackerPlayer.getUniqueId());
            if (attackerData != null) {
                sb.append("\n<aqua>▶ Attacker:</aqua> <white>").append(attackerPlayer.getName()).append("</white>\n");
                sb.append("<gray>├─ Offense Stats ─────────────────────┤</gray>\n");
                
                // Critical stats
                double critChance = attackerData.getStatMap().getStat("CRITICAL_STRIKE_CHANCE");
                double critPower = attackerData.getStatMap().getStat("CRITICAL_STRIKE_POWER");
                double skillCritChance = attackerData.getStatMap().getStat("SKILL_CRITICAL_STRIKE_CHANCE");
                double skillCritPower = attackerData.getStatMap().getStat("SKILL_CRITICAL_STRIKE_POWER");
                double elemCritChance = attackerData.getStatMap().getStat("ELEMENTAL_CRITICAL_STRIKE_CHANCE");
                double elemCritPower = attackerData.getStatMap().getStat("ELEMENTAL_CRITICAL_STRIKE_POWER");
                
                if (critChance > 0 || critPower > 0) {
                    sb.append("<gray>│</gray> <yellow>Weapon Crit:</yellow> <white>").append(fmt(critChance)).append("%</white> ");
                    sb.append("<yellow>Power:</yellow> <white>").append(fmt(critPower)).append("%</white>\n");
                }
                if (skillCritChance > 0 || skillCritPower > 0) {
                    sb.append("<gray>│</gray> <yellow>Skill Crit:</yellow> <white>").append(fmt(skillCritChance)).append("%</white> ");
                    sb.append("<yellow>Power:</yellow> <white>").append(fmt(skillCritPower)).append("%</white>\n");
                }
                if (elemCritChance > 0 || elemCritPower > 0) {
                    sb.append("<gray>│</gray> <yellow>Elem Crit:</yellow> <white>").append(fmt(elemCritChance)).append("%</white> ");
                    sb.append("<yellow>Power:</yellow> <white>").append(fmt(elemCritPower)).append("%</white>\n");
                }
                
                // Element-specific offense stats
                for (Element el : elements) {
                    String elId = el.getId().toUpperCase();
                    double elemDmg = attackerData.getStatMap().getStat(elId + "_DAMAGE");
                    double elemPct = attackerData.getStatMap().getStat(elId + "_DAMAGE_PERCENT");
                    double addElem = attackerData.getStatMap().getStat("ADDITIONAL_ELEMENTAL_DAMAGE");
                    
                    if (elemDmg > 0 || elemPct > 0) {
                        sb.append("<gray>│</gray> <aqua>").append(elId).append(":</aqua>");
                        if (elemDmg > 0) sb.append(" <white>").append(fmt(elemDmg)).append(" base</white>");
                        if (elemPct > 0) sb.append(" <white>+").append(fmt(elemPct)).append("%</white>");
                        sb.append("\n");
                    }
                    if (addElem > 0) {
                        sb.append("<gray>│</gray> <aqua>ALL ELEM:</aqua> <white>+").append(fmt(addElem)).append("%</white>\n");
                    }
                }
            }
        }
        
        // Victim stats (if player)
        if (victim instanceof Player victimPlayer) {
            io.lumine.mythic.lib.api.player.MMOPlayerData victimData = 
                io.lumine.mythic.lib.api.player.MMOPlayerData.get(victimPlayer.getUniqueId());
            if (victimData != null) {
                sb.append("\n<red>▶ Victim:</red> <white>").append(victimPlayer.getName()).append("</white>\n");
                sb.append("<gray>├─ Defense Stats ─────────────────────┤</gray>\n");
                
                // General defense
                double defense = victimData.getStatMap().getStat("DEFENSE");
                double dmgRed = victimData.getStatMap().getStat("DAMAGE_REDUCTION");
                
                if (defense > 0 || dmgRed > 0) {
                    sb.append("<gray>│</gray> <yellow>General:</yellow>");
                    if (defense > 0) sb.append(" <green>").append(fmt(defense)).append(" DEF</green>");
                    if (dmgRed > 0) sb.append(" <green>-").append(fmt(dmgRed)).append("% DMG</green>");
                    sb.append("\n");
                }
                
                // Element-specific defense stats
                for (Element el : elements) {
                    String elId = el.getId().toUpperCase();
                    double elemDef = victimData.getStatMap().getStat(elId + "_DEFENSE");
                    double elemDefPct = victimData.getStatMap().getStat(elId + "_DEFENSE_PERCENT");
                    double elemWeak = victimData.getStatMap().getStat(elId + "_WEAKNESS");
                    
                    if (elemDef > 0 || elemDefPct > 0 || elemWeak > 0) {
                        sb.append("<gray>│</gray> <aqua>").append(elId).append(":</aqua>");
                        if (elemDef > 0) sb.append(" <green>").append(fmt(elemDef)).append(" DEF</green>");
                        if (elemDefPct > 0) sb.append(" <green>-").append(fmt(elemDefPct)).append("%</green>");
                        if (elemWeak > 0) sb.append(" <red>+").append(fmt(elemWeak)).append("% WEAK</red>");
                        sb.append("\n");
                    }
                }
            }
        }
        
        sb.append("<gray>└─────────────────────────────┘</gray>");
        
        Msg.send(recipient, sb.toString());
    }

    public static String fmt(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) return String.valueOf((long) Math.rint(d));
        return String.format(Locale.US, "%.3f", d);
    }
}
