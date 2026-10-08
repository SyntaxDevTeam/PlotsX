package pl.syntaxdevteam.plotsx.commands

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.api.MemberUpdateResult
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
import pl.syntaxdevteam.plotsx.permissions.PlotAccess
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Authorization on the actor thread, permission capture on the recipient thread, JDBC on a worker. */
class OwnershipTransferService(private val plugin: PlotsX) {
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<MemberUpdateResult>>()
    @Volatile private var closed = false

    fun transfer(actor: CommandSender, plotId: Int, recipientId: UUID): CompletableFuture<MemberUpdateResult> {
        val result = CompletableFuture<MemberUpdateResult>()
        if (closed) return result.also { it.complete(MemberUpdateResult.DATABASE_ERROR) }
        pending.add(result)
        result.whenComplete { _, _ -> pending.remove(result) }
        // Close may have raced the registration; do not leave a future behind cancelled schedulers.
        if (closed) return result.also { it.complete(MemberUpdateResult.DATABASE_ERROR) }
        val plot = plugin.cacheManager.getPlot(plotId)
            ?: return result.also { it.complete(MemberUpdateResult.PLOT_NOT_FOUND) }
        if (!PlotAccess(plugin).owner(actor, plot)) return result.also { it.complete(MemberUpdateResult.DENIED) }
        if (recipientId == plot.ownerUuid) return result.also { it.complete(MemberUpdateResult.OWNER) }
        if (plugin.cacheManager.getMembers(plotId).orEmpty().none { it.memberUuid == recipientId.toString() })
            return result.also { it.complete(MemberUpdateResult.NOT_MEMBER) }
        val actorId = (actor as? Player)?.uniqueId ?: UUID(0, 0)
        val recipient = plugin.server.getPlayer(recipientId)
            ?: return result.also { it.complete(MemberUpdateResult.RECIPIENT_OFFLINE) }
        OwnershipTransferWorkflow(
            onRecipient = { task, retired -> plugin.schedulerAdapter.runForPlayer(recipient, task, retired) },
            onWorker = { plugin.schedulerAdapter.runAsync(it) },
            capture = {
                if (!recipient.isOnline) null else {
                    val limits = plugin.hookHandler.getPlotLimits(recipient)
                    Limits(plugin.hookHandler.getMaxPlots(recipient), limits.maxRadius,
                        if (plot.radius == null) plugin.hookHandler.getChunkMaxTotalArea(recipient) else limits.maxTotalArea,
                        plugin.hookHandler.getMaxChunksPerPlot(recipient), plugin.hookHandler.getMaxOwnedChunks(recipient))
                }
            },
            persist = { limits ->
                val success = plugin.databaseHandler.transferPlotOwnership(plotId, plot.ownerUuid, recipientId,
                    limits.maxPlots, limits.maxRadius, limits.maxArea, limits.perPlot, limits.owned)
                if (success) plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId,
                    "Member:transfer:$recipientId:member", actorId, System.currentTimeMillis()))
                if (success) MemberUpdateResult.UPDATED else MemberUpdateResult.TRANSFER_REJECTED
            },
            report = { plugin.logger.err("Ownership transfer failed: ${it.message}") }
        ).start(result)
        return result
    }

    private data class Limits(val maxPlots: Int, val maxRadius: Int, val maxArea: Long, val perPlot: Int, val owned: Int)

    fun open() { closed = false }

    fun close() {
        closed = true
        pending.forEach { it.complete(MemberUpdateResult.DATABASE_ERROR) }
        pending.clear()
    }
}

/** Scheduling contract kept independent of Bukkit so cross-region capture and retirement are testable. */
internal class OwnershipTransferWorkflow<L>(
    private val onRecipient: (Runnable, Runnable) -> Unit,
    private val onWorker: (Runnable) -> Unit,
    private val capture: () -> L?,
    private val persist: (L) -> MemberUpdateResult,
    private val report: (Exception) -> Unit
) {
    fun start(result: CompletableFuture<MemberUpdateResult>) {
        fun fail(failure: Exception) { report(failure); result.complete(MemberUpdateResult.DATABASE_ERROR) }
        try {
            onRecipient(Runnable {
                if (result.isDone) return@Runnable
                try {
                    val limits = capture()
                    if (limits == null) { result.complete(MemberUpdateResult.RECIPIENT_OFFLINE); return@Runnable }
                    onWorker(Runnable {
                        if (!result.isDone) try { result.complete(persist(limits)) }
                        catch (failure: Exception) { fail(failure) }
                    })
                } catch (failure: Exception) { fail(failure) }
            }, Runnable { result.complete(MemberUpdateResult.RECIPIENT_OFFLINE) })
        } catch (failure: Exception) { fail(failure) }
    }
}
