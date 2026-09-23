package com.Teenkung.devDamageHandler.Integration.PlaceholderAPI;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Handlers.DamageConfig;
import com.Teenkung.devDamageHandler.Handlers.PlayerDefenseApplicator;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for DevDamageHandler.
 * Prefix: ddh_
 *
 * ── Static config values ─────────────────────────────────────────────────
 *   ddh_flat_defense_formula          DIMINISHING | LINEAR | FLAT
 *   ddh_flat_defense_base             base constant used in the formula
 *   ddh_percent_defense_formula       formula type for DAMAGE_REDUCTION
 *   ddh_percent_defense_cap           cap (%) applied to percent reductions
 *   ddh_weakness_cap                  maximum weakness amplification (%)
 *
 * ── Raw player stats (from MythicLib stat map) ───────────────────────────
 *   ddh_flat_defense                  DEFENSE stat
 *   ddh_damage_reduction              DAMAGE_REDUCTION stat (%)
 *   ddh_pve_damage_reduction          PVE_DAMAGE_REDUCTION stat (%)
 *   ddh_pvp_damage_reduction          PVP_DAMAGE_REDUCTION stat (%)
 *   ddh_physical_damage_reduction     PHYSICAL_DAMAGE_REDUCTION stat (%)
 *   ddh_magic_damage_reduction        MAGIC_DAMAGE_REDUCTION stat (%)
 *   ddh_projectile_damage_reduction   PROJECTILE_DAMAGE_REDUCTION stat (%)
 *
 * ── Computed defense values ───────────────────────────────────────────────
 *   ddh_defense_reduction_pct             actual % damage reduced by DEFENSE via flat formula
 *   ddh_damage_reduction_pct             actual % reduced by DAMAGE_REDUCTION after formula/cap
 *   ddh_pve_damage_reduction_pct         actual % reduced by PVE_DAMAGE_REDUCTION after formula/cap
 *   ddh_pvp_damage_reduction_pct         actual % reduced by PVP_DAMAGE_REDUCTION after formula/cap
 *   ddh_physical_damage_reduction_pct    actual % reduced by PHYSICAL_DAMAGE_REDUCTION after formula/cap
 *   ddh_magic_damage_reduction_pct       actual % reduced by MAGIC_DAMAGE_REDUCTION after formula/cap
 *   ddh_projectile_damage_reduction_pct  actual % reduced by PROJECTILE_DAMAGE_REDUCTION after formula/cap
 *   ddh_damage_reduction_effective       remaining multiplier from DAMAGE_REDUCTION, e.g. "0.7500"
 *   ddh_total_reduction_pct             combined % from universal stats (DEFENSE + DAMAGE_REDUCTION)
 *
 * ── Damage simulation (_calc_<number>) ───────────────────────────────────
 *   ddh_defense_calc_<n>                   damage after DEFENSE only
 *   ddh_damage_reduction_calc_<n>          damage after DAMAGE_REDUCTION only
 *   ddh_pve_damage_reduction_calc_<n>      damage after PVE_DAMAGE_REDUCTION only
 *   ddh_pvp_damage_reduction_calc_<n>      damage after PVP_DAMAGE_REDUCTION only
 *   ddh_physical_damage_reduction_calc_<n> damage after PHYSICAL_DAMAGE_REDUCTION only
 *   ddh_magic_damage_reduction_calc_<n>    damage after MAGIC_DAMAGE_REDUCTION only
 *   ddh_projectile_damage_reduction_calc_<n> damage after PROJECTILE_DAMAGE_REDUCTION only
 *   ddh_total_calc_<n>                     damage after DEFENSE then DAMAGE_REDUCTION stacked
 *
 * ── Armor enchantment protection (vanilla-integration.armor-enchantments) ─
 *   ddh_armor_prot                  raw EPF% from Protection enchantments (capped at 80)
 *   ddh_armor_prot_pct              effective % damage reduced after DDH formula
 *   ddh_armor_prot_projectile       raw EPF% including Projectile Protection
 *   ddh_armor_prot_projectile_pct   effective % for projectile damage after DDH formula
 *
 * ── Element-specific placeholders (replace <ELEMENT> with element name) ─
 *   ddh_element_<ELEMENT>_defense                flat elemental defense stat
 *   ddh_element_<ELEMENT>_defense_pct            elemental percent defense stat
 *   ddh_element_<ELEMENT>_weakness               elemental weakness stat
 *   ddh_element_<ELEMENT>_defense_reduction_pct  computed % reduction from elemental flat defense
 */
public class DDHPlaceholders extends PlaceholderExpansion {

    private final DevDamageHandler plugin;

    public DDHPlaceholders(DevDamageHandler plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ddh";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        DamageConfig cfg = plugin.getDamageConfig();

        // ── Static config values (no player needed) ──────────────────────
        switch (params) {
            case "flat_defense_formula":  return cfg.getFlatDefenseType().name();
            case "flat_defense_base":     return fmt2(cfg.getFlatDefenseBase());
            case "percent_defense_formula": return cfg.getPercentDefenseType().name();
            case "percent_defense_cap":   return fmt2(cfg.getPercentDefenseCap());
            case "weakness_cap":          return fmt2(cfg.getWeaknessCap());
        }

        // ── Player-dependent placeholders ────────────────────────────────
        if (!(offlinePlayer instanceof Player player)) {
            return "";
        }

        MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
        if (data == null) return "0";

        // ── Raw stats ─────────────────────────────────────────────────────
        switch (params) {
            case "flat_defense":                return fmt2(data.getStatMap().getStat("DEFENSE"));
            case "damage_reduction":            return fmt2(data.getStatMap().getStat("DAMAGE_REDUCTION"));
            case "pve_damage_reduction":        return fmt2(data.getStatMap().getStat("PVE_DAMAGE_REDUCTION"));
            case "pvp_damage_reduction":        return fmt2(data.getStatMap().getStat("PVP_DAMAGE_REDUCTION"));
            case "physical_damage_reduction":   return fmt2(data.getStatMap().getStat("PHYSICAL_DAMAGE_REDUCTION"));
            case "magic_damage_reduction":      return fmt2(data.getStatMap().getStat("MAGIC_DAMAGE_REDUCTION"));
            case "projectile_damage_reduction": return fmt2(data.getStatMap().getStat("PROJECTILE_DAMAGE_REDUCTION"));
        }

        // ── Computed _pct: actual % reduced per stat after formula/cap ───
        switch (params) {
            case "defense_reduction_pct": {
                double defense = data.getStatMap().getStat("DEFENSE");
                double multiplier = cfg.calculateFlatDefenseMultiplier(defense);
                return fmt2((1.0 - Math.max(0.0, Math.min(1.0, multiplier))) * 100.0);
            }
            case "damage_reduction_pct":
                return fmt2(pctReduced(data.getStatMap().getStat("DAMAGE_REDUCTION"), cfg));
            case "pve_damage_reduction_pct":
                return fmt2(pctReduced(data.getStatMap().getStat("PVE_DAMAGE_REDUCTION"), cfg));
            case "pvp_damage_reduction_pct":
                return fmt2(pctReduced(data.getStatMap().getStat("PVP_DAMAGE_REDUCTION"), cfg));
            case "physical_damage_reduction_pct":
                return fmt2(pctReduced(data.getStatMap().getStat("PHYSICAL_DAMAGE_REDUCTION"), cfg));
            case "magic_damage_reduction_pct":
                return fmt2(pctReduced(data.getStatMap().getStat("MAGIC_DAMAGE_REDUCTION"), cfg));
            case "projectile_damage_reduction_pct":
                return fmt2(pctReduced(data.getStatMap().getStat("PROJECTILE_DAMAGE_REDUCTION"), cfg));
        }

        // ── Other computed values ─────────────────────────────────────────
        if (params.equals("damage_reduction_effective")) {
            return fmt4(cfg.calculatePercentDefenseMultiplier(
                    data.getStatMap().getStat("DAMAGE_REDUCTION")));
        }

        if (params.equals("total_reduction_pct")) {
            // Universal stats only: DEFENSE + DAMAGE_REDUCTION
            double flatMultiplier = flatDefenseMultiplier(data.getStatMap().getStat("DEFENSE"), cfg);
            double pctMultiplier  = cfg.calculatePercentDefenseMultiplier(
                    data.getStatMap().getStat("DAMAGE_REDUCTION"));
            return fmt2((1.0 - Math.max(0.0, flatMultiplier * pctMultiplier)) * 100.0);
        }

        // ── Damage simulation: ddh_<stat>_calc_<number> ───────────────────
        if (params.contains("_calc_")) {
            return resolveCalcParam(params, player, data, cfg);
        }

        // ── Armor enchantment protection ─────────────────────────────────
        if (params.startsWith("armor_prot")) {
            return resolveArmorProtParam(params, player, cfg);
        }

        // ── Element-specific ──────────────────────────────────────────────
        if (params.startsWith("element_")) {
            return resolveElementParam(params.substring("element_".length()), data, cfg);
        }

        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Handles ddh_<stat>_calc_<number>: returns the damage value a player would
     * take from <number> raw damage after the specified stat is applied.
     *
     * Supported prefixes:
     *   defense, damage_reduction, pve_damage_reduction, pvp_damage_reduction,
     *   physical_damage_reduction, magic_damage_reduction, projectile_damage_reduction,
     *   total  (DEFENSE flat first, then DAMAGE_REDUCTION percent)
     */
    private @Nullable String resolveCalcParam(String params, Player player, MMOPlayerData data, DamageConfig cfg) {
        int idx = params.lastIndexOf("_calc_");
        String prefix    = params.substring(0, idx);
        String numberStr = params.substring(idx + "_calc_".length());

        double damage;
        try {
            damage = Double.parseDouble(numberStr);
        } catch (NumberFormatException e) {
            return null;
        }
        if (damage < 0) return null;

        switch (prefix) {
            case "defense":
                return fmt2(cfg.applyFlatDefense(damage,
                        data.getStatMap().getStat("DEFENSE")));
            case "damage_reduction":
                return fmt2(damage * cfg.calculatePercentDefenseMultiplier(
                        data.getStatMap().getStat("DAMAGE_REDUCTION")));
            case "pve_damage_reduction":
                return fmt2(damage * cfg.calculatePercentDefenseMultiplier(
                        data.getStatMap().getStat("PVE_DAMAGE_REDUCTION")));
            case "pvp_damage_reduction":
                return fmt2(damage * cfg.calculatePercentDefenseMultiplier(
                        data.getStatMap().getStat("PVP_DAMAGE_REDUCTION")));
            case "physical_damage_reduction":
                return fmt2(damage * cfg.calculatePercentDefenseMultiplier(
                        data.getStatMap().getStat("PHYSICAL_DAMAGE_REDUCTION")));
            case "magic_damage_reduction":
                return fmt2(damage * cfg.calculatePercentDefenseMultiplier(
                        data.getStatMap().getStat("MAGIC_DAMAGE_REDUCTION")));
            case "projectile_damage_reduction":
                return fmt2(damage * cfg.calculatePercentDefenseMultiplier(
                        data.getStatMap().getStat("PROJECTILE_DAMAGE_REDUCTION")));
            case "total": {
                // 1. Flat DEFENSE, 2. DAMAGE_REDUCTION percent, 3. armor enchant protection
                double afterFlat = cfg.applyFlatDefense(damage,
                        data.getStatMap().getStat("DEFENSE"));
                double afterPct  = afterFlat * cfg.calculatePercentDefenseMultiplier(
                        data.getStatMap().getStat("DAMAGE_REDUCTION"));
                double armorProt = PlayerDefenseApplicator.calculateArmorEnchantProtection(player, false);
                double afterArmor = afterPct * cfg.calculatePercentDefenseMultiplier(armorProt);
                return fmt2(afterArmor);
            }
            default:
                return null;
        }
    }

    /**
     * Handles ddh_armor_prot[_projectile][_pct]:
     *   armor_prot                 raw EPF% from Protection only (e.g. 64.00)
     *   armor_prot_pct             actual % reduced after DDH formula
     *   armor_prot_projectile      raw EPF% including Projectile Protection
     *   armor_prot_projectile_pct  actual % reduced for projectile after DDH formula
     */
    private @Nullable String resolveArmorProtParam(String params, Player player, DamageConfig cfg) {
        boolean isProjectile = params.contains("projectile");
        boolean isPct        = params.endsWith("_pct");

        double rawPct = PlayerDefenseApplicator.calculateArmorEnchantProtection(player, isProjectile);

        if (isPct) {
            return fmt2((1.0 - cfg.calculatePercentDefenseMultiplier(rawPct)) * 100.0);
        } else {
            return fmt2(rawPct);
        }
    }

    private @Nullable String resolveElementParam(String sub, MMOPlayerData data, DamageConfig cfg) {
        if (sub.endsWith("_defense_reduction_pct")) {
            String element = sub.substring(0, sub.length() - "_defense_reduction_pct".length()).toUpperCase();
            return fmt2((1.0 - flatDefenseMultiplier(data.getStatMap().getStat(element + "_DEFENSE"), cfg)) * 100.0);
        }
        if (sub.endsWith("_defense_pct")) {
            String element = sub.substring(0, sub.length() - "_defense_pct".length()).toUpperCase();
            return fmt2(data.getStatMap().getStat(element + "_DEFENSE_PERCENT"));
        }
        if (sub.endsWith("_weakness")) {
            String element = sub.substring(0, sub.length() - "_weakness".length()).toUpperCase();
            return fmt2(data.getStatMap().getStat(element + "_WEAKNESS"));
        }
        if (sub.endsWith("_defense")) {
            String element = sub.substring(0, sub.length() - "_defense".length()).toUpperCase();
            return fmt2(data.getStatMap().getStat(element + "_DEFENSE"));
        }
        return null;
    }

    /** Returns the actual % of damage reduced by a percent-reduction stat after formula/cap. */
    private double pctReduced(double stat, DamageConfig cfg) {
        return (1.0 - cfg.calculatePercentDefenseMultiplier(stat)) * 100.0;
    }

    /**
     * Returns the remaining damage multiplier (0-1) after flat defense formula.
     * Returns 1.0 for FLAT formula since it uses subtraction, not a multiplier.
     */
    private double flatDefenseMultiplier(double defense, DamageConfig cfg) {
        if (defense <= 0) return 1.0;
        if (cfg.getFlatDefenseType() == DamageConfig.FormulaType.FLAT) return 1.0;
        return Math.max(0.0, Math.min(1.0, cfg.calculateFlatDefenseMultiplier(defense)));
    }

    private static String fmt2(double value) {
        return String.format("%.2f", value);
    }

    private static String fmt4(double value) {
        return String.format("%.4f", value);
    }
}
