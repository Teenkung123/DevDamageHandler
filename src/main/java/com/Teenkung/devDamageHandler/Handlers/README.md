# README — `DamageHandler`

## Overview

`DamageHandler` is the main pipeline for processing MythicLib damage and building floating combat text (“indicators”). It:

1. **Preprocesses** raw MythicLib damage (small physical/magic tweaks when elements exist).
2. **Applies modifiers** from config:
   - **Type modifiers** (e.g., MAGIC, PHYSICAL, WEAPON flags) with a configurable combine mode.
   - **Element modifiers** (per element vs a mob), elemental crits, and a global **ELEMENTAL_DAMAGE** stat.
3. **Builds indicator lines**:
   - One line per **element** (with crit/immune arrows & icons).
   - One **non‑element** line for the remainder.
   - “Killed‑by‑type” handling (0 damage because of type multipliers) shows an **IMMUNE** line with a **type icon** instead of the element icon.
4. **Scales** visible numbers to your chosen target (META, PACKETS, BUKKIT, NONE).
5. **Prints debug** dumps when per‑player debug is enabled.

The class is split into clear sections:
- Event filters
- Main pipeline (modify + show)
- Stages (flat tweaks / types / elements)
- Indicator assembly
- Helpers (carrier packet guess, ID lookup, formatting)
- Records/containers (results & context)
- Debug printer


## Data Flow

```
PlayerAttackEvent
    ├─ onDamageModify (HIGHEST)
    │    ├─ applyFlatTweaks
    │    ├─ applyTypeStage
    │    └─ applyElementStage   (crit, mob, stat multipliers; registers elemental crits)
    │           ↓
    │        HitContext (elemBase, elemMobMul, elemCritMul, elemStatMul, crit flags)
    └─ onDamageShow (MONITOR)
         └─ pushIndicatorsFinal
              ├─ Build element lines (include statMul)
              ├─ Build non‑element line
              ├─ “Killed by type” → convert to IMMUNE w/ type icon
              ├─ Optional scaling (META / PACKETS / BUKKIT / NONE)
              └─ Display via CustomIndicators
```


## Key Records & Structures

### `ElementStageResult`
Holds per-element multipliers:
- `critMul`: elemental crit multiplier actually applied to MythicLib damage.
- `mobMul`: mob resist/vulnerability from config.
- `statMul`: **ELEMENTAL_DAMAGE** multiplier (1.0 + percent/100).

### `HitContext`
Persists information between the modify and show phases:
- `elemBase`: raw base amounts per element (from MythicLib).
- `elemMobMul`, `elemCritMul`, `elemStatMul`: the factors used.
- `wasWeaponCrit`, `wasSkillCrit`: for non‑element line crit status.
- `mobId`: resolved mythic mob type (lowercase).

### `IndicatorLine` (from your indicators module)
- `value`, `immune`, `crit`, `element`, `types`, `mobMul`, `iconOverride`.
- `CustomIndicators` uses these to build the final text (icons, arrows, numbers).


## Method-by-Method

### `onIndicatorDisplay(IndicatorDisplayEvent)`
Cancels MythicLib’s default holograms that decode to exactly “0” (using your custom number font), preventing clutter.

### `onDamageModify(PlayerAttackEvent)` **(HIGHEST)**
Entry point for **mutating** MythicLib damage:
1. Resolve mob ID; return if not a MythicMob.
2. Fetch raw element map and create a `DebugPrinter` if player debug is on.
3. `applyFlatTweaks`: small fixed adjustments to PHYSICAL/MAGIC if any elemental damage is present.
4. `applyTypeStage`: apply type-level multipliers using your `TypeResolver` and config “Stacking”.
5. `applyElementStage`: per-element crit, mob multiplier, and the **ELEMENTAL_DAMAGE** stat (the new part).
6. Emit the deep debug dump (pre-indicator stage).
7. Package results into `HitContext` and remember it keyed by the MythicLib `DamageMetadata`.

### `applyFlatTweaks(DamageMetadata, Map<Element, Double>)`
If any elemental damage exists, applies small additive offsets to PHYSICAL/MAGIC, then zeroes them out if they drop below 0.1. Purpose: reduce weird tiny leftovers.

> **Tip:** Adjust or remove if you don’t want this behavior. It’s purely a cosmetic/consistency tweak.

### `applyTypeStage(DamageMetadata, TypeResolver, Map<DamageType, Double>, DebugPrinter)`
For each damage packet:
- Pick a **primary** damage type using `TypeResolver.primaryOrder`.
- Optionally include **flag types** (e.g., WEAPON, PROJECTILE) if `flags-affect-primary` is true.
- Combine applicable per-type multipliers via `CombineMode` (PRODUCT, MIN, MAX, AVERAGE, SOFT_ADD, RESIST_ADD).
- Apply the result either:
  - directly to packets with explicit types, or
  - to “orphan” packets (no explicit types) as a raw value multiply.

Writes summary info to `DebugPrinter`.

### `applyElementStage(DamageMetadata, raw, mods, ElementResolver, PlayerAttackEvent, DebugPrinter)`
For each element in `raw`:
- **Elemental crit**: if the hit is a weapon crit and `resolver.hasCrit()` is true, get `critMultiplier(attacker)`, multiply MythicLib’s element packet, and register the elemental crit.
- **Mob mul**: get the mob’s multiplier for that element (0 = immune, 1 = neutral, >1 weak).
- **ELEMENTAL_DAMAGE (stat)**: multiplier is `1.0 + resolver.statMultiplier(attacker)`. If player has 25% ELEMENTAL_DAMAGE, multiplier is 1.25. Applied directly to MythicLib damage per element.

Returns all three maps for later use and debugging.

### `onDamageShow(PlayerAttackEvent)` **(MONITOR)**
Gets the stored `HitContext` and calls `pushIndicatorsFinal`.

### `pushIndicatorsFinal(event, dmg, ctx)`
Builds & shows indicators:

1. **Element lines**: For each element:
   ```
   value = base * mobMul * critMul * statMul
   immune if mobMul == 0
   ```
   Passes `types` so CustomIndicators can append type icons (and apply “strip physical when element” if configured).

2. **Non‑element line**:  
   `packetsSum` = Σ final packet values (after all modifiers).  
   `carrierVal` = tiny type carrier packet (if elements exist).  
   `nonElem` = `packetsSum - carrierVal - elementFinalTotal`.  
   If positive, add one line (crits if weapon/skill crit flags are set).

3. **Killed by type?**  
   If `packetsSum` **or** MythicLib’s meta damage is ~0, convert all lines to **IMMUNE**:
   - Keep original element‐immune lines (where `mobMul == 0`) with **element icon**.
   - For the rest, mark immune with a **type icon** so `{icon}` in `immune-text` shows the weapon/magic icon—not the element.

4. **Scaling** (skip if killed by type):
   - Choose target via `scale-target`: `META`, `PACKETS`, `BUKKIT`, `NONE`.
   - Scale the non‑immune numeric values to match the target sum.

5. **Debug**: Prints a concise indicator stage dump (target, sums, scale, and each line).

6. **Display**: Sends the lines to `CustomIndicators`.

### `guessCarrierPacket(DamageMetadata, hasElements)`
If elements exist, MythicLib usually prepends a tiny typed packet. This method returns that packet to avoid double‑counting.

### `mythicId(LivingEntity)`
Returns MythicMob type ID or `null` if not a MythicMob`.


## Configuration Interactions (Quick Reference)

### Type stacking (ConfigLoader & TypeResolver)
- `primary-order`: priority when a packet has multiple primary candidates.
- `flag-types`: optional flags that can also affect the multiplier.
- `flags-affect-primary`: if `true`, flags’ multipliers participate.
- `combine-mode`: how multiple multipliers collapse into one.

### Elements (ConfigLoader & ElementResolver)
- Per‑element mob multipliers (e.g., `WATER: 0`, `FIRE: 0.5`).
- Elemental crits (if you enabled stats in `ElementResolver`).
- **ELEMENTAL_DAMAGE** stat used by `statMultiplier(attacker)`.

### Indicators (IndicatorSettings / CustomIndicators)
- Icon maps for `DamageType` (normal/crit).
- Show both element & types, icon order, max icons, separator.
- Strip PHYSICAL when elements present.
- `{icon}` in `immune-text` will be **element icon** if truly element‑immune, or **type icon** when killed by type modifiers.
- Scale target: `META | PACKETS | BUKKIT | NONE`.


## How‑To

### Add a new DamageType icon
```yaml
game-indicators:
  damage:
    icon:
      types:
        RUNE:
          normal: "⟡"
          crit:   "⟡"
```
No code change needed; `IndicatorSettings` loads it automatically.

### Change icon behavior
- Show both element & type icons: `show-both-when-element: true`
- Limit how many: `max-type-icons: 2`
- Order: `order: [ELEMENT, TYPES]` or `[TYPES, ELEMENT]`
- Remove PHYSICAL when elements present: `strip-physical-when-element: true`

### Make arrows reflect the stat boost too (optional)
Currently arrows (▲/▼) look at `mobMul` only. If you want them to reflect the stat impact as well, change `CustomIndicators.renderLine` to compute arrow from `(mobMul * statMul)`. You’d need to add `statMul` to `IndicatorLine` or encode it in `mobMul` when passing the line.

### Add another per‑element stat
1. Add a method in `ElementResolver` (e.g., `penetrationMultiplier(attacker)`).
2. In `applyElementStage`, compute it and:
   - call `dmg.multiplicativeModifier(value, el)`, and
   - store it into a new map in `ElementStageResult` & `HitContext`,
   - include it in the indicator value calculation and debug printer.

### Adjust “flat tweaks”
Edit or remove `applyFlatTweaks`. If you remove it, you may see tiny non‑element leftovers; that’s harmless.


## Debugging

- **Turn on per‑player debug**: however your plugin exposes it (e.g., `/ddh debug on`). The class checks `plugin.getPlayerDebugMode(player)`.
- **Modify stage dump** (header block):
  - Shows raw packets and their types, raw elements & values.
- **Type stage messages**:
  - “Applied Global Type Mul” for each DamageType applied.
  - “Applied Orphan Packet Mul” if a packet without explicit types was multiplied.
- **Element stage block**:
  - For each element: `base`, `mobMul`, `critMul`, `statMul`, and `finalMul = mob * crit * stat`.
- **Indicator stage dump**:
  - `packetsSum`, `metaDamageSum`, `carrierVal`, `elementFinalTotal`, `nonElem`, `sumDisplay`, `scale`, `killedByType`, and a list of final lines.
- **Common pitfalls**:
  - **Seeing element IMMUNE when you expected a type IMMUNE**: ensure “killed by type” actually triggered (packets/meta sum near zero); otherwise it’s true element immunity.
  - **Non‑zero dust (e.g., 0.01)**: scaling to META/PACKETS can yield tiny values when final damage is near zero. This class suppresses by converting to IMMUNE when sums are ~0 (killed by type). Adjust `EPS` if needed.
  - **Icons not appearing**: check your `icon.types` map and `order` settings; verify `{icon}` is present in `immune-text` and `line-format`.

## Performance Notes

- Uses simple maps/loops; no allocations on hot paths beyond per‑hit collections.
- `HitContext` is lightweight and only lives until `onDamageShow`.
- Debug printing is guarded behind your per‑player flag.


## Extending Safely

- Keep `EPS` consistent across compare points to avoid flicker.
- If you add new multipliers, always:
  1) apply them to MythicLib damage in `applyElementStage`/`applyTypeStage`,
  2) store them in the result/context,
  3) include them when computing indicator values,
  4) print them in the debug output.


## FAQ

**Q:** Does `ELEMENTAL_DAMAGE` apply to the non‑element line?  
**A:** No. It’s applied **per element** only.

**Q:** Where do I change how crits are detected for elements?  
**A:** In `ElementResolver.hasCrit()` and `critMultiplier(attacker)`.

**Q:** I want to disable IMMUNE lines entirely.  
**A:** Change your indicator config (e.g., `immune-indicator: false` if you’ve exposed that), or conditionally skip adding immune lines in `pushIndicatorsFinal`.
