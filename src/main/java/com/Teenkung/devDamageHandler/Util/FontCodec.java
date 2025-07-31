package com.Teenkung.devDamageHandler.Util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes numbers to custom glyphs (normal/crit) and decodes incoming glyph strings to plain digits.
 */
public class FontCodec {
    private final boolean enabled;
    private final Map<Character, String> normalEncode;
    private final Map<Character, String> critEncode;
    private final Map<String, String> decodeMap;

    public FontCodec(ConfigurationSection root) {
        if (root == null || !root.getBoolean("enabled", true)) {
            enabled = false;
            normalEncode = critEncode = Collections.emptyMap();
            decodeMap = Collections.emptyMap();
            return;
        }
        ConfigurationSection cf = root.getConfigurationSection("custom-font");
        if (cf == null || !cf.getBoolean("enabled", true)) {
            enabled = false;
            normalEncode = critEncode = Collections.emptyMap();
            decodeMap = Collections.emptyMap();
            return;
        }

        enabled = true;
        normalEncode = readEncode(cf.getConfigurationSection("normal"));
        critEncode   = readEncode(cf.getConfigurationSection("crit"));
        decodeMap    = buildDecodeMap(normalEncode, critEncode);
    }

    private Map<Character, String> readEncode(ConfigurationSection sec) {
        Map<Character, String> out = new HashMap<>();
        if (sec == null) return out;
        putIf(sec, out, '0', "0");
        putIf(sec, out, '1', "1");
        putIf(sec, out, '2', "2");
        putIf(sec, out, '3', "3");
        putIf(sec, out, '4', "4");
        putIf(sec, out, '5', "5");
        putIf(sec, out, '6', "6");
        putIf(sec, out, '7', "7");
        putIf(sec, out, '8', "8");
        putIf(sec, out, '9', "9");
        putIf(sec, out, '.', "dot");
        putIf(sec, out, '•', "dot");    // just in case
        // 'inter' is a separator, ignore encoding (decoding -> "")
        return out;
    }

    private void putIf(ConfigurationSection sec, Map<Character, String> map, char c, String path) {
        String v = sec.getString(path);
        if (v != null && !v.isEmpty()) map.put(c, v);
    }

    private Map<String, String> buildDecodeMap(Map<Character, String> normal, Map<Character, String> crit) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<Character, String> e : normal.entrySet()) {
            map.put(e.getValue(), String.valueOf(e.getKey()));
        }
        for (Map.Entry<Character, String> e : crit.entrySet()) {
            map.put(e.getValue(), String.valueOf(e.getKey()));
        }
        // add custom 'inter' -> ""
        // we can't know which key is 'inter' unless you check config; just check normal/crit both.
        // Quick hack: if value length==1 and not digit/dot, ignore. But easier -> user ensures not needed.
        return map;
    }

    /* ---------------- decode & encode ---------------- */

    /** Decode a raw hologram line to plain digits. Used to cancel "0" lines. */
    public String decodeNumbers(String raw) {
        if (!enabled || decodeMap.isEmpty()) return raw;
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            String s = decodeMap.get(String.valueOf(raw.charAt(i)));
            if (s != null) out.append(s);
        }
        return out.toString();
    }

    /** Encode a number into the font; choose crit set when crit==true. */
    public String encodeNumber(double value, boolean crit) {
        if (!enabled || (normalEncode.isEmpty() && critEncode.isEmpty())) {
            return String.valueOf(value); // fallback
        }
        String s = String.valueOf(value);
        Map<Character, String> enc = crit ? critEncode : normalEncode;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String repl = enc.get(c);
            if (repl != null) out.append(repl);
        }
        return out.toString();
    }

    public String encodeString(String digits, boolean crit) {
        if (!enabled) return digits;
        Map<Character, String> enc = crit ? critEncode : normalEncode;
        StringBuilder out = new StringBuilder(digits.length());
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            String rep = enc.get(c);
            if (rep != null) out.append(rep);
        }
        return out.toString();
    }

    public boolean isEnabled() { return enabled; }
}
