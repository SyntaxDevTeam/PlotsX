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
        val location = player.location
        val world = location.world.name
        val x = location.blockX
        val z = location.blockZ

        if (!PermissionChecker.canUnclaimPlot(player)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
            return
        }

        val currentPlot = plugin.cacheManager.getPlotAt(world, x, z)
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
                val current = plugin.cacheManager.getPlot(plotId)
                if (current == null || current.ownerUuid != p.uniqueId || !PermissionChecker.canUnclaimPlot(p)) {
                    p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "not_owner"))
                    return@UnclaimConfirmGUI
                }
                val actor = p.uniqueId
                plugin.schedulerAdapter.runAsync(Runnable {
                    // JDBC deletion and the audit write must never run on the server thread.
                    val success = try { plugin.databaseHandler.deletePlot(plotId) }
                    catch (failure: Exception) {
                        plugin.logger.err("Unclaim failed for plot $plotId: ${failure.message}")
                        false
                    }
                    if (success) {
                        try {
                            plugin.databaseHandler.logPlotAction(
                                PlotLogEntry(plotId, "DELETE", actor, System.currentTimeMillis())
                            )
                        } catch (failure: Exception) {
                            plugin.logger.warning("Cannot persist DELETE audit for plot $plotId: ${failure.message}")
                        }
                    }
                    if (!plugin.isEnabled) return@Runnable
                    plugin.schedulerAdapter.runForPlayer(p, Runnable {
                        if (!p.isOnline) return@Runnable
                        p.sendMessage(plugin.messageHandler.stringMessageToComponent(
                            if (success) "plots" else "error",
                            if (success) "unclaim_success" else "create_error"
                        ))
                    })
                })
            },
            onCancel = { p ->
                p.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "unclaim_cancelled"))
            }
        )

        plugin.guiHandler.registerGui(player, gui)
    }

    private fun plotList(player: Player): List<String> =
        plugin.cacheManager.getPlayerPlots(player.uniqueId).map { it.name }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!PermissionChecker.canUnclaimPlot(stack.sender)) return emptyList()
        if (args.size != 1) return emptyList()

        val player = stack.sender as? Player ?: return emptyList()
        return plotList(player).filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
