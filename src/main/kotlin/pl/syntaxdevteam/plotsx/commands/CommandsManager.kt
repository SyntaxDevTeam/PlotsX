package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.Plugin
import pl.syntaxdevteam.plotsx.PlotsX

@Suppress("UnstableApiUsage")
class CommandsManager(private val plugin: PlotsX) {

    fun registerCommands() {
        val manager: LifecycleEventManager<Plugin> = plugin.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands: Commands = event.registrar()
            commands.register(
                "claim",
                "Type /ptx help to check available commands",
                ClaimCMD(plugin)
            )
            commands.register(
                "unclaim",
                "Type /ptx help to check available commands",
                UnclaimCMD(plugin)
            )
            commands.register(
                "plotsx",
                "PlotsX plugin command. Type /ptx help to check available commands",
                PlotsXCMD(plugin)
            )
            val aliases = plugin.config.getConfigurationSection("aliases")
            aliases?.getKeys(false)?.forEach { key ->
                val commandName = aliases.getString(key) ?: key
                when (key) {
                    "claim" -> commands.register(
                        commandName,
                        plugin.messageHandler.getSimpleMessage("claim", "usage"),
                        ClaimCMD(plugin)
                    )

                    "unclaim" -> commands.register(
                        commandName,
                        plugin.messageHandler.getSimpleMessage("unclaim", "usage"),
                        UnclaimCMD(plugin)
                    )

                }
            }
        }
    }
}