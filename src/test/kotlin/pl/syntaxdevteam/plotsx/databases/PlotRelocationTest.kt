package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.*
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class PlotRelocationTest {
    private fun databases(test: (Connection) -> Unit) {
        listOf("sqlite" to "jdbc:sqlite::memory:", "h2" to "jdbc:h2:mem:${UUID.randomUUID()}").forEach { (type, url) ->
            DriverManager.getConnection(url).use { c ->
                DatabaseSchema.statements(type).forEach { sql -> c.createStatement().use { it.execute(sql) } }
                DatabaseMigrations.migrate(c, type)
                PlotSpawnRepository.migrate(c)
                test(c)
            }
        }
    }
    private fun insert(c: Connection, shape: PlotGeometry, x: Int, z: Int): PlotData {
        val owner = UUID.randomUUID()
        val id = (ClaimTransaction.create(c, owner, owner, "world", x, 64, z, shape,
            10, Long.MAX_VALUE, 64, 64, "Home", mapOf("build" to false)) as ClaimTransaction.Result.Success).id
        return PlotRepository.readAll(c, id).single().toPlotData()
    }

    @Test fun `classic migration retains extensions identity flags and members and resets spawn`() = databases { c ->
        val plot = insert(c, ClassicGeometry(PlotSegment(0, 0, 2), listOf(PlotSegment(5, 0, 2))), 0, 0)
        c.createStatement().use {
            it.executeUpdate("INSERT INTO plot_spawns VALUES (${plot.id}, 0, 70, 0)")
            it.executeUpdate("INSERT INTO plot_members VALUES (${plot.id}, '${UUID.randomUUID()}', 'manager')")
        }
        val before = PlotCacheLoader.load(c)
        assertEquals(PlotRelocation.Result.SUCCESS, PlotRelocation.move(c, plot, PlotRelocation.Target("other", 100, 80, -100), UUID.randomUUID()))
        val after = PlotCacheLoader.load(c)
        val moved = after.plots.single()
        assertEquals(plot.id, moved.id); assertEquals(plot.ownerUuid, moved.ownerUuid)
        assertEquals(plot.area, moved.area); assertEquals(plot.name, moved.name)
        assertEquals(listOf(PlotSegment(105, -100, 2)), moved.extensions)
        assertEquals(1L, moved.geometryRevision); assertNull(moved.teleportSpawn)
        assertEquals(before.flags, after.flags); assertEquals(before.members, after.members)
        assertEquals(PlotRelocation.Result.STALE, PlotRelocation.move(c, plot, PlotRelocation.Target("world", 200, 64, 200), UUID.randomUUID()))
    }

    @Test fun `chunk migration preserves layout across negative coordinates and rejects collisions`() = databases { c ->
        val plot = insert(c, ChunkGeometry(setOf(ChunkPosition(-1, -1), ChunkPosition(0, -1))), -3, -2)
        insert(c, ClassicGeometry(PlotSegment(16, 8, 1)), 16, 8)
        val actor = UUID.randomUUID()
        assertEquals(PlotRelocation.Result.COLLISION, PlotRelocation.move(c, plot, PlotRelocation.Target("WORLD", 1, 64, 1), actor))
        assertEquals(plot.chunks, PlotRepository.readAll(c, plot.id).single().toPlotData().chunks)
        assertEquals(PlotRelocation.Result.SUCCESS, PlotRelocation.move(c, plot, PlotRelocation.Target("other", 1, 70, 1), actor))
        val moved = PlotRepository.readAll(c, plot.id).single().toPlotData()
        assertEquals(setOf(ChunkPosition(0, 0), ChunkPosition(1, 0)), moved.chunks)
        assertEquals(13, moved.x); assertEquals(14, moved.z)
    }

    @Test fun `failure writing log rolls back relocation and geometry`() = databases { c ->
        val plot = insert(c, ChunkGeometry(setOf(ChunkPosition(0, 0))), 0, 0)
        c.createStatement().use { it.execute("DROP TABLE plot_logs") }
        assertThrows(java.sql.SQLException::class.java) {
            PlotRelocation.move(c, plot, PlotRelocation.Target("other", 32, 64, 32), UUID.randomUUID())
        }
        assertTrue(c.autoCommit)
        assertEquals(plot, PlotRepository.readAll(c, plot.id).single().toPlotData())
    }
}
