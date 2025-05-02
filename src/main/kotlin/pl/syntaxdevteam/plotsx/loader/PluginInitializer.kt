package pl.syntaxdevteam.plotsx.loader

import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.cache.CacheManager
import pl.syntaxdevteam.plotsx.commands.CommandsManager
import pl.syntaxdevteam.plotsx.common.*
import pl.syntaxdevteam.plotsx.hooks.HookHandler
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler
import pl.syntaxdevteam.plotsx.gui.GUIHandler
import pl.syntaxdevteam.plotsx.protection.PlotProtectionListener

@Suppress("UnstableApiUsage")
class PluginInitializer(private val plugin: PlotsX) {

    fun onEnable() {
        setUpLogger()
        setupConfig()
        setupUUID()
        setupDatabase()
        setupHandlers()
        registerEvents()
        registerCommands()
        checkForUpdates()
    }

    fun onDisable() {
        plugin.databaseHandler.closeConnection()
        plugin.logger.err(plugin.pluginMeta.name + " " + plugin.pluginMeta.version + " has been disabled ☹️")
    }

    private fun setUpLogger() {
        plugin.pluginConfig = plugin.getConfig()
        plugin.logger = Logger(plugin)
    }

    private fun setupConfig() {
        plugin.saveDefaultConfig()
        plugin.configHandler = ConfigHandler(plugin)
        plugin.configHandler.verifyAndUpdateConfig()
    }

    private fun setupUUID() {
        plugin.uuidManager = UUIDManager(plugin)
    }

    private fun setupDatabase() {

        plugin.databaseHandler = DatabaseHandler(plugin)
        plugin.databaseHandler.openConnection()
        plugin.databaseHandler.createTables()
    }

    private fun setupHandlers() {
        plugin.messageHandler = MessageHandler(plugin).apply { initial() }
        plugin.pluginsManager = PluginManager(plugin)
        plugin.hookHandler = HookHandler(plugin)
        plugin.guiHandler = GUIHandler(plugin)
        plugin.cacheManager = CacheManager(plugin)
    }

    private fun registerCommands() {
        plugin.commandsManager = CommandsManager(plugin)
        plugin.commandsManager.registerCommands()
    }

    private fun registerEvents() {
        plugin.server.pluginManager.registerEvents(plugin.guiHandler, plugin)
        plugin.cacheManager.clearAllCaches()
        plugin.cacheManager.reloadAllCachesSync()
        plugin.server.pluginManager.registerEvents(plugin.guiHandler, plugin)
        plugin.server.pluginManager.registerEvents(PlotProtectionListener(plugin), plugin)


        if (plugin.hookHandler.checkPlaceholderAPI()) {
            //PlaceholderHandler(plugin).register()
        }
        val cleanerXApi = HookHandler(plugin).checkAndGetCleanerXApi()
        if (cleanerXApi != null) {
            plugin.logger.info("CleanerX API detected - integration enabled.")
        } else {
            plugin.logger.info("CleanerX API not detected - skipping integration.")
        }
    }

    private fun checkForUpdates() {
        plugin.statsCollector = StatsCollector(plugin)
        plugin.updateChecker = UpdateChecker(plugin)
        plugin.updateChecker.checkForUpdates()
    }
}