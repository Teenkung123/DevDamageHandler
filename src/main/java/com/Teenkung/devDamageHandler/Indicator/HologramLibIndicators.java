package com.Teenkung.devDamageHandler.Indicator;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import com.Teenkung.devDamageHandler.Util.FontCodec;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.event.IndicatorDisplayEvent;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Optimized damage indicator display using HologramLib's packet-based API.
 * Based on reference implementation from DamageIndicator plugin.
 */
public class HologramLibIndicators {

    private static final Random RNG = new Random();
    
    private final DevDamageHandler plugin;
    private final IndicatorSettings settings;
    private final DecimalFormat formatter;
    private final FontCodec font;
    private final HologramManager hologramManager;
    
    // Physics settings
    private final double yOffset;
    private final double rOffset;
    private final double entityWidthPercent;
    private final double entityHeightPercent;

    public HologramLibIndicators(DevDamageHandler plugin, IndicatorSettings settings, FontCodec font, HologramManager hologramManager) {
        this.plugin = plugin;
        this.settings = settings;
        this.formatter = MythicLib.plugin.getMMOConfig().newDecimalFormat(settings.numberFormat);
        this.font = font;
        this.hologramManager = hologramManager;
        
        // Cache physics settings
        this.yOffset = settings.yOffset;
        this.rOffset = settings.rOffset;
        this.entityWidthPercent = settings.entityWidthPercent;
        this.entityHeightPercent = settings.entityHeightPercent;
    }

    public void displayLines(Entity entity, List<IndicatorLine> lines) {
        if (!settings.enabled || lines.isEmpty()) return;

        if (settings.split) {
            for (IndicatorLine line : lines) displayOne(entity, line);
        } else {
            String joined = lines.stream().map(this::renderLine).collect(Collectors.joining(" "));
            displayOneRaw(entity, joined);
        }
    }

    private void displayOne(Entity entity, IndicatorLine line) {
        displayOneRaw(entity, renderLine(line));
    }

    private String renderLine(IndicatorLine l) {
        // 1) IMMUNE
        if (l.immune) {
            return renderImmuneLine(l);
        }

        // 2) Arrow based on mob multiplier
        String arrow = buildArrow(l.mobMul);

        // 3) Icon
        String icon = buildIcon(l);

        // 4) Number
        String plain = formatter.format(l.value);
        String number = (font != null && font.isEnabled()) ? font.encodeString(plain, l.crit) : plain;

        // 5) Compose - HologramLib uses MiniMessage, no legacy code conversion needed
        return settings.lineFormat
                .replace("{arrow}", arrow)
                .replace("{icon}", safe(icon))
                .replace("{value}", number);
    }

    private String renderImmuneLine(IndicatorLine l) {
        final double EPS = 1e-6;
        boolean elementImmune = l.element != null && l.mobMul != null && Math.abs(l.mobMul) < EPS;
        boolean typeImmune = !elementImmune;

        String icon = l.iconOverride;
        if (icon == null || icon.isEmpty()) {
            icon = resolveImmuneIcon(l, elementImmune, typeImmune);
        }

        return settings.immuneText.replace("{icon}", safe(icon));
    }

    private String resolveImmuneIcon(IndicatorLine l, boolean elementImmune, boolean typeImmune) {
        if (typeImmune && l.types != null && !l.types.isEmpty()) {
            return settings.iconFor(l.types, false);
        }
        if (elementImmune && l.element != null) {
            return buildElementIcon(l.element, false);
        }
        return settings.defaultIconNormal;
    }

    private String buildArrow(Double mobMul) {
        double mul = (mobMul == null ? 1.0 : mobMul);
        if (mul > 1.0001) return settings.arrowUp;
        if (mul < 0.9999) return settings.arrowDown;
        return "";
    }

    private String buildIcon(IndicatorLine l) {
        if (l.iconOverride != null && !l.iconOverride.isEmpty()) {
            return l.iconOverride;
        }

        List<String> parts = new ArrayList<>();
        for (IndicatorSettings.IconOrder ord : settings.iconOrder) {
            switch (ord) {
                case ELEMENT -> addElementIconPart(l, parts);
                case TYPES -> addTypeIconParts(l, parts);
            }
        }

        // Fallback to default icon
        if (parts.isEmpty()) {
            String def = l.crit ? settings.defaultIconCrit : settings.defaultIconNormal;
            if (!def.isEmpty()) parts.add(def);
        }

        return settings.joinIcons(parts);
    }

    private void addElementIconPart(IndicatorLine l, List<String> parts) {
        if (l.element != null) {
            parts.add(buildElementIcon(l.element, l.crit));
        }
    }

    private void addTypeIconParts(IndicatorLine l, List<String> parts) {
        if (!settings.showBothWhenElement && l.element != null) {
            return;
        }

        Set<DamageType> filtered = getFilteredTypes(l);
        List<String> typeIcons = new ArrayList<>(settings.iconListFor(filtered, l.crit));

        if (l.skillCrit && !settings.skillCritIcon.isEmpty()) {
            if (!typeIcons.isEmpty()) {
                typeIcons.set(0, settings.skillCritIcon + typeIcons.get(0));
            } else {
                typeIcons.add(settings.skillCritIcon);
            }
        }
        parts.addAll(typeIcons);
    }

    private Set<DamageType> getFilteredTypes(IndicatorLine l) {
        if (l.types == null || l.types.isEmpty()) {
            return Collections.emptySet();
        }
        if (l.element != null && settings.stripPhysicalWhenElement) {
            Set<DamageType> filtered = EnumSet.copyOf(l.types);
            filtered.remove(DamageType.PHYSICAL);
            return filtered;
        }
        return l.types;
    }

    private String buildElementIcon(Element element, boolean crit) {
        String color = safe(element.getColor());
        String glyph = safe(element.getLoreIcon());
        String critAddon = crit ? safe(settings.elementalCritIcon) : "";
        return critAddon + color + glyph;
    }

    private String safe(String s) { return s == null ? "" : s; }

    /**
     * Converts legacy color codes (& or §) to MiniMessage format.
     * Preserves existing MiniMessage tags by doing string replacement only on legacy codes.
     */
    private String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) return text;
        
        StringBuilder sb = new StringBuilder(text.length() + 32);
        int len = text.length();
        
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = text.charAt(i + 1);
                String tag = getMiniMessageTag(code);
                if (tag != null) {
                    sb.append(tag);
                    i++; // skip code char
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String getMiniMessageTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private void displayOneRaw(Entity entity, String message) {
        IndicatorDisplayEvent called = new IndicatorDisplayEvent(entity, message,
                IndicatorDisplayEvent.IndicatorType.DAMAGE);
        Bukkit.getPluginManager().callEvent(called);
        if (called.isCancelled()) return;

        // Calculate spawn position
        double a = RNG.nextDouble() * Math.PI * 2;
        double width = (entity.getBoundingBox().getWidthX() + entity.getBoundingBox().getWidthZ()) / 2.0;
        double r = rOffset + width * entityWidthPercent;
        double h = yOffset + entity.getHeight() * entityHeightPercent;
        Location baseLoc = entity.getLocation().add(Math.cos(a) * r, h, Math.sin(a) * r);

        // Create unique ID for this hologram
        String holoId = "devdmg_" + ThreadLocalRandom.current().nextLong();
        
        // Convert legacy & codes to MiniMessage format
        String miniMsg = legacyToMiniMessage(message);
        
        // Create HologramLib hologram with auto-update for smooth motion
        TextHologram hologram = new TextHologram(holoId)
                .setUpdateTaskPeriod(1L)  // Auto-update every tick
                .setMiniMessageText(miniMsg);
        
        // Spawn at location
        hologramManager.spawn(hologram, baseLoc);

        if (!settings.move) {
            // Static hologram - just remove after lifespan
            new BukkitRunnable() {
                @Override
                public void run() {
                    hologramManager.remove(holoId);
                }
            }.runTaskLater(plugin, settings.lifespan);
        } else {
            // Animated fly-out with smooth physics
            animateFlyOut(hologram, holoId, baseLoc, entity);
        }
    }

    /**
     * Animate the indicator drifting up with physics.
     * Uses teleport() for position updates - HologramLib handles the packet sending.
     */
    private void animateFlyOut(TextHologram hologram, String holoId, Location startLoc, Entity entity) {
        final double animSpeed = settings.radialVelocity;
        final double initialYSpeed = settings.initialUpwardVelocity;
        final double gravity = settings.gravity;
        final long lifespan = settings.lifespan;
        final long updateRate = settings.tickPeriod;
        
        // Random horizontal direction
        double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
        final double xSpeed = animSpeed * Math.cos(angle) / 20.0;  // Per tick
        final double zSpeed = animSpeed * Math.sin(angle) / 20.0;
        final double[] ySpeed = { initialYSpeed / 20.0 };
        final double gravPerTick = gravity / 400.0;  // Per tick squared
        
        final Location loc = startLoc.clone();
        
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                // Stop when below entity or max ticks reached
                if (ticks >= lifespan || loc.getY() <= entity.getLocation().getY()) {
                    hologramManager.remove(holoId);
                    cancel();
                    return;
                }
                
                // Update position
                loc.add(xSpeed * updateRate, ySpeed[0] * updateRate, zSpeed * updateRate);
                ySpeed[0] -= gravPerTick * updateRate;
                
                // Teleport hologram to new position
                hologram.teleport(loc);
                
                ticks += updateRate;
            }
        }.runTaskTimer(plugin, 0L, updateRate);
    }
}
