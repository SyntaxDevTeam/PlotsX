package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry

@Suppress("UnstableApiUsage")
class ClaimCMD(private var plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val player = stack.sender as Player
        val dbh = plugin.databaseHandler
        val location = player.location
        val world = location.world.name
        val x = location.blockX
        val z = location.blockZ
        val radius = plugin.config.getInt("plots.radius", 16)

        val currentPlot = dbh.getPlotAtLocation(world, x, z)
        if (currentPlot != null) {
            player.sendMessage("§cNie możesz postawić działki tutaj, ponieważ działka już istnieje.")
            return
        }

        if (dbh.doesPlotOverlap(x, z, radius, world)) {
            player.sendMessage("§cNie możesz założyć działki w tym miejscu, ponieważ koliduje z inną działką.")
            return
        }

        val plotId = dbh.createNewPlot(player.uniqueId, world, x, z, radius, "${player.name}'s_Plot")
        if (plotId == null) {
            player.sendMessage("§cWystąpił błąd podczas tworzenia działki.")
            return
        }

        dbh.logPlotAction(
            PlotLogEntry(
                plotId = plotId,
                action = "CREATE",
                actorUUID = player.uniqueId,
                timestamp = System.currentTimeMillis()
            )
        )

        player.sendMessage("§aPomyślnie założyłeś nową działkę!")
    }

}