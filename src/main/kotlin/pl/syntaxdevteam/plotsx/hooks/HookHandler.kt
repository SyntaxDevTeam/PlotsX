package pl.syntaxdevteam.plotsx.hooks

import net.luckperms.api.LuckPerms
import net.luckperms.api.cacheddata.CachedMetaData
import net.luckperms.api.model.user.User
import net.milkbowl.vault.chat.Chat
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX

/**
 * The [HookHandler] class is responsible for hooking into external services such as LuckPerms, Vault, and VaultUnlocked.
 * It allows retrieving player-specific data like group, prefix, and suffix information from the respective permission and chat services.
 * This class ensures that the plugin integrates smoothly with these services if they are available on the server.
 *
 * @property plugin The instance of the FormatterX plugin, used for logging messages and accessing other plugin functionalities.
 */
class HookHandler(private val plugin: PlotsX) {

    data class PlotLimits(val maxRadius: Int, val maxTotalArea: Long)


    private var luckPerms: LuckPerms? = null
    private var chat: Chat? = null
    private var permission: Permission? = null
    private var cleanerXAPI: Any? = null

    /**
     * Initializes the HookHandler by checking if the required services are available on the server.
     * If the services are found, they are hooked into, and success messages are logged.
     * If the services are not found, warning messages are logged.
     */
    init {
        checkPlaceholderAPI()
        checkLuckPerms()
        checkVault()
        if (chat == null || permission == null) {
            checkVaultUnlocked()
        }
    }
    fun connectCleanerX(): Boolean {
        val cleanerX = Bukkit.getPluginManager().getPlugin("CleanerX")
            ?.takeIf { it.isEnabled }
            ?: return false

        return try {
            cleanerXAPI = cleanerX.javaClass.getMethod("getApi").invoke(cleanerX)
            cleanerXAPI != null
        } catch (exception: IllegalStateException) {
            plugin.logger.warning(
                "CleanerX integration could not be loaded because a dependency classloader is unavailable: " +
                    exception.message
            )
            false
        } catch (exception: ReflectiveOperationException) {
            plugin.logger.warning("CleanerX API is incompatible: ${exception.message}")
            false
        } catch (error: LinkageError) {
            plugin.logger.warning("CleanerX integration could not be linked: ${error.message}")
            false
        }
    }

    fun censorWithCleanerX(message: String): String {
        val api = cleanerXAPI ?: return message
        return try {
            api.javaClass.getMethod("censorMessage", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(api, message, true) as? String ?: message
        } catch (exception: ReflectiveOperationException) {
            plugin.logger.warning("CleanerX failed to censor a plot name: ${exception.message}")
            message
        }
    }

    /**
     * Checks if the LuckPerms service is available on the server.
     * If the service is found, it is hooked into, and a success message is logged.
     * If the service is not found, a warning message is logged.
     */
    private fun checkLuckPerms() {
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            val provider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
            if (provider != null) {
                luckPerms = provider.provider
                plugin.logger.debug("Hooked into LuckPerms!")
            }
        } else {
            plugin.logger.warning("LuckPerms plugin not found on server!")
        }
    }

    /**
     * Checks if the Vault service is available on the server.
     * If the service is found, it is hooked into, and a success message is logged.
     * If the service is not found, a warning message is logged.
     */
    private fun checkVault() {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            val chatProvider = Bukkit.getServicesManager().getRegistration(Chat::class.java)
            val permProvider = Bukkit.getServicesManager().getRegistration(Permission::class.java)

            if (chatProvider != null && permProvider != null) {
                chat = chatProvider.provider
                permission = permProvider.provider
                plugin.logger.debug("Hooked into Vault!")
            }
        } else {
            plugin.logger.warning("Vault plugin not found on server!")
        }
    }

    /**
     * Checks if the VaultUnlocked service is available on the server.
     * If the service is found, it is hooked into, and a success message is logged.
     * If the service is not found, a warning message is logged.
     */
    private fun checkVaultUnlocked() {
        if (Bukkit.getPluginManager().isPluginEnabled("VaultUnlocked")) {
            val chatProvider = Bukkit.getServicesManager().getRegistration(Chat::class.java)
            val permProvider = Bukkit.getServicesManager().getRegistration(Permission::class.java)

            if (chatProvider != null && permProvider != null) {
                chat = chatProvider.provider
                permission = permProvider.provider
                plugin.logger.debug("Hooked into VaultUnlocked!")
            }
        } else {
            plugin.logger.warning("VaultUnlocked plugin not found on server!")
        }
    }

    /**
     * Checks if the PlaceholderAPI service is available on the server.
     * If the service is found, it is hooked into, and a success message is logged.
     * If the service is not found, a warning message is logged.
     */
    fun checkPlaceholderAPI(): Boolean {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.logger.debug("Hooked into PlaceholderAPI!")
            return true
        } else {
            plugin.logger.warning("PlaceholderAPI plugin not found on server!")
            return false
        }
    }

    /**
     * Checks if the MiniPlaceholders service is available on the server.
     * If the service is found, it is hooked into, and a success message is logged.
     * If the service is not found, a warning message is logged.
     */
    fun checkMiniPlaceholderAPI(): Boolean {
        if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            plugin.logger.debug("Hooked into MiniPlaceholders!")
            return true
        } else {
            plugin.logger.warning("MiniPlaceholders plugin not found on server!")
            return false
        }

    }

    /**
     * Retrieves the primary group of a player from the LuckPerms or Vault service.
     * If neither service is available, the default group "default" is returned.
     *
     * @param player The player whose primary group is being retrieved.
     * @return The primary group of the player as a [String].
     */
    fun getPrimaryGroup(player: Player): String {
        val lpGroup = luckPerms?.getPlayerAdapter(Player::class.java)?.getMetaData(player)?.primaryGroup
        if (lpGroup != null) {
            plugin.logger.debug("LuckPerms primary group for ${player.name}: $lpGroup")
            return lpGroup
        }
        val groups = permission?.getPrimaryGroup(player)
        if (!groups.isNullOrEmpty()) {
            plugin.logger.debug("Vault groups for ${player.name}: $groups")
            return groups
        }
        return "default"
    }

    /**
     * Retrieves the prefix of a player from the LuckPerms or Vault service.
     * If neither service is available, an empty string is returned.
     *
     * @param player The player whose prefix is being retrieved.
     * @return The prefix of the player as a [String].
     */
    fun getPlayerPrefix(player: Player): String {
        return luckPerms?.getPlayerAdapter(Player::class.java)?.getMetaData(player)?.prefix
            ?: chat?.getPlayerPrefix(player)
            ?: ""
    }

    /**
     * Retrieves the suffix of a player from the LuckPerms or Vault service.
     * If neither service is available, an empty string is returned.
     *
     * @param player The player whose suffix is being retrieved.
     * @return The suffix of the player as a [String].
     */
    fun getPlayerSuffix(player: Player): String {
        return luckPerms?.getPlayerAdapter(Player::class.java)?.getMetaData(player)?.suffix
            ?: chat?.getPlayerSuffix(player)
            ?: ""
    }

    /**
     * Retrieves a specific metadata value for a player from the LuckPerms service.
     * This is typically used to get additional player data that is not covered by basic prefix/suffix/group attributes.
     *
     * @param player The player whose metadata value is being retrieved.
     * @param key The key for the metadata value being retrieved.
     * @return The metadata value associated with the given key, or null if not found.
     */
    fun getLuckPermsMetaValue(player: Player, key: String): String? {
        val user: User? = luckPerms?.userManager?.getUser(player.uniqueId)
        return user?.cachedData?.metaData?.getMetaValue(key)
    }

    /**
     * Retrieves all metadata for a player from the LuckPerms service.
     * This is typically used to get all metadata values associated with a player.
     *
     * @param player The player whose metadata is being retrieved.
     * @return The metadata for the player as a [CachedMetaData] object, or null if not found.
     */
    fun getAllLuckPermsMetData(player: Player): CachedMetaData? {
        return luckPerms?.getPlayerAdapter(Player::class.java)?.getMetaData(player)
    }

    /**
     * Resolves numeric plot limits from effective permission nodes. When more than one
     * node is inherited, the highest value wins.
     *
     * Supported nodes:
     * - plotsx.plot.size.<radius>
     * - plotsx.plot.max-area.<blocks>
     */
    fun getPlotLimits(player: Player): PlotLimits {
        val defaultRadius = plugin.config.getInt("plots.expansion.defaultMaxRadius", 64).coerceAtLeast(1)
        val defaultArea = plugin.config.getLong("plots.expansion.defaultMaxTotalArea", 16641L).coerceAtLeast(1L)

        if (player.isOp || player.hasPermission("plotsx.admin.bypass")) {
            return PlotLimits(Int.MAX_VALUE, Long.MAX_VALUE)
        }

        val permissions = player.effectivePermissions
            .asSequence()
            .filter { it.value }
            .map { it.permission.lowercase() }
            .toList()

        val maxRadius = numericPermission(permissions, "plotsx.plot.size.")?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt() ?: defaultRadius
        val maxArea = numericPermission(permissions, "plotsx.plot.max-area.") ?: defaultArea
        return PlotLimits(maxRadius.coerceAtLeast(1), maxArea.coerceAtLeast(1L))
    }

    fun getMaxPlots(player: Player): Int = numericPlayerSetting(player, "plotsx.plot.max-plots.",
        plugin.config.getInt("plots.maxPlots", 5).coerceAtLeast(0))

    fun getClaimRadius(player: Player): Int = numericPlayerSetting(player, "plotsx.plot.radius.",
        plugin.config.getInt("plots.radius", 16).coerceAtLeast(1)).coerceAtLeast(1)

    private fun numericPlayerSetting(player: Player, prefix: String, fallback: Int): Int =
        player.effectivePermissions.asSequence()
            .filter { it.value }
            .map { it.permission.lowercase() }
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
            .filter { it >= 0 }
            .maxOrNull() ?: fallback

    private fun numericPermission(nodes: List<String>, prefix: String): Long? = nodes.asSequence()
        .filter { it.startsWith(prefix) }
        .mapNotNull { it.removePrefix(prefix).toLongOrNull() }
        .filter { it > 0L }
        .maxOrNull()
}
