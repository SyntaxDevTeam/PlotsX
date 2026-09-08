package pl.syntaxdevteam.plotsx.commands

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
import pl.syntaxdevteam.plotsx.permissions.PlotAccess
import java.util.UUID

/** Shared command/GUI operations. Always reload authorization before changing a plot. */
class PlotMembers(private val plugin: PlotsX) {
    private val access = PlotAccess(plugin)
    fun reply(sender: CommandSender, key: String, values: Map<String, String> = emptyMap()) =
        sender.sendMessage(plugin.messageHandler.stringMessageToComponent("members", key, values))

    fun name(id: UUID) = plugin.server.getOfflinePlayer(id).name ?: id.toString()

    fun execute(sender: CommandSender, plotId: Int, args: List<String>): Boolean {
        try {
            val plot = plugin.databaseHandler.getPlotById(plotId) ?: return fail(sender, "stand_on_plot")
            val action = args.firstOrNull()?.lowercase() ?: return fail(sender, "usage")
            if (!access.canOpen(sender, plot)) return fail(sender, "denied")
            val members = plugin.databaseHandler.getPlotMembers(plot.id)
            if (action == "members" && args.size == 1) {
                reply(sender, "list", mapOf("owner" to name(plot.ownerUuid), "players" to members.joinToString(", ") {
                    "${name(UUID.fromString(it.memberUuid))} (${it.memberRole})"
                }.ifEmpty { "-" }))
                return true
            }
            if (action == "permission") {
                if (!access.owner(sender, plot)) return fail(sender, "denied")
                if (args.size != 4 || args[1] !in access.roles || args[2] !in access.actions ||
                    args[3].toBooleanStrictOrNull() == null) return fail(sender, "usage")
                if (!plugin.databaseHandler.updatePlotFlag(plot.id, access.key(args[1], args[2]), args[3].toBoolean()))
                    return fail(sender, "failed")
                plugin.cacheManager.reloadFlagsSync(plot.id)
                plugin.databaseHandler.logPlotAction(PlotLogEntry(plot.id, "RolePermission:${args.drop(1).joinToString(":")}",
                    (sender as? Player)?.uniqueId ?: UUID(0, 0), System.currentTimeMillis()))
                reply(sender, "updated")
                return true
            }
            val expected = when (action) { "add", "remove" -> 2; "role", "transfer" -> 3; else -> return fail(sender, "usage") }
            if (args.size != expected) return fail(sender, "usage")
            val permitted = when (action) {
                "add" -> access.allowed(sender, plot, "invite")
                "remove" -> access.allowed(sender, plot, "kick")
                else -> access.owner(sender, plot)
            }
            if (!permitted) return fail(sender, "denied")
            val input = args[1]
            val parsed = runCatching { UUID.fromString(input) }.getOrNull()
            val target = if (action == "add") {
                plugin.server.getPlayerExact(input)?.uniqueId ?: parsed?.let { plugin.server.getPlayer(it)?.uniqueId } ?: plugin.server.offlinePlayers
                    .firstOrNull { it.uniqueId == parsed || it.name.equals(input, true) }?.uniqueId
            } else members.map { UUID.fromString(it.memberUuid) }.firstOrNull { it == parsed || name(it).equals(input, true) }
            if (target == null) return fail(sender, if (action == "add") "unknown_player" else "not_member")
            if (target == plot.ownerUuid) return fail(sender, "owner")
            val targetMember = members.firstOrNull { it.memberUuid == target.toString() }
            if (action == "add" && targetMember != null) return fail(sender, "already_member")
            // Delegated kick cannot remove peers/superiors, including unknown legacy roles.
            if (action == "remove" && !access.owner(sender, plot)) {
                val actorRole = members.firstOrNull { it.memberUuid == (sender as Player).uniqueId.toString() }?.memberRole
                if (!RoleHierarchy.canRemove(actorRole, targetMember?.memberRole)) return fail(sender, "denied")
            }
            val success = when (action) {
                "add" -> plugin.databaseHandler.addPlotMember(plot.id, target)
                "remove" -> plugin.databaseHandler.removePlotMember(plot.id, target)
                "role" -> {
                    if (args[2] !in access.roles) return fail(sender, "usage")
                    plugin.databaseHandler.updatePlotMemberRole(plot.id, target, args[2])
                }
                else -> {
                    if (args[2] != "confirm") return fail(sender, "usage")
                    // Online recipient supplies current permission-based size/area limits.
                    val recipient = plugin.server.getPlayer(target) ?: return fail(sender, "recipient_offline")
                    val limits = plugin.hookHandler.getPlotLimits(recipient)
                    plugin.databaseHandler.transferPlotOwnership(plot.id, plot.ownerUuid, target,
                        plugin.config.getInt("plots.maxPlots", 5).coerceAtLeast(0), limits.maxRadius, limits.maxTotalArea)
                }
            }
            if (!success) return fail(sender, if (action == "transfer") "transfer_failed" else "failed")
            plugin.cacheManager.reloadMembersSync(plot.id)
            plugin.cacheManager.reloadPlotSync(plot.id)
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plot.id,
                "Member:$action:$target:${args.getOrNull(2).orEmpty()}",
                (sender as? Player)?.uniqueId ?: UUID(0, 0), System.currentTimeMillis()))
            reply(sender, when (action) { "add" -> "added"; "remove" -> "removed"; "transfer" -> "transferred"; else -> "updated" },
                mapOf("player" to name(target)))
            return true
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("Plot member operation failed: ${ex.message}")
            return fail(sender, "failed")
        }
    }

    private fun fail(sender: CommandSender, key: String): Boolean { reply(sender, key); return false }
}

internal object RoleHierarchy {
    private val roles = listOf("member", "builder", "manager")
    fun canRemove(actor: String?, target: String?): Boolean =
        actor in roles && target in roles && roles.indexOf(actor) > roles.indexOf(target)
}
