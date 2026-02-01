package com.Teenkung.devDamageHandler.Handlers;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class PlayerStatProvider implements StatProvider {

    private final Player player;
    private final MMOPlayerData data;

    public PlayerStatProvider(Player player) {
        this.player = player;
        this.data = MMOPlayerData.get(player.getUniqueId());
    }

    @Override
    public Double apply(String statId) {
        if (data == null) return 0.0;
        return data.getStatMap().getStat(statId);
    }

    @Override
    public LivingEntity getEntity() {
        return player;
    }
}
