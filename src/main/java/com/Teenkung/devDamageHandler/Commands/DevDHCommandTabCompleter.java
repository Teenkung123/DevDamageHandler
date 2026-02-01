package com.Teenkung.devDamageHandler.Commands;

import io.lumine.mythic.lib.MythicLib;
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
            return filter(List.of("reload", "debug", "damage", "stats", "debuglibreforge"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            return switch (sub) {
                case "debug" -> filter(List.of("on", "off", "toggle"), args[1]);
                case "damage" -> filter(suggestSelfTargetPlayers(), args[1]);
                case "debuglibreforge", "stats" -> filter(onlinePlayerNames(), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "debug" -> filter(onlinePlayerNames(), args[2]);
                case "damage" -> filter(suggestSelfTargetPlayers(), args[2]);
                case "stats" -> filter(suggestCommonStats(), args[2]);
                default -> List.of();
            };
        }

        if (args.length == 4 && sub.equals("damage")) {
            return filter(List.of("10", "100", "1"), args[3]);
        }

        if (args.length == 5 && sub.equals("damage")) {
            return filter(Arrays.stream(DamageType.values()).map(Enum::name).toList(), args[4]);
        }

        if (args.length == 6 && sub.equals("damage")) {
            List<String> elems = new ArrayList<>();
            elems.add("NONE");
            elems.addAll(MythicLib.plugin.getElements().getAll().stream().map(Element::getId).toList());
            return filter(elems, args[5]);
        }

        return List.of();
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
