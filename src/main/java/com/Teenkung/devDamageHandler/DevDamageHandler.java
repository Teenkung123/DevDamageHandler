package com.Teenkung.devDamageHandler;

import com.Teenkung.devDamageHandler.Commands.DevDHCommand;
import com.Teenkung.devDamageHandler.Config.ElementResolver;
import com.Teenkung.devDamageHandler.Config.ElementTemplateManager;
import com.Teenkung.devDamageHandler.Config.StackingProfile;
import com.Teenkung.devDamageHandler.Config.TypeResolver;
import com.Teenkung.devDamageHandler.Handlers.DamageHandler;
import com.Teenkung.devDamageHandler.Handlers.StatHandler;
import com.Teenkung.devDamageHandler.Indicator.CustomIndicators;
import com.Teenkung.devDamageHandler.Indicator.IndicatorSettings;
import com.Teenkung.devDamageHandler.Util.FontCodec;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageMetadata;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class DevDamageHandler extends JavaPlugin {

    private ConfigLoader configLoader;
    private FontCodec fontCodec;
    private CustomIndicators indicators;
    private IndicatorSettings indicatorSettings;

    private ElementTemplateManager templateManager;
    // cache for indicator stage
    private final Map<DamageMetadata, DamageHandler.HitContext> hitCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    // debug toggles
    private final Set<UUID> debugPlayers = Collections.synchronizedSet(new HashSet<>());

    private StatHandler statHandler;

    /* =======================
       Lifecycle
       ======================= */
    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configLoader = new ConfigLoader(this);

        // font map still at root.font-map
        fontCodec = new FontCodec(getConfig().getConfigurationSection("font-map"));

        // indicators: new nested path
        ConfigurationSection dmgSec = getConfig().getConfigurationSection("game-indicators.damage");
        if (dmgSec == null) dmgSec = getConfig().createSection("game-indicators").createSection("damage");

        indicatorSettings = new IndicatorSettings(dmgSec);
        indicators = new CustomIndicators(dmgSec, fontCodec);
        templateManager = new ElementTemplateManager(this);
        templateManager.loadAll();

        Bukkit.getPluginManager().registerEvents(new DamageHandler(this), this);
        this.statHandler = new StatHandler(this);
        Bukkit.getPluginManager().registerEvents(statHandler, this);

        if (getCommand("ddh") != null) {
            DevDHCommand cmd = new DevDHCommand(this);
            getCommand("ddh").setExecutor(cmd);
            getCommand("ddh").setTabCompleter(cmd);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            MMOPlayerData data = MMOPlayerData.online(player);
            statHandler.elementalDamage.register(data);
            statHandler.elementalCriticalPower.register(data);
            statHandler.elementalCriticalChance.register(data);
        }
    }

    @Override
    public void onDisable() {
        if (statHandler == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            MMOPlayerData data = MMOPlayerData.online(player);
            statHandler.elementalDamage.unregister(data);
            statHandler.elementalCriticalPower.unregister(data);
            statHandler.elementalCriticalChance.unregister(data);
        }
    }

    /* =======================
       Public API
       ======================= */

    public ConfigLoader getConfigLoader() { return configLoader; }

    public CustomIndicators getIndicators() { return indicators; }

    public IndicatorSettings getIndicatorSettings() { return indicatorSettings; }

    public FontCodec getFontCodec() { return fontCodec; }

    public ElementTemplateManager getTemplateManager() {
        return templateManager;
    }

    // Debug API
    public boolean getPlayerDebugMode(Player player) { return debugPlayers.contains(player.getUniqueId()); }

    public void setPlayerDebugMode(Player player, boolean enabled) {
        if (enabled) debugPlayers.add(player.getUniqueId());
        else debugPlayers.remove(player.getUniqueId());
    }

    public boolean togglePlayerDebug(Player p) {
        return !debugPlayers.add(p.getUniqueId()); // returns previous state inverted
    }

    // Hit context store
    public void rememberHit(DamageMetadata meta, DamageHandler.HitContext ctx) { hitCache.put(meta, ctx); }

    public DamageHandler.HitContext consumeHit(DamageMetadata meta) { return hitCache.remove(meta); }

    // Resolvers from stacking profiles
    public TypeResolver getTypeResolver(String mobId) {
        StackingProfile profile = configLoader.getProfile(mobId);
        return profile.typeResolver();
    }

    public ElementResolver getElementResolver(String mobId) {
        StackingProfile profile = configLoader.getProfile(mobId);
        return profile.elementResolver();
    }

    /* =======================
       Reload EVERYTHING
       ======================= */
    public void reloadEverything() {
        reloadConfig();
        templateManager.loadAll();

        // Load/Reload DamageModifiers
        if (configLoader == null) configLoader = new ConfigLoader(this);
        else configLoader.reload();

        // Font map
        fontCodec = new FontCodec(getConfig().getConfigurationSection("font-map"));

        // Indicators section (IMPORTANT: correct path!)
        ConfigurationSection giRoot = getConfig().getConfigurationSection("game-indicators");
        ConfigurationSection dmgSec = giRoot == null ? null : giRoot.getConfigurationSection("damage");
        if (dmgSec == null) {
            getLogger().warning("Missing 'game-indicators.damage' section. Using defaults.");
            dmgSec = getConfig().createSection("game-indicators.damage"); // empty stub so ctor won't NPE
        }

        indicatorSettings = new IndicatorSettings(dmgSec);
        indicators        = new CustomIndicators(dmgSec, fontCodec);

        getLogger().info("[DDH] scale-target resolved to: " + indicatorSettings.scaleTarget);
    }
}
