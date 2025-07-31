package com.Teenkung.devDamageHandler.Indicator;

import com.Teenkung.devDamageHandler.Config.ScaleTarget;
import io.lumine.mythic.lib.damage.DamageType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class IndicatorSettings {

    /* ==========================
       PUBLIC FIELDS (used by others)
       ========================== */
    public final boolean enabled;
    public final boolean split;

    public final String lineFormat;
    public final String immuneText;
    public final String numberFormat;

    public final String arrowUp;
    public final String arrowDown;

    // icon system
    private final Map<DamageType, IconPair> typeIcons;
    public final String elementalCritIcon;
    public final String defaultIconNormal;
    public final String defaultIconCrit;

    public final boolean showBothWhenElement;
    public final boolean multiTypeIcons;
    public final int     maxTypeIcons;
    public final String  iconSeparator;
    public final List<IconOrder> iconOrder;
    public final boolean stripPhysicalWhenElement;

    // physics
    public final double radialVelocity;
    public final double gravity;
    public final double initialUpwardVelocity;
    public final double entityHeightPercent;
    public final double entityWidthPercent;
    public final double yOffset;
    public final double rOffset;
    public final boolean move;
    public final long lifespan;
    public final long tickPeriod;

    // scaling
    public final ScaleTarget scaleTarget;

    // also needed by stubForGameIndicators
    public final String decimalFormat; // from numbers.decimal-format
    public final String rawFormat;     // from numbers.format

    public IndicatorSettings(ConfigurationSection damageRoot) {
        if (damageRoot == null) throw new IllegalArgumentException("game-indicators.damage section missing!");

        // ----- 1) GENERAL -----
        ConfigurationSection general = getSec(damageRoot, "general");
        enabled = getBool(general, "enabled", true);
        split   = getBool(general, "split-holograms", true);

        // ----- 2) NUMBERS -----
        ConfigurationSection numbers = getSec(damageRoot, "numbers");
        decimalFormat = getStr(numbers, "decimal-format", "0.##");
        numberFormat  = getStr(numbers, "number-format", "0.##");
        rawFormat     = getStr(numbers, "format", "{value}");

        // ----- 3) TEXT -----
        ConfigurationSection text = getSec(damageRoot, "text");
        lineFormat  = getStr(text, "line-format", "{arrow}{icon}<white>{value}</white>");
        immuneText  = getStr(text, "immune-text", "{icon}<gray>IMMUNE");

        // ----- 4) ICON -----
        ConfigurationSection icon = getSec(damageRoot, "icon");
        elementalCritIcon      = getStr(icon, "elemental-crit", "");
        defaultIconNormal      = getStr(icon, "default-normal", "");
        defaultIconCrit        = getStr(icon, "default-crit", "");

        showBothWhenElement    = getBool(icon,"show-both-when-element", false);
        multiTypeIcons         = getBool(icon,"multi-type-icons", false);
        maxTypeIcons           = icon.getInt("max-type-icons", 0);
        iconSeparator          = getStr(icon,"icon-separator", "");
        stripPhysicalWhenElement = getBool(icon,"strip-physical-when-element", true);

        // order list
        List<String> ord = icon.getStringList("order");
        if (ord == null || ord.isEmpty()) ord = List.of("ELEMENT", "TYPES");
        iconOrder = new ArrayList<>();
        for (String s : ord) {
            try { iconOrder.add(IconOrder.valueOf(s.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) {}
        }
        if (iconOrder.isEmpty()) iconOrder.add(IconOrder.ELEMENT);

        // map DamageType -> IconPair
        Map<DamageType, IconPair> tmp = new EnumMap<>(DamageType.class);
        ConfigurationSection typesSec = icon.getConfigurationSection("types");
        if (typesSec != null) {
            for (String key : typesSec.getKeys(false)) {
                try {
                    DamageType dt = DamageType.valueOf(key.toUpperCase(Locale.ROOT));
                    ConfigurationSection pair = typesSec.getConfigurationSection(key);
                    if (pair == null) continue;
                    String n = pair.getString("normal", "");
                    String c = pair.getString("crit", n);
                    tmp.put(dt, new IconPair(n, c));
                } catch (IllegalArgumentException ignored) { /* skip unknown */ }
            }
        }
        typeIcons = Collections.unmodifiableMap(tmp);

        // ----- 5) ARROWS -----
        ConfigurationSection arrows = getSec(damageRoot, "arrows");
        arrowUp   = getStr(arrows, "arrow-up", "▲");
        arrowDown = getStr(arrows, "arrow-down", "▼");

        // ----- 6) PHYSICS -----
        ConfigurationSection physics = getSec(damageRoot, "physics");
        radialVelocity        = physics.getDouble("radial-velocity", 1.0);
        gravity               = physics.getDouble("gravity", 1.0);
        initialUpwardVelocity = physics.getDouble("initial-upward-velocity", 1.0);
        entityHeightPercent   = physics.getDouble("entity-height-percent", 0.75);
        entityWidthPercent    = physics.getDouble("entity-width-percent", 0.75);
        yOffset               = physics.getDouble("y-offset", 0.1);
        rOffset               = physics.getDouble("r-offset", 0.1);
        move                  = physics.getBoolean("move", true);
        lifespan              = physics.getLong("lifespan", 20L);
        tickPeriod            = physics.getLong("tick-period", 3L);

        // ----- 7) SCALING -----
        ConfigurationSection scaling = getSec(damageRoot, "scaling");
        scaleTarget = ScaleTarget.fromString(scaling.getString("scale-target"), ScaleTarget.BUKKIT);
    }

    /* ---------- helpers ---------- */

    private static ConfigurationSection getSec(ConfigurationSection root, String path) {
        return root != null ? root.getConfigurationSection(path) : null;
    }
    private static boolean getBool(ConfigurationSection sec, String key, boolean def) {
        return sec != null ? sec.getBoolean(key, def) : def;
    }
    private static String getStr(ConfigurationSection sec, String key, String def) {
        return sec != null ? sec.getString(key, def) : def;
    }

    public String iconFor(Collection<DamageType> types, boolean crit) {
        List<String> list = iconListFor(types, crit);
        return list.isEmpty() ? (crit ? defaultIconCrit : defaultIconNormal) : list.get(0);
    }

    public List<String> iconListFor(Collection<DamageType> types, boolean crit) {
        if (types == null || types.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (DamageType dt : types) {
            IconPair p = typeIcons.get(dt);
            if (p == null) continue;
            out.add(crit ? p.crit() : p.normal());
            if (!multiTypeIcons && !out.isEmpty()) break;
            if (maxTypeIcons > 0 && out.size() >= maxTypeIcons) break;
        }
        return out;
    }

    public String joinIcons(List<String> parts) {
        if (parts.isEmpty()) return "";
        if (iconSeparator == null || iconSeparator.isEmpty())
            return String.join("", parts);
        return String.join(iconSeparator, parts);
    }

    private record IconPair(String normal, String crit) {}
    public enum IconOrder { ELEMENT, TYPES }
}
