package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
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
            plugin.messageHandler.getMessage("error", "no_permission")
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

        plugin.guiHandler.openGUI(player, ClaimConfirmGUI(plugin))
    }
}