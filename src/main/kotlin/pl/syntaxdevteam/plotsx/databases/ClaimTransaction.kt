package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.PlotGeometry
import java.sql.Connection
import java.util.UUID

internal object ClaimTransaction {
    sealed interface Result {
        data class Success(val id: Int) : Result
        data object PlotLimit : Result
        data object AreaLimit : Result
        data object ChunkLimit : Result
        data object Overlap : Result
    }
    fun create(conn: Connection, owner: UUID, actor: UUID, world: String, x: Int, y: Int, z: Int,
               geometry: PlotGeometry, maxPlots: Int, maxArea: Long, maxChunksPerPlot: Int, maxChunksOwned: Int,
               prefix: String, flags: Map<String, Boolean>): Result {
        require(conn.autoCommit)
        val isolation = conn.transactionIsolation
        conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
        conn.autoCommit = false
        try {
            fun reject(result: Result): Result { conn.rollback(); return result }
            val all = PlotRepository.readAll(conn)
            val owned = all.filter { it.ownerUuid == owner }
            if (owned.size >= maxPlots) return reject(Result.PlotLimit)
            val used = owned.fold(0L) { sum, plot -> saturatedAdd(sum, plot.geometry.area) }
            if (geometry.area > maxArea || used > maxArea - geometry.area) return reject(Result.AreaLimit)
            if (geometry is ChunkGeometry) {
                val chunks = owned.sumOf { (it.geometry as? ChunkGeometry)?.chunks?.size?.toLong() ?: 0L }
                if (geometry.chunks.size > maxChunksPerPlot || chunks + geometry.chunks.size > maxChunksOwned)
                    return reject(Result.ChunkLimit)
            }
            if (all.any { PlotGeometryRepository.worldKey(it.world) == PlotGeometryRepository.worldKey(world) && it.geometry.intersects(geometry) })
                return reject(Result.Overlap)
            val id = PlotRepository.insert(conn, owner, world, "$prefix ${owned.size + 1}", x, y, z, System.currentTimeMillis(), geometry)
            conn.prepareStatement("INSERT INTO plot_flags (plot_id, flag_name, flag_value) VALUES (?, ?, ?)").use { stmt ->
                for ((flag, value) in flags) {
                    stmt.setInt(1, id); stmt.setString(2, flag); stmt.setString(3, value.toString()); stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.prepareStatement("INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)").use {
                it.setInt(1, id); it.setString(2, "CREATE"); it.setString(3, actor.toString()); it.setLong(4, System.currentTimeMillis()); it.executeUpdate()
            }
            conn.commit()
            return Result.Success(id)
        } catch (failure: Exception) {
            conn.rollback()
            throw failure
        } finally { conn.autoCommit = true; conn.transactionIsolation = isolation }
    }

    fun saturatedAdd(left: Long, right: Long): Long = if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
