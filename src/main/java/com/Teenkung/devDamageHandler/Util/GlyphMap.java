package com.Teenkung.devDamageHandler.Util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GlyphMap {
    private final boolean enabled;
    private final Map<String, String> map;

    public GlyphMap(ConfigurationSection sec) {
        this.enabled = sec != null && sec.getBoolean("enabled", true);
        if (!enabled || sec == null) {
            this.map = Collections.emptyMap();
            return;
        }
        Map<String, String> tmp = new HashMap<>();
        loadSection(tmp, sec.getConfigurationSection("normal"));
        loadSection(tmp, sec.getConfigurationSection("crit"));
        // ignore list = chars that map to ""
        if (sec.getStringList("ignore") != null) {
            for (String s : sec.getStringList("ignore")) {
                if (s != null && !s.isEmpty()) tmp.put(s, "");
            }
        }
        this.map = Collections.unmodifiableMap(tmp);
    }

    private void loadSection(Map<String, String> dest, ConfigurationSection sub) {
        if (sub == null) return;
        for (String key : sub.getKeys(false)) {
            dest.put(key, String.valueOf(sub.get(key)));
        }
    }

    public String filter(String raw) {
        if (!enabled || map.isEmpty()) return raw;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            String repl = map.get(String.valueOf(raw.charAt(i)));
            if (repl != null) sb.append(repl);
        }
        return sb.toString();
    }
}
