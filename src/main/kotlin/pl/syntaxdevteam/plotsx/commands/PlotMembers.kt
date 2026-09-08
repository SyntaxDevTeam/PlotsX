package pl.syntaxdevteam.plotsx.commands

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
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
                val result = plugin.api.setRolePermission(sender, plot.id, args[1], args[2], args[3].toBoolean())
                if (result != pl.syntaxdevteam.plotsx.api.MemberUpdateResult.UPDATED) return reportFailure(sender, result)
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
            if (action == "transfer" && args[2] != "confirm") return fail(sender, "usage")
            val result = when (action) {
                "add" -> plugin.api.addMember(sender, plot.id, target)
                "remove" -> plugin.api.removeMember(sender, plot.id, target)
                "role" -> plugin.api.setMemberRole(sender, plot.id, target, args[2])
                else -> plugin.api.transferOwnership(sender, plot.id, target)
            }
            if (result != pl.syntaxdevteam.plotsx.api.MemberUpdateResult.UPDATED) return reportFailure(sender, result)
            reply(sender, when (action) { "add" -> "added"; "remove" -> "removed"; "transfer" -> "transferred"; else -> "updated" },
                mapOf("player" to name(target)))
            return true
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("Plot member operation failed: ${ex.message}")
            return fail(sender, "failed")
        }
    }

    private fun reportFailure(sender: CommandSender, result: pl.syntaxdevteam.plotsx.api.MemberUpdateResult): Boolean {
        val key = when (result) {
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.DENIED -> "denied"
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.OWNER -> "owner"
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.ALREADY_MEMBER -> "already_member"
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.NOT_MEMBER -> "not_member"
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.PLOT_NOT_FOUND -> "stand_on_plot"
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.RECIPIENT_OFFLINE -> "recipient_offline"
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.TRANSFER_REJECTED -> "transfer_failed"
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.UNKNOWN_ROLE,
            pl.syntaxdevteam.plotsx.api.MemberUpdateResult.UNKNOWN_ACTION -> "usage"
            else -> "failed"
        }
        return fail(sender, key)
    }

    private fun fail(sender: CommandSender, key: String): Boolean { reply(sender, key); return false }
}

internal object RoleHierarchy {
    private val roles = listOf("member", "builder", "manager")
    fun canRemove(actor: String?, target: String?): Boolean =
        actor in roles && target in roles && roles.indexOf(actor) > roles.indexOf(target)
}
