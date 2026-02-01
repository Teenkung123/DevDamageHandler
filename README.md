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
*Developed by Teenkung123*
