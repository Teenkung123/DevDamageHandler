package com.Teenkung.devDamageHandler.API;

import com.Teenkung.devDamageHandler.DevDamageHandler;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageMetadata;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.element.Element;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Public API for DevDamageHandler.
 * Use this class to interact with the damage handling system from other plugins.
 *
 * <p>Example usage:</p>
 * <pre>
 * DevDamageAPI api = DevDamageAPI.getInstance();
 * if (api != null) {
 *     double stat = api.getPlayerStat(player, "CRITICAL_STRIKE_CHANCE");
 * }
 * </pre>
 */
public final class DevDamageAPI {

    private static DevDamageAPI instance;
    private final DevDamageHandler plugin;

    private DevDamageAPI(DevDamageHandler plugin) {
        this.plugin = plugin;
    }

    /**
     * Get the API instance.
     * @return The API instance, or null if the plugin is not loaded
     */
    public static @Nullable DevDamageAPI getInstance() {
        if (instance == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("DevDamageHandler");
            if (plugin instanceof DevDamageHandler ddh && ddh.isEnabled()) {
                instance = new DevDamageAPI(ddh);
            }
        }
        return instance;
    }

    /**
     * Check if the API is available.
     * @return true if the API is ready to use
     */
    public static boolean isAvailable() {
        return getInstance() != null;
    }

    /**
     * Get the underlying plugin instance.
     * @return The DevDamageHandler plugin
     */
    public @NotNull DevDamageHandler getPlugin() {
        return plugin;
    }

    /* ========== Player Stats ========== */

    /**
     * Get a player's stat value from MythicLib.
     * @param player The player
     * @param stat The stat name (e.g., "CRITICAL_STRIKE_CHANCE")
     * @return The stat value, or 0 if not found
     */
    public double getPlayerStat(@NotNull Player player, @NotNull String stat) {
        MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
        if (data == null) return 0.0;
        return data.getStatMap().getStat(stat);
    }

    /**
     * Get a player's elemental damage bonus.
     * @param player The player
     * @param element The element ID (e.g., "FIRE")
     * @return The elemental damage percentage bonus
     */
    public double getElementalDamageBonus(@NotNull Player player, @NotNull String element) {
        return getPlayerStat(player, element.toUpperCase() + "_DAMAGE");
    }

    /**
     * Get a player's elemental defense.
     * @param player The player
     * @param element The element ID (e.g., "FIRE")
     * @return The elemental defense value
     */
    public double getElementalDefense(@NotNull Player player, @NotNull String element) {
        return getPlayerStat(player, element.toUpperCase() + "_DEFENSE");
    }

    /**
     * Get a player's critical strike chance.
     * @param player The player
     * @return Critical strike chance as a percentage (0-100)
     */
    public double getCriticalChance(@NotNull Player player) {
        return getPlayerStat(player, "CRITICAL_STRIKE_CHANCE");
    }

    /**
     * Get a player's critical strike power.
     * @param player The player
     * @return Critical strike power multiplier
     */
    public double getCriticalPower(@NotNull Player player) {
        return getPlayerStat(player, "CRITICAL_STRIKE_POWER");
    }

    /**
     * Get a player's elemental critical chance.
     * @param player The player
     * @return Elemental critical chance as a percentage
     */
    public double getElementalCriticalChance(@NotNull Player player) {
        return getPlayerStat(player, "ELEMENTAL_CRITICAL_STRIKE_CHANCE");
    }

    /**
     * Get a player's elemental critical power.
     * @param player The player
     * @return Elemental critical power multiplier
     */
    public double getElementalCriticalPower(@NotNull Player player) {
        return getPlayerStat(player, "ELEMENTAL_CRITICAL_STRIKE_POWER");
    }

    /* ========== Debug Mode ========== */

    /**
     * Check if a player has debug mode enabled.
     * @param player The player
     * @return true if debug mode is enabled
     */
    public boolean isDebugEnabled(@NotNull Player player) {
        return plugin.isDebugging(player);
    }

    /**
     * Set a player's debug mode.
     * @param player The player
     * @param enabled Whether to enable debug mode
     */
    public void setDebugEnabled(@NotNull Player player, boolean enabled) {
        plugin.setPlayerDebugMode(player, enabled);
    }

    /**
     * Toggle a player's debug mode.
     * @param player The player
     * @return The new debug state
     */
    public boolean toggleDebug(@NotNull Player player) {
        return plugin.togglePlayerDebug(player);
    }

    /* ========== Utility Methods ========== */

    /**
     * Create a new DamageMetadata with elemental damage.
     * @param damage The base damage amount
     * @param element The element (from MythicLib)
     * @param types The damage types
     * @return A new DamageMetadata instance
     */
    public @NotNull DamageMetadata createElementalDamage(double damage, @NotNull Element element,
                                                         @NotNull DamageType... types) {
        return new DamageMetadata(damage, element, types);
    }

    /**
     * Create a new DamageMetadata without elemental damage.
     * @param damage The base damage amount
     * @param types The damage types
     * @return A new DamageMetadata instance
     */
    public @NotNull DamageMetadata createDamage(double damage, @NotNull DamageType... types) {
        return new DamageMetadata(damage, types);
    }

    /**
     * Get an Element by its ID.
     * @param elementId The element ID (e.g., "FIRE")
     * @return The Element, or empty if not found
     */
    public @NotNull Optional<Element> getElement(@NotNull String elementId) {
        return Optional.of(MythicLib.plugin.getElements().get(elementId));
    }

    /**
     * Get all registered elements.
     * @return Collection of all registered Elements
     */
    public @NotNull Collection<Element> getAllElements() {
        return MythicLib.plugin.getElements().getAll();
    }

    /* ========== Internal - Called by plugin ========== */

    /**
     * Called when the plugin is disabled to cleanup the API instance.
     * Internal use only.
     */
    public static void cleanup() {
        instance = null;
    }
}



