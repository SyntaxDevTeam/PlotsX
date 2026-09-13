package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.gui.MembersGUI
import pl.syntaxdevteam.plotsx.gui.PlotGUI
import pl.syntaxdevteam.plotsx.gui.PlotListGUI
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker
import pl.syntaxdevteam.plotsx.permissions.PlotAccess

class PlotCMD(private val plugin: PlotsX) : BasicCommand {
    private val actions = listOf("add", "remove", "members", "role", "permission", "transfer")
    private val access = PlotAccess(plugin)

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        val members = PlotMembers(plugin)
        if (args.firstOrNull().equals("admin", true)) {
            if (!access.admin(sender)) { members.reply(sender, "denied"); return }
            if (args.getOrNull(1).equals("list", true)) {
                if (args.size != 3) { members.reply(sender, "admin_usage"); return }
                val input = args[2]
                val uuid = runCatching { java.util.UUID.fromString(input) }.getOrNull()
                    ?: plugin.server.getPlayerExact(input)?.uniqueId
                    ?: plugin.server.offlinePlayers.firstOrNull { it.name.equals(input, true) }?.uniqueId
                if (uuid == null) { members.reply(sender, "unknown_player"); return }
                val plots = plugin.databaseHandler.getPlotsByOwner(uuid).sortedBy { it.id }
                members.reply(sender, "admin_list_header", mapOf("player" to members.name(uuid), "count" to plots.size.toString()))
                plots.forEach { plot ->
                    // Names are literal text, never MiniMessage markup.
                    sender.sendMessage(net.kyori.adventure.text.Component.text(
                        "#${plot.id} | ${plot.name} | ${plot.world}: ${plot.x}, ${plot.z} | ${plot.radius?.let { "r=$it" } ?: "chunks=${plot.chunks.size}"}"
                    ))
                }
                return
            }
            val id = args.getOrNull(1)?.toIntOrNull()
            if (id == null) { members.reply(sender, "admin_usage"); return }
            val plot = plugin.databaseHandler.getPlotById(id)
            if (plot == null) { members.reply(sender, "stand_on_plot"); return }
            if (args.size == 2 && sender is Player) {
                plugin.guiHandler.registerGui(sender, MembersGUI(plugin, id))
            } else members.execute(sender, id, args.drop(2))
            return
        }
        val player = sender as? Player ?: run { members.reply(sender, "admin_usage"); return }
        if (!PermissionChecker.canManagePlot(player)) { members.reply(player, "denied"); return }
        val standing = plugin.databaseHandler.getPlotAtLocation(player.world.name, player.location.blockX, player.location.blockZ)
        if (args.firstOrNull()?.lowercase() in actions) {
            if (standing == null) { members.reply(player, "stand_on_plot"); return }
            if (args.size == 1 && args[0].equals("members", true) && access.canOpen(player, standing)) {
                plugin.guiHandler.registerGui(player, MembersGUI(plugin, standing.id))
            } else members.execute(player, standing.id, args.toList())
            return
        }
        val plot = if (args.isEmpty()) standing else {
            plugin.databaseHandler.getPlotByName(args[0], player.uniqueId) ?: plugin.databaseHandler.getPlotsFromAllUsers()
                .firstOrNull { it.name.equals(args[0], true) && access.canOpen(player, it) }
        }
        if (plot != null) {
            if (!access.canOpen(player, plot)) { members.reply(player, "denied"); return }
            plugin.guiHandler.registerGui(player, PlotGUI(plugin, plot, plot.ownerUuid))
        } else if (args.isEmpty()) {
            val plots = plugin.databaseHandler.getPlotsByOwner(player.uniqueId)
            if (plots.isEmpty()) members.reply(player, "stand_on_plot")
            else plugin.guiHandler.registerGui(player, PlotListGUI(plugin, plots, player.uniqueId))
        } else player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "plot_not_found"))
    }

    override fun suggest(stack: CommandSourceStack, args: Array<String>): List<String> {
        val sender = stack.sender
        if (!PermissionChecker.canManagePlot(sender) && !access.admin(sender)) return emptyList()
        val admin = args.firstOrNull().equals("admin", true)
        if (admin && !access.admin(sender)) return emptyList()
        if (admin && args.size == 3 && args[1].equals("list", true)) return plugin.server.offlinePlayers
            .mapNotNull { it.name }.filter { it.startsWith(args[2], true) }.distinct().sorted()
        if (admin && args.size == 2) return (listOf("list") + plugin.cacheManager.getCachedPlots().map { it.id.toString() })
            .filter { it.startsWith(args[1]) }
        val words = if (admin) args.drop(2) else args.toList()
        val plot = if (admin) args.getOrNull(1)?.toIntOrNull()?.let { plugin.cacheManager.getPlot(it) }
            else (sender as? Player)?.let { p -> plugin.cacheManager.getCachedPlots().firstOrNull {
                it.world == p.world.name && it.contains(p.location.blockX, p.location.blockZ)
            } }
        val candidates = when {
            words.size <= 1 -> actions + (if (!admin && access.admin(sender)) listOf("admin") else emptyList())
            plot == null || !access.canOpen(sender, plot) -> emptyList()
            words.size == 2 && words[0] == "permission" -> if (access.owner(sender, plot)) access.roles else emptyList()
            words.size == 3 && words[0] == "permission" -> access.actions
            words.size == 4 && words[0] == "permission" -> listOf("true", "false")
            words.size == 3 && words[0] == "role" -> access.roles
            words.size == 3 && words[0] == "transfer" -> listOf("confirm")
            words.size == 2 && words[0] in listOf("add", "remove", "role", "transfer") -> {
                if (words[0] == "add") plugin.server.onlinePlayers.filter { sender !is Player || sender.canSee(it) }.map { it.name }
                else plugin.databaseHandler.getPlotMembers(plot.id).map { PlotMembers(plugin).name(java.util.UUID.fromString(it.memberUuid)) }
            }
            else -> emptyList()
        }
        return candidates.filter { it.startsWith(words.lastOrNull().orEmpty(), true) }.distinct().sorted()
    }
}
