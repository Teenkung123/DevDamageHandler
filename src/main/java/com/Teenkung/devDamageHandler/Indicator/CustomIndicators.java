package com.Teenkung.devDamageHandler.Indicator;

import com.Teenkung.devDamageHandler.Util.FontCodec;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.event.IndicatorDisplayEvent;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import io.lumine.mythic.lib.hologram.Hologram;
import io.lumine.mythic.lib.listener.option.GameIndicators;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CustomIndicators extends GameIndicators {

    private static final Random RNG = new Random();

    private final IndicatorSettings settings;
    private final DecimalFormat formatter;
    private final FontCodec font;

    public CustomIndicators(ConfigurationSection original, FontCodec font) {
        // Give GameIndicators a fake section with the numeric/physics stuff it needs
        super(stubForGameIndicators(original));
        this.settings  = new IndicatorSettings(original);
        this.formatter = MythicLib.plugin.getMMOConfig().newDecimalFormat(settings.numberFormat);
        this.font      = font;
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
                            if (l.types != null && !l.types.isEmpty()) {
                                Set<DamageType> filtered = l.types;
                                if (l.element != null && settings.stripPhysicalWhenElement) {
                                    // remove PHYSICAL if we detected any element damage
                                    filtered = EnumSet.copyOf(filtered);
                                    filtered.remove(DamageType.PHYSICAL);
                                }
                                if (!filtered.isEmpty()) {
                                    parts.addAll(settings.iconListFor(filtered, l.crit));
                                }
                            }
                        }
                    }

                }
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
        // MythicLib “color” is usually a legacy/§ color; loreIcon is the actual glyph
        String color = safe(element.getColor());
        String glyph = safe(element.getLoreIcon());

        // Optional: prepend your crit icon. If you don’t want that, just remove this line.
        String critAddon = crit ? safe(settings.elementalCritIcon) : "";

        // DO NOT font-encode this glyph—leave it raw so MythicLib’s RP font shows it.
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
            holo.flyOut(this, randomDir(entity));
        }
    }

    private Vector randomDir(Entity e) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        return new Vector(Math.cos(ang), 0, Math.sin(ang));
    }

    /**
     * Build a minimal section containing only what GameIndicators cares about
     * so we can keep all the fancy stuff in our own settings.
     */
    private static ConfigurationSection stubForGameIndicators(ConfigurationSection damageRoot) {
        // damageRoot = game-indicators.damage
        MemoryConfiguration mc = new MemoryConfiguration();

        // numbers
        ConfigurationSection numbers = damageRoot.getConfigurationSection("numbers");
        String dec = numbers != null ? numbers.getString("decimal-format", "0.##") : "0.##";
        String fmt = numbers != null ? numbers.getString("format", "{value}")      : "{value}";
        mc.set("decimal-format", dec);
        mc.set("format", fmt);

        // physics
        ConfigurationSection physics = damageRoot.getConfigurationSection("physics");
        mc.set("radial-velocity",         physics != null ? physics.getDouble("radial-velocity", 1.0) : 1.0);
        mc.set("gravity",                 physics != null ? physics.getDouble("gravity", 1.0) : 1.0);
        mc.set("initial-upward-velocity", physics != null ? physics.getDouble("initial-upward-velocity", 1.0) : 1.0);
        mc.set("entity-height-percent",   physics != null ? physics.getDouble("entity-height-percent", 0.75) : 0.75);
        mc.set("entity-width-percent",    physics != null ? physics.getDouble("entity-width-percent", 0.75) : 0.75);
        mc.set("y-offset",                physics != null ? physics.getDouble("y-offset", 0.1) : 0.1);
        mc.set("r-offset",                physics != null ? physics.getDouble("r-offset", 0.1) : 0.1);
        mc.set("move",                    physics != null ? physics.getBoolean("move", true) : true);
        mc.set("lifespan",                physics != null ? physics.getLong("lifespan", 20L) : 20L);
        mc.set("tick-period",             physics != null ? physics.getLong("tick-period", 3L) : 3L);

        return mc;
    }



}
