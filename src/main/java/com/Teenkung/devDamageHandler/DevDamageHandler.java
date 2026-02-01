package com.Teenkung.devDamageHandler;

import com.Teenkung.devDamageHandler.Commands.DevDHCommandExecutor;
import com.Teenkung.devDamageHandler.Commands.DevDHCommandTabCompleter;
import com.Teenkung.devDamageHandler.Handlers.DamageConfig;
import com.Teenkung.devDamageHandler.Handlers.DamageHandler;
import com.Teenkung.devDamageHandler.Integration.MMOItems.StatHandler;
import com.Teenkung.devDamageHandler.Indicator.CustomIndicators;
import com.Teenkung.devDamageHandler.Indicator.HologramLibIndicators;
import com.Teenkung.devDamageHandler.Indicator.IndicatorSettings;
import com.Teenkung.devDamageHandler.Integration.LibReforge.LibreForgeHook;
import com.Teenkung.devDamageHandler.Integration.LibReforge.StatTracking;
import com.Teenkung.devDamageHandler.Util.FontCodec;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hologram.HologramManager;
import io.lumine.mythic.lib.damage.DamageMetadata;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * DevDamageHandler
 * 
 * Features:
 * - Element modifiers (ELEMENT_FIRE, ELEMENT_NONE, etc.)
 * - Type modifiers (TYPE_PHYSICAL, TYPE_MAGIC, etc.)
 * - Custom crit system (elemental vs non-elemental)
 * - MMOItems custom stats for elemental crit and bonus damage
 * - HologramLib-based indicators for optimized performance
 */
public final class DevDamageHandler extends JavaPlugin {

    // Indicator system
    private FontCodec fontCodec;
    private CustomIndicators indicators;
    private HologramLibIndicators hologramLibIndicators;
    private IndicatorSettings indicatorSettings;
    private HologramManager hologramManager;
    private boolean useHologramLib = false;
    
    // Stats handler
    private StatHandler statHandler;
    
    // Damage configuration
    private DamageConfig damageConfig;

    // Hit context cache (for passing data between damage phases)
    private final Map<DamageMetadata, DamageHandler.HitContext> hitCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Debug toggles per player
    private final Set<UUID> debugPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> libReforgeDebugPlayers = Collections.synchronizedSet(new HashSet<>());

    /* =======================
       Lifecycle
       ======================= */
    @Override
    public void onLoad() {
        // Initialize HologramLib early
        try {
            HologramLib.onLoad(this);
            getLogger().info("HologramLib initialized successfully");
        } catch (Exception | NoClassDefFoundError e) {
            getLogger().warning("HologramLib not available, using MythicLib indicators");
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Try to get HologramLib manager
        try {
            HologramLib.getManager().ifPresentOrElse(
                manager -> {
                    hologramManager = manager;
                    useHologramLib = true;
                    getLogger().info("Using HologramLib for damage indicators (packet-based, optimized)");
                },
                () -> getLogger().info("HologramLib manager not available, falling back to MythicLib")
            );
        } catch (Exception | NoClassDefFoundError e) {
            getLogger().info("HologramLib not loaded, using MythicLib for indicators");
        }

        // Load indicator settings
        loadIndicatorSettings();
        
        // Register custom MMOItems stats
        statHandler = new StatHandler(this);
        Bukkit.getPluginManager().registerEvents(statHandler, this);

        // Register damage handler
        Bukkit.getPluginManager().registerEvents(new DamageHandler(this), this);

        // Register custom mechanics
        Bukkit.getPluginManager().registerEvents(new com.Teenkung.devDamageHandler.Mechanics.MechanicRegistry(), this);

        // Register commands
        if (getCommand("ddh") != null) {
            DevDHCommandExecutor cmd = new DevDHCommandExecutor(this);
            DevDHCommandTabCompleter tab = new DevDHCommandTabCompleter();
            //noinspection DataFlowIssue
            getCommand("ddh").setExecutor(cmd);
            //noinspection DataFlowIssue
            getCommand("ddh").setTabCompleter(tab);
        }

        getLogger().info("DevDamageHandler enabled - Elements, Types, Crits, and Custom Stats");

        // Register LibreForge hook
        if (Bukkit.getPluginManager().isPluginEnabled("libreforge")) {
            LibreForgeHook.register(this);
            getLogger().info("LibreForge integration enabled.");
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {    
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mythicmobs reload");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mmoitems reload");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ecoenchants reload");
        }, 20L);
    }

    @Override
    public void onDisable() {
        // Cleanup HologramLib holograms
        if (useHologramLib && hologramManager != null) {
            try {
                for (String holoId : hologramManager.getHologramIds()) {
                    if (holoId.startsWith("devdmg_")) {
                        hologramManager.remove(holoId);
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Error cleaning up holograms: " + e.getMessage());
            }
        }

        StatTracking.removeAllPlayers();

        getLogger().info("DevDamageHandler disabled");
    }

    /* =======================
       Config Loading
       ======================= */
    private void loadIndicatorSettings() {
        // Load damage configuration
        damageConfig = new DamageConfig(getConfig());
        
        ConfigurationSection dmgSec = getConfig().getConfigurationSection("game-indicators.damage");
        if (dmgSec == null) {
            dmgSec = getConfig().createSection("game-indicators").createSection("damage");
        }

        // Font codec for number encoding
        fontCodec = new FontCodec(getConfig().getConfigurationSection("font-map"));

        // Indicator settings
        indicatorSettings = new IndicatorSettings(dmgSec);
        
        // Create indicators - prefer HologramLib if available
        if (useHologramLib && hologramManager != null) {
            hologramLibIndicators = new HologramLibIndicators(this, indicatorSettings, fontCodec, hologramManager);
            indicators = null;  // Not used
        } else {
            indicators = new CustomIndicators(dmgSec, fontCodec);
            hologramLibIndicators = null;
        }
    }

    /* =======================
       Public API
       ======================= */

    /**
     * Get the indicator display system.
     * Returns CustomIndicators for legacy compatibility - use displayIndicatorLines() for HologramLib support.
     */
    public CustomIndicators getIndicators() { 
        return indicators; 
    }
    
    /**
     * Get the HologramLib-based indicator system if available.
     */
    public HologramLibIndicators getHologramLibIndicators() {
        return hologramLibIndicators;
    }
    
    /**
     * Check if using HologramLib for indicators.
     */
    public boolean isUsingHologramLib() {
        return useHologramLib;
    }
    
    /**
     * Get the HologramManager for direct access.
     */
    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public IndicatorSettings getIndicatorSettings() { 
        return indicatorSettings; 
    }

    public FontCodec getFontCodec() { 
        return fontCodec; 
    }
    
    public DamageConfig getDamageConfig() {
        return damageConfig;
    }

    // Debug API
    public boolean getPlayerDebugMode(Player player) { 
        return debugPlayers.contains(player.getUniqueId()); 
    }
    
    public boolean isDebugging(Player player) {
        return getPlayerDebugMode(player);
    }

    public boolean isLibReforgeDebugging(Player player) {
        return getLibReforgeDebugMode(player);
    }

    public void setPlayerDebugMode(Player player, boolean enabled) {
        if (enabled) {
            debugPlayers.add(player.getUniqueId());
        } else {
            debugPlayers.remove(player.getUniqueId());
        }
    }

    public boolean togglePlayerDebug(Player p) {
        if (debugPlayers.contains(p.getUniqueId())) {
            debugPlayers.remove(p.getUniqueId());
            return false;
        } else {
            debugPlayers.add(p.getUniqueId());
            return true;
        }
    }

    public boolean getLibReforgeDebugMode(Player player) {
        return libReforgeDebugPlayers.contains(player.getUniqueId());
    }

    public void setLibReforgeDebugMode(Player player, boolean enabled) {
        if (enabled) {
            libReforgeDebugPlayers.add(player.getUniqueId());
        } else {
            libReforgeDebugPlayers.remove(player.getUniqueId());
        }
    }

    public boolean toggleLibReforgeDebug(Player p) {
        if (libReforgeDebugPlayers.contains(p.getUniqueId())) {
            libReforgeDebugPlayers.remove(p.getUniqueId());
            return false;
        } else {
            libReforgeDebugPlayers.add(p.getUniqueId());
            return true;
        }
    }

    // Hit context store (for passing data between HIGHEST and MONITOR phases)
    public void rememberHit(DamageMetadata meta, DamageHandler.HitContext ctx) { 
        hitCache.put(meta, ctx); 
    }

    public DamageHandler.HitContext consumeHit(DamageMetadata meta) { 
        return hitCache.remove(meta); 
    }

    /* =======================
       Reload
       ======================= */
    public void reloadEverything() {
        reloadConfig();
        loadIndicatorSettings();
        getLogger().info("DevDamageHandler configuration reloaded");
    }

}
