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
    }
}
