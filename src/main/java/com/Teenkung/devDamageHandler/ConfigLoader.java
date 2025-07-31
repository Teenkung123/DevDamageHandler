package com.Teenkung.devDamageHandler;

import com.Teenkung.devDamageHandler.Config.*;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigLoader {

    private final DevDamageHandler plugin;

    private final Map<String, Map<Element, Double>>        elementModifiers    = new HashMap<>();
    private final Map<String, EnumMap<DamageType, Double>> damageTypeModifiers = new HashMap<>();

    private StackingProfile defaultProfile;
    private final Map<String, StackingProfile> profiles = new HashMap<>();
    private String elementalDamageStatLore;

    public ConfigLoader(DevDamageHandler plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void reload() {
        elementModifiers.clear();
        damageTypeModifiers.clear();
        profiles.clear();
        loadConfig();
    }

    /* =========================================================== */

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "DamageModifiers.yml");
        if (!file.exists()) plugin.saveResource("DamageModifiers.yml", false);

        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Failed to load DamageModifiers.yml: " + ex.getMessage());
            return;
        }

        // 0) others
        elementalDamageStatLore = plugin.getConfig().getString("stats.elemental_damage");

        // 1) global defaults
        ConfigurationSection defSec = path(cfg, "stacking.defaults");
        defaultProfile = parseStackingProfile(defSec, null);

        // 2) per-mob modifiers
        ConfigurationSection mods = cfg.getConfigurationSection("modifiers");
        if (mods == null) {
            plugin.getLogger().warning("Missing 'modifiers' section in DamageModifiers.yml");
            return;
        }

        for (String mobIdRaw : mods.getKeys(false)) {
            String mobId = mobIdRaw.toLowerCase(Locale.ROOT);
            ConfigurationSection node = mods.getConfigurationSection(mobIdRaw);
            if (node == null) {
                plugin.getLogger().warning("'" + mobIdRaw + "' is not a section, skipping.");
                continue;
            }

            // per-mob stacking override
            StackingProfile mobProfile = parseStackingProfile(path(node, "stacking"), defaultProfile);
            profiles.put(mobId, mobProfile);

            boolean loaded = false;

            // elements
            ConfigurationSection elSec = node.getConfigurationSection("elements");
            if (elSec != null) loaded |= parseElements(mobId, elSec);

            // damage types
            ConfigurationSection dtSec = node.getConfigurationSection("damage-types");
            if (dtSec != null) loaded |= parseDamageTypes(mobId, dtSec);

            if (!loaded) plugin.getLogger().info("No modifiers defined for mob '" + mobIdRaw + "'");
        }
    }

    /* =========================================================== */

    private StackingProfile parseStackingProfile(ConfigurationSection sec, StackingProfile fallback) {
        if (sec == null) return fallback != null ? fallback : createDefaultProfile();

        // base we copy from
        StackingProfile base = (fallback != null ? fallback : createDefaultProfile());

        // copy
        List<DamageType> primaryOrder = new ArrayList<>(base.typeResolver().getPrimaryOrder());
        Set<DamageType>  flags        = EnumSet.copyOf(base.typeResolver().getFlagTypes());
        boolean flagsAffect           = base.typeResolver().isFlagsAffectPrimary();
        CombineMode typeMode          = base.typeResolver().getCombineMode();

        CombineMode elemMode          = base.elementResolver().getCombineMode();
        boolean showImmune            = base.elementResolver().isShowImmune();
        String immuneFormat           = base.elementResolver().getImmuneFormat();
        String critStat               = base.elementResolver().getCritStat();

        // ---- damage-types sub-sec ----
        ConfigurationSection dts = sec.getConfigurationSection("damage-types");
        if (dts != null) {
            List<String> po = dts.getStringList("primary-order");
            if (!po.isEmpty()) {
                primaryOrder.clear();
                for (String s : po) {
                    try {
                        primaryOrder.add(DamageType.valueOf(s.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[Stacking] Unknown DamageType in primary-order: " + s);
                    }
                }
            }

            List<String> ft = dts.getStringList("flag-types");
            if (!ft.isEmpty()) {
                flags.clear();
                for (String s : ft) {
                    try {
                        flags.add(DamageType.valueOf(s.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[Stacking] Unknown flag DamageType: " + s);
                    }
                }
            }

            flagsAffect = dts.getBoolean("flags-affect-primary", flagsAffect);
            typeMode    = CombineMode.fromString(dts.getString("combine-mode"), typeMode);
        }

        // ---- elements sub-sec ----
        ConfigurationSection els = sec.getConfigurationSection("elements");
        if (els != null) {
            elemMode     = CombineMode.fromString(els.getString("combine-mode"), elemMode);
            showImmune   = els.getBoolean("immune-indicator", showImmune);
            immuneFormat = els.getString("immune-format", immuneFormat);
            critStat     = els.getString("crit-stat", critStat);
            if (critStat != null && "null".equalsIgnoreCase(critStat)) critStat = null;
        }

        TypeResolver tr = new TypeResolver(primaryOrder, flags, flagsAffect, typeMode);
        ElementResolver er = new ElementResolver(elemMode, showImmune, immuneFormat, critStat);
        return new StackingProfile(tr, er);
    }

    private StackingProfile createDefaultProfile() {
        List<DamageType> primaryOrder = List.of(DamageType.MAGIC, DamageType.PHYSICAL, DamageType.UNARMED);
        Set<DamageType> flags = EnumSet.of(DamageType.WEAPON, DamageType.PROJECTILE, DamageType.DOT,
                DamageType.MINION, DamageType.ON_HIT, DamageType.SKILL);
        boolean flagsAffect = false;
        CombineMode typeMode = CombineMode.MIN;

        CombineMode elemMode = CombineMode.PRODUCT;
        boolean showImmune = true;
        String immuneFormat = "<gray>IMMUNE";
        String critStat = "CRITICAL_STRIKE_POWER";

        return new StackingProfile(
                new TypeResolver(primaryOrder, flags, flagsAffect, typeMode),
                new ElementResolver(elemMode, showImmune, immuneFormat, critStat)
        );
    }

    private boolean parseElements(String mobId, ConfigurationSection sec) {
        Map<Element, Double> map = new HashMap<>();
        boolean any = false;

        for (String key : sec.getKeys(false)) {
            Object raw = sec.get(key);
            if (!(raw instanceof Number num)) {
                plugin.getLogger().warning("Element '" + key + "' for '" + mobId + "' is not numeric, skipping.");
                continue;
            }
            Element el = MythicLib.plugin.getElements().getOrNull(key);
            if (el == null) {
                plugin.getLogger().warning("Unknown element '" + key + "' for '" + mobId + "', skipping.");
                continue;
            }
            map.put(el, num.doubleValue());
            any = true;
        }

        if (any) elementModifiers.put(mobId, Collections.unmodifiableMap(map));
        return any;
    }

    private boolean parseDamageTypes(String mobId, ConfigurationSection sec) {
        EnumMap<DamageType, Double> map = new EnumMap<>(DamageType.class);
        boolean any = false;

        for (String key : sec.getKeys(false)) {
            Object raw = sec.get(key);
            if (!(raw instanceof Number num)) {
                plugin.getLogger().warning("DamageType '" + key + "' for '" + mobId + "' is not numeric, skipping.");
                continue;
            }
            try {
                DamageType dt = DamageType.valueOf(key.toUpperCase(Locale.ROOT));
                map.put(dt, num.doubleValue());
                any = true;
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown DamageType '" + key + "' for '" + mobId + "', skipping.");
            }
        }

        if (any) damageTypeModifiers.put(mobId, map);
        return any;
    }

    /* =========================================================== */
    /*   PUBLIC ACCESSORS                                          */
    /* =========================================================== */
    public Map<Element, Double> getElementalModifiers(String mobId) {
        return elementModifiers.getOrDefault(mobId.toLowerCase(Locale.ROOT), Collections.emptyMap());
    }

    public EnumMap<DamageType, Double> getDamageTypeModifiers(String mobId) {
        return damageTypeModifiers.getOrDefault(mobId.toLowerCase(Locale.ROOT),
                new EnumMap<>(DamageType.class));
    }

    public StackingProfile getProfile(String mobId) {
        return profiles.getOrDefault(mobId.toLowerCase(Locale.ROOT), defaultProfile);
    }

    public StackingProfile getDefaultProfile() { return defaultProfile; }

    public Map<String, StackingProfile> getProfiles() { return Collections.unmodifiableMap(profiles); }

    public Map<String, Map<Element, Double>> getElementModifiers() { return Collections.unmodifiableMap(elementModifiers); }

    public Map<String, EnumMap<DamageType, Double>> getDamageTypeModifiersMap() { return Collections.unmodifiableMap(damageTypeModifiers); }

    public String getElementalDamageStatLore() {
        return elementalDamageStatLore;
    }

    /* =========================================================== */
    /*   UTIL                                                      */
    /* =========================================================== */
    private static ConfigurationSection path(ConfigurationSection root, String path) {
        return root != null ? root.getConfigurationSection(path) : null;
    }
}
