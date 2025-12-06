package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.gui.PlotGUI
import pl.syntaxdevteam.plotsx.gui.PlotListGUI
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker

@Suppress("UnstableApiUsage")
class PlotCMD(private val plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        if (stack.sender !is Player) {
            stack.sender.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "console"))
            return
        }

        val player = stack.sender as Player
        if (!PermissionChecker.canManagePlot(player)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
            return
        }

        val uuid = plugin.uuidManager.getUUID(player.name)
        val plotName = args.getOrNull(0)

        if (plotName != null) {
            val plot = plugin.databaseHandler.getPlotByName(plotName, uuid)
            if (plot != null) {
                if (plot.ownerUuid != uuid && !PermissionChecker.canBypassPlots(player)) {
                    player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "not_owner"))
                    return
                }
                plugin.guiHandler.registerGui(player, PlotGUI(plugin, plot, plot.ownerUuid))
            } else {
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "plot_not_found"))
            }
            return
        }

        val standingPlot = plugin.databaseHandler.getPlotAtLocation(
            player.world.name,
            player.location.blockX,
            player.location.blockZ
        )
        if (standingPlot != null) {
            if (standingPlot.ownerUuid != uuid && !player.hasPermission("plotsx.plot.bypass")) {
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "not_owner"))
                return
            }
            plugin.guiHandler.registerGui(player, PlotGUI(plugin, standingPlot, standingPlot.ownerUuid))
            return
        }

        val playerPlots = plugin.databaseHandler.getPlayerPlots(uuid)
        if (playerPlots.isNotEmpty()) {
            plugin.guiHandler.registerGui(player, PlotListGUI(plugin, playerPlots, uuid))
        } else {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_plot_found"))
        }
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!PermissionChecker.canManagePlot(stack.sender)) return emptyList()
        if (args.size != 1) return emptyList()

        val player = stack.sender as? Player ?: return emptyList()
        val uuid = plugin.uuidManager.getUUID(player.name)

        return plugin.databaseHandler
            .getPlayerPlots(uuid)
            .map { it.name }
            .filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
