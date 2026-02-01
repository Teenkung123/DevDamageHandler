package com.Teenkung.devDamageHandler.API;

import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamagePacket;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Immutable per-hit snapshot of damage computation results.
 *
 * Stores:
 * - Elemental raw base (pre any multipliers)
 * - Element multipliers (mob/config, crit, stat) + final per element
 * - Type totals (final) derived from packets (and optional per-type multipliers if you track them)
 * - Packet breakdown (final value + types)
 * - Totals: packetsSum, elementFinalTotal, nonElementFinal, metaDamage (dmg.getDamage())
 * - Flags: weapon/skill crit
 * - Source modifiers (MythicMobs normalized DamageModifiers + merged config element mods)
 */
public final class DamageData {

    /* ===================== Core identity / metadata ===================== */

    private final @NotNull String targetId;
    private final boolean weaponCrit;
    private final boolean skillCrit;

    /* ===================== Elemental ===================== */

    /** Raw elemental base from MythicLib before our multipliers (your elemRaw snapshot). */
    private final @NotNull Map<Element, Double> elementBaseRaw;

    /** Multipliers applied per element in your element stage. */
    private final @NotNull Map<Element, Double> elementMobMul;   // (template×cfg)×MM
    private final @NotNull Map<Element, Double> elementCritMul;  // elemental crit only
    private final @NotNull Map<Element, Double> elementStatMul;  // ADDITIONAL_ELEMENTAL_DAMAGE % etc.

    /** Final per-element damage AFTER all element multipliers. */
    private final @NotNull Map<Element, Double> elementFinal;

    /* ===================== Type ===================== */

    /**
     * Final damage per DamageType (derived from final packets).
     * NOTE: Packets may contain multiple types; we "split" by attributing the packet's full value to each type.
     * If you want a different policy (primary-only, weighted, etc.), adjust the builder logic.
     */
    private final @NotNull Map<DamageType, Double> typeFinal;

    /* ===================== Packets ===================== */

    private final @NotNull List<PacketEntry> packets;

    /* ===================== Totals ===================== */

    private final double packetsSum;         // Σ packet.final
    private final double elementFinalTotal;  // Σ elementFinal
    private final double nonElementFinal;    // packetsSum - carrier - elementFinalTotal (like your indicator calc)
    private final double metaDamage;         // dmg.getDamage()

    /* ===================== Optional “source” data ===================== */

    /** Merged element mods BEFORE MythicMobs (template->cfg). */
    private final @NotNull Map<String, Double> mergedElementConfigMulById; // key = ELEMENT_ID upper

    /** MythicMobs DamageModifiers normalized (UPPER, '-' -> '_') */
    private final @NotNull Map<String, Double> mythicDamageModifiers;

    private DamageData(Builder b) {
        this.targetId = b.targetId;
        this.weaponCrit = b.weaponCrit;
        this.skillCrit = b.skillCrit;

        this.elementBaseRaw = unmodifiableCopyElement(b.elementBaseRaw);
        this.elementMobMul = unmodifiableCopyElement(b.elementMobMul);
        this.elementCritMul = unmodifiableCopyElement(b.elementCritMul);
        this.elementStatMul = unmodifiableCopyElement(b.elementStatMul);
        this.elementFinal = unmodifiableCopyElement(b.elementFinal);

        this.typeFinal = Collections.unmodifiableMap(new EnumMap<>(b.typeFinal));

        this.packets = Collections.unmodifiableList(new ArrayList<>(b.packets));

        this.packetsSum = b.packetsSum;
        this.elementFinalTotal = b.elementFinalTotal;
        this.nonElementFinal = b.nonElementFinal;
        this.metaDamage = b.metaDamage;

        this.mergedElementConfigMulById = Collections.unmodifiableMap(new LinkedHashMap<>(b.mergedElementConfigMulById));
        this.mythicDamageModifiers = Collections.unmodifiableMap(new LinkedHashMap<>(b.mythicDamageModifiers));
    }

    /* ===================== Getters ===================== */

    public @NotNull String getTargetId() { return targetId; }
    public boolean isWeaponCrit() { return weaponCrit; }
    public boolean isSkillCrit() { return skillCrit; }

    public @NotNull Map<Element, Double> getElementBaseRaw() { return elementBaseRaw; }
    public @NotNull Map<Element, Double> getElementMobMul() { return elementMobMul; }
    public @NotNull Map<Element, Double> getElementCritMul() { return elementCritMul; }
    public @NotNull Map<Element, Double> getElementStatMul() { return elementStatMul; }
    public @NotNull Map<Element, Double> getElementFinal() { return elementFinal; }

    public @NotNull Map<DamageType, Double> getTypeFinal() { return typeFinal; }

    public @NotNull List<PacketEntry> getPackets() { return packets; }

    public double getPacketsSum() { return packetsSum; }
    public double getElementFinalTotal() { return elementFinalTotal; }
    public double getNonElementFinal() { return nonElementFinal; }
    public double getMetaDamage() { return metaDamage; }

    public @NotNull Map<String, Double> getMergedElementConfigMulById() { return mergedElementConfigMulById; }
    public @NotNull Map<String, Double> getMythicDamageModifiers() { return mythicDamageModifiers; }

    /* ===================== Convenience ===================== */

    public double getFinalElementDamage(@NotNull String elementIdUpper) {
        for (Map.Entry<Element, Double> e : elementFinal.entrySet()) {
            if (e.getKey() != null && elementIdUpper.equalsIgnoreCase(e.getKey().getId())) return e.getValue();
        }
        return 0.0;
    }

    public double getFinalTypeDamage(@NotNull DamageType type) {
        return typeFinal.getOrDefault(type, 0.0);
    }

    /* ===================== Builder ===================== */

    public static final class Builder {
        private static final double EPS = 1e-6;

        private String targetId = "UNKNOWN";
        private boolean weaponCrit;
        private boolean skillCrit;

        private final Map<Element, Double> elementBaseRaw = new HashMap<>();
        private final Map<Element, Double> elementMobMul = new HashMap<>();
        private final Map<Element, Double> elementCritMul = new HashMap<>();
        private final Map<Element, Double> elementStatMul = new HashMap<>();
        private final Map<Element, Double> elementFinal = new HashMap<>();

        private final Map<DamageType, Double> typeFinal = new EnumMap<>(DamageType.class);

        private final List<PacketEntry> packets = new ArrayList<>();

        private double packetsSum;
        private double elementFinalTotal;
        private double nonElementFinal;
        private double metaDamage;

        private final Map<String, Double> mergedElementConfigMulById = new LinkedHashMap<>();
        private final Map<String, Double> mythicDamageModifiers = new LinkedHashMap<>();

        public Builder targetId(@NotNull String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder critFlags(boolean weaponCrit, boolean skillCrit) {
            this.weaponCrit = weaponCrit;
            this.skillCrit = skillCrit;
            return this;
        }

        public Builder addMergedElementConfigMul(@NotNull String elementIdUpper, double mul) {
            mergedElementConfigMulById.put(elementIdUpper, mul);
            return this;
        }

        public Builder mythicDamageModifiers(@NotNull Map<String, Double> normalizedMmMods) {
            mythicDamageModifiers.clear();
            mythicDamageModifiers.putAll(normalizedMmMods);
            return this;
        }

        public Builder fromHitContext(@NotNull Map<Element, Double> elemBaseRaw,
                                      @NotNull Map<Element, Double> mobMul,
                                      @NotNull Map<Element, Double> critMul,
                                      @NotNull Map<Element, Double> statMul) {
            this.elementBaseRaw.clear();
            this.elementBaseRaw.putAll(elemBaseRaw);

            this.elementMobMul.clear();
            this.elementMobMul.putAll(mobMul);

            this.elementCritMul.clear();
            this.elementCritMul.putAll(critMul);

            this.elementStatMul.clear();
            this.elementStatMul.putAll(statMul);

            // compute final per element
            this.elementFinal.clear();
            this.elementFinalTotal = 0.0;

            for (Map.Entry<Element, Double> e : this.elementBaseRaw.entrySet()) {
                Element el = e.getKey();
                double base = e.getValue() == null ? 0.0 : e.getValue();
                double m = this.elementMobMul.getOrDefault(el, 1.0);
                double c = this.elementCritMul.getOrDefault(el, 1.0);
                double s = this.elementStatMul.getOrDefault(el, 1.0);

                double val = (Math.abs(m) < EPS) ? 0.0 : (base * m * c * s);
                this.elementFinal.put(el, val);
                this.elementFinalTotal += val;
            }

            return this;
        }

        /**
         * Snapshot packet list + build type totals from packet final values.
         */
        public Builder fromDamageMetadata(@NotNull DamageMetadata dmg,
                                          @Nullable DamagePacket carrierPacketIfAny) {
            this.packets.clear();
            this.typeFinal.clear();

            double sum = 0.0;
            for (DamagePacket p : dmg.getPackets()) {
                double v = p.getFinalValue();
                sum += v;

                List<DamageType> types = p.getTypes();
                EnumSet<DamageType> set = (types == null || types.isEmpty())
                        ? EnumSet.noneOf(DamageType.class)
                        : EnumSet.copyOf(types);

                packets.add(new PacketEntry(v, set));

                // Policy: attribute full packet value to each type it claims
                for (DamageType t : set) {
                    typeFinal.merge(t, v, Double::sum);
                }
            }
            this.packetsSum = sum;
            this.metaDamage = dmg.getDamage();

            // mirror your indicator logic for non-element:
            double carrierVal = (carrierPacketIfAny == null) ? 0.0 : carrierPacketIfAny.getFinalValue();
            double nonElem = sum - carrierVal - this.elementFinalTotal;
            if (nonElem < 0.0) nonElem = 0.0;
            this.nonElementFinal = nonElem;

            return this;
        }

        public DamageData build() {
            return new DamageData(this);
        }
    }

    /* ===================== Nested types ===================== */

    public record PacketEntry(double finalValue, @NotNull Set<DamageType> types) {}

    private static Map<Element, Double> unmodifiableCopyElement(Map<Element, Double> src) {
        return Collections.unmodifiableMap(new HashMap<>(src));
    }
}
