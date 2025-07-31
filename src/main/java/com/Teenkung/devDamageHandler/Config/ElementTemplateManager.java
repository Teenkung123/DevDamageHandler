package com.Teenkung.devDamageHandler.Config;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ElementTemplateManager {
    private final DevDamageHandler plugin;

    // templateId -> (Element -> mul)
    private final Map<String, Map<Element, Double>> templates = new HashMap<>();
    // mobId -> templateId
    private final Map<String, String> mobBindings = new HashMap<>();

    public ElementTemplateManager(DevDamageHandler plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        templates.clear();
        mobBindings.clear();
        loadTemplates();
        loadBindings();
        plugin.getLogger().info("[DevDamageHandler] Loaded " + templates.size() + " element templates, "
                + mobBindings.size() + " mob bindings.");
    }

    public Map<Element, Double> getTemplate(String templateId) {
        return templates.getOrDefault(templateId, Collections.emptyMap());
    }

    public String getTemplateForMob(String mobId) {
        return mobBindings.get(mobId);
    }

    public Map<Element, Double> getForMob(String mobId) {
        String id = getTemplateForMob(mobId);
        if (id == null) return Collections.emptyMap();
        return getTemplate(id);
    }

    // -------------- internals --------------

    private void loadTemplates() {
        File f = new File(plugin.getDataFolder(), "element-templates.yml");
        if (!f.exists()) {
            // Ship a default file once; safe if you already include it in your jar.
            plugin.saveResource("element-templates.yml", false);
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = yml.getConfigurationSection("templates");
        if (root == null) return;

        for (String templateId : root.getKeys(false)) {
            ConfigurationSection tplSec = root.getConfigurationSection(templateId);
            if (tplSec == null) continue;

            // Preferred: templates.<id>.modifiers.<ELEMENT>: <double>
            ConfigurationSection modSec = tplSec.getConfigurationSection("modifiers");
            ConfigurationSection src = (modSec != null) ? modSec : tplSec;

            Map<Element, Double> map = new HashMap<>();
            for (String k : src.getKeys(false)) {
                Element el = parseElementKey(k);     // returns null for non-element keys like "modifiers", "notes", etc.
                if (el == null) continue;
                double val = src.getDouble(k);
                map.put(el, val);
            }

            templates.put(templateId.toUpperCase(Locale.ROOT), map);
        }
    }

    private void loadBindings() {
        // Ensure plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        File f = new File(plugin.getDataFolder(), "mob-templates.yml");

        // Try to copy the default from the JAR (if it exists)
        if (!f.exists()) {
            try {
                plugin.saveResource("mob-templates.yml", false);
                plugin.getLogger().info("[DevDamageHandler] Extracted default mob-templates.yml");
            } catch (IllegalArgumentException noResource) {
                // Not packaged in the jar – create a minimal file on disk
                plugin.getLogger().warning("[DevDamageHandler] mob-templates.yml not found in jar; creating a minimal one.");
                try {
                    YamlConfiguration y = new YamlConfiguration();
                    // minimal structure
                    y.createSection("mobs");
                    y.save(f);
                } catch (Exception ioe) {
                    plugin.getLogger().severe("[DevDamageHandler] Failed to create mob-templates.yml: " + ioe.getMessage());
                    return;
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("[DevDamageHandler] Failed to save mob-templates.yml: " + ex.getMessage());
                return;
            }
        }

        // Load and parse
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = yml.getConfigurationSection("mobs");
        if (root == null) {
            // If the file exists but has no 'mobs' section, create it so users see the structure
            yml.createSection("mobs");
            try { yml.save(f); } catch (Exception ignored) {}
            return;
        }

        // Clear previous bindings before loading fresh ones
        mobBindings.clear();

        for (String mobId : root.getKeys(false)) {
            String template = root.getString(mobId);
            if (template == null || template.isEmpty()) continue;

            String normalizedTemplate = template.toUpperCase(Locale.ROOT);
            mobBindings.put(mobId, normalizedTemplate);
        }

        plugin.getLogger().info("[DevDamageHandler] Loaded " + mobBindings.size() + " mob template bindings from mob-templates.yml");
    }

    /** Accepts 'ELEMENT_FIRE', 'FIRE', 'element_fire', etc. */
    private Element parseElementKey(String raw) {
        if (raw == null) return null;

        String s = raw.trim();
        if (s.isEmpty()) return null;

        // normalize delimiters/case and strip optional ELEMENT_ prefix
        s = s.replace('-', '_').toUpperCase(Locale.ROOT);
        if (s.startsWith("ELEMENT_")) s = s.substring("ELEMENT_".length());

        // common aliases
        if (s.equals("LIGHTNESS")) s = "LIGHT";
        if (s.equals("SHADOW")) s = "DARKNESS";

        try {
            return Element.valueOf(s); // MythicLib lookup
        } catch (Throwable ex) { // catches NPE from ElementManager + any IllegalArgumentException variants
            plugin.getLogger().warning("[DevDamageHandler] Unknown element key in template: '" + raw + "' (normalized: '" + s + "')");
            return null;
        }
    }
}
