package pl.syntaxdevteam.plotsx.databases

import java.sql.Connection
import java.util.UUID

internal object OwnershipTransfer {
    /** Caller owns the connection. Every rejected or failed transfer rolls back all changes. */
    fun transfer(conn: Connection, plotId: Int, expectedOwner: UUID, recipient: UUID,
                 maxPlots: Int, maxRadius: Int, maxArea: Long,
                 maxChunksPerPlot: Int = Int.MAX_VALUE, maxChunksOwned: Int = Int.MAX_VALUE): Boolean {
        val autoCommit = conn.autoCommit
        val isolation = conn.transactionIsolation
        try {
            conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            conn.autoCommit = false
            fun reject(): Boolean { conn.rollback(); return false }
            if (recipient == expectedOwner) return reject()
            val all = PlotRepository.readAll(conn)
            val plot = all.singleOrNull { it.id == plotId } ?: return reject()
            if (plot.ownerUuid != expectedOwner) return reject()
            val shape = plot.geometry
            if (shape is pl.syntaxdevteam.plotsx.geometry.ClassicGeometry && shape.segments.any {
                maxOf(kotlin.math.abs(it.x.toLong() - plot.x), kotlin.math.abs(it.z.toLong() - plot.z)) + it.radius > maxRadius
            }) return reject()
            val owned = all.filter { it.ownerUuid == recipient }
            val totalArea = owned.fold(shape.area) { total, other -> ClaimTransaction.saturatedAdd(total, other.geometry.area) }
            if (owned.size >= maxPlots || totalArea > maxArea || owned.any { it.name.equals(plot.name, true) }) return reject()
            if (shape is pl.syntaxdevteam.plotsx.geometry.ChunkGeometry) {
                val used = owned.sumOf { (it.geometry as? pl.syntaxdevteam.plotsx.geometry.ChunkGeometry)?.chunks?.size?.toLong() ?: 0L }
                if (shape.chunks.size > maxChunksPerPlot || used + shape.chunks.size > maxChunksOwned) return reject()
            }
            val member = conn.prepareStatement("SELECT role FROM plot_members WHERE plot_id = ? AND member_uuid = ?").use {
                it.setInt(1, plotId); it.setString(2, recipient.toString())
                it.executeQuery().use { rs -> rs.next() }
            }
            if (!member) return reject()
            val changed = conn.prepareStatement("UPDATE plots SET owner_uuid = ? WHERE plot_id = ? AND owner_uuid = ?").use {
                it.setString(1, recipient.toString()); it.setInt(2, plotId); it.setString(3, expectedOwner.toString())
                it.executeUpdate()
            }
            if (changed != 1) return reject()
            conn.prepareStatement("DELETE FROM plot_members WHERE plot_id = ? AND (member_uuid = ? OR member_uuid = ?)").use {
                it.setInt(1, plotId); it.setString(2, recipient.toString()); it.setString(3, expectedOwner.toString())
                it.executeUpdate()
            }
            conn.prepareStatement("INSERT INTO plot_members (plot_id, member_uuid, role) VALUES (?, ?, ?)").use {
                it.setInt(1, plotId); it.setString(2, expectedOwner.toString()); it.setString(3, "member")
                it.executeUpdate()
            }
            conn.commit()
            return true
        } catch (ex: Exception) {
            conn.rollback()
            throw ex
        } finally {
            conn.autoCommit = autoCommit
            conn.transactionIsolation = isolation
        }
    }

}
