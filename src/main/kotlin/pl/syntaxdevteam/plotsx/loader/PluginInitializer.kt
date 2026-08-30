package pl.syntaxdevteam.plotsx.loader

import pl.syntaxdevteam.core.SyntaxCore
import pl.syntaxdevteam.message.SyntaxMessages
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.cache.CacheManager
import pl.syntaxdevteam.plotsx.commands.CommandsManager
import pl.syntaxdevteam.plotsx.common.ConfigHandler
import pl.syntaxdevteam.plotsx.common.UUIDManager
import pl.syntaxdevteam.plotsx.hooks.HookHandler
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler
import pl.syntaxdevteam.plotsx.gui.GUIHandler
import pl.syntaxdevteam.plotsx.hooks.CoreProtectHook
import pl.syntaxdevteam.plotsx.hooks.WorldGuardHook
import pl.syntaxdevteam.plotsx.protection.PlotProtectionListener
import pl.syntaxdevteam.plotsx.listener.RenamePlotChatListener

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
        plugin.pluginConfig = plugin.config
        plugin.logger = SyntaxCore.logger
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
        SyntaxMessages.initialize(plugin)
        plugin.messageHandler = SyntaxMessages.messages
        plugin.pluginsManager = SyntaxCore.pluginManagerx
        plugin.hookHandler = HookHandler(plugin)
        plugin.guiHandler = GUIHandler(plugin)
        plugin.cacheManager = CacheManager(plugin)
        plugin.renamePlotChatListener = RenamePlotChatListener(plugin)
    }

    private fun registerCommands() {
        plugin.commandsManager = CommandsManager(plugin)
        plugin.commandsManager.registerCommands()
    }

    private fun registerEvents() {
        plugin.server.pluginManager.registerEvents(plugin.guiHandler, plugin)
        plugin.versionChecker = VersionChecker(plugin)
        plugin.cacheManager.clearAllCaches()
        plugin.cacheManager.reloadAllCachesSync()
        plugin.server.pluginManager.registerEvents(PlotProtectionListener(plugin), plugin)
        plugin.server.pluginManager.registerEvents(plugin.renamePlotChatListener, plugin)
        plugin.coreProtectHook = CoreProtectHook(plugin)
        plugin.server.pluginManager.registerEvents(plugin.coreProtectHook, plugin)
        if (plugin.server.pluginManager.isPluginEnabled("WorldGuard")) {
            plugin.regionProtectionHook = WorldGuardHook(plugin)
            plugin.logger.success("WorldGuard detected - claims cannot overlap WorldGuard regions.")
        } else {
            plugin.logger.info("WorldGuard not detected - region overlap check disabled.")
        }

        if (!plugin.coreProtectHook.isEnabled()) {
            plugin.logger.warning("CoreProtect not connected - some features may not work.")
        }

        if (plugin.hookHandler.checkPlaceholderAPI()) {
            //PlaceholderHandler(plugin).register()
        }
        if (plugin.hookHandler.connectCleanerX()) {
            plugin.logger.info("CleanerX API detected - integration enabled.")
        } else {
            plugin.logger.info("CleanerX API not detected - skipping integration.")
        }
    }

    private fun checkForUpdates() {
        plugin.statsCollector = SyntaxCore.statsCollector
        SyntaxCore.updateChecker.checkAsync()
    }
}
