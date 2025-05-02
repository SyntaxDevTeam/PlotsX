package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.gui.PlotGUI
import pl.syntaxdevteam.plotsx.gui.PlotListGUI

@Suppress("UnstableApiUsage")
class PlotCMD(private val plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val sender = stack.sender
        if (sender !is Player) {
            sender.sendMessage(plugin.messageHandler.getMessage("error", "console"))
            return
        }

        if (!sender.hasPermission("plotsx.cmd.plot")) {
            sender.sendMessage(plugin.messageHandler.getMessage("error", "no_permission"))
            return
        }

        val player = sender
        val uuid = plugin.uuidManager.getUUID(player.name)
        val plotName = args.getOrNull(0)

        // 1. Jeśli podano nazwę działki jako argument — otwórz GUI tej działki
        if (plotName != null) {
            val plot = plugin.databaseHandler.getPlotByName(plotName, uuid)
            if (plot != null) {
                if (plot.ownerUuid != uuid && !player.hasPermission("plotsx.plot.bypass")) {
                    player.sendMessage(plugin.messageHandler.getMessage("error", "not_owner"))
                    return
                }
                plugin.guiHandler.registerGui(player, PlotGUI(plugin, plot))
            } else {
                player.sendMessage(plugin.messageHandler.getMessage("error", "plot_not_found"))
            }
            return
        }

        // 2. Jeśli gracz stoi na działce — otwórz GUI tej działki
        val standingPlot = plugin.databaseHandler.getPlotAtLocation(
            player.world.name,
            player.location.blockX,
            player.location.blockZ
        )
        if (standingPlot != null) {
            if (standingPlot.ownerUuid != uuid && !player.hasPermission("plotsx.plot.bypass")) {
                player.sendMessage(plugin.messageHandler.getMessage("error", "not_owner"))
                return
            }
            plugin.guiHandler.registerGui(player, PlotGUI(plugin, standingPlot))
            return
        }

        // 3. Jeśli nie podano argumentu i nie stoi na działce — pokaż listę działek
        val playerPlots = plugin.databaseHandler.getPlayerPlots(uuid)
        if (playerPlots.isNotEmpty()) {
            plugin.guiHandler.registerGui(player, PlotListGUI(plugin, playerPlots))
        } else {
            player.sendMessage(plugin.messageHandler.getMessage("error", "no_plot_found"))
        }
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!stack.sender.hasPermission("plotsx.cmd.plot")) return emptyList()
        if (args.size != 1) return emptyList()

        val player = stack.sender as? Player ?: return emptyList()
        val uuid = plugin.uuidManager.getUUID(player.name)

        return plugin.databaseHandler
            .getPlayerPlots(uuid)
            .map { it.name }
            .filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
