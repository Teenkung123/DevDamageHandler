package com.Teenkung.devDamageHandler.Mechanics;

import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MechanicRegistry implements Listener {

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        if (event.getMechanicName().equalsIgnoreCase("dev-damage") || 
            event.getMechanicName().equalsIgnoreCase("smart-damage")) {
            event.register(new DevDamageMechanic(event.getConfig()));
        }
    }
}
