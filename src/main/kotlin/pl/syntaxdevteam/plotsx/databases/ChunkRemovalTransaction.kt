package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.sql.Connection
import java.util.UUID

/**
 * Removes one chunk from a chunk plot. The operation never refunds money and never decreases
 * plot_expansion_levels, so removing land cannot be used to reset progressive expansion pricing.
 */
internal object ChunkRemovalTransaction {
    data class Request(
        val plotId: Int,
        val owner: UUID,
        val actor: UUID,
        val target: ChunkPosition,
        val expectedRevision: Long
    )

    sealed interface Result {
        data class Success(val chunk: ChunkPosition, val revision: Long) : Result
        data object PlotNotFound : Result
        data object NotOwner : Result
        data object WrongGeometry : Result
        data object StaleQuote : Result
        data object ChunkNotFound : Result
        data object LastChunk : Result
        data object AnchorChunk : Result
        data object WouldDisconnect : Result
    }

    fun remove(conn: Connection, request: Request): Result {
        require(conn.autoCommit) { "Chunk removal requires a dedicated connection" }
        val isolation = conn.transactionIsolation
        try {
            conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            conn.autoCommit = false
            val result = applyInTransaction(conn, request)
            if (result is Result.Success) conn.commit() else conn.rollback()
            return result
        } catch (failure: Exception) {
            try { conn.rollback() } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        } finally {
            conn.autoCommit = true
            if (conn.transactionIsolation != isolation) conn.transactionIsolation = isolation
        }
    }

    fun applyInTransaction(conn: Connection, request: Request): Result {
        require(!conn.autoCommit) { "An existing transaction is required" }
        val plot = PlotRepository.readAll(conn).singleOrNull { it.id == request.plotId }
            ?: return Result.PlotNotFound
        if (plot.ownerUuid != request.owner) return Result.NotOwner
        val geometry = plot.geometry as? ChunkGeometry ?: return Result.WrongGeometry
        if (request.expectedRevision < 0 || plot.geometryRevision != request.expectedRevision ||
            plot.geometryRevision == Long.MAX_VALUE) return Result.StaleQuote
        if (request.target !in geometry.chunks) return Result.ChunkNotFound
        if (geometry.chunks.size <= 1) return Result.LastChunk

        val anchor = ChunkPosition.atBlock(plot.x, plot.z)
        if (request.target == anchor) return Result.AnchorChunk
        if (geometry.without(request.target) == null) return Result.WouldDisconnect

        val nextRevision = plot.geometryRevision + 1
        conn.prepareStatement("""
            UPDATE plots SET geometry_revision = ?
            WHERE plot_id = ? AND owner_uuid = ? AND geometry_type = 'chunks' AND geometry_revision = ?
        """.trimIndent()).use {
            it.setLong(1, nextRevision)
            it.setInt(2, plot.id)
            it.setString(3, request.owner.toString())
            it.setLong(4, request.expectedRevision)
            if (it.executeUpdate() != 1) return Result.StaleQuote
        }
        conn.prepareStatement("""
            DELETE FROM plot_chunks
            WHERE plot_id = ? AND world_key = ? AND chunk_x = ? AND chunk_z = ?
        """.trimIndent()).use {
            it.setInt(1, plot.id)
            it.setString(2, PlotGeometryRepository.worldKey(plot.world))
            it.setInt(3, request.target.x)
            it.setInt(4, request.target.z)
            if (it.executeUpdate() != 1) return Result.StaleQuote
        }
        conn.prepareStatement("INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)").use {
            it.setInt(1, plot.id)
            it.setString(2, "REMOVE_CHUNK:${request.target.x},${request.target.z}:$nextRevision")
            it.setString(3, request.actor.toString())
            it.setLong(4, System.currentTimeMillis())
            it.executeUpdate()
        }
        return Result.Success(request.target, nextRevision)
    }
}
