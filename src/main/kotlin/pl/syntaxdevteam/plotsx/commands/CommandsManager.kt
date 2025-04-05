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
                "PunisherX plugin command. Type /punisherx help to check available commands",
                ClaimCMD(plugin)
            )
            commands.register(
                "unclaim",
                "PunisherX plugin command. Type /prx help to check available commands",
                UnclaimCMD(plugin)
            )
        }
    }
}