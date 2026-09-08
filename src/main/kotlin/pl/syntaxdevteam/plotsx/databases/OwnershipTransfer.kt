package pl.syntaxdevteam.plotsx.databases

import java.sql.Connection
import java.util.UUID

internal object OwnershipTransfer {
    /** Caller owns the connection. Every rejected or failed transfer rolls back all changes. */
    fun transfer(conn: Connection, plotId: Int, expectedOwner: UUID, recipient: UUID,
                 maxPlots: Int, maxRadius: Int, maxArea: Long): Boolean {
        val autoCommit = conn.autoCommit
        val isolation = conn.transactionIsolation
        try {
            conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            conn.autoCommit = false
            fun reject(): Boolean { conn.rollback(); return false }
            if (recipient == expectedOwner) return reject()
            val plot = conn.prepareStatement("SELECT owner_uuid, radius, name FROM plots WHERE plot_id = ?").use {
                it.setInt(1, plotId)
                it.executeQuery().use { rs ->
                    if (!rs.next()) null else Triple(rs.getString(1), rs.getInt(2), rs.getString(3))
                }
            } ?: return reject()
            if (plot.first != expectedOwner.toString() || plot.second > maxRadius) return reject()
            val member = conn.prepareStatement("SELECT role FROM plot_members WHERE plot_id = ? AND member_uuid = ?").use {
                it.setInt(1, plotId); it.setString(2, recipient.toString())
                it.executeQuery().use { rs -> rs.next() }
            }
            if (!member) return reject()
            var count = 0
            var area = area(plot.second)
            var duplicateName = false
            conn.prepareStatement("SELECT radius, name FROM plots WHERE owner_uuid = ?").use {
                it.setString(1, recipient.toString())
                it.executeQuery().use { rs -> while (rs.next()) {
                    count++
                    val added = area(rs.getInt(1))
                    area = if (area > Long.MAX_VALUE - added) Long.MAX_VALUE else area + added
                    if (rs.getString(2).equals(plot.third, true)) duplicateName = true
                } }
            }
            if (count >= maxPlots || area > maxArea || duplicateName) return reject()
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

    private fun area(radius: Int): Long {
        val side = radius.toLong() * 2 + 1
        return if (side > 3037000499L) Long.MAX_VALUE else side * side
    }
}
