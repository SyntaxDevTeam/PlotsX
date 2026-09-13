package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import pl.syntaxdevteam.plotsx.geometry.PlotGeometry
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.util.UUID

internal data class StoredPlot(
    val id: Int, val ownerUuid: UUID, val world: String, val name: String,
    val x: Int, val y: Int, val z: Int, val creationTime: Long,
    val geometry: PlotGeometry, val geometryRevision: Long
) {
    fun toPlotData(): PlotData = when (val shape = geometry) {
        is ClassicGeometry -> PlotData(id, ownerUuid, x, z, y, shape.base.radius, world, name, creationTime,
            shape.segments.drop(1), geometryRevision = geometryRevision)
        is ChunkGeometry -> PlotData(id, ownerUuid, x, z, y, null, world, name, creationTime,
            chunks = shape.chunks, geometryRevision = geometryRevision)
    }
}

/** Persistence only. The caller owns the transaction and enforces claim authorization and limits. */
internal object PlotRepository {
    fun insert(conn: Connection, owner: UUID, world: String, name: String, x: Int, y: Int, z: Int,
               creationTime: Long, geometry: PlotGeometry): Int {
        require(!conn.autoCommit) { "Plot insertion requires an existing transaction" }
        require(world.isNotBlank() && world.length <= 255 && name.isNotBlank() && name.length <= 255) { "Invalid plot metadata" }
        require(geometry.contains(x, z)) { "Plot anchor must be inside its geometry" }
        if (geometry is ClassicGeometry) require(geometry.base.x == x && geometry.base.z == z) { "Classic anchor must match the base segment" }
        val savepoint = conn.setSavepoint()
        try {
            val id = conn.prepareStatement("""
                INSERT INTO plots (owner_uuid, x, z, y, radius, world, name, creation_time, geometry_type, geometry_revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """.trimIndent(), Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setString(1, owner.toString()); stmt.setInt(2, x); stmt.setInt(3, z); stmt.setInt(4, y)
                if (geometry is ClassicGeometry) stmt.setInt(5, geometry.base.radius) else stmt.setNull(5, Types.INTEGER)
                stmt.setString(6, world); stmt.setString(7, name); stmt.setLong(8, creationTime)
                stmt.setString(9, if (geometry is ClassicGeometry) "classic" else "chunks")
                check(stmt.executeUpdate() == 1) { "Plot was not inserted" }
                stmt.generatedKeys.use { keys -> check(keys.next()) { "Missing plot ID" }; keys.getInt(1) }
            }
            when (geometry) {
                is ChunkGeometry -> PlotGeometryRepository.insertChunks(conn, id, geometry)
                is ClassicGeometry -> conn.prepareStatement("INSERT INTO plot_segments (plot_id, x, z, radius) VALUES (?, ?, ?, ?)").use { stmt ->
                    for (segment in geometry.segments.drop(1)) {
                        stmt.setInt(1, id); stmt.setInt(2, segment.x); stmt.setInt(3, segment.z); stmt.setInt(4, segment.radius)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            conn.releaseSavepoint(savepoint)
            return id
        } catch (failure: Exception) {
            conn.rollback(savepoint)
            conn.releaseSavepoint(savepoint)
            throw failure
        }
    }

    /** Bulk loading preserves the saved type. Caller supplies a snapshot transaction or quiescent database. */
    fun readAll(conn: Connection): List<StoredPlot> {
        val geometries = PlotGeometryRepository.readAll(conn).associateBy { it.plotId }
        return conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT plot_id, owner_uuid, world, name, x, y, z, creation_time FROM plots ORDER BY plot_id").use { rows ->
                buildList {
                    while (rows.next()) {
                        val id = rows.getInt(1)
                        val stored = requireNotNull(geometries[id]) { "Missing geometry for plot $id" }
                        add(StoredPlot(id, UUID.fromString(rows.getString(2)), rows.getString(3), rows.getString(4),
                            rows.getInt(5), rows.getInt(6), rows.getInt(7), rows.getLong(8), stored.geometry, stored.revision))
                    }
                }
            }
        }
    }
}
