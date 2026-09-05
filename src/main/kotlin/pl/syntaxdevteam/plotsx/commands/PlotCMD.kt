package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.gui.PlotGUI
import pl.syntaxdevteam.plotsx.gui.PlotListGUI
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker

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

        // The command sender is online, so no external/offline UUID lookup is needed.
        val uuid = player.uniqueId
        val plotName = args.getOrNull(0)

        if (plotName?.lowercase() in listOf("add", "remove", "members")) {
            manageMembers(player, args)
            return
        }

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
            if (standingPlot.ownerUuid != uuid && !PermissionChecker.canBypassPlots(player)) {
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

    private fun manageMembers(player: Player, args: Array<String>) {
        fun reply(key: String, values: Map<String, String> = emptyMap()) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("members", key, values))
        }
        val action = args[0].lowercase()
        if (args.size != if (action == "members") 1 else 2) {
            reply("usage")
            return
        }
        val plot = plugin.databaseHandler.getPlotAtLocation(player.world.name, player.location.blockX, player.location.blockZ)
        if (plot == null) {
            reply("stand_on_plot")
            return
        }
        if (plot.ownerUuid != player.uniqueId) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "not_owner"))
            return
        }
        try {
            val members = plugin.databaseHandler.getPlotMembers(plot.id)
            fun name(id: java.util.UUID) = plugin.server.getOfflinePlayer(id).name ?: id.toString()
            if (action == "members") {
                reply("list", mapOf("owner" to name(plot.ownerUuid), "players" to
                    members.map { name(java.util.UUID.fromString(it.memberUuid)) }.sorted().joinToString(", ").ifEmpty { "-" }))
                return
            }
            val input = args[1]
            val parsed = runCatching { java.util.UUID.fromString(input) }.getOrNull()
            val target = if (action == "remove") {
                members.map { java.util.UUID.fromString(it.memberUuid) }
                    .firstOrNull { it == parsed || name(it).equals(input, true) }
            } else {
                plugin.server.getPlayerExact(input)?.uniqueId ?: plugin.server.offlinePlayers
                    .firstOrNull { it.uniqueId == parsed || it.name.equals(input, true) }?.uniqueId
            }
            if (target == null) {
                reply(if (action == "remove") "not_member" else "unknown_player")
                return
            }
            if (target == plot.ownerUuid) {
                reply("owner")
                return
            }
            if (action == "add" && members.any { it.memberUuid == target.toString() }) {
                reply("already_member")
                return
            }
            val success = if (action == "add") plugin.databaseHandler.addPlotMember(plot.id, target)
                else plugin.databaseHandler.removePlotMember(plot.id, target)
            if (!success) {
                reply("failed")
                return
            }
            plugin.cacheManager.reloadMembersSync(plot.id)
            reply(if (action == "add") "added" else "removed", mapOf("player" to name(target)))
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("Cannot update plot membership: ${ex.message}")
            reply("failed")
        }
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!PermissionChecker.canManagePlot(stack.sender)) return emptyList()
        if (args.size != 1) return emptyList()

        val player = stack.sender as? Player ?: return emptyList()
        val uuid = player.uniqueId

        return (listOf("add", "remove", "members") + plugin.databaseHandler
            .getPlayerPlots(uuid)
            .map { it.name })
            .filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
