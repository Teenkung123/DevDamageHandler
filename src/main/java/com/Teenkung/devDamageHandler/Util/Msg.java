package com.Teenkung.devDamageHandler.Util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private Msg() {}

    public static void send(CommandSender sender, String mini) {
        Component c = MM.deserialize(mini);
        sender.sendMessage(c);
    }
}
