package pl.syntaxdevteam.plotsx.permissions

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.protection.PlotFlagRegistry

/** Role grants are scoped to a plot and resolved entirely from the atomic runtime cache. */
class PlotAccess(private val plugin: PlotsX) {
    val roles = listOf("member", "builder", "manager")
    val actions get() = listOf("invite", "kick", "rename") +
        PlotFlagRegistry.visibleFlags.map { "flag.${it.name}" }

    fun admin(sender: CommandSender) = PermissionChecker.canManagePlots(sender)
    fun owner(sender: CommandSender, plot: PlotData) = admin(sender) ||
        (sender is Player && sender.uniqueId == plot.ownerUuid)

    fun canOpen(sender: CommandSender, plot: PlotData): Boolean = owner(sender, plot) ||
        (sender is Player && plugin.cacheManager.isMember(plot.id, sender.uniqueId))

    fun granted(plotId: Int, role: String, action: String): Boolean {
        if (role !in roles || action !in actions) return false
        return plugin.cacheManager.getFlagValue(plotId, key(role, action))
            ?: FlagEditPolicy.roleDefault(defaults(role), action)
    }

    private fun defaults(role: String): List<String> {
        val path = "plot-roles.$role.permissions"
        return if (plugin.config.contains(path)) plugin.config.getStringList(path)
            else if (role == "manager") listOf("invite", "kick", "rename") else emptyList()
    }

    fun grants(plotId: Int, role: String): Set<String> {
        if (role !in roles) return emptySet()
        val settings = plugin.cacheManager.getFlags(plotId).orEmpty().associate { it.name to it.value }
        val defaults = defaults(role)
        return actions.filter { settings[key(role, it)]?.toBooleanStrictOrNull() ?: FlagEditPolicy.roleDefault(defaults, it) }.toSet()
    }

    /** Global permission ceiling: applies before ownership or delegated role grants. */
    fun flagAllowed(sender: CommandSender, action: String): Boolean {
        if (!action.startsWith("flag.")) return true
        if (action.removePrefix("flag.") !in PlotFlagRegistry.allFlags) return false
        if (sender !is Player) return true
        val path = "flags.permissions"
        return FlagEditPolicy.allowed(
            enabled = plugin.config.getBoolean("$path.enabled", true),
            bypass = plugin.config.getBoolean("$path.admin-bypass", true) && admin(sender),
            defaultAccess = plugin.config.getBoolean("$path.default-access", true),
            permission = { node -> if (sender.isPermissionSet(node)) sender.hasPermission(node) else null },
            flag = action.removePrefix("flag.")
        )
    }

    fun allowedActions(sender: CommandSender, plot: PlotData): Set<String> {
        val local = if (owner(sender, plot)) actions.toSet() else {
            val player = sender as? Player ?: return emptySet()
            val role = plugin.cacheManager.getMember(plot.id, player.uniqueId)?.memberRole ?: return emptySet()
            grants(plot.id, role)
        }
        return local.filter { flagAllowed(sender, it) }.toSet()
    }

    fun allowed(sender: CommandSender, plot: PlotData, action: String): Boolean {
        if (!flagAllowed(sender, action)) return false
        if (owner(sender, plot)) return true
        val player = sender as? Player ?: return false
        val role = plugin.cacheManager.getMember(plot.id, player.uniqueId)?.memberRole ?: return false
        return granted(plot.id, role, action)
    }

    fun key(role: String, action: String) = "role_permission.$role.$action"
}
