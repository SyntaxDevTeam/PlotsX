package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class ChunkRemovalTransactionTest {
    private val owner = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val anchor = ChunkPosition(0, 0)
    private val east = ChunkPosition(1, 0)
    private val east2 = ChunkPosition(2, 0)

    private fun databases(
        geometry: ChunkGeometry = ChunkGeometry(setOf(anchor, east)),
        action: (Connection, Int) -> Unit
    ) {
        for ((type, url) in listOf("sqlite" to "jdbc:sqlite::memory:", "h2" to "jdbc:h2:mem:${UUID.randomUUID()}")) {
            DriverManager.getConnection(url).use { conn ->
                DatabaseSchema.statements(type).forEach { sql -> conn.createStatement().use { it.execute(sql) } }
                DatabaseMigrations.migrate(conn, type)
                val id = insert(conn, geometry)
                action(conn, id)
            }
        }
    }

    private fun insert(conn: Connection, geometry: pl.syntaxdevteam.plotsx.geometry.PlotGeometry): Int {
        conn.autoCommit = false
        val classic = geometry as? ClassicGeometry
        val x = classic?.base?.x ?: 0
        val z = classic?.base?.z ?: 0
        val id = PlotRepository.insert(conn, owner, "world", "plot", x, 64, z, 0, geometry)
        conn.commit()
        conn.autoCommit = true
        return id
    }

    private fun request(id: Int, target: ChunkPosition = east, revision: Long = 0) =
        ChunkRemovalTransaction.Request(id, owner, actor, target, revision)

    @Test fun `removes one leaf chunk increments revision logs actor and preserves price level`() =
        databases { conn, id ->
            conn.prepareStatement("INSERT INTO plot_expansion_levels (expansion_level, plot_id) VALUES (?, ?)").use {
                it.setInt(1, 7); it.setInt(2, id); it.executeUpdate()
            }
            val result = ChunkRemovalTransaction.remove(conn, request(id))
            assertEquals(ChunkRemovalTransaction.Result.Success(east, 1), result)
            val plot = PlotRepository.readAll(conn).single()
            assertEquals(setOf(anchor), (plot.geometry as ChunkGeometry).chunks)
            assertEquals(1L, plot.geometryRevision)
            conn.prepareStatement("SELECT expansion_level FROM plot_expansion_levels WHERE plot_id = ?").use {
                it.setInt(1, id)
                it.executeQuery().use { rs -> assertTrue(rs.next()); assertEquals(7, rs.getInt(1)) }
            }
            conn.createStatement().use { it.executeQuery("SELECT actor_uuid, action FROM plot_logs").use { rs ->
                assertTrue(rs.next())
                assertEquals(actor.toString(), rs.getString(1))
                assertEquals("REMOVE_CHUNK:1,0:1", rs.getString(2))
                assertFalse(rs.next())
            } }
            assertTrue(conn.autoCommit)
        }

    @Test fun `rejects last anchor and disconnecting chunks`() {
        databases(ChunkGeometry(setOf(anchor))) { conn, id ->
            assertEquals(ChunkRemovalTransaction.Result.LastChunk, ChunkRemovalTransaction.remove(conn, request(id, anchor)))
        }
        databases(ChunkGeometry(setOf(anchor, east))) { conn, id ->
            assertEquals(ChunkRemovalTransaction.Result.AnchorChunk, ChunkRemovalTransaction.remove(conn, request(id, anchor)))
        }
        databases(ChunkGeometry(setOf(anchor, east, east2))) { conn, id ->
            assertEquals(ChunkRemovalTransaction.Result.WouldDisconnect, ChunkRemovalTransaction.remove(conn, request(id, east)))
            assertEquals(setOf(anchor, east, east2), (PlotRepository.readAll(conn).single().geometry as ChunkGeometry).chunks)
        }
    }

    @Test fun `rejects stale foreign missing and classic targets`() = databases { conn, id ->
        assertEquals(ChunkRemovalTransaction.Result.StaleQuote,
            ChunkRemovalTransaction.remove(conn, request(id, revision = 1)))
        assertEquals(ChunkRemovalTransaction.Result.NotOwner,
            ChunkRemovalTransaction.remove(conn, request(id).copy(owner = UUID.randomUUID())))
        assertEquals(ChunkRemovalTransaction.Result.ChunkNotFound,
            ChunkRemovalTransaction.remove(conn, request(id, ChunkPosition(9, 9))))
        assertEquals(ChunkRemovalTransaction.Result.PlotNotFound,
            ChunkRemovalTransaction.remove(conn, request(Int.MAX_VALUE)))
        val classic = insert(conn, ClassicGeometry(PlotSegment(100, 100, 1)))
        assertEquals(ChunkRemovalTransaction.Result.WrongGeometry,
            ChunkRemovalTransaction.remove(conn, request(classic)))
    }

    @Test fun `late log failure rolls back deleted chunk and revision`() = databases { conn, id ->
        conn.createStatement().use { it.execute("DROP TABLE plot_logs") }
        assertThrows(java.sql.SQLException::class.java) { ChunkRemovalTransaction.remove(conn, request(id)) }
        val plot = PlotRepository.readAll(conn).single()
        assertEquals(0L, plot.geometryRevision)
        assertEquals(setOf(anchor, east), (plot.geometry as ChunkGeometry).chunks)
        assertTrue(conn.autoCommit)
    }

    @Test fun `caller transaction can roll back successful removal`() = databases { conn, id ->
        conn.autoCommit = false
        assertTrue(ChunkRemovalTransaction.applyInTransaction(conn, request(id)) is ChunkRemovalTransaction.Result.Success)
        conn.rollback()
        conn.autoCommit = true
        val plot = PlotRepository.readAll(conn).single()
        assertEquals(0L, plot.geometryRevision)
        assertEquals(setOf(anchor, east), (plot.geometry as ChunkGeometry).chunks)
    }
}
