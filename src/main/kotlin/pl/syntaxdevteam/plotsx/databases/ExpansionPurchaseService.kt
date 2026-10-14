package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.hooks.ExpansionEconomy
import pl.syntaxdevteam.plotsx.protection.ProtectionCoordinator
import java.sql.Connection

/** Scoped connection access; implementations must not let the Connection escape [withConnection]. */
internal interface JdbcConnectionScope {
    fun <T> withConnection(block: (Connection) -> T): T
}

/** Runs on a worker. Only validation/economy callbacks are marshalled to the server thread.
 * A reservation spans the entire workflow, but never holds a thread lock across callbacks.
 */
internal class ExpansionPurchaseService(
    private val connections: JdbcConnectionScope,
    private val coordinator: ProtectionCoordinator,
    private val publish: () -> Unit,
    private val server: ServerCalls,
    private val report: (String) -> Unit = {},
) {
    /** Compatibility constructor retained for focused JDBC unit tests. Runtime uses SyntaxDB scope. */
    constructor(
        connection: () -> Connection,
        coordinator: ProtectionCoordinator,
        publish: () -> Unit,
        server: ServerCalls,
        report: (String) -> Unit = {},
    ) : this(
        object : JdbcConnectionScope {
            override fun <T> withConnection(block: (Connection) -> T): T = connection().use(block)
        },
        coordinator,
        publish,
        server,
        report,
    )

    interface ServerCalls {
        fun <T> call(action: () -> T): T
        fun refund(action: () -> Boolean): Boolean = call(action)
    }

    enum class Result {
        SUCCESS, BUSY, REJECTED, AREA_LIMIT, RADIUS_LIMIT, CHUNK_LIMIT, COLLISION, DUPLICATE,
        DECLINED, REFUNDED, REVIEW_REQUIRED,
    }

    fun purchase(
        operation: OperationJournal.Operation,
        expectedLevel: Int,
        limits: ChunkExpansionTransaction.Limits,
        account: ExpansionEconomy.Account?,
        validate: () -> Boolean,
        classicLimits: ClassicExpansionTransaction.Limits? = null,
    ): Result {
        val reservation = coordinator.reservePurchase() ?: return Result.BUSY
        var result = Result.REVIEW_REQUIRED
        try {
            result = run(operation, expectedLevel, limits, account, validate, classicLimits)
        } catch (failure: Exception) {
            report("Purchase ${operation.id}: ${failure.message}; inspect its journal before retrying")
        } finally {
            try {
                coordinator.finishPurchase(reservation, publish)
            } catch (failure: Exception) {
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

    private fun state(op: OperationJournal.Operation) = connections.withConnection { connection ->
        OperationJournal.readAll(connection).singleOrNull { it.id == op.id }?.state
    }

    private fun transition(op: OperationJournal.Operation, from: OperationJournal.State, to: OperationJournal.State) {
        connections.withConnection {
            check(OperationJournal.transition(it, op.id, from, to, System.currentTimeMillis()))
        }
    }

    private fun run(
        op: OperationJournal.Operation,
        expectedLevel: Int,
        limits: ChunkExpansionTransaction.Limits,
        account: ExpansionEconomy.Account?,
        validate: () -> Boolean,
        classicLimits: ClassicExpansionTransaction.Limits?,
    ): Result {
        if (state(op) != null) return Result.DUPLICATE
        if (op.amount.signum() > 0 &&
            (account == null || account.providerId != op.provider || account.currencyId != op.currency)
        ) return Result.REJECTED

        val checked = connections.withConnection { connection ->
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            connection.autoCommit = false
            try {
                land(connection, op, expectedLevel, limits, classicLimits, validateOnly = true)
            } finally {
                connection.rollback()
            }
        }
        if (checked != Result.SUCCESS) return checked
        if (!server.call(validate)) return Result.REJECTED
        if (!connections.withConnection { OperationJournal.prepare(it, op) }) return Result.DUPLICATE

        if (op.amount.signum() > 0) {
            transition(op, OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED)
            val paid = try {
                server.call { account!!.withdraw() }
            } catch (failure: Exception) {
                transition(op, OperationJournal.State.DEBIT_REQUESTED, OperationJournal.State.UNCERTAIN)
                report("Uncertain debit ${op.id}: ${failure.message}")
                return Result.REVIEW_REQUIRED
            }
            transition(
                op,
                OperationJournal.State.DEBIT_REQUESTED,
                if (paid) OperationJournal.State.DEBITED else OperationJournal.State.DECLINED,
            )
            if (!paid) return Result.DECLINED
        }

        try {
            if (server.call(validate)) connections.withConnection { connection ->
                connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
                connection.autoCommit = false
                try {
                    val applied = land(connection, op, expectedLevel, limits, classicLimits, validateOnly = false)
                    if (applied == Result.SUCCESS) {
                        connection.commit()
                        return@withConnection Result.SUCCESS
                    }
                    connection.rollback()
                    applied
                } catch (failure: Exception) {
                    try {
                        connection.rollback()
                    } catch (rollback: Exception) {
                        failure.addSuppressed(rollback)
                    }
                    throw failure
                }
            }.also { if (it == Result.SUCCESS) return Result.SUCCESS }
        } catch (failure: Exception) {
            report("Land write ${op.id}: ${failure.message}")
        }

        // A failed commit response is ambiguous. Verify using a NEW managed connection before any refund.
        return when (state(op)) {
            OperationJournal.State.LAND_COMMITTED -> Result.SUCCESS
            OperationJournal.State.PREPARED -> {
                transition(op, OperationJournal.State.PREPARED, OperationJournal.State.CANCELLED)
                Result.REJECTED
            }
            OperationJournal.State.DEBITED -> refund(op, requireNotNull(account))
            else -> Result.REVIEW_REQUIRED
        }
    }

    private fun land(
        connection: Connection,
        op: OperationJournal.Operation,
        expectedLevel: Int,
        limits: ChunkExpansionTransaction.Limits,
        classicLimits: ClassicExpansionTransaction.Limits?,
        validateOnly: Boolean,
    ): Result {
        if (op.classicRadius != null) {
            val classic = requireNotNull(classicLimits)
            val result = if (validateOnly) {
                ClassicExpansionTransaction.apply(connection, op, expectedLevel, classic, true)
            } else {
                OperationJournal.applyClassicLand(connection, op.id, expectedLevel, classic, System.currentTimeMillis())
            }
            return when (result) {
                ClassicExpansionTransaction.Result.SUCCESS -> Result.SUCCESS
                ClassicExpansionTransaction.Result.REJECTED -> Result.REJECTED
                ClassicExpansionTransaction.Result.RADIUS_LIMIT -> Result.RADIUS_LIMIT
                ClassicExpansionTransaction.Result.AREA_LIMIT -> Result.AREA_LIMIT
                ClassicExpansionTransaction.Result.COLLISION -> Result.COLLISION
            }
        }

        val direction = ExpansionDirection.entries.single { op.source.neighbour(it) == op.target }
        val result = if (validateOnly) {
            ChunkExpansionTransaction.applyInTransaction(
                connection,
                ChunkExpansionTransaction.Request(
                    op.plotId, op.owner, op.actor, op.source, direction, op.target, op.expectedRevision,
                ),
                limits,
                validateOnly = true,
            )
        } else {
            OperationJournal.applyLand(connection, op.id, limits, System.currentTimeMillis())
        }
        return when (result) {
            is ChunkExpansionTransaction.Result.Success ->
                if (!validateOnly || result.expansionLevel.toLong() == expectedLevel.toLong() + 1) Result.SUCCESS
                else Result.REJECTED
            ChunkExpansionTransaction.Result.AreaLimit -> Result.AREA_LIMIT
            ChunkExpansionTransaction.Result.PlotChunkLimit,
            ChunkExpansionTransaction.Result.OwnerChunkLimit -> Result.CHUNK_LIMIT
            ChunkExpansionTransaction.Result.Overlap -> Result.COLLISION
            else -> Result.REJECTED
        }
    }

    private fun refund(op: OperationJournal.Operation, account: ExpansionEconomy.Account): Result {
        transition(op, OperationJournal.State.DEBITED, OperationJournal.State.REFUND_REQUIRED)
        transition(op, OperationJournal.State.REFUND_REQUIRED, OperationJournal.State.REFUND_REQUESTED)
        val refunded = try {
            server.refund { account.refund() }
        } catch (failure: Exception) {
            transition(op, OperationJournal.State.REFUND_REQUESTED, OperationJournal.State.UNCERTAIN)
            report("Uncertain refund ${op.id}: ${failure.message}")
            return Result.REVIEW_REQUIRED
        }
        transition(
            op,
            OperationJournal.State.REFUND_REQUESTED,
            if (refunded) OperationJournal.State.REFUNDED else OperationJournal.State.REFUND_REQUIRED,
        )
        return if (refunded) Result.REFUNDED else Result.REVIEW_REQUIRED
    }
}
