package pl.syntaxdevteam.plotsx.permissions

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.protection.PlotFlagRegistry

/** Role grants are scoped to a plot and stored alongside its boolean settings. */
class PlotAccess(private val plugin: PlotsX) {
    val roles = listOf("member", "builder", "manager")
    val actions get() = listOf("invite", "kick", "rename") +
        PlotFlagRegistry.visibleFlags.map { "flag.${it.name}" }

    fun admin(sender: CommandSender) = PermissionChecker.canManagePlots(sender)
    fun owner(sender: CommandSender, plot: PlotData) = admin(sender) ||
        (sender is Player && sender.uniqueId == plot.ownerUuid)

    fun canOpen(sender: CommandSender, plot: PlotData): Boolean = owner(sender, plot) ||
        (sender is Player && plugin.databaseHandler.getPlotMembers(plot.id).any { it.memberUuid == sender.uniqueId.toString() })

    fun granted(plotId: Int, role: String, action: String): Boolean {
        if (role !in roles || action !in actions) return false
        return plugin.databaseHandler.getPlotFlag(plotId, key(role, action))?.value?.toBooleanStrictOrNull()
            ?: defaults(role).contains(action)
    }

    private fun defaults(role: String): List<String> {
        val path = "plot-roles.$role.permissions"
        return if (plugin.config.contains(path)) plugin.config.getStringList(path)
            else if (role == "manager") listOf("invite", "kick", "rename") else emptyList()
    }

    fun grants(plotId: Int, role: String): Set<String> {
        if (role !in roles) return emptySet()
        val settings = plugin.databaseHandler.getPlotFlags(plotId).associate { it.name to it.value }
        val defaults = defaults(role)
        return actions.filter { settings[key(role, it)]?.toBooleanStrictOrNull() ?: (it in defaults) }.toSet()
    }

    fun allowedActions(sender: CommandSender, plot: PlotData): Set<String> {
        if (owner(sender, plot)) return actions.toSet()
        val player = sender as? Player ?: return emptySet()
        val role = plugin.databaseHandler.getPlotMembers(plot.id)
            .firstOrNull { it.memberUuid == player.uniqueId.toString() }?.memberRole ?: return emptySet()
        return grants(plot.id, role)
    }

    fun allowed(sender: CommandSender, plot: PlotData, action: String): Boolean {
        if (owner(sender, plot)) return true
        val player = sender as? Player ?: return false
        val role = plugin.databaseHandler.getPlotMembers(plot.id)
            .firstOrNull { it.memberUuid == player.uniqueId.toString() }?.memberRole ?: return false
        return granted(plot.id, role, action)
    }

    fun key(role: String, action: String) = "role_permission.$role.$action"
}
