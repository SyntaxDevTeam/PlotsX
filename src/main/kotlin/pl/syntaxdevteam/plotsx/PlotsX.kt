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
        versionChecker.checkAndLog()
    }

    override fun onDisable() {
        databaseHandler.closeConnection()
        pluginInitializer.onDisable()
    }
    fun onReload() {
        super.reloadConfig()
        logger.success("Config reloaded.")
    }
}
