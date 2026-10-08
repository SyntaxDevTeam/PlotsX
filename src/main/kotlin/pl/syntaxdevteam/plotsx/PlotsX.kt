package pl.syntaxdevteam.plotsx

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.HandlerList
import org.bukkit.configuration.file.FileConfiguration
import pl.syntaxdevteam.core.SyntaxCore
import pl.syntaxdevteam.core.manager.PluginManagerX
import pl.syntaxdevteam.core.logging.Logger
import pl.syntaxdevteam.core.stats.StatsCollector
import pl.syntaxdevteam.core.update.GitHubSource
import pl.syntaxdevteam.core.update.ModrinthSource
import pl.syntaxdevteam.message.MessageHandler
import pl.syntaxdevteam.plotsx.cache.CacheManager
import pl.syntaxdevteam.plotsx.commands.CommandsManager
import pl.syntaxdevteam.plotsx.hooks.HookHandler
import pl.syntaxdevteam.plotsx.hooks.CoreProtectHook
import pl.syntaxdevteam.plotsx.hooks.EconomyHook
import pl.syntaxdevteam.plotsx.hooks.RegionProtectionHook
import pl.syntaxdevteam.plotsx.hooks.WorldGuardFlags
import pl.syntaxdevteam.plotsx.common.ConfigHandler
import pl.syntaxdevteam.plotsx.common.UUIDManager
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler
import pl.syntaxdevteam.plotsx.gui.GUIHandler
import pl.syntaxdevteam.plotsx.identity.IdentityAliasRegistry
import pl.syntaxdevteam.plotsx.identity.PlotsXIdentityMigrationService
import pl.syntaxdevteam.plotsx.loader.PluginInitializer
import pl.syntaxdevteam.plotsx.listener.RenamePlotChatListener
import pl.syntaxdevteam.plotsx.compatibility.VersionChecker
import pl.syntaxdevteam.plotsx.compatibility.VersionCompatibility
import pl.syntaxdevteam.plotsx.compatibility.platform.SchedulerAdapter
import pl.syntaxdevteam.plotsx.protection.PrivateChestManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class PlotsX : JavaPlugin() {
    private val stopping = AtomicBoolean(false)
    val protectionCoordinator = pl.syntaxdevteam.plotsx.protection.ProtectionCoordinator()
    var claimMode = pl.syntaxdevteam.plotsx.claiming.ClaimMode.CLASSIC
        private set

    internal var worldGuardFlagRegistrationError: String? = null
        private set

    fun initializeClaimMode() { claimMode = validateClaimConfig(config) }

    private fun validateClaimConfig(candidate: org.bukkit.configuration.ConfigurationSection): pl.syntaxdevteam.plotsx.claiming.ClaimMode {
        require(!candidate.contains("plots.claiming") || candidate.isConfigurationSection("plots.claiming")) { "plots.claiming must be a section" }
        for ((key, fallback) in mapOf("maxPerPlot" to 32, "maxTotalOwned" to 64)) {
            val value = candidate.get("plots.chunks.$key", fallback)
            require(value is Int && value >= 0) { "plots.chunks.$key must be a nonnegative integer" }
        }
        return pl.syntaxdevteam.plotsx.claiming.ClaimMode.fromSection(candidate.getConfigurationSection("plots.claiming")?.getValues(false).orEmpty())
    }
    lateinit var api: pl.syntaxdevteam.plotsx.api.PlotsXApi
        private set
    private var apiImplementation: pl.syntaxdevteam.plotsx.api.internal.DefaultPlotsXApi? = null
    private lateinit var pluginInitializer: PluginInitializer

    lateinit var logger: Logger
    lateinit var messageHandler: MessageHandler
    lateinit var pluginsManager: PluginManagerX

    lateinit var uuidManager: UUIDManager
    lateinit var configHandler: ConfigHandler
    lateinit var pluginConfig: FileConfiguration
    lateinit var statsCollector: StatsCollector

    lateinit var databaseHandler: DatabaseHandler
    lateinit var commandsManager: CommandsManager
    lateinit var hookHandler: HookHandler
    lateinit var economyHook: EconomyHook
    val ownershipTransfers = pl.syntaxdevteam.plotsx.commands.OwnershipTransferService(this)
    lateinit var guiHandler: GUIHandler
    lateinit var cacheManager: CacheManager
    lateinit var borderVisualizer: pl.syntaxdevteam.plotsx.visualization.PlotBorderVisualizer
    lateinit var privateChestManager: PrivateChestManager
    val identityAliasRegistry = IdentityAliasRegistry()
    //lateinit var invitationManager: InvitationManager
    lateinit var renamePlotChatListener: RenamePlotChatListener
    lateinit var interactions: pl.syntaxdevteam.plotsx.interaction.PlotInteractions
    lateinit var coreProtectHook: CoreProtectHook
    var regionProtectionHook: RegionProtectionHook? = null
    lateinit var versionChecker: VersionChecker
    lateinit var versionCompatibility: VersionCompatibility
    lateinit var schedulerAdapter: SchedulerAdapter

    override fun onLoad() {
        if (server.pluginManager.getPlugin("WorldGuard") == null) return

        worldGuardFlagRegistrationError = try {
            WorldGuardFlags.register()
            null
        } catch (exception: RuntimeException) {
            exception.message ?: exception.javaClass.simpleName
        } catch (error: LinkageError) {
            error.message ?: error.javaClass.simpleName
        }
    }

    override fun onEnable() {
        stopping.set(false)
        ownershipTransfers.open()
        SyntaxCore.registerUpdateSources(
            GitHubSource("SyntaxDevTeam/PlotsX"),
            ModrinthSource("")
        )
        SyntaxCore.init(this, versionType = "paper")
        borderVisualizer = pl.syntaxdevteam.plotsx.visualization.PlotBorderVisualizer(this)
        server.pluginManager.registerEvents(borderVisualizer, this)
        pluginInitializer = PluginInitializer(this)
        pluginInitializer.onEnable()
        val service = pl.syntaxdevteam.plotsx.api.internal.DefaultPlotsXApi(this)
        apiImplementation = service
        api = service
        server.servicesManager.register(pl.syntaxdevteam.plotsx.api.PlotsXApi::class.java, service, this,
            org.bukkit.plugin.ServicePriority.Normal)
        server.servicesManager.register(
            PlotsXIdentityMigrationService::class.java,
            PlotsXIdentityMigrationService(this),
            this,
            org.bukkit.plugin.ServicePriority.Normal,
        )
        server.pluginManager.registerEvents(service, this)
        versionChecker.checkAndLog()
    }

    override fun onDisable() {
        // PlugManX calls the normal Paper disable path, but doing the teardown here explicitly
        // keeps callbacks from the old classloader from racing the newly loaded instance.
        if (!stopping.compareAndSet(false, true)) return

        server.servicesManager.unregisterAll(this)
        ownershipTransfers.close()
        apiImplementation?.close()
        apiImplementation = null
        HandlerList.unregisterAll(this)

        if (::borderVisualizer.isInitialized) borderVisualizer.close()

        if (!pl.syntaxdevteam.core.platform.ServerEnvironment.isFoliaBased()) server.scheduler.cancelTasks(this)
        server.globalRegionScheduler.cancelTasks(this)
        server.asyncScheduler.cancelTasks(this)

        if (::interactions.isInitialized) interactions.close()
        if (::renamePlotChatListener.isInitialized) renamePlotChatListener.close()
        if (::guiHandler.isInitialized) guiHandler.close()
        if (::cacheManager.isInitialized) cacheManager.close()
        if (::uuidManager.isInitialized) uuidManager.close()
        if (::hookHandler.isInitialized) hookHandler.close()
        if (::economyHook.isInitialized) economyHook.close()
        if (::coreProtectHook.isInitialized) coreProtectHook.close()
        regionProtectionHook = null

        if (::databaseHandler.isInitialized) databaseHandler.closeConnection()
        if (::pluginInitializer.isInitialized) pluginInitializer.onDisable()
    }

    /**
     * Reloads configuration immediately on the server thread, then rebuilds the DB-backed runtime snapshot
     * on a worker. Protection keeps serving the previous immutable snapshot until the replacement is ready.
     */
    @JvmOverloads
    fun onReload(completion: (Exception?) -> Unit = {}) {
        val candidate = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(File(dataFolder, "config.yml"))
        require(validateClaimConfig(candidate) == claimMode) { "Changing plots.claiming.mode requires a server restart" }
        if (::borderVisualizer.isInitialized) borderVisualizer.close()
        super.reloadConfig()

        schedulerAdapter.runAsync(Runnable {
            val failure = try {
                protectionCoordinator.recover { cacheManager.reloadAllCachesSync() }
                null
            } catch (exception: Exception) {
                exception
            }
            if (!isEnabled) return@Runnable
            schedulerAdapter.runSync(Runnable {
                if (failure == null) logger.success("Config reloaded.")
                else logger.err("Config reload cache refresh failed: ${failure.message}")
                completion(failure)
            })
        })
    }
}
