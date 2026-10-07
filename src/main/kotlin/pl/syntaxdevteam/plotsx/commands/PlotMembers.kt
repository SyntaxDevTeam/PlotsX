package pl.syntaxdevteam.plotsx.commands

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.api.MemberUpdateResult
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
import pl.syntaxdevteam.plotsx.permissions.PlotAccess
import java.util.UUID

/** Shared command/GUI operations. Runtime reads are cache-only and every JDBC mutation runs on a worker. */
class PlotMembers(private val plugin: PlotsX) {
    private val access = PlotAccess(plugin)

    fun reply(sender: CommandSender, key: String, values: Map<String, String> = emptyMap()) =
        sender.sendMessage(plugin.messageHandler.stringMessageToComponent("members", key, values))

    fun name(id: UUID) = plugin.server.getOfflinePlayer(id).name ?: id.toString()

    fun execute(sender: CommandSender, plotId: Int, args: List<String>, onComplete: () -> Unit = {}): Boolean {
        val plot = plugin.cacheManager.getPlot(plotId) ?: return fail(sender, "stand_on_plot")
        val action = args.firstOrNull()?.lowercase() ?: return fail(sender, "usage")
        if (!access.canOpen(sender, plot)) return fail(sender, "denied")
        val members = plugin.cacheManager.getMembers(plot.id).orEmpty()

        if (action == "members" && args.size == 1) {
            reply(sender, "list", mapOf("owner" to name(plot.ownerUuid), "players" to members.joinToString(", ") {
                "${name(UUID.fromString(it.memberUuid))} (${it.memberRole})"
            }.ifEmpty { "-" }))
            return true
        }

        val actorId = (sender as? Player)?.uniqueId ?: UUID(0, 0)
        if (action == "permission") {
            if (!access.owner(sender, plot)) return fail(sender, "denied")
            if (args.size != 4 || args[1] !in access.roles || args[2] !in access.actions ||
                args[3].toBooleanStrictOrNull() == null) return fail(sender, "usage")
            val role = args[1]
            val permission = args[2]
            val allowed = args[3].toBoolean()
            return dispatch(sender, onComplete) {
                val success = plugin.databaseHandler.updatePlotFlag(plot.id, access.key(role, permission), allowed)
                if (success) plugin.databaseHandler.logPlotAction(PlotLogEntry(
                    plot.id, "RolePermission:$role:$permission:$allowed", actorId, System.currentTimeMillis()
                ))
                if (success) MemberUpdateResult.UPDATED else MemberUpdateResult.DATABASE_ERROR
            }
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
            plugin.server.getPlayerExact(input)?.uniqueId
                ?: parsed?.let { plugin.server.getPlayer(it)?.uniqueId }
                ?: plugin.server.offlinePlayers.firstOrNull { it.uniqueId == parsed || it.name.equals(input, true) }?.uniqueId
        } else members.map { UUID.fromString(it.memberUuid) }.firstOrNull { it == parsed || name(it).equals(input, true) }
        if (target == null) return fail(sender, if (action == "add") "unknown_player" else "not_member")
        if (target == plot.ownerUuid) return fail(sender, "owner")
        val existing = members.firstOrNull { it.memberUuid == target.toString() }
        if (action == "add" && existing != null) return fail(sender, "already_member")
        if (action != "add" && existing == null) return fail(sender, "not_member")
        if (action == "remove" && !access.owner(sender, plot)) {
            val actorRole = members.firstOrNull { it.memberUuid == (sender as Player).uniqueId.toString() }?.memberRole
            if (!RoleHierarchy.canRemove(actorRole, existing?.memberRole)) return fail(sender, "denied")
        }
        if (action == "role" && args[2] !in access.roles) return fail(sender, "usage")
        if (action == "transfer" && args[2] != "confirm") return fail(sender, "usage")

        // Bukkit/permission-derived transfer limits are captured before dispatching JDBC work.
        val transfer = if (action == "transfer") {
            val recipient = plugin.server.getPlayer(target) ?: return fail(sender, "recipient_offline")
            val limits = plugin.hookHandler.getPlotLimits(recipient)
            TransferLimits(
                maxPlots = plugin.hookHandler.getMaxPlots(recipient),
                maxRadius = limits.maxRadius,
                maxArea = if (plot.radius == null) plugin.hookHandler.getChunkMaxTotalArea(recipient) else limits.maxTotalArea,
                maxChunksPerPlot = plugin.hookHandler.getMaxChunksPerPlot(recipient),
                maxChunksOwned = plugin.hookHandler.getMaxOwnedChunks(recipient)
            )
        } else null
        val role = args.getOrNull(2) ?: "member"

        return dispatch(sender, onComplete, successMessage = when (action) {
            "add" -> "added"
            "remove" -> "removed"
            "transfer" -> "transferred"
            else -> "updated"
        }, successValues = mapOf("player" to name(target))) {
            val success = when (action) {
                "add" -> plugin.databaseHandler.addPlotMember(plot.id, target)
                "remove" -> plugin.databaseHandler.removePlotMember(plot.id, target)
                "role" -> plugin.databaseHandler.updatePlotMemberRole(plot.id, target, role)
                else -> plugin.databaseHandler.transferPlotOwnership(
                    plot.id, plot.ownerUuid, target,
                    requireNotNull(transfer).maxPlots,
                    transfer.maxRadius,
                    transfer.maxArea,
                    transfer.maxChunksPerPlot,
                    transfer.maxChunksOwned
                )
            }
            if (success) plugin.databaseHandler.logPlotAction(PlotLogEntry(
                plot.id, "Member:$action:$target:$role", actorId, System.currentTimeMillis()
            ))
            if (success) MemberUpdateResult.UPDATED
            else if (action == "transfer") MemberUpdateResult.TRANSFER_REJECTED
            else MemberUpdateResult.DATABASE_ERROR
        }
    }

    private data class TransferLimits(
        val maxPlots: Int,
        val maxRadius: Int,
        val maxArea: Long,
        val maxChunksPerPlot: Int,
        val maxChunksOwned: Int
    )

    private fun dispatch(
        sender: CommandSender,
        onComplete: () -> Unit,
        successMessage: String = "updated",
        successValues: Map<String, String> = emptyMap(),
        operation: () -> MemberUpdateResult
    ): Boolean {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = try { operation() }
            catch (failure: Exception) {
                plugin.logger.err("Plot member operation failed: ${failure.message}")
                MemberUpdateResult.DATABASE_ERROR
            }
            if (!plugin.isEnabled) return@Runnable
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (result == MemberUpdateResult.UPDATED) reply(sender, successMessage, successValues)
                else reportFailure(sender, result)
                onComplete()
            })
        })
        return true
    }

    private fun reportFailure(sender: CommandSender, result: MemberUpdateResult): Boolean {
        val key = when (result) {
            MemberUpdateResult.DENIED -> "denied"
            MemberUpdateResult.OWNER -> "owner"
            MemberUpdateResult.ALREADY_MEMBER -> "already_member"
            MemberUpdateResult.NOT_MEMBER -> "not_member"
            MemberUpdateResult.PLOT_NOT_FOUND -> "stand_on_plot"
            MemberUpdateResult.RECIPIENT_OFFLINE -> "recipient_offline"
            MemberUpdateResult.TRANSFER_REJECTED -> "transfer_failed"
            MemberUpdateResult.UNKNOWN_ROLE,
            MemberUpdateResult.UNKNOWN_ACTION -> "usage"
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
