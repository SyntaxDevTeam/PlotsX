package pl.syntaxdevteam.plotsx.loader

import pl.syntaxdevteam.plotsx.compatibility.VersionChecker
import pl.syntaxdevteam.plotsx.compatibility.VersionCompatibility
import pl.syntaxdevteam.plotsx.compatibility.platform.BukkitSchedulerAdapter
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
import pl.syntaxdevteam.plotsx.hooks.EconomyHook
import pl.syntaxdevteam.plotsx.hooks.WorldGuardFlags
import pl.syntaxdevteam.plotsx.hooks.WorldGuardHook
import pl.syntaxdevteam.plotsx.protection.AnimalFlagMigration
import pl.syntaxdevteam.plotsx.protection.AnimalProtectionListener
import pl.syntaxdevteam.plotsx.protection.PlotProtectionListener
import pl.syntaxdevteam.plotsx.protection.PrivateChestManager
import pl.syntaxdevteam.plotsx.protection.ProtectionEventRegistration
import pl.syntaxdevteam.plotsx.listener.RenamePlotChatListener

class PluginInitializer(private val plugin: PlotsX) {

    fun onEnable() {
        setUpLogger()
        setupCompatibility()
        setupConfig()
        setupUUID()
        setupDatabase()
        setupHandlers()
        registerEvents()
        registerCommands()
        checkForUpdates()
    }

    fun onDisable() {
        plugin.logger.err(plugin.pluginMeta.name + " " + plugin.pluginMeta.version + " has been disabled ☹️")
    }

    private fun setUpLogger() {
        plugin.pluginConfig = plugin.config
        plugin.logger = SyntaxCore.logger
    }

    private fun setupCompatibility() {
        plugin.versionChecker = VersionChecker(plugin)
        plugin.versionCompatibility = VersionCompatibility(plugin.versionChecker)
        plugin.schedulerAdapter = BukkitSchedulerAdapter(plugin)
    }

    private fun setupConfig() {
        plugin.saveDefaultConfig()
        plugin.configHandler = ConfigHandler(plugin)
        plugin.configHandler.verifyAndUpdateConfig()
        plugin.configHandler.verifyAndUpdateLanguageFiles()
        plugin.initializeClaimMode()
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
        plugin.economyHook = EconomyHook(plugin)
        plugin.server.pluginManager.registerEvents(plugin.economyHook, plugin)
        plugin.guiHandler = GUIHandler(plugin)
        plugin.cacheManager = CacheManager(plugin)
        plugin.privateChestManager = PrivateChestManager(plugin)
        plugin.renamePlotChatListener = RenamePlotChatListener(plugin)
        plugin.interactions = pl.syntaxdevteam.plotsx.interaction.PlotInteractions(plugin)
        plugin.server.pluginManager.registerEvents(plugin.interactions, plugin)
    }

    private fun registerCommands() {
        plugin.commandsManager = CommandsManager(plugin)
        plugin.commandsManager.registerCommands()
    }

    private fun registerEvents() {
        plugin.server.pluginManager.registerEvents(plugin.guiHandler, plugin)
        AnimalFlagMigration.migrate(plugin)
        plugin.cacheManager.clearAllCaches()
        plugin.cacheManager.reloadAllCachesSync()

        ProtectionEventRegistration.register(plugin, PlotProtectionListener(plugin))
        ProtectionEventRegistration.register(plugin, AnimalProtectionListener(plugin))
        plugin.server.pluginManager.registerEvents(plugin.renamePlotChatListener, plugin)
        plugin.coreProtectHook = CoreProtectHook(plugin)
        plugin.server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onPluginEnable(event: org.bukkit.event.server.PluginEnableEvent) {
                plugin.coreProtectHook.onPluginEnable(event)
            }
        }, plugin)
        if (plugin.server.pluginManager.isPluginEnabled("WorldGuard")) {
            plugin.regionProtectionHook = WorldGuardHook(plugin)
            if (WorldGuardFlags.claimFlag != null) {
                plugin.logger.success(
                    "WorldGuard detected - '${WorldGuardFlags.CLAIM_FLAG_NAME}' flag enabled for claim/expansion control."
                )
            } else {
                val reason = plugin.worldGuardFlagRegistrationError?.let { ": $it" }.orEmpty()
                plugin.logger.warning(
                    "WorldGuard detected, but '${WorldGuardFlags.CLAIM_FLAG_NAME}' is unavailable$reason. " +
                        "Claims overlapping WorldGuard regions remain blocked for safety."
                )
            }
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
