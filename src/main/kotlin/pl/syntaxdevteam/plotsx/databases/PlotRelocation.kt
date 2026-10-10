package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.*
import java.sql.Connection
import java.util.UUID

/** Moves protection metadata only. Chunk plots are translated in whole chunks. */
internal object PlotRelocation {
    data class Target(val world: String, val x: Int, val y: Int, val z: Int)
    enum class Result { SUCCESS, STALE, COLLISION }

    fun translated(plot: PlotData, target: Target): PlotData {
        val sourceChunk = ChunkPosition.atBlock(plot.x, plot.z)
        val targetChunk = ChunkPosition.atBlock(target.x, target.z)
        val dx = if (plot.radius == null) (targetChunk.x.toLong() - sourceChunk.x) * 16 else target.x.toLong() - plot.x
        val dz = if (plot.radius == null) (targetChunk.z.toLong() - sourceChunk.z) * 16 else target.z.toLong() - plot.z
        fun shift(value: Int, delta: Long) = Math.toIntExact(value.toLong() + delta)
        val moved = plot.copy(world = target.world, x = shift(plot.x, dx), y = target.y, z = shift(plot.z, dz),
            extensions = plot.extensions.map { it.copy(x = shift(it.x, dx), z = shift(it.z, dz)) },
            chunks = plot.chunks.map { ChunkPosition(shift(it.x, dx / 16), shift(it.z, dz / 16)) }.toSet(),
            teleportSpawn = null)
        require(moved.geometry.bounds.let { it.minX >= Int.MIN_VALUE && it.maxX <= Int.MAX_VALUE &&
            it.minZ >= Int.MIN_VALUE && it.maxZ <= Int.MAX_VALUE })
        return moved
    }

    /** Caller holds the protection mutation barrier; all writes commit or roll back together. */
    fun move(conn: Connection, expected: PlotData, target: Target, actor: UUID): Result {
        require(conn.autoCommit)
        val isolation = conn.transactionIsolation
        conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
        conn.autoCommit = false
        try {
            val plots = PlotRepository.readAll(conn)
            val current = plots.singleOrNull { it.id == expected.id }?.toPlotData()
                ?: return Result.STALE
            if (current.geometryRevision != expected.geometryRevision || current.world != expected.world ||
                current.x != expected.x || current.z != expected.z || current.ownerUuid != expected.ownerUuid) return Result.STALE
            val moved = translated(current, target)
            if (plots.any { it.id != current.id && it.world.equals(target.world, true) && it.geometry.intersects(moved.geometry) })
                return Result.COLLISION
            conn.prepareStatement("UPDATE plots SET world = ?, x = ?, y = ?, z = ?, geometry_revision = geometry_revision + 1 WHERE plot_id = ? AND geometry_revision = ?").use {
                it.setString(1, moved.world); it.setInt(2, moved.x); it.setInt(3, moved.y); it.setInt(4, moved.z)
                it.setInt(5, moved.id); it.setLong(6, expected.geometryRevision)
                if (it.executeUpdate() != 1) return Result.STALE
            }
            // Delete and reinsert avoids unique-key collisions when translating adjacent cells.
            for (table in listOf("plot_segments", "plot_chunks", "plot_spawns")) conn.prepareStatement("DELETE FROM $table WHERE plot_id = ?").use {
                it.setInt(1, moved.id); it.executeUpdate()
            }
            if (moved.radius == null) PlotGeometryRepository.insertChunks(conn, moved.id, moved.geometry as ChunkGeometry)
            else conn.prepareStatement("INSERT INTO plot_segments (plot_id, x, z, radius) VALUES (?, ?, ?, ?)").use { stmt ->
                moved.extensions.forEach {
                    stmt.setInt(1, moved.id); stmt.setInt(2, it.x); stmt.setInt(3, it.z); stmt.setInt(4, it.radius); stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.prepareStatement("INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)").use {
                it.setInt(1, moved.id); it.setString(2, "RELOCATE:${current.x},${current.y},${current.z}->${moved.x},${moved.y},${moved.z}")
                it.setString(3, actor.toString()); it.setLong(4, System.currentTimeMillis()); it.executeUpdate()
            }
            conn.commit()
            return Result.SUCCESS
        } finally {
            conn.rollback()
            conn.autoCommit = true
            conn.transactionIsolation = isolation
        }
    }
}
