package com.Teenkung.devDamageHandler.Commands;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tab completer for /ddh.
 *
 * Register alongside the executor:
 *   cmd.setExecutor(new DevDHCommandExecutor(plugin));
 *   cmd.setTabCompleter(new DevDHCommandTabCompleter());
 */
public final class DevDHCommandTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String alias,
                                      String[] args) {

        if (args.length == 1) {
            return filter(List.of("reload", "debug", "damage", "stats", "debuglibreforge", "stat", "tempstat"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "stat", "tempstat" -> handleStatTabComplete(sender, sub, args);
            case "damage" -> handleDamageTabComplete(args);
            case "debug" -> handleDebugTabComplete(args);
            case "debuglibreforge", "stats" -> handleStatsTabComplete(args);
            default -> List.of();
        };
    }

    private List<String> handleDebugTabComplete(String[] args) {
        return switch (args.length) {
            case 2 -> filter(List.of("on", "off", "toggle"), args[1]);
            case 3 -> filter(onlinePlayerNames(), args[2]);
            default -> List.of();
        };
    }

    private List<String> handleStatsTabComplete(String[] args) {
        return switch (args.length) {
            case 2 -> filter(onlinePlayerNames(), args[1]);
            case 3 -> filter(suggestCommonStats(), args[2]);
            default -> List.of();
        };
    }

    private List<String> handleDamageTabComplete(String[] args) {
        return switch (args.length) {
            case 2 -> filter(suggestSelfTargetPlayers(), args[1]);
            case 3 -> filter(suggestSelfTargetPlayers(), args[2]);
            case 4 -> filter(List.of("10", "100", "1"), args[3]);
            case 5 -> filter(Arrays.stream(DamageType.values()).map(Enum::name).toList(), args[4]);
            case 6 -> filter(suggestElements(), args[5]);
            default -> List.of();
        };
    }

    private List<String> suggestElements() {
        List<String> elems = new ArrayList<>();
        elems.add("NONE");
        elems.addAll(MythicLib.plugin.getElements().getAll().stream().map(Element::getId).toList());
        return elems;
    }

    private List<String> handleStatTabComplete(CommandSender sender, String sub, String[] args) {
        boolean isTempStat = sub.equals("tempstat");

        // args[1] = action (add/remove/list)
        if (args.length == 2) {
            return filter(List.of("add", "remove", "list"), args[1]);
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        return switch (action) {
            case "list" -> handleListTabComplete(args);
            case "add" -> handleAddTabComplete(args, isTempStat);
            case "remove" -> handleRemoveTabComplete(sender, args);
            default -> List.of();
        };
    }

    private List<String> handleListTabComplete(String[] args) {
        // /ddh stat list <stat>
        if (args.length == 3) {
            return filter(suggestCommonStats(), args[2]);
        }
        return List.of();
    }

    private List<String> handleAddTabComplete(String[] args, boolean isTempStat) {
        return switch (args.length) {
            case 3 -> filter(List.of("<modifier_name>"), args[2]);  // modifier name
            case 4 -> filter(suggestCommonStats(), args[3]);         // stat name
            case 5 -> filter(List.of("FLAT", "RELATIVE"), args[4]); // type
            case 6 -> filter(List.of("1", "10", "100", "0.5", "-10"), args[5]); // value
            case 7 -> isTempStat
                    ? filter(List.of("20", "100", "200", "600", "1200"), args[6])  // ticks for tempstat
                    : filter(List.of("true", "false"), args[6]);                    // silent for stat
            case 8 -> isTempStat
                    ? filter(List.of("true", "false"), args[7])  // silent for tempstat
                    : List.of();
            default -> List.of();
        };
    }

    private List<String> handleRemoveTabComplete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 3 -> filter(suggestExistingModifierNames(sender), args[2]); // modifier name
            case 4 -> filter(suggestCommonStats(), args[3]);                  // stat name
            case 5 -> filter(List.of("true", "false"), args[4]);             // silent
            default -> List.of();
        };
    }

    private List<String> suggestExistingModifierNames(CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();

        MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
        if (data == null) return List.of();

        Set<String> names = new HashSet<>();
        for (StatInstance instance : data.getStatMap().getInstances()) {
            for (StatModifier mod : instance.getModifiers()) {
                String key = mod.getKey();
                if (key.startsWith("ddh_")) {
                    names.add(key.substring(4)); // Remove "ddh_" prefix
                }
            }
        }

        return new ArrayList<>(names);
    }

    private List<String> suggestSelfTargetPlayers() {
        List<String> out = new ArrayList<>();
        out.add("@self");
        out.add("@target");
        out.addAll(onlinePlayerNames());
        return out;
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> suggestCommonStats() {
        return new ArrayList<>(Arrays.asList(
                "MAX_HEALTH", "MAX_MANA", "MAX_STAMINA", "MAX_STELLIUM",
                "HEALTH_REGENERATION", "MANA_REGENERATION", "STAMINA_REGENERATION", "STELLIUM_REGENERATION",
                "ATTACK_DAMAGE", "ATTACK_SPEED", "MOVEMENT_SPEED",
                "DEFENSE", "ARMOR", "ARMOR_TOUGHNESS",
                "DAMAGE_REDUCTION", "PVE_DAMAGE_REDUCTION", "PVP_DAMAGE_REDUCTION",
                "MAGIC_DAMAGE_REDUCTION", "PHYSICAL_DAMAGE_REDUCTION", "PROJECTILE_DAMAGE_REDUCTION",
                "CRITICAL_STRIKE_CHANCE", "CRITICAL_STRIKE_POWER",
                "SKILL_CRITICAL_STRIKE_CHANCE", "SKILL_CRITICAL_STRIKE_POWER",
                "BLOCK_POWER", "BLOCK_REGENERATION", "BLOCK_COOLDOWN_REDUCTION",
                "DODGE_CHANCE", "PARRY_CHANCE", "HIT_CHANCE",
                "PVP_DAMAGE", "PVE_DAMAGE", "WEAPON_DAMAGE", "SKILL_DAMAGE",
                "PROJECTILE_DAMAGE", "MAGIC_DAMAGE", "PHYSICAL_DAMAGE",
                "ELEMENTAL_CRITICAL_STRIKE_CHANCE", "ELEMENTAL_CRITICAL_STRIKE_POWER",
                "ADDITIONAL_ELEMENTAL_DAMAGE"
        ));
    }

    private List<String> filter(List<String> base, String token) {
        if (token == null || token.isEmpty()) return base;
        String low = token.toLowerCase(Locale.ROOT);
        return base.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(low))
                .collect(Collectors.toList());
    }
}
