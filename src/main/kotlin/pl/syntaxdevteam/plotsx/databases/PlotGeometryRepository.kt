package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import pl.syntaxdevteam.plotsx.geometry.PlotGeometry
import java.sql.Connection
import java.util.Locale

internal data class StoredPlotGeometry(val plotId: Int, val world: String, val revision: Long, val geometry: PlotGeometry)

/** Transaction-bound geometry storage. World/owner limits, payments and claim coordination live above it. */
internal object PlotGeometryRepository {
    fun worldKey(world: String): String = world.lowercase(Locale.ROOT)

    /** Batch reads also reject orphan rows, mixed types, invalid world keys and disconnected chunks. */
    fun readAll(conn: Connection): List<StoredPlotGeometry> {
        val segments = mutableMapOf<Int, MutableList<PlotSegment>>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT plot_id, x, z, radius FROM plot_segments ORDER BY plot_id, x, z").use { rows ->
                while (rows.next()) {
                    val radius = rows.getInt(4)
                    require(radius >= 0) { "Invalid segment radius for plot ${rows.getInt(1)}" }
                    segments.getOrPut(rows.getInt(1)) { mutableListOf() }.add(PlotSegment(rows.getInt(2), rows.getInt(3), radius))
                }
            }
        }
        val chunks = mutableMapOf<Int, MutableList<Pair<String, ChunkPosition>>>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT plot_id, world_key, chunk_x, chunk_z FROM plot_chunks ORDER BY plot_id, chunk_x, chunk_z").use { rows ->
                while (rows.next()) chunks.getOrPut(rows.getInt(1)) { mutableListOf() }
                    .add(rows.getString(2) to ChunkPosition(rows.getInt(3), rows.getInt(4)))
            }
        }
        val plots = mutableListOf<StoredPlotGeometry>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT plot_id, world, x, z, radius, geometry_type, geometry_revision FROM plots ORDER BY plot_id").use { rows ->
                while (rows.next()) {
                    val id = rows.getInt(1)
                    val world = rows.getString(2)
                    val radius = rows.getObject(5)?.let { (it as Number).toInt() }
                    val revision = rows.getLong(7)
                    require(revision >= 0) { "Invalid geometry revision for plot $id" }
                    val extension = segments.remove(id).orEmpty()
                    val ownedChunks = chunks.remove(id).orEmpty()
                    val geometry = when (rows.getString(6)) {
                        "classic" -> {
                            require(radius != null && radius >= 0 && ownedChunks.isEmpty()) { "Invalid classic geometry for plot $id" }
                            ClassicGeometry(PlotSegment(rows.getInt(3), rows.getInt(4), radius), extension)
                        }
                        "chunks" -> {
                            require(radius == null && extension.isEmpty() && ownedChunks.all { it.first == worldKey(world) }) {
                                "Invalid chunk geometry or world for plot $id"
                            }
                            ChunkGeometry(ownedChunks.map { it.second })
                        }
                        else -> error("Unknown geometry type for plot $id")
                    }
                    plots.add(StoredPlotGeometry(id, world, revision, geometry))
                }
            }
        }
        require(segments.isEmpty() && chunks.isEmpty()) { "Orphan plot geometry records" }
        return plots
    }

    /** Explicit-ID metadata must already exist in the caller's transaction; the first chunk is included. */
    fun insertChunks(conn: Connection, plotId: Int, geometry: ChunkGeometry) {
        require(!conn.autoCommit) { "Chunk geometry must be written inside a transaction" }
        val world = conn.prepareStatement("SELECT world, radius, geometry_type FROM plots WHERE plot_id = ?").use { stmt ->
            stmt.setInt(1, plotId)
            stmt.executeQuery().use { rows ->
                require(rows.next()) { "Missing plot $plotId" }
                require(rows.getObject(2) == null && rows.getString(3) == "chunks") { "Plot $plotId is not chunk-based" }
                rows.getString(1)
            }
        }
        for (table in listOf("plot_segments", "plot_chunks")) conn.prepareStatement("SELECT 1 FROM $table WHERE plot_id = ?").use {
            it.setInt(1, plotId)
            it.executeQuery().use { rows -> require(!rows.next()) { "Geometry already exists for plot $plotId" } }
        }
        conn.prepareStatement("INSERT INTO plot_chunks (plot_id, world_key, chunk_x, chunk_z) VALUES (?, ?, ?, ?)").use { stmt ->
            for (chunk in geometry.chunks) {
                stmt.setInt(1, plotId); stmt.setString(2, worldKey(world)); stmt.setInt(3, chunk.x); stmt.setInt(4, chunk.z)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    /** Maintenance/import validation; quadratic across plots, never called from block protection. */
    fun validateNoOverlaps(plots: List<StoredPlotGeometry>) {
        for (group in plots.groupBy { worldKey(it.world) }.values) {
            for (i in group.indices) for (j in i + 1 until group.size) require(!group[i].geometry.intersects(group[j].geometry)) {
                "Plots ${group[i].plotId} and ${group[j].plotId} overlap"
            }
        }
    }
}
