package com.Teenkung.devDamageHandler.Config;

import io.lumine.mythic.lib.damage.DamageType;

import java.util.*;

public class TypeResolver {

    private final List<DamageType> primaryOrder;
    private final Set<DamageType>  flagTypes;
    private final boolean          flagsAffectPrimary;
    private final CombineMode      combineMode;

    public TypeResolver(List<DamageType> primaryOrder,
                        Set<DamageType> flagTypes,
                        boolean flagsAffectPrimary,
                        CombineMode combineMode) {
        this.primaryOrder = List.copyOf(primaryOrder);
        this.flagTypes = EnumSet.copyOf(flagTypes);
        this.flagsAffectPrimary = flagsAffectPrimary;
        this.combineMode = combineMode;
    }

    public DamageType pickPrimary(Set<DamageType> packetTypes) {
        for (DamageType t : primaryOrder) {
            if (packetTypes.contains(t)) return t;
        }
        return packetTypes.stream().findFirst().orElse(null);
    }

    public double compute(Set<DamageType> types, Map<DamageType, Double> table) {
        DamageType primary = pickPrimary(types);
        if (primary == null) return 1.0;

        List<Double> vals = new ArrayList<>();
        Double pMul = table.get(primary);
        if (pMul != null) vals.add(pMul);

        if (flagsAffectPrimary) {
            for (DamageType t : types) {
                if (t == primary) continue;
                if (flagTypes.contains(t)) {
                    Double mul = table.get(t);
                    if (mul != null) vals.add(mul);
                }
            }
        }

        return MultiplierUtil.combine(vals, combineMode);
    }

    public boolean isFlag(DamageType t) {
        return flagTypes.contains(t);
    }

    public double combine(Collection<Double> list) {
        return MultiplierUtil.combine(list, combineMode);
    }

    // Getters for cloning/overriding
    public List<DamageType> getPrimaryOrder() {
        return primaryOrder;
    }

    public Set<DamageType> getFlagTypes() {
        return flagTypes;
    }

    public boolean isFlagsAffectPrimary() {
        return flagsAffectPrimary;
    }

    public CombineMode getCombineMode() {
        return combineMode;
    }
}
