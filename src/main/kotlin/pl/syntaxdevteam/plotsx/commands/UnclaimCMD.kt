package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX

@Suppress("UnstableApiUsage")
class UnclaimCMD(private var plugin: PlotsX) : BasicCommand {

        override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
            val player = stack.sender as Player
            val location = player.location
            val plotId = plugin.databaseHandler.getPlotIdByLocation(location)

            if (plotId == null) {
                player.sendMessage(plugin.messageHandler.getMessage("error", "there_is_no_plot"))
                return
            }

            val plot = plugin.databaseHandler.getPlotById(plotId)

            if (plot != null) {
                if (plot.ownerUuid != player.uniqueId.toString()) {
                    player.sendMessage(plugin.messageHandler.getMessage("error", "not_owner"))
                    return
                }
            }

            plugin.databaseHandler.deletePlot(plotId)
            player.sendMessage(plugin.messageHandler.getMessage("plots", "unclaim_success"))
        }
    }