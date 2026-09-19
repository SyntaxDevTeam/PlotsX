package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.math.BigDecimal
import java.sql.Connection
import java.util.UUID

/** Durable evidence of external payment attempts. Caller serializes writes with the mutation coordinator.
 * No provider call is retried by this repository.
 */
internal object OperationJournal {
    enum class State(val terminal: Boolean = false) {
        PREPARED, DEBIT_REQUESTED, DEBITED, REFUND_REQUIRED, REFUND_REQUESTED, UNCERTAIN,
        LAND_COMMITTED(true), REFUNDED(true), DECLINED(true), CANCELLED(true)
    }

    data class Operation(
        val id: UUID, val plotId: Int, val owner: UUID, val actor: UUID, val world: String,
        val source: ChunkPosition, val target: ChunkPosition, val expectedRevision: Long,
        val amount: BigDecimal, val provider: String, val currency: String,
        val state: State = State.PREPARED, val createdAt: Long, val updatedAt: Long = createdAt
    ) {
        init {
            require(plotId > 0 && expectedRevision in 0 until Long.MAX_VALUE)
            require(world.isNotBlank() && world.length <= 255)
            require(provider.isNotBlank() && provider.length <= 255 && currency.isNotBlank() && currency.length <= 255)
            require(amount.signum() >= 0 && amount.precision() <= 38 && amount.scale() in 0..18)
            require(createdAt >= 0 && updatedAt >= createdAt)
            require(kotlin.math.abs(source.x.toLong() - target.x) + kotlin.math.abs(source.z.toLong() - target.z) == 1L)
        }
    }

    // No FK: financial evidence must survive plot deletion and an unresolved refund.
    fun schema(): String = """
        CREATE TABLE IF NOT EXISTS plot_operations (
            operation_id VARCHAR(36) PRIMARY KEY,
            plot_id INTEGER NOT NULL,
            owner_uuid VARCHAR(36) NOT NULL,
            actor_uuid VARCHAR(36) NOT NULL,
            world VARCHAR(255) NOT NULL,
            source_x INTEGER NOT NULL,
            source_z INTEGER NOT NULL,
            target_x INTEGER NOT NULL,
            target_z INTEGER NOT NULL,
            expected_revision BIGINT NOT NULL,
            amount VARCHAR(64) NOT NULL,
            provider VARCHAR(255) NOT NULL,
            currency VARCHAR(255) NOT NULL,
            state VARCHAR(32) NOT NULL,
            created_at BIGINT NOT NULL,
            updated_at BIGINT NOT NULL
        )
    """.trimIndent()

    fun exists(conn: Connection): Boolean = conn.metaData.getTables(conn.catalog, conn.schema, "%", null).use { rows ->
        var found = false
        while (rows.next()) if (rows.getString("TABLE_NAME").equals("plot_operations", true)) found = true
        found
    }

    fun migrate(conn: Connection) {
        require(conn.autoCommit)
        conn.prepareStatement("SELECT name FROM schema_migrations WHERE version = 2").use {
            it.executeQuery().use { rows -> if (rows.next()) {
                require(rows.getString(1) == "operation_journal")
                require(exists(conn)) { "Journal migration is recorded but financial evidence table is missing" }
                readAll(conn)
                return
            } }
        }
        conn.createStatement().use { it.execute(schema()) }
        readAll(conn) // Validate a partially created/imported journal before marking migration complete.
        conn.createStatement().use { it.executeUpdate("INSERT INTO schema_migrations (version, name) VALUES (2, 'operation_journal')") }
    }

    fun readAll(conn: Connection): List<Operation> = conn.createStatement().use { stmt ->
        stmt.executeQuery("SELECT * FROM plot_operations ORDER BY created_at, operation_id").use { rows -> buildList {
            while (rows.next()) add(Operation(
                UUID.fromString(rows.getString("operation_id")), rows.getInt("plot_id"),
                UUID.fromString(rows.getString("owner_uuid")), UUID.fromString(rows.getString("actor_uuid")), rows.getString("world"),
                ChunkPosition(rows.getInt("source_x"), rows.getInt("source_z")), ChunkPosition(rows.getInt("target_x"), rows.getInt("target_z")),
                rows.getLong("expected_revision"), rows.getString("amount").toBigDecimal(), rows.getString("provider"), rows.getString("currency"),
                State.valueOf(rows.getString("state")), rows.getLong("created_at"), rows.getLong("updated_at")
            ))
        } }
    }

    fun pending(conn: Connection): List<Operation> = readAll(conn).filterNot { it.state.terminal }

    /** Return false for an identical existing operation: the caller must NOT withdraw again. */
    fun prepare(conn: Connection, operation: Operation): Boolean {
        require(conn.autoCommit) { "Preparation must be durable before a provider is called" }
        require(operation.state == State.PREPARED && operation.createdAt == operation.updatedAt)
        val existing = readAll(conn).singleOrNull { it.id == operation.id }
        if (existing != null) {
            require(existing.copy(state = State.PREPARED, updatedAt = existing.createdAt) == operation) { "Operation ID reused with a different request" }
            return false
        }
        require(pending(conn).none { it.owner == operation.owner || it.plotId == operation.plotId }) {
            "An unresolved operation reserves this owner or plot"
        }
        conn.prepareStatement("INSERT INTO plot_operations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").use {
            it.setString(1, operation.id.toString()); it.setInt(2, operation.plotId)
            it.setString(3, operation.owner.toString()); it.setString(4, operation.actor.toString()); it.setString(5, operation.world)
            it.setInt(6, operation.source.x); it.setInt(7, operation.source.z); it.setInt(8, operation.target.x); it.setInt(9, operation.target.z)
            it.setLong(10, operation.expectedRevision); it.setString(11, operation.amount.toPlainString())
            it.setString(12, operation.provider); it.setString(13, operation.currency); it.setString(14, operation.state.name)
            it.setLong(15, operation.createdAt); it.setLong(16, operation.updatedAt); it.executeUpdate()
        }
        return true
    }

    private val transitions = mapOf(
        State.PREPARED to setOf(State.DEBIT_REQUESTED, State.CANCELLED),
        State.DEBIT_REQUESTED to setOf(State.DEBITED, State.DECLINED, State.UNCERTAIN),
        State.DEBITED to setOf(State.REFUND_REQUIRED),
        State.REFUND_REQUIRED to setOf(State.REFUND_REQUESTED),
        State.REFUND_REQUESTED to setOf(State.REFUNDED, State.REFUND_REQUIRED, State.UNCERTAIN)
    )

    fun transition(conn: Connection, id: UUID, expected: State, next: State, now: Long): Boolean {
        require(conn.autoCommit) { "Payment status must be durable before the next external action" }
        require(next in transitions[expected].orEmpty()) { "Illegal payment transition: $expected -> $next" }
        return update(conn, id, expected, next, now)
    }

    private fun update(conn: Connection, id: UUID, expected: State, next: State, now: Long): Boolean {
        require(now >= 0)
        return conn.prepareStatement("""
            UPDATE plot_operations SET state = ?, updated_at = ?
            WHERE operation_id = ? AND state = ? AND updated_at <= ?
        """.trimIndent()).use {
            it.setString(1, next.name); it.setLong(2, now); it.setString(3, id.toString()); it.setString(4, expected.name); it.setLong(5, now)
            it.executeUpdate() == 1
        }
    }

    /** Caller owns transaction AND mutation coordinator; rollback on any failure or rejection. */
    fun applyLand(conn: Connection, id: UUID, limits: ChunkExpansionTransaction.Limits, now: Long): ChunkExpansionTransaction.Result {
        require(!conn.autoCommit)
        val operation = readAll(conn).single { it.id == id }
        require(operation.state == State.DEBITED || (operation.state == State.PREPARED && operation.amount.signum() == 0)) {
            "Land requires confirmed payment or a prepared free operation"
        }
        val plot = PlotRepository.readAll(conn).singleOrNull { it.id == operation.plotId }
        require(plot == null || plot.world.equals(operation.world, true)) { "Operation world differs from current plot" }
        val direction = ExpansionDirection.entries.single { operation.source.neighbour(it) == operation.target }
        val result = ChunkExpansionTransaction.applyInTransaction(conn, ChunkExpansionTransaction.Request(
            operation.plotId, operation.owner, operation.actor, operation.source, direction, operation.target, operation.expectedRevision
        ), limits)
        if (result is ChunkExpansionTransaction.Result.Success) {
            check(update(conn, id, operation.state, State.LAND_COMMITTED, now)) { "Operation changed before land commit" }
        }
        return result
    }

    enum class Resolution { NO_DEBIT, REFUND_CONFIRMED }

    /** Dedicated connection; caller holds the mutation coordinator. No money or land is changed. */
    fun reconcile(conn: Connection, id: UUID, expected: State, expectedUpdatedAt: Long,
                  resolution: Resolution, administrator: UUID, reason: String, now: Long): Boolean {
        require(conn.autoCommit)
        require(expected in setOf(State.UNCERTAIN, State.REFUND_REQUIRED)) { "Operation is not awaiting reconciliation" }
        require(resolution != Resolution.NO_DEBIT || expected == State.UNCERTAIN) { "A confirmed debit requires a confirmed refund" }
        require(reason.length in 8..120 && reason.none { it.isISOControl() }) { "Evidence reference/reason must contain 8–120 characters without control characters" }
        require(now >= expectedUpdatedAt && expectedUpdatedAt >= 0)
        val next = if (resolution == Resolution.NO_DEBIT) State.CANCELLED else State.REFUNDED
        conn.autoCommit = false
        try {
            val operation = readAll(conn).singleOrNull { it.id == id }
            if (operation == null || operation.state != expected || operation.updatedAt != expectedUpdatedAt) {
                conn.rollback()
                return false
            }
            val changed = conn.prepareStatement("UPDATE plot_operations SET state = ?, updated_at = ? WHERE operation_id = ? AND state = ? AND updated_at = ?").use {
                it.setString(1, next.name); it.setLong(2, now); it.setString(3, id.toString())
                it.setString(4, expected.name); it.setLong(5, expectedUpdatedAt); it.executeUpdate() == 1
            }
            if (!changed) { conn.rollback(); return false }
            // Existing non-cascading audit table is included in every backup format.
            conn.prepareStatement("INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)").use {
                it.setInt(1, operation.plotId)
                it.setString(2, "RECONCILE $id ${expected.name}->${next.name} $reason")
                it.setString(3, administrator.toString()); it.setLong(4, now); it.executeUpdate()
            }
            conn.commit()
            return true
        } catch (failure: Exception) {
            try { conn.rollback() } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        } finally { conn.autoCommit = true }
    }

    /** A backup can predate a later debit/refund. Imported pending work must never resume blindly. */
    fun quarantineRestored(conn: Connection, now: Long) {
        require(!conn.autoCommit) { "Import quarantine must commit with restored data" }
        for (operation in pending(conn)) {
            check(update(conn, operation.id, operation.state, State.UNCERTAIN, maxOf(now, operation.updatedAt)))
        }
    }

    /** Startup/maintenance only. Never calls an economy provider; uncertain attempts need reconciliation. */
    fun recoverInterrupted(conn: Connection, now: Long): List<Operation> {
        require(conn.autoCommit)
        conn.autoCommit = false
        try {
            for (operation in pending(conn)) {
                val next = when (operation.state) {
                    State.PREPARED -> State.CANCELLED
                    State.DEBIT_REQUESTED, State.REFUND_REQUESTED -> State.UNCERTAIN
                    State.DEBITED -> State.REFUND_REQUIRED
                    else -> continue
                }
                check(update(conn, operation.id, operation.state, next, maxOf(now, operation.updatedAt)))
            }
            conn.commit()
        } catch (failure: Exception) { conn.rollback(); throw failure }
        finally { conn.autoCommit = true }
        return pending(conn)
    }
}
