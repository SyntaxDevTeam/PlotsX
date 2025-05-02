package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
import pl.syntaxdevteam.plotsx.gui.ClaimConfirmGUI

@Suppress("UnstableApiUsage")
class ClaimCMD(private var plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val dbh = plugin.databaseHandler
        val player = stack.sender as Player
        val location = player.location
        val world = location.world.name
        val x = location.blockX
        val z = location.blockZ
        val radius = plugin.config.getInt("plots.radius", 16)

        if (stack.sender !is Player) {
            stack.sender.sendMessage(plugin.messageHandler.getMessage("error", "console"))
            return
        }

        if (!stack.sender.hasPermission("plotsx.cmd.claim")) {
            player.sendMessage(plugin.messageHandler.getMessage("error", "no_permission"))
            return
        }

        val currentPlot = dbh.getPlotAtLocation(world, x, z)
        if (currentPlot != null) {
            player.sendMessage(plugin.messageHandler.getMessage("error", "is_plot"))
            return
        }

        if (dbh.doesPlotOverlap(x, z, radius, world)) {
            player.sendMessage(plugin.messageHandler.getMessage("error", "in_collision"))
            return
        }

        openClaimGui(player)
    }

    fun openClaimGui(player: Player) {
        val gui = ClaimConfirmGUI(
            plugin = plugin,
            player = player,
            onConfirm = { p ->
                val dbh      = plugin.databaseHandler
                val loc      = p.location
                val uuid     = p.uniqueId
                val world    = loc.world!!.name
                val x        = loc.blockX
                val z        = loc.blockZ
                val radius   = plugin.config.getInt("plots.radius", 16)

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val existing = dbh.getPlotsByOwner(uuid)
                    val nextNum  = existing.size + 1
                    val plotName = "${p.name}_Plot_$nextNum"

                    val plotId = dbh.createNewPlot(uuid, world, x, z, radius, plotName)
                    if (plotId == null) {
                        p.sendMessage(plugin.messageHandler.getMessage("error", "create_error"))
                    } else {
                        dbh.logPlotAction(
                            PlotLogEntry(
                                plotId = plotId,
                                action = "CREATE",
                                actorUUID = uuid,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        p.sendMessage(plugin.messageHandler.getMessage("plots", "claim_success"))

                        // odśwież cache
                        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                            plugin.cacheManager.refreshAllCachesAsync()
                        })
                    }
                })
            },
            onCancel = { p ->
                p.sendMessage(plugin.messageHandler.getMessage("plots", "claim_cancelled"))
            }
        )

        plugin.guiHandler.registerGui(player, gui)
    }

}