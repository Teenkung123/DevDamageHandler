package com.Teenkung.devDamageHandler.Handlers;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuration for the damage system.
 * Loaded from config.yml under the 'damage', 'formulas', and 'elements' sections.
 */
public class DamageConfig {
    
    /**
     * Types of defense formulas available.
     */
    public enum FormulaType {
        /**
         * Diminishing returns: damage * (1 - defense / (defense + base))
         * Higher defense has less impact per point.
         */
        DIMINISHING,
        
        /**
         * Linear: damage * (1 - defense / 100)
         * Each point of defense = 1% reduction.
         */
        LINEAR,
        
        /**
         * Flat: damage - defense (minimum 1)
         * Direct subtraction.
         */
        FLAT
    }
    
    // Damage settings
    private final boolean ignoreCarrierOnElemental;
    private final boolean transferCarrierToElemental;

    // Flat defense formula settings
    private final FormulaType flatDefenseType;
    private final double flatDefenseBase;
    
    // Percent defense formula settings
    private final FormulaType percentDefenseType;
    private final double percentDefenseBase;
    private final double percentDefenseCap;
    
    // Weakness settings
    private final double weaknessCap;
    
    // Element settings
    private final boolean showImmuneIndicator;
    private final boolean immunityNegatesElement;
    
    public DamageConfig(ConfigurationSection root) {
        // Damage section
        ConfigurationSection damageSec = root != null ? root.getConfigurationSection("damage") : null;
        this.ignoreCarrierOnElemental = damageSec != null && damageSec.getBoolean("ignore-carrier-on-elemental", true);
        this.transferCarrierToElemental = damageSec == null || damageSec.getBoolean("transfer-carrier-to-elemental", true);

        // Formulas section
        ConfigurationSection formulaSec = root != null ? root.getConfigurationSection("formulas") : null;
        
        // Flat defense formula
        ConfigurationSection flatDefSec = formulaSec != null ? formulaSec.getConfigurationSection("flat-defense") : null;
        this.flatDefenseType = parseFormulaType(flatDefSec != null ? flatDefSec.getString("type", "DIMINISHING") : "DIMINISHING");
        this.flatDefenseBase = flatDefSec != null ? flatDefSec.getDouble("base", 100) : 100;
        
        // Percent defense formula
        ConfigurationSection pctDefSec = formulaSec != null ? formulaSec.getConfigurationSection("percent-defense") : null;
        this.percentDefenseType = parseFormulaType(pctDefSec != null ? pctDefSec.getString("type", "LINEAR") : "LINEAR");
        this.percentDefenseBase = pctDefSec != null ? pctDefSec.getDouble("base", 100) : 100;
        this.percentDefenseCap = pctDefSec != null ? pctDefSec.getDouble("cap", 90) : 90;
        
        // Weakness
        ConfigurationSection weakSec = formulaSec != null ? formulaSec.getConfigurationSection("weakness") : null;
        this.weaknessCap = weakSec != null ? weakSec.getDouble("cap", 200) : 200;
        
        // Elements section
        ConfigurationSection elemSec = root != null ? root.getConfigurationSection("elements") : null;
        this.showImmuneIndicator = elemSec == null || elemSec.getBoolean("show-immune-indicator", true);
        this.immunityNegatesElement = elemSec == null || elemSec.getBoolean("immunity-negates-element", true);
    }
    
    private FormulaType parseFormulaType(String type) {
        try {
            return FormulaType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FormulaType.DIMINISHING;
        }
    }
    
    // Default constructor with sane defaults
    public DamageConfig() {
        this.ignoreCarrierOnElemental = true;
        this.transferCarrierToElemental = true;
        this.flatDefenseType = FormulaType.DIMINISHING;
        this.flatDefenseBase = 100;
        this.percentDefenseType = FormulaType.LINEAR;
        this.percentDefenseBase = 100;
        this.percentDefenseCap = 90;
        this.weaknessCap = 200;
        this.showImmuneIndicator = true;
        this.immunityNegatesElement = true;
    }
    
    // Getters
    public boolean isIgnoreCarrierOnElemental() {
        return ignoreCarrierOnElemental;
    }
    
    public boolean isTransferCarrierToElemental() {
        return transferCarrierToElemental;
    }

    public FormulaType getFlatDefenseType() {
        return flatDefenseType;
    }
    
    public double getFlatDefenseBase() {
        return flatDefenseBase;
    }
    
    public double getPercentDefenseCap() {
        return percentDefenseCap;
    }

    public FormulaType getPercentDefenseType() {
        return percentDefenseType;
    }

    public double getPercentDefenseBase() {
        return percentDefenseBase;
    }
    
    public double getWeaknessCap() {
        return weaknessCap;
    }
    
    public boolean isShowImmuneIndicator() {
        return showImmuneIndicator;
    }
    
    public boolean isImmunityNegatesElement() {
        return immunityNegatesElement;
    }
    
    /**
     * Calculate flat defense multiplier using the configured formula.
     * 
     * @param defense The defense value
     * @return The damage multiplier (0 to 1, where 1 = full damage)
     */
    public double calculateFlatDefenseMultiplier(double defense) {
        if (defense <= 0) return 1.0;
        
        double multiplier;
        switch (flatDefenseType) {
            case DIMINISHING:
                // damage * (1 - defense / (defense + base))
                multiplier = 1.0 - (defense / (defense + flatDefenseBase));
                break;
            case LINEAR:
                // damage * (1 - defense / 100)
                multiplier = 1.0 - (defense / 100.0);
                break;
            case FLAT:
                // For FLAT, we return a special value that indicates subtraction
                // This is handled differently in the caller
                return -defense; // Negative indicates flat subtraction
            default:
                multiplier = 1.0;
        }
        
        return Math.max(0.0, Math.min(1.0, multiplier));
    }
    
    /**
     * Apply flat defense to damage value.
     * Handles all formula types including FLAT subtraction.
     * 
     * @param damage The incoming damage
     * @param defense The defense value
     * @return The reduced damage (minimum 1 for FLAT formula, minimum 0 for others)
     */
    public double applyFlatDefense(double damage, double defense) {
        if (defense <= 0) return damage;
        
        switch (flatDefenseType) {
            case DIMINISHING:
                return damage * (1.0 - (defense / (defense + flatDefenseBase)));
            case LINEAR:
                return damage * Math.max(0.0, 1.0 - (defense / 100.0));
            case FLAT:
                return Math.max(1.0, damage - defense);
            default:
                return damage;
        }
    }
    
    /**
     * Calculate percent defense multiplier using the configured formula.
     *
     * @param percentReduction The reduction percentage (0-100+)
     * @return The damage multiplier (0 to 1)
     */
    public double calculatePercentDefenseMultiplier(double percentReduction) {
        if (percentReduction <= 0) return 1.0;

        switch (percentDefenseType) {
            case DIMINISHING:
                // reduction / (reduction + base) → never reaches 1.0
                // With base=100: 50 → 33%, 100 → 50%, 200 → 67%
                return 1.0 - (percentReduction / (percentReduction + percentDefenseBase));
            case LINEAR:
            default:
                // Direct percentage, capped to prevent immunity
                double capped = Math.min(percentReduction, percentDefenseCap);
                return 1.0 - (capped / 100.0);
        }
    }
    
    /**
     * Calculate weakness multiplier.
     * 
     * @param weaknessPercent The weakness percentage (0-100+)
     * @return The damage multiplier (1.0+)
     */
    public double calculateWeaknessMultiplier(double weaknessPercent) {
        if (weaknessPercent <= 0) return 1.0;
        double capped = Math.min(weaknessPercent, weaknessCap);
        return 1.0 + (capped / 100.0);
    }
}
