# DevDamageHandler

DevDamageHandler is an advanced combat calculation system tailored for Paper 1.21 servers. It is designed to intercept and override standard damage mechanics, providing a robust framework for elemental damage, specific attack types, and custom critical hit calculations.

This project integrates deeply with **MythicLib**, **MythicMobs**, and **MMOItems** to offer granular control over damage logic and visual feedback.

## Key Features

*   **Advanced Damage Mechanics**
    *   **Elemental Damage:** Complete implementation of MythicLib elements (e.g., Fire, Ice) with support for weighted distribution across multiple elements.
    *   **Attack Types:** Differentiation between various damage sources such as `PHYSICAL`, `MAGIC`, and `SKILL`.
    *   **Custom Critical Hits:** distinct calculation logic for Elemental Critical Hits versus standard Physical Critical Hits.

*   **Custom Statistics (MMOItems Integration)**
    *   `ELEMENTAL_CRITICAL_STRIKE_CHANCE`: Probability of triggering an elemental critical hit.
    *   `ELEMENTAL_CRITICAL_STRIKE_POWER`: Multiplier applied during an elemental critical hit.
    *   `ADDITIONAL_ELEMENTAL_DAMAGE`: Flat or percentage-based bonus to elemental damage.
    *   These statistics are full integrated into the MMOItems stat editor.

*   **Optimized Damage Indicators**
    *   **HologramLib Support:** Utilizes packet-based holograms for optimal server performance.
    *   **MythicLib Fallback:** Automatically reverts to MythicLib indicator handling if HologramLib is unavailable.
    *   **Custom Fonts:** Configurable mapping allows for the use of custom resource pack fonts for damage values via the `font-map` configuration.

*   **Developer Tools**
    *   **Debug Mode:** detailed, per-player combat logs displaying raw damage packets, active multipliers, and final calculated values.
    *   **Damage Simulation:** Command-based tools to manually simulate complex damage events for testing purposes.
    *   **Stat Dump:** Comprehensive inspection tool to view all active stats and modifiers on any entity.

## System Requirements

*   **Java Runtime**: Version 21 or higher
*   **Server Software**: Paper 1.21 (or compatible fork)
*   **Required Dependencies**:
    *   MythicLib
    *   MythicMobs
    *   MMOItems
*   **Optional Dependencies**:
    *   HologramLib (Highly recommended for performance)
    *   EcoEnchants / LibreForge (For extended hook integration)
    *   PlaceholderAPI (For `ddh_` placeholders)

## Installation

1.  Ensure the server is stopped.
2.  Place the `DevDamageHandler.jar` file into the server's `/plugins` directory.
3.  Verify that all required dependencies are installed and up to date.
4.  Start the server.
5.  Review the generated `config.yml` to customize damage handling parameters and indicator styles.

## Command Reference

**Main Command:** `/ddh` (Alias: `/devdamagehandler`)

| Subcommand | Usage | Description |
| :--- | :--- | :--- |
| **Reload** | `/ddh reload` | Reloads the configuration files and indicator settings. |
| **Debug** | `/ddh debug [on\|off\|toggle] [player]` | Toggles the debug mode. When enabled, detailed combat logs are sent to the player. |
| **Stats** | `/ddh stats [player] [filter]` | Displays a dump of all stats (MMOItems/MythicLib) for the specified player or target entity. |
| **Damage** | `/ddh damage <atk> <vic> <amt> <types> <elems>` | Simulates a damage event. <br>Example: `/ddh damage @self @target 10 PHYSICAL FIRE:1,ICE:1` |

## Permissions

*   `devdamagehandler.use` - Grants access to the main command. (Default: OP)
*   `devdamagehandler.debug` - Grants access to debug and statistics commands. (Default: OP)
*   `devdamagehandler.reload` - Grants permission to reload the plugin configuration. (Default: OP)

## Configuration

The `config.yml` file allows for the customization of:
*   **Indicators:** Formatting, positional offsets, and icon mappings.
*   **Font Map:** Assignment of custom resource pack fonts to specific damage numbers.
*   **Damage Formulas:** Adjustment of calculation logic where exposed.

---

## PlaceholderAPI Expansion

When PlaceholderAPI is installed, DevDamageHandler registers a PAPI expansion with the prefix **`ddh_`**.

These placeholders expose values that MythicLib's own PAPI integration does **not** cover — specifically the *computed* defense effectiveness derived from this plugin's configurable formulas (DIMINISHING / LINEAR / FLAT) and all the per-player stat reads that feed into the damage pipeline.

### Static Config Values

These return information about the server's configured formulas and caps. They do **not** require an online player.

| Placeholder | Example Output | Description |
| :--- | :--- | :--- |
| `%ddh_flat_defense_formula%` | `DIMINISHING` | Formula used for the DEFENSE stat: `DIMINISHING`, `LINEAR`, or `FLAT`. |
| `%ddh_flat_defense_base%` | `100.00` | Base constant used in the DIMINISHING formula (`defense / (defense + base)`). |
| `%ddh_percent_defense_formula%` | `LINEAR` | Formula used for DAMAGE_REDUCTION and other % reductions. |
| `%ddh_percent_defense_cap%` | `90.00` | Maximum percentage that can be reduced via % reduction stats. |
| `%ddh_weakness_cap%` | `200.00` | Maximum weakness amplification (%). |

### Raw Player Stats

Direct reads from the MythicLib stat map — the same values that feed into the damage calculation.

| Placeholder | Stat Read | Description |
| :--- | :--- | :--- |
| `%ddh_flat_defense%` | `DEFENSE` | Player's flat defense value. |
| `%ddh_damage_reduction%` | `DAMAGE_REDUCTION` | Player's generic % damage reduction. |
| `%ddh_pve_damage_reduction%` | `PVE_DAMAGE_REDUCTION` | Player's % reduction against mob attacks. |
| `%ddh_pvp_damage_reduction%` | `PVP_DAMAGE_REDUCTION` | Player's % reduction against player attacks. |
| `%ddh_physical_damage_reduction%` | `PHYSICAL_DAMAGE_REDUCTION` | Player's % reduction against physical damage. |
| `%ddh_magic_damage_reduction%` | `MAGIC_DAMAGE_REDUCTION` | Player's % reduction against magic damage. |
| `%ddh_projectile_damage_reduction%` | `PROJECTILE_DAMAGE_REDUCTION` | Player's % reduction against projectile damage. |

### Computed Defense Values

These placeholders run the configured formulas against the player's current stats and return the actual effective result.

| Placeholder | Example Output | Description |
| :--- | :--- | :--- |
| `%ddh_defense_reduction_pct%` | `33.33` | Actual % reduced by the `DEFENSE` stat using the flat-defense formula (e.g. DIMINISHING base 100, 50 DEFENSE → 33.33%). |
| `%ddh_damage_reduction_pct%` | `25.00` | Actual % reduced by `DAMAGE_REDUCTION` after the percent-defense formula and cap. |
| `%ddh_pve_damage_reduction_pct%` | `15.00` | Actual % reduced by `PVE_DAMAGE_REDUCTION` after formula and cap. |
| `%ddh_pvp_damage_reduction_pct%` | `10.00` | Actual % reduced by `PVP_DAMAGE_REDUCTION` after formula and cap. |
| `%ddh_physical_damage_reduction_pct%` | `20.00` | Actual % reduced by `PHYSICAL_DAMAGE_REDUCTION` after formula and cap. |
| `%ddh_magic_damage_reduction_pct%` | `5.00` | Actual % reduced by `MAGIC_DAMAGE_REDUCTION` after formula and cap. |
| `%ddh_projectile_damage_reduction_pct%` | `0.00` | Actual % reduced by `PROJECTILE_DAMAGE_REDUCTION` after formula and cap. |
| `%ddh_damage_reduction_effective%` | `0.7500` | Remaining damage multiplier after `DAMAGE_REDUCTION` (e.g. `0.75` = 25% off). Useful when you need the raw multiplier rather than the % form. |
| `%ddh_total_reduction_pct%` | `54.20` | Combined % reduction from the two **universal** stats only: `DEFENSE` + `DAMAGE_REDUCTION`, stacked geometrically. Situational stats (PVE/PVP/type-specific) are excluded because they depend on the combat context. |

> **`_pct` vs raw stat:** The raw `%ddh_pvp_damage_reduction%` gives you the stat value as configured on the item/class (e.g. `25.00`). The `_pct` variant `%ddh_pvp_damage_reduction_pct%` gives you the *actual* damage % reduced after the server's formula (LINEAR / DIMINISHING) and cap are applied — these differ when using DIMINISHING or when the cap is hit.

> **Note:** The `FLAT` formula type uses direct subtraction (`damage - defense`) and cannot be expressed as a percentage without knowing the incoming damage. `defense_reduction_pct` and related flat-defense computed fields will return `0.00` when the flat-defense formula is set to `FLAT`.

### Damage Simulation (`_calc_<number>`)

These placeholders simulate how much damage a player would actually receive from a given raw damage value, with one or more stats applied. Replace `<number>` with a positive integer or decimal (e.g. `100`, `250.5`).

**Per-stat simulation** — applies only that one stat in isolation:

| Placeholder | Applies | Example (value = 100) |
| :--- | :--- | :--- |
| `%ddh_defense_calc_<n>%` | DEFENSE (flat formula) | FLAT 10 defense → `90.00` |
| `%ddh_damage_reduction_calc_<n>%` | DAMAGE_REDUCTION | 25% LINEAR → `75.00` |
| `%ddh_pve_damage_reduction_calc_<n>%` | PVE_DAMAGE_REDUCTION | 20% LINEAR → `80.00` |
| `%ddh_pvp_damage_reduction_calc_<n>%` | PVP_DAMAGE_REDUCTION | 10% LINEAR → `90.00` |
| `%ddh_physical_damage_reduction_calc_<n>%` | PHYSICAL_DAMAGE_REDUCTION | 30% LINEAR → `70.00` |
| `%ddh_magic_damage_reduction_calc_<n>%` | MAGIC_DAMAGE_REDUCTION | 15% LINEAR → `85.00` |
| `%ddh_projectile_damage_reduction_calc_<n>%` | PROJECTILE_DAMAGE_REDUCTION | 0% → `100.00` |

**Total simulation** — stacks the two universal stats in order (flat defense first, then % reduction):

| Placeholder | Applies | Example (value = 100) |
| :--- | :--- | :--- |
| `%ddh_total_calc_<n>%` | DEFENSE → DAMAGE_REDUCTION | FLAT 10 + 50% DIMINISHING → `45.00` |

> **Order matters for `total_calc`:** Flat defense is subtracted first, then the percent reduction multiplier is applied to the remainder. This matches the actual in-game pipeline.

> **`_calc_` numbers can be decimals:** `%ddh_total_calc_157.5%` is valid.

### Element Placeholders

Replace `<ELEMENT>` with the element's ID in upper- or lower-case (e.g. `fire`, `ice`, `FIRE`).

| Placeholder | Stat Read | Description |
| :--- | :--- | :--- |
| `%ddh_element_<ELEMENT>_defense%` | `{ELEMENT}_DEFENSE` | Player's flat defense against that element. |
| `%ddh_element_<ELEMENT>_defense_pct%` | `{ELEMENT}_DEFENSE_PERCENT` | Player's % reduction against that element. |
| `%ddh_element_<ELEMENT>_weakness%` | `{ELEMENT}_WEAKNESS` | Player's weakness to that element (% increased damage taken). |
| `%ddh_element_<ELEMENT>_defense_reduction_pct%` | computed | Actual % absorbed by the elemental flat defense using the configured formula. |

**Examples:**
```
%ddh_element_fire_defense%           → 75.00
%ddh_element_fire_defense_pct%       → 20.00
%ddh_element_fire_weakness%          → 0.00
%ddh_element_fire_defense_reduction_pct%  → 42.86
```

---
*Developed by Teenkung123*
