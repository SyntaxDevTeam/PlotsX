package pl.syntaxdevteam.plotsx

import org.bukkit.plugin.java.JavaPlugin
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
import pl.syntaxdevteam.plotsx.hooks.RegionProtectionHook
import pl.syntaxdevteam.plotsx.common.ConfigHandler
import pl.syntaxdevteam.plotsx.common.UUIDManager
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler
import pl.syntaxdevteam.plotsx.gui.GUIHandler
import pl.syntaxdevteam.plotsx.loader.PluginInitializer
import pl.syntaxdevteam.plotsx.listener.RenamePlotChatListener
import pl.syntaxdevteam.plotsx.loader.VersionChecker
import pl.syntaxdevteam.plotsx.protection.PrivateChestManager
import java.io.File

class PlotsX : JavaPlugin() {
    val protectionCoordinator = pl.syntaxdevteam.plotsx.protection.ProtectionCoordinator()
    var claimMode = pl.syntaxdevteam.plotsx.claiming.ClaimMode.CLASSIC
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
    lateinit var guiHandler: GUIHandler
    lateinit var cacheManager: CacheManager
    lateinit var privateChestManager: PrivateChestManager
    //lateinit var invitationManager: InvitationManager
    lateinit var renamePlotChatListener: RenamePlotChatListener
    lateinit var interactions: pl.syntaxdevteam.plotsx.interaction.PlotInteractions
    lateinit var coreProtectHook: CoreProtectHook
    var regionProtectionHook: RegionProtectionHook? = null
    lateinit var versionChecker: VersionChecker

    override fun onEnable() {
        SyntaxCore.registerUpdateSources(
            GitHubSource("SyntaxDevTeam/PlotsX"),
            ModrinthSource("")
        )
        SyntaxCore.init(this)
        pluginInitializer = PluginInitializer(this)
        pluginInitializer.onEnable()
        val service = pl.syntaxdevteam.plotsx.api.internal.DefaultPlotsXApi(this)
        apiImplementation = service
        api = service
        server.servicesManager.register(pl.syntaxdevteam.plotsx.api.PlotsXApi::class.java, service, this,
            org.bukkit.plugin.ServicePriority.Normal)
        server.pluginManager.registerEvents(service, this)
        versionChecker.checkAndLog()
    }

    override fun onDisable() {
        if (::interactions.isInitialized) interactions.close()
        if (::renamePlotChatListener.isInitialized) renamePlotChatListener.close()
        server.servicesManager.unregisterAll(this)
        apiImplementation?.close()
        databaseHandler.closeConnection()
        pluginInitializer.onDisable()
    }
    fun onReload() {
        val candidate = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(File(dataFolder, "config.yml"))
        require(validateClaimConfig(candidate) == claimMode) { "Changing plots.claiming.mode requires a server restart" }
        super.reloadConfig()
        protectionCoordinator.recover { cacheManager.reloadAllCachesSync() }
        logger.success("Config reloaded.")
    }
}
