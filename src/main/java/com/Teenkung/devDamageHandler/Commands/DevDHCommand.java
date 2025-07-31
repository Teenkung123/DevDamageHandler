package com.Teenkung.devDamageHandler.Commands;

import com.Teenkung.devDamageHandler.Config.StackingProfile;
import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Util.Msg;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class DevDHCommand implements CommandExecutor, TabCompleter {

    private final DevDamageHandler plugin;

    public DevDHCommand(DevDamageHandler plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("devdamagehandler.reload")) {
                    Msg.send(sender, "<red>No permission.");
                    return true;
                }
                plugin.reloadEverything();
                Msg.send(sender, "<green>Reloaded DamageModifiers.yml");
            }

            case "debug" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, "<red>Players only.");
                    return true;
                }
                if (!sender.hasPermission("devdamagehandler.debug")) {
                    Msg.send(sender, "<red>No permission.");
                    return true;
                }

                if (args.length == 1) {
                    // toggle self
                    boolean state = plugin.togglePlayerDebug(p);
                    Msg.send(sender, "<gray>Debug: " + (state ? "<green>ON" : "<red>OFF"));
                } else {
                    String action = args[1].toLowerCase(Locale.ROOT);
                    Player target = p;
                    if (args.length >= 3) {
                        target = Bukkit.getPlayerExact(args[2]);
                        if (target == null) {
                            Msg.send(sender, "<red>Player not found.");
                            return true;
                        }
                    }
                    switch (action) {
                        case "on" -> plugin.setPlayerDebugMode(target, true);
                        case "off" -> plugin.setPlayerDebugMode(target, false);
                        case "toggle" -> plugin.togglePlayerDebug(target);
                        default -> {
                            Msg.send(sender, "<yellow>Usage:</yellow> /" + label + " debug <on|off|toggle> [player]");
                            return true;
                        }
                    }
                    Msg.send(sender, "<gray>Debug for "
                            + target.getName() + ": "
                            + (plugin.getPlayerDebugMode(target) ? "<green>ON" : "<red>OFF"));
                }
            }

            case "show" -> {
                if (args.length < 2) {
                    Msg.send(sender, "<yellow>Usage:</yellow> /" + label + " show <mobId>");
                    return true;
                }
                String mobId = args[1].toLowerCase(Locale.ROOT);
                var typeMods = plugin.getConfigLoader().getDamageTypeModifiers(mobId);
                var elemMods = plugin.getConfigLoader().getElementalModifiers(mobId);
                StackingProfile profile = plugin.getConfigLoader().getProfile(mobId);

                if (typeMods.isEmpty() && elemMods.isEmpty() && profile == plugin.getConfigLoader().getDefaultProfile()) {
                    Msg.send(sender, "<gray>No specific modifiers or profile for <white>" + mobId + "</white>. Using defaults.");
                }

                Msg.send(sender, "<yellow>Mob:</yellow> <white>" + mobId + "</white>");
                Msg.send(sender, "<gray>-- DamageTypes --</gray>");
                if (typeMods.isEmpty()) {
                    Msg.send(sender, "<dark_gray>(none)</dark_gray>");
                } else {
                    for (Map.Entry<DamageType, Double> e : typeMods.entrySet()) {
                        Msg.send(sender, "  <white>" + e.getKey() + "</white>: <white>" + e.getValue() + "</white>");
                    }
                }

                Msg.send(sender, "<gray>-- Elements --</gray>");
                if (elemMods.isEmpty()) {
                    Msg.send(sender, "<dark_gray>(none)</dark_gray>");
                } else {
                    for (Map.Entry<Element, Double> e : elemMods.entrySet()) {
                        Msg.send(sender, "  <white>" + e.getKey().getId() + "</white>: <white>" + e.getValue() + "</white>");
                    }
                }

                Msg.send(sender, "<gray>-- Stacking Profile --</gray>");
                var tr = profile.typeResolver();
                var er = profile.elementResolver();
                Msg.send(sender, "<gray>primary-order:</gray> <white>" + tr.getPrimaryOrder() + "</white>");
                Msg.send(sender, "<gray>flag-types:</gray> <white>" + tr.getFlagTypes() + "</white>");
                Msg.send(sender, "<gray>flags-affect-primary:</gray> <white>" + tr.isFlagsAffectPrimary() + "</white>");
                Msg.send(sender, "<gray>damage-type combine-mode:</gray> <white>" + tr.getCombineMode() + "</white>");

                Msg.send(sender, "<gray>element combine-mode:</gray> <white>" + er.getCombineMode() + "</white>");
                Msg.send(sender, "<gray>immune-indicator:</gray> <white>" + er.isShowImmune() + "</white>");
                Msg.send(sender, "<gray>immune-format:</gray> <white>" + er.getImmuneFormat() + "</white>");
                Msg.send(sender, "<gray>crit-stat:</gray> <white>" + er.getCritStat() + "</white>");
            }

            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        Msg.send(sender,
                """
                <gray>========= <gold>DevDamageHandler</gold> <gray>=========</gray>
                <yellow>/" + %s + " reload</yellow> <gray>- reload DamageModifiers.yml</gray>
                <yellow>/" + %s + " debug [on|off|toggle] [player]</yellow> <gray>- toggle debug mode</gray>
                <yellow>/" + %s + " show <mobId></yellow> <gray>- inspect a mob's modifiers & stacking</gray>
                """.replace("%s", label));
    }

    // --- Tab Complete ---
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "debug", "show"), args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "debug" -> {
                    return filter(List.of("on", "off", "toggle"), args[1]);
                }
                case "show" -> {
                    // Better: gather from profile map + element map
                    Set<String> all = new HashSet<>();
                    all.addAll(plugin.getConfigLoader().getProfiles().keySet());
                    all.addAll(plugin.getConfigLoader().getElementModifiers().keySet());
                    all.addAll(plugin.getConfigLoader().getDamageTypeModifiersMap().keySet());
                    return filter(new ArrayList<>(all), args[1]);
                }
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            // player list
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> base, String token) {
        if (token == null || token.isEmpty()) return base;
        String low = token.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(low)).collect(Collectors.toList());
    }
}
