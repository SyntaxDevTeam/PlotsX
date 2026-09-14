package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.sql.Connection
import java.util.UUID

/** Storage operation, not a purchase API. The caller serializes mutations and publishes the cache.
 * External permissions, region checks and eventual payment orchestration belong to that caller.
 */
internal object ChunkExpansionTransaction {
    data class Request(
        val plotId: Int,
        val owner: UUID,
        val actor: UUID,
        val source: ChunkPosition,
        val direction: ExpansionDirection,
        val expectedTarget: ChunkPosition,
        val expectedRevision: Long
    )

    data class Limits(val maxArea: Long, val maxChunksPerPlot: Int, val maxOwnedChunks: Int) {
        init { require(maxArea >= 0 && maxChunksPerPlot >= 0 && maxOwnedChunks >= 0) }
    }

    sealed interface Result {
        data class Success(val chunk: ChunkPosition, val revision: Long, val expansionLevel: Int) : Result
        data object PlotNotFound : Result
        data object NotOwner : Result
        data object WrongGeometry : Result
        data object StaleQuote : Result
        data object InvalidTarget : Result
        data object AreaLimit : Result
        data object PlotChunkLimit : Result
        data object OwnerChunkLimit : Result
        data object Overlap : Result
    }

    /** Requires a dedicated connection, like ClaimTransaction. Rejections change no persisted data. */
    fun expand(conn: Connection, request: Request, limits: Limits): Result {
        require(conn.autoCommit) { "Chunk expansion requires a dedicated connection" }
        val isolation = conn.transactionIsolation
        try {
            conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            conn.autoCommit = false
            val result = applyInTransaction(conn, request, limits)
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

    /** Leaves commit/rollback to the caller, allowing a future payment journal to commit with land.
     * On rejection or exception the caller MUST roll back the transaction.
     */
    fun applyInTransaction(conn: Connection, request: Request, limits: Limits, validateOnly: Boolean = false): Result {
        require(!conn.autoCommit) { "An existing transaction is required" }
        val all = PlotRepository.readAll(conn)
        val plot = all.singleOrNull { it.id == request.plotId } ?: return Result.PlotNotFound
        if (plot.ownerUuid != request.owner) return Result.NotOwner
        val geometry = plot.geometry as? ChunkGeometry ?: return Result.WrongGeometry
        if (request.expectedRevision < 0 || plot.geometryRevision != request.expectedRevision ||
            plot.geometryRevision == Long.MAX_VALUE) return Result.StaleQuote
        val target = geometry.expansion(request.direction, request.source)
        if (target == null || target != request.expectedTarget) return Result.InvalidTarget
        if (geometry.chunks.size >= limits.maxChunksPerPlot) return Result.PlotChunkLimit
        val owned = all.filter { it.ownerUuid == request.owner }
        val usedChunks = owned.sumOf { (it.geometry as? ChunkGeometry)?.chunks?.size?.toLong() ?: 0L }
        if (usedChunks >= limits.maxOwnedChunks.toLong()) return Result.OwnerChunkLimit
        val usedArea = owned.fold(0L) { total, other -> ClaimTransaction.saturatedAdd(total, other.geometry.area) }
        if (limits.maxArea < ChunkPosition.AREA || usedArea > limits.maxArea - ChunkPosition.AREA) return Result.AreaLimit
        val candidate = ChunkGeometry(setOf(target))
        if (all.any { it.id != plot.id && PlotGeometryRepository.worldKey(it.world) == PlotGeometryRepository.worldKey(plot.world) &&
                it.geometry.intersects(candidate) }) return Result.Overlap

        val previousLevel = conn.prepareStatement("SELECT expansion_level FROM plot_expansion_levels WHERE plot_id = ?").use {
            it.setInt(1, plot.id)
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        }
        // Pre-E6 chunk imports have no counter. Infer their existing number of extensions once.
        val level = previousLevel ?: (geometry.chunks.size - 1)
        require(level >= 0 && level < Int.MAX_VALUE) { "Invalid chunk expansion level for plot ${plot.id}" }
        val nextLevel = level + 1
        val nextRevision = plot.geometryRevision + 1
        if (validateOnly) return Result.Success(target, nextRevision, nextLevel)
        conn.prepareStatement("""
            UPDATE plots SET geometry_revision = ?
            WHERE plot_id = ? AND owner_uuid = ? AND geometry_type = 'chunks' AND geometry_revision = ?
        """.trimIndent()).use {
            it.setLong(1, nextRevision); it.setInt(2, plot.id); it.setString(3, request.owner.toString())
            it.setLong(4, request.expectedRevision)
            if (it.executeUpdate() != 1) return Result.StaleQuote
        }
        conn.prepareStatement("INSERT INTO plot_chunks (plot_id, world_key, chunk_x, chunk_z) VALUES (?, ?, ?, ?)").use {
            it.setInt(1, plot.id); it.setString(2, PlotGeometryRepository.worldKey(plot.world))
            it.setInt(3, target.x); it.setInt(4, target.z); it.executeUpdate()
        }
        val levelSql = if (previousLevel == null)
            "INSERT INTO plot_expansion_levels (expansion_level, plot_id) VALUES (?, ?)"
        else "UPDATE plot_expansion_levels SET expansion_level = ? WHERE plot_id = ?"
        conn.prepareStatement(levelSql).use {
            it.setInt(1, nextLevel); it.setInt(2, plot.id); check(it.executeUpdate() == 1)
        }
        conn.prepareStatement("INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)").use {
            it.setInt(1, plot.id); it.setString(2, "EXPAND_CHUNK:${request.direction.name}:${target.x},${target.z}:$nextRevision")
            it.setString(3, request.actor.toString()); it.setLong(4, System.currentTimeMillis()); it.executeUpdate()
        }
        return Result.Success(target, nextRevision, nextLevel)
    }
}
