package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX

@Suppress("UnstableApiUsage")
class ClaimCMD(private var plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val player = stack.sender as Player
        val location = player.location
        val isPlot = plugin.databaseHandler.getPlotIdByLocation(location)

        if (isPlot != null) {
            player.sendMessage("Nie możesz postawić działki tutaj, ponieważ działka już istnieje.")
            return
        }

        plugin.databaseHandler.saveNewPlot(
            ownerUuid = player.uniqueId.toString(),
            x = location.blockX,
            z = location.blockZ,
            radius = plugin.config.getDouble("plots.radius", 16.0).toInt(),
            world = location.world.name,
            name = "${player.name}'s_plot",
            creationTime = System.currentTimeMillis(),
            expirationTime = null
        )

        player.sendMessage(plugin.messageHandler.getMessage("plots", "success"))
    }
}