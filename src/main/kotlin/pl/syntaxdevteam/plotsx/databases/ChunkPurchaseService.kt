package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.hooks.ExpansionEconomy
import pl.syntaxdevteam.plotsx.protection.ProtectionCoordinator
import java.sql.Connection

/** Runs on a worker. Only validation/economy callbacks are marshalled to the server thread.
 * A reservation spans the entire workflow, but never holds a thread lock across callbacks.
 */
internal class ChunkPurchaseService(
    private val connection: () -> Connection,
    private val coordinator: ProtectionCoordinator,
    private val publish: () -> Unit,
    private val server: ServerCalls,
    private val report: (String) -> Unit = {}
) {
    interface ServerCalls { fun <T> call(action: () -> T): T }
    enum class Result { SUCCESS, BUSY, REJECTED, AREA_LIMIT, CHUNK_LIMIT, COLLISION, DUPLICATE, DECLINED, REFUNDED, REVIEW_REQUIRED }

    fun purchase(operation: OperationJournal.Operation, expectedLevel: Int, limits: ChunkExpansionTransaction.Limits,
                 account: ExpansionEconomy.Account?, validate: () -> Boolean): Result {
        val reservation = coordinator.reservePurchase() ?: return Result.BUSY
        var result = Result.REVIEW_REQUIRED
        try { result = run(operation, expectedLevel, limits, account, validate) }
        catch (failure: Exception) { report("Purchase ${operation.id}: ${failure.message}; inspect its journal before retrying") }
        finally {
            try { coordinator.finishPurchase(reservation, publish) }
            catch (failure: Exception) {
                // Commit may have succeeded. Never refund because cache publication failed.
                report("Protection publication failed for ${operation.id}: ${failure.message}")
                result = Result.REVIEW_REQUIRED
            }
        }
        if (result == Result.REVIEW_REQUIRED) report(
            "PAYMENT REVIEW: operation=${operation.id} plot=${operation.plotId} owner=${operation.owner} " +
                "provider=${operation.provider} currency=${operation.currency} amount=${operation.amount}"
        )
        return result
    }

    private fun state(op: OperationJournal.Operation) = connection().use { c -> OperationJournal.readAll(c).singleOrNull { it.id == op.id }?.state }
    private fun transition(op: OperationJournal.Operation, from: OperationJournal.State, to: OperationJournal.State) {
        connection().use { check(OperationJournal.transition(it, op.id, from, to, System.currentTimeMillis())) }
    }

    private fun run(op: OperationJournal.Operation, expectedLevel: Int, limits: ChunkExpansionTransaction.Limits,
                    account: ExpansionEconomy.Account?, validate: () -> Boolean): Result {
        if (state(op) != null) return Result.DUPLICATE
        if (op.amount.signum() > 0 && (account == null || account.providerId != op.provider || account.currencyId != op.currency)) return Result.REJECTED
        val direction = ExpansionDirection.entries.single { op.source.neighbour(it) == op.target }
        val checked = connection().use { c ->
            c.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            c.autoCommit = false
            try {
                ChunkExpansionTransaction.applyInTransaction(c, ChunkExpansionTransaction.Request(
                    op.plotId, op.owner, op.actor, op.source, direction, op.target, op.expectedRevision
                ), limits, validateOnly = true)
            } finally { c.rollback() }
        }
        when (checked) {
            ChunkExpansionTransaction.Result.AreaLimit -> return Result.AREA_LIMIT
            ChunkExpansionTransaction.Result.PlotChunkLimit, ChunkExpansionTransaction.Result.OwnerChunkLimit -> return Result.CHUNK_LIMIT
            ChunkExpansionTransaction.Result.Overlap -> return Result.COLLISION
            is ChunkExpansionTransaction.Result.Success -> if (checked.expansionLevel.toLong() != expectedLevel.toLong() + 1) return Result.REJECTED
            else -> return Result.REJECTED
        }
        if (!server.call(validate)) return Result.REJECTED
        if (!connection().use { OperationJournal.prepare(it, op) }) return Result.DUPLICATE
        if (op.amount.signum() > 0) {
            transition(op, OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED)
            val paid = try { server.call { account!!.withdraw() } }
            catch (failure: Exception) {
                transition(op, OperationJournal.State.DEBIT_REQUESTED, OperationJournal.State.UNCERTAIN)
                report("Uncertain debit ${op.id}: ${failure.message}")
                return Result.REVIEW_REQUIRED
            }
            transition(op, OperationJournal.State.DEBIT_REQUESTED, if (paid) OperationJournal.State.DEBITED else OperationJournal.State.DECLINED)
            if (!paid) return Result.DECLINED
        }
        try {
            if (server.call(validate)) connection().use { c ->
                c.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
                c.autoCommit = false
                try {
                    val applied = OperationJournal.applyLand(c, op.id, limits, System.currentTimeMillis())
                    if (applied is ChunkExpansionTransaction.Result.Success) { c.commit(); return Result.SUCCESS }
                    c.rollback()
                } catch (failure: Exception) {
                    try { c.rollback() } catch (rollback: Exception) { failure.addSuppressed(rollback) }
                    throw failure
                }
            }
        } catch (failure: Exception) { report("Land write ${op.id}: ${failure.message}") }
        // A failed commit response is ambiguous. Verify using a NEW connection before any refund.
        return when (state(op)) {
            OperationJournal.State.LAND_COMMITTED -> Result.SUCCESS
            OperationJournal.State.PREPARED -> {
                transition(op, OperationJournal.State.PREPARED, OperationJournal.State.CANCELLED); Result.REJECTED
            }
            OperationJournal.State.DEBITED -> refund(op, requireNotNull(account))
            else -> Result.REVIEW_REQUIRED
        }
    }

    private fun refund(op: OperationJournal.Operation, account: ExpansionEconomy.Account): Result {
        transition(op, OperationJournal.State.DEBITED, OperationJournal.State.REFUND_REQUIRED)
        transition(op, OperationJournal.State.REFUND_REQUIRED, OperationJournal.State.REFUND_REQUESTED)
        val refunded = try { server.call { account.refund() } }
        catch (failure: Exception) {
            transition(op, OperationJournal.State.REFUND_REQUESTED, OperationJournal.State.UNCERTAIN)
            report("Uncertain refund ${op.id}: ${failure.message}")
            return Result.REVIEW_REQUIRED
        }
        transition(op, OperationJournal.State.REFUND_REQUESTED,
            if (refunded) OperationJournal.State.REFUNDED else OperationJournal.State.REFUND_REQUIRED)
        return if (refunded) Result.REFUNDED else Result.REVIEW_REQUIRED
    }
}
