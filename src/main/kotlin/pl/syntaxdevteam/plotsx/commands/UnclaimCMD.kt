package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
import pl.syntaxdevteam.plotsx.gui.UnclaimConfirmGUI
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker

class UnclaimCMD(private val plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val player = stack.sender as? Player ?: run {
            stack.sender.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "console"))
            return
        }
        val dbh = plugin.databaseHandler
        val location = player.location
        val world = location.world.name
        val x = location.blockX
        val z = location.blockZ

        if (!PermissionChecker.canUnclaimPlot(player)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
            return
        }

        val currentPlot = dbh.getPlotAtLocation(world, x, z)

        if (currentPlot == null) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_in_plot"))
            return
        }

        if (currentPlot.ownerUuid != player.uniqueId) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "not_owner"))
            return
        }

        openUnclaimGui(player, currentPlot.id)
    }

    private fun openUnclaimGui(player: Player, plotId: Int) {
        val gui = UnclaimConfirmGUI(
            plugin = plugin,
            player = player,
            onConfirm = { p ->
                val success = plugin.databaseHandler.deletePlot(plotId)
                if (!success) {
                    p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "create_error"))
                } else {
                    plugin.databaseHandler.logPlotAction(
                        PlotLogEntry(
                            plotId = plotId,
                            action = "DELETE",
                            actorUUID = p.uniqueId,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    p.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "unclaim_success"))
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        plugin.cacheManager.invalidatePlot(plotId)
                    })
                }
            },
            onCancel = { p ->
                p.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "unclaim_cancelled"))
            }
        )

        plugin.guiHandler.registerGui(player, gui)
    }

    private fun plotList(player: Player): List<String> {
        val uuid = plugin.uuidManager.getUUID(player.name)
        return plugin.databaseHandler.getPlayerPlots(uuid).map { it.name }
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!PermissionChecker.canUnclaimPlot(stack.sender)) return emptyList()
        if (args.size != 1) return emptyList()

        val player = stack.sender as? Player ?: return emptyList()
        return plotList(player).filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
