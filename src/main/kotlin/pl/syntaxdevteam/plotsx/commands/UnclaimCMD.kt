package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry

@Suppress("UnstableApiUsage")
class UnclaimCMD(private val plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val player = stack.sender as Player
        val dbh = plugin.databaseHandler
        val location = player.location
        val world = location.world.name
        val x = location.blockX
        val z = location.blockZ

        if (!stack.sender.hasPermission("plotsx.cmd.unclaim")) {
            plugin.messageHandler.getMessage("error", "no_permission")
            return
        }
        // TODO: Pozmieniać komunikaty na messages.yml
        val currentPlot = dbh.getPlotAtLocation(world, x, z)
        if (currentPlot == null) {
            player.sendMessage("§cNie znajdujesz się na żadnej działce.")
            return
        }

        if (currentPlot.ownerUuid != player.uniqueId) {
            player.sendMessage("§cNie jesteś właścicielem tej działki.")
            return
        }

        val success = dbh.deletePlot(currentPlot.id)
        if (!success) {
            player.sendMessage("§cWystąpił błąd podczas usuwania działki.")
            return
        }

        dbh.logPlotAction(
            PlotLogEntry(
                plotId = currentPlot.id,
                action = "DELETE",
                actorUUID = player.uniqueId,
                timestamp = System.currentTimeMillis()
            )
        )

        player.sendMessage(plugin.messageHandler.getMessage("plots", "unclaim_success"))
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            plugin.cacheManager.refreshAllCachesAsync()
        })
    }

    private fun plotList(player: Player): List<String> {
        val uuid = plugin.uuidManager.getUUID(player.name)
        return plugin.databaseHandler.getPlayerPlots(uuid).map { it.name }
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!stack.sender.hasPermission("plotsx.cmd.unclaim")) return emptyList()
        if (args.size != 1) return emptyList()

        val player = stack.sender as? Player ?: return emptyList()
        return plotList(player).filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
