package com.Teenkung.devDamageHandler.Indicator;

import com.Teenkung.devDamageHandler.Util.FontCodec;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.event.IndicatorDisplayEvent;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import io.lumine.mythic.lib.hologram.Hologram;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CustomIndicators {

    private static final Random RNG = new Random();

    private final IndicatorSettings settings;
    private final DecimalFormat formatter;
    private final FontCodec font;

    // Physics settings from indicators
    private final double yOffset;
    private final double rOffset;
    private final double entityWidthPercent;
    private final double entityHeightPercent;

    public CustomIndicators(ConfigurationSection baseConfig, FontCodec font) {
        this.settings  = new IndicatorSettings(baseConfig);
        this.formatter = MythicLib.plugin.getMMOConfig().newDecimalFormat(settings.numberFormat);
        this.font      = font;
        
        // Cache physics for quicker access
        this.yOffset = settings.yOffset;
        this.rOffset = settings.rOffset;
        this.entityWidthPercent = settings.entityWidthPercent;
        this.entityHeightPercent = settings.entityHeightPercent;
    }

    /* --------------------------------------------------------- */
    /*  PUBLIC ENTRY                                             */
    /* --------------------------------------------------------- */
    public void displayLines(Entity entity, List<IndicatorLine> lines) {
        if (!settings.enabled || lines.isEmpty()) return;

        if (settings.split) {
            for (IndicatorLine line : lines) displayOne(entity, line);
        } else {
            String joined = lines.stream().map(this::renderLine).collect(Collectors.joining(" "));
            displayOneRaw(entity, joined);
        }
    }

    /* --------------------------------------------------------- */

    private void displayOne(Entity entity, IndicatorLine line) {
        displayOneRaw(entity, renderLine(line));
    }

    private String renderLine(IndicatorLine l) {
        // 1) IMMUNE
        if (l.immune) {
            final double EPS = 1e-6;

            // Decide whether this immunity came from element (mobMul == 0) or from type (packets zeroed)
            boolean elementImmune = l.element != null && l.mobMul != null && Math.abs(l.mobMul) < EPS;
            boolean typeImmune    = !elementImmune; // everything else we treat as type-based immunity

            String icon = l.iconOverride;
            if (icon == null || icon.isEmpty()) {
                if (typeImmune && l.types != null && !l.types.isEmpty()) {
                    // Use damage-type icon
                    icon = settings.iconFor(l.types, false);
                } else if (elementImmune && l.element != null) {
                    // Use element icon
                    icon = buildElementIcon(l.element, false);
                } else {
                    icon = settings.defaultIconNormal;
                }
            }

            String immune = settings.immuneText.replace("{icon}", safe(icon));
            return MythicLib.plugin.parseColors(immune);
        }

        // 2) Arrow (based on mobMul)
        String arrow = "";
        double mobMul = (l.mobMul == null ? 1.0 : l.mobMul);
        if (mobMul > 1.0001)       arrow = settings.arrowUp;
        else if (mobMul < 0.9999)  arrow = settings.arrowDown;

        // 3) Icon
        String icon = (l.iconOverride == null || l.iconOverride.isEmpty()) ? null : l.iconOverride;

        if (icon == null) {
            List<String> parts = new ArrayList<>();

            // Add element icon first or later depending on config.order
            for (IndicatorSettings.IconOrder ord : settings.iconOrder) {
                switch (ord) {
                    case ELEMENT -> {
                        if (l.element != null) {
                            parts.add(buildElementIcon(l.element, l.crit));
                        }
                    }
                    case TYPES -> {
                        if (settings.showBothWhenElement || l.element == null) {
                            // Get types or empty set
                            Set<DamageType> filtered = Collections.emptySet();
                            if (l.types != null && !l.types.isEmpty()) {
                                if (l.element != null && settings.stripPhysicalWhenElement) {
                                    filtered = EnumSet.copyOf(l.types);
                                    filtered.remove(DamageType.PHYSICAL);
                                } else {
                                    filtered = l.types;
                                }
                            }
                            
                            // Get base type icons (mutable list)
                            List<String> typeIcons = new ArrayList<>(settings.iconListFor(filtered, l.crit));
                            
                            // Inject Skill Crit icon
                            if (l.skillCrit && !settings.skillCritIcon.isEmpty()) {
                                if (!typeIcons.isEmpty()) {
                                    // Prepend to first icon
                                    typeIcons.set(0, settings.skillCritIcon + typeIcons.get(0));
                                } else {
                                    // No type icons -> add skill crit icon standalone
                                    typeIcons.add(settings.skillCritIcon);
                                }
                            }
                            
                            parts.addAll(typeIcons);
                        }
                    }

                }
            }

            // Fallback: Default icons if nothing added yet
            if (parts.isEmpty()) {
                String def = l.crit ? settings.defaultIconCrit : settings.defaultIconNormal;
                if (!def.isEmpty()) parts.add(def);
            }

            icon = settings.joinIcons(parts);
        }

        icon = safe(icon);

        // 4) Number (format -> then encode if needed)
        String plain = formatter.format(l.value);
        String number = (font != null && font.isEnabled()) ? font.encodeString(plain, l.crit) : plain;

        // 5) Compose line
        String mini = settings.lineFormat
                .replace("{arrow}", arrow)
                .replace("{icon}", icon)
                .replace("{value}", number);

        return MythicLib.plugin.parseColors(mini);
    }

    private String buildElementIcon(Element element, boolean crit) {
        // MythicLib "color" is usually a legacy/§ color; loreIcon is the actual glyph
        String color = safe(element.getColor());
        String glyph = safe(element.getLoreIcon());

        // Optional: prepend your crit icon. If you don't want that, just remove this line.
        String critAddon = crit ? safe(settings.elementalCritIcon) : "";

        // DO NOT font-encode this glyph—leave it raw so MythicLib's RP font shows it.
        return critAddon + color + glyph;
    }


    private String safe(String s) { return s == null ? "" : s; }


    /** Convert MiniMessage → legacy '&' → MythicLib color parser (if you still need it). */
    @SuppressWarnings("unused")
    private String toLegacy(String mini) {
        String legacy = LegacyComponentSerializer.legacyAmpersand()
                .serialize(MiniMessage.miniMessage().deserialize(mini));
        return MythicLib.plugin.parseColors(legacy);
    }

    private void displayOneRaw(Entity entity, String legacyMsg) {
        IndicatorDisplayEvent called = new IndicatorDisplayEvent(entity, legacyMsg,
                IndicatorDisplayEvent.IndicatorType.DAMAGE);
        Bukkit.getPluginManager().callEvent(called);
        if (called.isCancelled()) return;

        // Positioning
        double a = RNG.nextDouble() * Math.PI * 2;
        double width = (entity.getBoundingBox().getWidthX() + entity.getBoundingBox().getWidthZ()) / 2.0;
        double r = this.rOffset + width * this.entityWidthPercent;
        double h = this.yOffset + entity.getHeight() * this.entityHeightPercent;
        Location loc = entity.getLocation().add(Math.cos(a) * r, h, Math.sin(a) * r);

        Hologram holo = Hologram.create(loc, Collections.singletonList(legacyMsg));
        if (!settings.move) {
            Bukkit.getScheduler().runTaskLater(MythicLib.plugin, holo::despawn, settings.lifespan);
        } else {
            // Smooth physics - run every tick for fluid motion
            final long lifespan = settings.lifespan;
            final double radialVel = settings.radialVelocity;
            final double upwardVel = settings.initialUpwardVelocity;
            final double gravity = settings.gravity;
            
            new org.bukkit.scheduler.BukkitRunnable() {
                long ticks = 0;
                double x = loc.getX();
                double y = loc.getY();
                double z = loc.getZ();
                
                // Velocity per tick (divide by 20 to convert blocks/sec to blocks/tick)
                Vector dir = randomDir();
                double vx = dir.getX() * radialVel / 20.0;
                double vy = upwardVel / 20.0;
                double vz = dir.getZ() * radialVel / 20.0;
                
                // Gravity per tick squared (acceleration in blocks/tick²)
                double grav = gravity / 400.0;  // 20*20 = 400

                @Override
                public void run() {
                    if (++ticks >= lifespan || !holo.isSpawned()) {
                        holo.despawn();
                        cancel();
                        return;
                    }
                    
                    // Apply gravity
                    vy -= grav;
                    
                    // Update position
                    x += vx;
                    y += vy;
                    z += vz;
                    
                    holo.updateLocation(new Location(loc.getWorld(), x, y, z));
                }
            }.runTaskTimer(MythicLib.plugin, 1L, 1L);  // Every tick for smooth motion
        }
    }
    
    private Vector randomDir() {
        double ang = RNG.nextDouble() * Math.PI * 2;
        return new Vector(Math.cos(ang), 0, Math.sin(ang));
    }
}
