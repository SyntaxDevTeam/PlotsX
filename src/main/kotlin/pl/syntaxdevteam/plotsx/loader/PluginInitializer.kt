package pl.syntaxdevteam.plotsx.loader

import pl.syntaxdevteam.plotsx.PlotsX
//import pl.syntaxdevteam.plotsx.commands.CommandsManager
import pl.syntaxdevteam.plotsx.common.*
import pl.syntaxdevteam.plotsx.hooks.CleanerXHook
import pl.syntaxdevteam.plotsx.hooks.HookHandler
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler

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
    }

    private fun registerCommands() {
        //plugin.commandsManager = CommandsManager(plugin)
        //plugin.commandsManager.registerCommands()
    }

    private fun registerEvents() {
        /*plugin.server.pluginManager.registerEvents(GUIHandler(plugin), plugin)
        if (plugin.hookHandler.checkPlaceholderAPI()) {
            PlaceholderHandler(plugin).register()
        }*/
        val cleanerXApi = CleanerXHook.getApi()
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