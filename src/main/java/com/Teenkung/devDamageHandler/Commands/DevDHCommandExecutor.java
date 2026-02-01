package com.Teenkung.devDamageHandler.Commands;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import com.Teenkung.devDamageHandler.Handlers.DamageDebug;
import com.Teenkung.devDamageHandler.Handlers.DamageHandler;
import com.Teenkung.devDamageHandler.Handlers.MobStatProvider;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Command executor for DevDamageHandler.
 * Commands:
 * - /ddh reload
 * - /ddh debug [on|off|toggle] [player]
 * - /ddh damage <attacker> <victim> <amount> <types> <elements>
 * - /ddh stats [player] [filter]
 */
@SuppressWarnings("SameReturnValue")
public final class DevDHCommandExecutor implements CommandExecutor {

    private final DevDamageHandler plugin;

    public DevDHCommandExecutor(DevDamageHandler plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, label, args);
            case "debuglibreforge" -> handleLibReforgeDebug(sender, label, args);
            case "damage" -> handleDamage(sender, label, args);
            case "stats" -> handleStats(sender, args);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    // ----------------------------
    // Subcommand handlers
    // ----------------------------

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("devdamagehandler.reload")) {
            Msg.send(sender, "<red>No permission.");
            return true;
        }
        plugin.reloadEverything();
        Msg.send(sender, "<green>DevDamageHandler configuration reloaded.");
        return true;
    }

    private boolean handleDebug(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Players only.");
            return true;
        }
        if (!sender.hasPermission("devdamagehandler.debug")) {
            Msg.send(sender, "<red>No permission.");
            return true;
        }

        // /ddh debug
        if (args.length == 1) {
            boolean state = plugin.togglePlayerDebug(player);
            Msg.send(sender, "<gray>Debug: " + (state ? "<green>ON" : "<red>OFF"));
            return true;
        }

        // /ddh debug <on|off|toggle> [player]
        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = (args.length >= 3) ? Bukkit.getPlayerExact(args[2]) : player;

        if (target == null) {
            Msg.send(sender, "<red>Player not found.");
            return true;
        }

        if (!applyDebugAction(sender, label, action, target)) {
            return true;
        }

        Msg.send(sender, "<gray>Debug for "
                + target.getName() + ": "
                + (plugin.getPlayerDebugMode(target) ? "<green>ON" : "<red>OFF"));

        return true;
    }

    private boolean handleLibReforgeDebug(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Players only.");
            return true;
        }
        if (!sender.hasPermission("devdamagehandler.debug")) {
            Msg.send(sender, "<red>No permission.");
            return true;
        }

        Player target = (args.length >= 2) ? Bukkit.getPlayerExact(args[1]) : player;
        if (target == null) {
            Msg.send(sender, "<red>Player not found.");
            return true;
        }

        plugin.toggleLibReforgeDebug(target);

        Msg.send(sender, "<gray>LibReforge Debug for "
                + target.getName() + ": "
                + (plugin.getLibReforgeDebugMode(target) ? "<green>ON" : "<red>OFF"));

        return true;
    }

    private boolean applyDebugAction(CommandSender sender, String label, String action, Player target) {
        switch (action) {
            case "on" -> plugin.setPlayerDebugMode(target, true);
            case "off" -> plugin.setPlayerDebugMode(target, false);
            case "toggle" -> plugin.togglePlayerDebug(target);
            default -> {
                Msg.send(sender, "<yellow>Usage:</yellow> /" + label + " debug <on|off|toggle> [player]");
                return false;
            }
        }
        return true;
    }

    private boolean handleDamage(CommandSender sender, String label, String[] args) {
        // /ddh damage <attacker> <victim> <amount> <types> <elements>
        if (!sender.hasPermission("devdamagehandler.debug")) {
            Msg.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 6) {
            sendDamageUsage(sender, label);
            return true;
        }

        LivingEntity attacker = resolveLivingEntity(sender, args[1], "Attacker");
        if (attacker == null) return true;

        LivingEntity victim = resolveLivingEntity(sender, args[2], "Victim");
        if (victim == null) return true;

        Double amount = parseDouble(sender, args[3], "Amount");
        if (amount == null) return true;

        Set<DamageType> types = parseDamageTypes(sender, args[4]);
        if (types == null) return true;

        Map<Element, Double> elements = parseElementsWeighted(sender, args[5], amount);

        triggerDamageWithOverrides(sender, attacker, victim, amount, types, elements);
        return true;
    }

    private void sendDamageUsage(CommandSender sender, String label) {
        Msg.send(sender, "<yellow>Usage: /" + label + " damage <attacker> <victim> <amount> <types> <elements></yellow>");
        Msg.send(sender, "<gray>Attacker/Victim: @self, @target, or PlayerName</gray>");
        Msg.send(sender, "<gray>Types: SKILL,PHYSICAL (comma-separated)</gray>");
        Msg.send(sender, "<gray>Elements: FIRE,ICE (split equally) or ICE:2,FIRE:1 (weighted)</gray>");
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("devdamagehandler.debug")) {
            Msg.send(sender, "<red>No permission.");
            return true;
        }

        LivingEntity target = resolveStatsTarget(sender, args);
        if (target == null) {
            Msg.send(sender, "<red>Target not found.");
            return true;
        }

        String filter = (args.length >= 3) ? args[2].toLowerCase(Locale.ROOT) : null;

        String name = (target.getCustomName() != null) ? target.getCustomName() : target.getName();
        Msg.send(sender, "<gold>========= Stats: " + name + " =========</gold>");

        if (target instanceof Player pTarget) {
            dumpPlayerStats(sender, pTarget, filter);
        } else {
            dumpMobStats(sender, target, filter);
        }

        Msg.send(sender, "<gold>=============================</gold>");
        return true;
    }

    // ----------------------------
    // Damage helpers
    // ----------------------------

    private LivingEntity resolveLivingEntity(CommandSender sender, String token, String what) {
        LivingEntity entity = null;

        if (sender instanceof Player p) {
            if (token.equalsIgnoreCase("@self")) {
                entity = p;
            } else if (token.equalsIgnoreCase("@target")) {
                Entity t = p.getTargetEntity(10);
                if (t instanceof LivingEntity le) entity = le;
            }
        }

        if (entity == null) {
            Player named = Bukkit.getPlayerExact(token);
            if (named != null) entity = named;
        }

        if (entity == null) {
            Msg.send(sender, "<red>" + what + " not found: " + token);
        }
        return entity;
    }

    private Double parseDouble(CommandSender sender, String raw, String fieldName) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            Msg.send(sender, "<red>Invalid " + fieldName + ": " + raw);
            return null;
        }
    }

    private Set<DamageType> parseDamageTypes(CommandSender sender, String raw) {
        Set<DamageType> out = new HashSet<>();
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            try {
                out.add(DamageType.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                Msg.send(sender, "<red>Invalid Type: " + s);
                return null;
            }
        }
        if (out.isEmpty()) {
            Msg.send(sender, "<red>No valid types provided.</red>");
            return null;
        }
        return out;
    }

    /**
     * Parses elements like:
     * - "ICE,FIRE" => equal weights
     * - "ICE:2,FIRE:1" => weighted
     * - "NONE" / "NULL" ignored
     *
     * Returns amounts normalized so the total == amount (unless no valid elements -> empty map).
     */
    private Map<Element, Double> parseElementsWeighted(CommandSender sender, String raw, double amount) {
        Map<Element, Double> weights = new LinkedHashMap<>();
        double totalWeight = 0.0;

        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            if (s.equalsIgnoreCase("NONE") || s.equalsIgnoreCase("NULL")) continue;

            String[] pieces = s.split(":");
            String id = pieces[0].trim();
            if (id.isEmpty()) continue;

            double weight = 1.0;
            if (pieces.length > 1) {
                Double parsed = parseDouble(sender, pieces[1].trim(), "Element Weight");
                if (parsed == null) continue;
                weight = parsed;
            }

            Element element = findElementById(id);
            if (element == null) {
                Msg.send(sender, "<red>Invalid Element: " + id + " (Ignored)");
                continue;
            }

            if (weight <= 0) continue;
            weights.merge(element, weight, Double::sum);
            totalWeight += weight;
        }

        Map<Element, Double> amounts = new LinkedHashMap<>();
        if (totalWeight <= 0) return amounts;

        for (Map.Entry<Element, Double> e : weights.entrySet()) {
            amounts.put(e.getKey(), (e.getValue() / totalWeight) * amount);
        }
        return amounts;
    }

    private Element findElementById(String id) {
        for (Element e : MythicLib.plugin.getElements().getAll()) {
            if (e.getId().equalsIgnoreCase(id)) return e;
        }
        return null;
    }

    private void triggerDamageWithOverrides(CommandSender sender,
                                            LivingEntity attacker,
                                            LivingEntity victim,
                                            double amount,
                                            Set<DamageType> types,
                                            Map<Element, Double> elements) {
        DamageHandler.pendingElements.set(elements);
        DamageHandler.pendingTypes.set(types);
        DamageHandler.debugOverride.set(sender);

        try {
            victim.damage(Math.max(0.01, amount), attacker);
            Msg.send(sender, "<green>Damage event triggered: " + amount + " (" + elements.size() + " elems)");
        } catch (Exception e) {
            Msg.send(sender, "<red>Error: " + e.getMessage());
        } finally {
            DamageHandler.pendingElements.remove();
            DamageHandler.pendingTypes.remove();
            DamageHandler.debugOverride.remove();
        }
    }

    // ----------------------------
    // Stats helpers
    // ----------------------------

    private LivingEntity resolveStatsTarget(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player named = Bukkit.getPlayerExact(args[1]);
            if (named != null) return named;
        }

        if (sender instanceof Player p) {
            Entity t = p.getTargetEntity(10);
            if (t instanceof LivingEntity le) return le;
            return p;
        }

        return null;
    }

    private void dumpPlayerStats(CommandSender sender, Player target, String filter) {
        MMOPlayerData data = MMOPlayerData.get(target.getUniqueId());
        if (data == null) {
            Msg.send(sender, "<red>No MMOPlayerData found.</red>");
            return;
        }

        StatMap statMap = data.getStatMap();
        List<StatInstance> sorted = new ArrayList<>(statMap.getInstances());
        sorted.sort(Comparator.comparing(StatInstance::getStat));

        for (StatInstance inst : sorted) {
            if (inst.getTotal() == 0 && inst.getBase() == 0) continue;
            if (!matchesFilter(inst.getStat(), filter)) continue;
            Msg.send(sender, formatStatInstance(inst));
        }
    }

    private boolean matchesFilter(String text, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        return text.toLowerCase(Locale.ROOT).contains(filter);
    }

    private String formatStatInstance(StatInstance inst) {
        StringBuilder sb = new StringBuilder();
        sb.append("<gold>").append(inst.getStat()).append("</gold>: ");
        sb.append("<aqua>").append(DamageDebug.fmt(inst.getTotal())).append("</aqua> ");
        sb.append("<yellow>(Base: ").append(DamageDebug.fmt(inst.getBase())).append(")</yellow>");

        List<StatModifier> mods = new ArrayList<>(inst.getModifiers());
        mods.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        double totalFlat = computeTotalFlat(inst.getBase(), inst.getModifiers());

        for (StatModifier mod : mods) {
            sb.append("\n    ").append(formatModifierLine(mod, totalFlat));
        }

        return sb.toString();
    }

    private double computeTotalFlat(double base, Collection<StatModifier> mods) {
        double totalFlat = base;
        for (StatModifier mod : mods) {
            if ("FLAT".equals(mod.getType().toString())) {
                totalFlat += mod.getValue();
            }
        }
        return totalFlat;
    }

    private String formatModifierLine(StatModifier mod, double totalFlat) {
        double val = mod.getValue();
        boolean relative = "RELATIVE".equals(mod.getType().toString());

        String color = val >= 0 ? "<green>" : "<red>";
        String sign = val >= 0 ? "+ " : "";
        String extra = "";

        String valStr;
        if (relative) {
            valStr = DamageDebug.fmt(val) + "%";
            double flatEq = totalFlat * (val / 100.0);
            if (Math.abs(flatEq) > 0.001) {
                extra = " <yellow>[" + (flatEq >= 0 ? "+" : "") + DamageDebug.fmt(flatEq) + "]</yellow>";
            }
        } else {
            valStr = DamageDebug.fmt(val);
        }

        return color + sign + valStr + extra + " <gray>(" + mod.getKey() + ")</gray>";
    }

    private void dumpMobStats(CommandSender sender, LivingEntity target, String filter) {
        MobStatProvider provider = new MobStatProvider(target);

        Msg.send(sender, "<yellow>Vanilla/Bukkit:</yellow>");

        if (matchesFilter("health", filter)) {
            AttributeInstance maxHp = target.getAttribute(Attribute.MAX_HEALTH);
            double max = (maxHp != null) ? maxHp.getValue() : 0;
            Msg.send(sender, "  <gray>Health:</gray> <white>"
                    + DamageDebug.fmt(target.getHealth())
                    + "/" + DamageDebug.fmt(max)
                    + "</white>");
        }

        AttributeInstance armor = target.getAttribute(Attribute.ARMOR);
        if (armor != null && matchesFilter("armor", filter)) {
            Msg.send(sender, "  <gray>Armor:</gray> <white>" + DamageDebug.fmt(armor.getValue()) + "</white>");
        }

        AttributeInstance atk = target.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk != null && matchesFilter("attack damage", filter)) {
            Msg.send(sender, "  <gray>Attack Damage:</gray> <white>" + DamageDebug.fmt(atk.getValue()) + "</white>");
        }

        if (MythicBukkit.inst().getMobManager().isMythicMob(target)) {
            Msg.send(sender, "<yellow>MythicMob Variables (Stats):</yellow>");

            String[] keys = {
                    "CRITICAL_STRIKE_CHANCE", "CRITICAL_STRIKE_POWER",
                    "ELEMENTAL_CRITICAL_STRIKE_CHANCE", "ELEMENTAL_CRITICAL_STRIKE_POWER",
                    "ADDITIONAL_ELEMENTAL_DAMAGE", "PVP_DAMAGE", "PVE_DAMAGE"
            };

            boolean foundAny = false;
            for (String k : keys) {
                if (!matchesFilter(k, filter)) continue;
                double val = provider.apply(k);
                if (val != 0) {
                    Msg.send(sender, "  <green>" + k + "</green>: <white>" + DamageDebug.fmt(val) + "</white>");
                    foundAny = true;
                }
            }

            if (!foundAny) Msg.send(sender, "  <gray>(None of the standard stat variables found)</gray>");

            if (provider.getActiveMob() != null && (filter == null || filter.isEmpty())) {
                Msg.send(sender, "<yellow>MythicMob Info:</yellow>");
                Msg.send(sender, "  <gray>Type:</gray> <white>" + provider.getActiveMob().getMobType() + "</white>");
                Msg.send(sender, "  <gray>Level:</gray> <white>" + provider.getActiveMob().getLevel() + "</white>");
                Msg.send(sender, "  <gray>Power:</gray> <white>" + provider.getActiveMob().getPower() + "</white>");
            }
        } else {
            Msg.send(sender, "<gray>(Not a MythicMob)</gray>");
        }
    }

    // ----------------------------
    // Help
    // ----------------------------

    private void sendHelp(CommandSender sender, String label) {
        Msg.send(sender,
                """
                <gray>========= <gold>DevDamageHandler</gold> <gray>=========</gray>
                <yellow>/%s reload</yellow> <gray>- reload configuration</gray>
                <yellow>/%s debug [on|off|toggle] [player]</yellow> <gray>- toggle damage debug mode</gray>
                <yellow>/%s damage <att> <vic> <amt> <types> <elems></yellow> <gray>- debug damage</gray>
                <yellow>/%s stats [player] [stat]</yellow> <gray>- dump player stats (can filter)</gray>
                
                <gray>When debug is ON, attacking mobs shows:</gray>
                <gray>- Raw damage packets</gray>
                <gray>- Element values and multipliers</gray>
                <gray>- MythicMobs DamageModifiers</gray>
                <gray>- Final calculated damage</gray>
                """.formatted(label, label, label, label));
    }
}
