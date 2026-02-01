# DevDamageMechanic

A custom MythicMobs mechanic that acts as a "smart" replacements for the standard `damage` mechanic. It allows skills to deal **elemental damage** that is either explicitly defined or **inherited from the caster's weapon**.

## Usage

Use `dev-damage` (or alias `smart-damage`) in your MythicMobs skills instead of `damage`.

```yaml
Skills:
- dev-damage{amount=10} @target
- smart-damage{a=20;element=FIRE} @target
```

## Attributes

| Attribute | Alias | Description | Default |
|-----------|-------|-------------|---------|
| `amount` | `a` | The amount of damage to deal. | `1` |
| `element` | `e` | Explicit element definition. | `null` |
| `damage-type` | `type`, `t`, `dt`, `types` | Comma-separated list of damage types (e.g., `PHYSICAL`, `MAGIC`). | `null` |
| `debug` | `d` | Enable debug mode for this skill cast. | `false` |
| `prevent-knockback` | `pkb`, `preventknockback` | Prevent knockback from the damage. | `false` |
| `hit-players` | `hp`, `hitplayers` | Whether to damage players. | `true` |
| `hit-non-players` | `hnp`, `hitnonplayers` | Whether to damage non-players. | `true` |
| `ignore-immunity` | `ii`, `ignoreimmunity` | Ignore damage immunity (reset no-damage-ticks). | `false` |
| `ignore-armor` | `ia`, `ignorearmor` | Ignore armor (currently experimental/partial support). | `false` |

## Damage Types
 You can specify valid MythicLib damage types using the `damage-type` attribute. These will be added to the damage packet.

**Examples:**
```yaml
# Deals 10 Magic Damage
- dev-damage{a=10;type=MAGIC}

# Deals 10 Physical Damage + Weapon Elements
- dev-damage{a=10;type=PHYSICAL}

# Deals 10 Magic Damage with Fire Element
- dev-damage{a=10;type=MAGIC;element=FIRE}
```

## Elemental Logic

### 1. Inherit from Weapon (Default)
If you **do not** specify the `element` attribute, the mechanic looks at the caster's **Main Hand Item**.
- It scans for NBT tags like `MMOITEMS_ELEMENT_FIRE`, `MMOITEMS_ELEMENT_ICE`, etc.
- If found, it calculates the **distribution** of elements on the weapon.
- The skill's `amount` is then split according to these ratios.

**Example:**
- Weapon has `Fire Damage: 10` and `Ice Damage: 10` (50/50 split).
- Skill `dev-damage{a=50}` is cast.
- Result: The target takes **25 Fire** damage and **25 Ice** damage.

### 2. Explicit Elements
You can manually force specific elements using the `element` attribute.

**Syntax:** `ELEMENT_NAME` or `ELEMENT_NAME:PERCENTAGE`

- `element=FIRE`: 100% Fire damage.
- `element=FIRE:50,ICE:50`: 50% Fire, 50% Ice.
- `element=LIGHTNING:200`: Deals double damage as Lightning (percentage of `amount`).

**Examples:**
```yaml
# Deals 10 Fire Damage
- dev-damage{a=10;e=FIRE} 

# Deals 5 Fire and 5 Ice Damage
- dev-damage{a=10;e=FIRE:50,ICE:50}
```

## Integration
This mechanic injects the calculated elemental data into `DamageHandler`, ensuring it works seamlessly with the plugin's:
- Elemental resistance/weakness calculations.
- Damage indicators (Holograms).
- Stats and multipliers.
