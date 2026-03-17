package com.Teenkung.devDamageHandler.Util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private Msg() {}

    public static void send(CommandSender sender, String mini) {
        // Strip any legacy formatting codes (§x) before MiniMessage parsing
        String clean = mini.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        Component c = MM.deserialize(clean);
        sender.sendMessage(c);
    }
}
