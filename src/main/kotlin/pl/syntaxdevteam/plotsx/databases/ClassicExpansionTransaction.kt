package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import java.sql.Connection

/** Shares the caller's transaction with the payment journal; never commits independently. */
internal object ClassicExpansionTransaction {
    data class Limits(val maxRadius: Int, val maxArea: Long)
    enum class Result { SUCCESS, REJECTED, RADIUS_LIMIT, AREA_LIMIT, COLLISION }

    fun apply(conn: Connection, op: OperationJournal.Operation, expectedLevel: Int, limits: Limits,
              validateOnly: Boolean = false): Result {
        require(!conn.autoCommit)
        val radius = requireNotNull(op.classicRadius)
        val all = PlotRepository.readAll(conn)
        val stored = all.singleOrNull { it.id == op.plotId } ?: return Result.REJECTED
        if (stored.ownerUuid != op.owner || stored.geometryRevision != op.expectedRevision ||
            !stored.world.equals(op.world, true)) return Result.REJECTED
        val plot = stored.toPlotData()
        if (plot.radius != radius || plot.extensions.size != expectedLevel) return Result.REJECTED
        val source = PlotSegment(op.source.x, op.source.z, radius)
        val target = PlotSegment(op.target.x, op.target.z, radius)
        val direction = ExpansionDirection.entries.single { it.dx == op.target.x.compareTo(op.source.x) &&
            it.dz == op.target.z.compareTo(op.source.z) }
        if (plot.expansion(direction, source) != target) return Result.REJECTED
        val extent = (plot.segments + target).maxOf {
            maxOf(kotlin.math.abs(it.x.toLong() - plot.x), kotlin.math.abs(it.z.toLong() - plot.z)) + it.radius
        }
        if (extent > limits.maxRadius) return Result.RADIUS_LIMIT
        val used = all.filter { it.ownerUuid == op.owner }.fold(0L) { sum, p -> ClaimTransaction.saturatedAdd(sum, p.geometry.area) }
        if (target.area > limits.maxArea || used > limits.maxArea - target.area) return Result.AREA_LIMIT
        if (all.any { it.id != plot.id && it.world.equals(plot.world, true) && it.geometry.intersects(ClassicGeometry(target)) })
            return Result.COLLISION
        if (validateOnly) return Result.SUCCESS
        conn.prepareStatement("INSERT INTO plot_segments (plot_id, x, z, radius) VALUES (?, ?, ?, ?)").use {
            it.setInt(1, plot.id); it.setInt(2, target.x); it.setInt(3, target.z); it.setInt(4, radius); it.executeUpdate()
        }
        conn.prepareStatement("UPDATE plots SET geometry_revision = geometry_revision + 1 WHERE plot_id = ? AND geometry_revision = ?").use {
            it.setInt(1, plot.id); it.setLong(2, op.expectedRevision); check(it.executeUpdate() == 1)
        }
        conn.prepareStatement("INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)").use {
            it.setInt(1, plot.id); it.setString(2, "EXPAND:${direction.name}:${target.x},${target.z},$radius")
            it.setString(3, op.actor.toString()); it.setLong(4, System.currentTimeMillis()); it.executeUpdate()
        }
        return Result.SUCCESS
    }
}
