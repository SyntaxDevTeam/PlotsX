package pl.syntaxdevteam.plotsx

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import pl.syntaxdevteam.core.SyntaxCore
import pl.syntaxdevteam.core.manager.PluginManagerX
import pl.syntaxdevteam.core.messaging.MessageHandler
import pl.syntaxdevteam.core.logging.Logger
import pl.syntaxdevteam.core.stats.StatsCollector
import pl.syntaxdevteam.plotsx.cache.CacheManager
import pl.syntaxdevteam.plotsx.commands.CommandsManager
import pl.syntaxdevteam.plotsx.hooks.HookHandler
import pl.syntaxdevteam.plotsx.hooks.CoreProtectHook
import pl.syntaxdevteam.plotsx.common.ConfigHandler
import pl.syntaxdevteam.plotsx.common.UUIDManager
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler
import pl.syntaxdevteam.plotsx.gui.GUIHandler
import pl.syntaxdevteam.plotsx.loader.PluginInitializer
import pl.syntaxdevteam.plotsx.listener.RenamePlotChatListener
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
    //lateinit var invitationManager: InvitationManager
    lateinit var renamePlotChatListener: RenamePlotChatListener
    lateinit var coreProtectHook: CoreProtectHook

    override fun onEnable() {
        SyntaxCore.init(this)
        pluginInitializer = PluginInitializer(this)
        pluginInitializer.onEnable()
    }

    override fun onDisable() {
        databaseHandler.closeConnection()
        pluginInitializer.onDisable()
    }
    fun onReload() {
        super.reloadConfig()
        logger.success("Config reloaded.")
    }

    fun getPluginFile(): File {
        return this.file
    }
}
