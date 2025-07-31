package com.Teenkung.devDamageHandler.Config;

import com.Teenkung.devDamageHandler.Util.IndicatorUtil;
import io.lumine.mythic.lib.element.Element;
import io.lumine.mythic.lib.player.PlayerMetadata;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.teenkung123.damageindicator.api.DamageIndicatorAPI;

import java.util.concurrent.ThreadLocalRandom;

public class ElementResolver {

    private final CombineMode combineMode;
    private final boolean     showImmune;
    private final String      immuneFormat;
    private final String      critStat;

    public ElementResolver(CombineMode combineMode, boolean showImmune, String immuneFormat, String critStat) {
        this.combineMode = combineMode;
        this.showImmune = showImmune;
        this.immuneFormat = immuneFormat;
        this.critStat = critStat;
    }

    public boolean hasCrit() {
        return critStat != null;
    }

    public double critMultiplier(PlayerMetadata attacker) {
        if (critStat == null) return 1.0;
        return attacker.getStat(critStat) / 100.0;
    }

    public double statMultiplier(PlayerMetadata attacker) {
        return attacker.getStat("ADDITIONAL_ELEMENTAL_DAMAGE") / 100.0;
    }

    public double elementalCritChance(PlayerMetadata attacker) {
        // % chance from 0..100
        return attacker.getStat("ELEMENTAL_CRITICAL_STRIKE_CHANCE");
    }

    public double elementalCritPower(PlayerMetadata attacker) {
        // % bonus; e.g. 50 => +50% dmg => x1.50
        return attacker.getStat("ELEMENTAL_CRITICAL_STRIKE_POWER");
    }

    /** Roll elemental crit using chance stat. */
    public boolean rollElementalCrit(PlayerMetadata attacker) {
        double chance = Math.max(0, elementalCritChance(attacker));
        if (chance <= 0) return false;
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    /** Multiplier when a crit happens, e.g. power=50 => 1.50. */
    public double critPowerMultiplier(PlayerMetadata attacker) {
        double power = Math.max(0, elementalCritPower(attacker));
        return 1.0 + (power / 100.0);
    }

    public void indicateImmune(LivingEntity target, Element el) {
        if (!showImmune) return;
        IndicatorUtil.show(target, el.getLoreIcon() + immuneFormat);
//        DamageIndicatorAPI.getInstance().displayDamageIndicator(
//                target, el.getLoreIcon() + immuneFormat
//        );
    }

    public CombineMode getCombineMode() {
        return combineMode;
    }

    public boolean isShowImmune() {
        return showImmune;
    }

    public String getImmuneFormat() {
        return immuneFormat;
    }

    public String getCritStat() {
        return critStat;
    }
}
