package com.Teenkung.devDamageHandler.Indicator;

import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;

import java.util.Set;

public class IndicatorLine {
    public final double value;
    public final boolean immune;
    public final Boolean crit;
    public final Element element;          // null if non-element
    public final Set<DamageType> types;    // to decide weapon vs skill icon
    public final Double mobMul;            // for arrows
    public final String iconOverride;      // if you want to force icon

    public IndicatorLine(double value, boolean immune, boolean crit,
                         Element element, Set<DamageType> types, double mobMul, String iconOverride) {
        this.value = value;
        this.immune = immune;
        this.crit = crit;
        this.element = element;
        this.types = types;
        this.mobMul = mobMul;
        this.iconOverride = iconOverride;
    }
}
