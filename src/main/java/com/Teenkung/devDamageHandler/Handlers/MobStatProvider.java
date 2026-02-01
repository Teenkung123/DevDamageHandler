package com.Teenkung.devDamageHandler.Handlers;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.LivingEntity;

public class MobStatProvider implements StatProvider {

    private final LivingEntity entity;
    private final ActiveMob activeMob;

    public MobStatProvider(LivingEntity entity) {
        this.entity = entity;
        if (MythicBukkit.inst().getMobManager().isActiveMob(entity.getUniqueId())) {
            this.activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);
        } else {
            this.activeMob = null;
        }
    }

    @Override
    public Double apply(String statId) {
        if (activeMob == null) return 0.0;
        
        // Lookup variable from the mob
        // We use the stat ID as the variable name (case-insensitive usually, but variables are specific)
        // MythicMobs variables are often lowercase. Let's try direct lookup first, then lowercase.
        
        // Using getVariableMap().getString/getDouble might be version dependent.
        // MythicBukkit 5.x: activeMob.getVariableMap() returns a VariableMap.
        
        // Assuming we want to support variables defined in the mob config like:
        // Skills:
        // - setvariable{var=CRITICAL_STRIKE_CHANCE;val=20} @Self ~onSpawn
        
        // Or strictly config based? User asked for "add base element / type to the mob".
        // Config variables are cleaner.
        
        // Let's try to get a variable.
        // If not found, check if it's defined in the Mob's configuration section directly?
        // MythicMobs doesn't standardly expose arbitrary config keys easily at runtime without parsing.
        // Variables are the standard way to attach data to a mob instance.
        
        // Let's try getting a variable named after the stat.
        // We convert to lower case for variable convention: critical_strike_chance
        
        String varName = statId.toLowerCase();
        
        // MythicMobs 5.x uses VariableRegistry - API mismatch handling
        // TODO: Fix ActiveMob variable access (getVariableRegistry / getVariables undefined?)
        /*
        if (activeMob.getVariableRegistry().hasVariable(varName)) {
             try {
                 String val = activeMob.getVariableRegistry().getString(varName);
                 return Double.parseDouble(val);
             } catch (Exception e) {
                 return 0.0;
             }
        }
        */
        return 0.0;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }
    
    public ActiveMob getActiveMob() {
        return activeMob;
    }
}
