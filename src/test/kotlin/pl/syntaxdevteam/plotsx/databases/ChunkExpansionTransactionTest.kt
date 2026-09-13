package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.*
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class ChunkExpansionTransactionTest {
    private val owner = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val source = ChunkPosition(-1, -1)
    private val target = ChunkPosition(0, -1)
    private val limits = ChunkExpansionTransaction.Limits(100000, 32, 64)

    private fun databases(action: (Connection, Int) -> Unit) {
        for ((type, url) in listOf("sqlite" to "jdbc:sqlite::memory:", "h2" to "jdbc:h2:mem:${UUID.randomUUID()}")) {
            DriverManager.getConnection(url).use { conn ->
                DatabaseSchema.statements(type).forEach { sql -> conn.createStatement().use { it.execute(sql) } }
                DatabaseMigrations.migrate(conn, type)
                val id = insert(conn, ChunkGeometry(setOf(source)))
                action(conn, id)
            }
        }
    }
    private fun insert(conn: Connection, geometry: PlotGeometry, world: String = "world", ownerId: UUID = owner): Int {
        conn.autoCommit = false
        val x = (geometry as? ClassicGeometry)?.base?.x ?: geometry.bounds.minX.toInt()
        val z = (geometry as? ClassicGeometry)?.base?.z ?: geometry.bounds.minZ.toInt()
        val id = PlotRepository.insert(conn, ownerId, world, UUID.randomUUID().toString(), x, 64, z, 0, geometry)
        conn.commit(); conn.autoCommit = true
        return id
    }
    private fun request(id: Int) = ChunkExpansionTransaction.Request(id, owner, actor, source, ExpansionDirection.EAST, target, 0)
    private fun level(conn: Connection, id: Int): Int? = conn.prepareStatement("SELECT expansion_level FROM plot_expansion_levels WHERE plot_id = ?").use {
        it.setInt(1, id); it.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
    }
    private fun unchanged(conn: Connection, id: Int) {
        val plot = PlotRepository.readAll(conn).single { it.id == id }
        assertEquals(0L, plot.geometryRevision)
        assertEquals(setOf(source), (plot.geometry as ChunkGeometry).chunks)
        assertNull(level(conn, id)); assertTrue(conn.autoCommit)
    }

    @Test fun `commits exact adjacent chunk revision counter and actor log together`() = databases { conn, id ->
        val isolation = conn.transactionIsolation
        val result = ChunkExpansionTransaction.expand(conn, request(id), limits)
        assertEquals(ChunkExpansionTransaction.Result.Success(target, 1, 1), result)
        val plot = PlotCacheLoader.load(conn).plots.single()
        assertNull(plot.radius); assertEquals(512L, plot.area)
        assertTrue(plot.contains(15, -1)); assertFalse(plot.contains(16, -1))
        assertEquals(1L, plot.geometryRevision); assertEquals(1, level(conn, id))
        assertEquals(isolation, conn.transactionIsolation)
        conn.createStatement().use { it.executeQuery("SELECT actor_uuid, action FROM plot_logs").use { rs ->
            assertTrue(rs.next()); assertEquals(actor.toString(), rs.getString(1))
            assertEquals("EXPAND_CHUNK:EAST:0,-1:1", rs.getString(2)); assertFalse(rs.next())
        } }
    }
    @Test fun `stale and repeated confirmations cannot add or count a second chunk`() = databases { conn, id ->
        assertEquals(ChunkExpansionTransaction.Result.StaleQuote,
            ChunkExpansionTransaction.expand(conn, request(id).copy(expectedRevision = 1), limits))
        unchanged(conn, id)
        ChunkExpansionTransaction.expand(conn, request(id), limits)
        assertEquals(ChunkExpansionTransaction.Result.StaleQuote, ChunkExpansionTransaction.expand(conn, request(id), limits))
        assertEquals(512L, PlotRepository.readAll(conn).single().geometry.area)
        assertEquals(1, level(conn, id))
    }
    @Test fun `rejects deleted transferred and classic plots`() = databases { conn, id ->
        assertEquals(ChunkExpansionTransaction.Result.PlotNotFound, ChunkExpansionTransaction.expand(conn, request(Int.MAX_VALUE), limits))
        assertEquals(ChunkExpansionTransaction.Result.NotOwner, ChunkExpansionTransaction.expand(conn, request(id).copy(owner = UUID.randomUUID()), limits))
        val classic = insert(conn, ClassicGeometry(PlotSegment(100, 100, 1)))
        assertEquals(ChunkExpansionTransaction.Result.WrongGeometry, ChunkExpansionTransaction.expand(conn, request(classic), limits))
        unchanged(conn, id)
    }
    @Test fun `rejects foreign source diagonal jumped and occupied target`() = databases { conn, id ->
        val invalid = listOf(
            request(id).copy(source = ChunkPosition(5, 5)),
            request(id).copy(expectedTarget = ChunkPosition(0, 0)),
            request(id).copy(expectedTarget = ChunkPosition(1, -1)),
            request(id).copy(expectedTarget = source)
        )
        invalid.forEach { assertEquals(ChunkExpansionTransaction.Result.InvalidTarget, ChunkExpansionTransaction.expand(conn, it, limits)) }
        unchanged(conn, id)
    }
    @Test fun `checks per plot and owner chunk limits and exact mixed area in other worlds`() = databases { conn, id ->
        insert(conn, ChunkGeometry(setOf(ChunkPosition(20, 20))), "another_world")
        insert(conn, ClassicGeometry(PlotSegment(100, 100, 1)), "another_world")
        assertEquals(ChunkExpansionTransaction.Result.PlotChunkLimit,
            ChunkExpansionTransaction.expand(conn, request(id), limits.copy(maxChunksPerPlot = 1)))
        assertEquals(ChunkExpansionTransaction.Result.OwnerChunkLimit,
            ChunkExpansionTransaction.expand(conn, request(id), limits.copy(maxOwnedChunks = 2)))
        assertEquals(ChunkExpansionTransaction.Result.AreaLimit,
            ChunkExpansionTransaction.expand(conn, request(id), limits.copy(maxArea = 776)))
        unchanged(conn, id)
        assertTrue(ChunkExpansionTransaction.expand(conn, request(id), limits.copy(maxArea = 777, maxOwnedChunks = 3, maxChunksPerPlot = 2))
            is ChunkExpansionTransaction.Result.Success)
    }
    @Test fun `mixed collision includes one column and ignores owner and world casing`() = databases { conn, id ->
        insert(conn, ClassicGeometry(PlotSegment(16, -8, 1)), "WORLD", UUID.randomUUID())
        assertEquals(ChunkExpansionTransaction.Result.Overlap, ChunkExpansionTransaction.expand(conn, request(id), limits))
        unchanged(conn, id)
    }
    @Test fun `chunk collision rejects own other plot but identical coordinates in another world do not collide`() = databases { conn, id ->
        insert(conn, ChunkGeometry(setOf(target)), "other_world")
        val other = insert(conn, ChunkGeometry(setOf(target)))
        assertEquals(ChunkExpansionTransaction.Result.Overlap, ChunkExpansionTransaction.expand(conn, request(id), limits))
        unchanged(conn, id)
        conn.createStatement().use { it.executeUpdate("DELETE FROM plot_chunks WHERE plot_id = $other"); it.executeUpdate("DELETE FROM plots WHERE plot_id = $other") }
        assertTrue(ChunkExpansionTransaction.expand(conn, request(id), limits) is ChunkExpansionTransaction.Result.Success)
    }
    @Test fun `late log failure rolls back chunk counter and revision`() = databases { conn, id ->
        conn.createStatement().use { it.execute("DROP TABLE plot_logs") }
        assertThrows(java.sql.SQLException::class.java) { ChunkExpansionTransaction.expand(conn, request(id), limits) }
        unchanged(conn, id)
    }
    @Test fun `caller transaction can roll back successful expansion with its future journal`() = databases { conn, id ->
        conn.autoCommit = false
        assertTrue(ChunkExpansionTransaction.applyInTransaction(conn, request(id), limits) is ChunkExpansionTransaction.Result.Success)
        assertFalse(conn.autoCommit)
        conn.rollback(); conn.autoCommit = true
        unchanged(conn, id)
    }
    @Test fun `imported multi chunk geometry initializes its existing expansion level once`() = databases { conn, _ ->
        val base = ChunkPosition(10, 10)
        val second = ChunkPosition(11, 10)
        val id = insert(conn, ChunkGeometry(setOf(base, second)))
        val firstRequest = request(id).copy(source = second, expectedTarget = ChunkPosition(12, 10))
        assertEquals(ChunkExpansionTransaction.Result.Success(ChunkPosition(12, 10), 1, 2),
            ChunkExpansionTransaction.expand(conn, firstRequest, limits))
        assertEquals(ChunkExpansionTransaction.Result.Success(ChunkPosition(13, 10), 2, 3),
            ChunkExpansionTransaction.expand(conn, firstRequest.copy(source = ChunkPosition(12, 10), expectedTarget = ChunkPosition(13, 10), expectedRevision = 1), limits))
    }
    @Test fun `revision overflow is rejected without wrapping into negative values`() = databases { conn, id ->
        conn.createStatement().use { it.executeUpdate("UPDATE plots SET geometry_revision = ${Long.MAX_VALUE} WHERE plot_id = $id") }
        assertEquals(ChunkExpansionTransaction.Result.StaleQuote,
            ChunkExpansionTransaction.expand(conn, request(id).copy(expectedRevision = Long.MAX_VALUE), limits))
        assertEquals(256L, PlotRepository.readAll(conn).single().geometry.area)
        assertNull(level(conn, id))
    }
    @Test fun `competing expansions of one revision publish exactly one new chunk`() {
        val url = "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url).use { conn ->
            DatabaseSchema.statements("h2").forEach { sql -> conn.createStatement().use { it.execute(sql) } }
            DatabaseMigrations.migrate(conn, "h2")
            val id = insert(conn, ChunkGeometry(setOf(source)))
            val coordinator = pl.syntaxdevteam.plotsx.protection.ProtectionCoordinator()
            val published = java.util.concurrent.atomic.AtomicReference(PlotCacheLoader.load(conn).plots)
            val ready = java.util.concurrent.CountDownLatch(2)
            val start = java.util.concurrent.CountDownLatch(1)
            val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val jobs = (0..1).map { index -> executor.submit<ChunkExpansionTransaction.Result> {
                    val offer = if (index == 0) request(id) else request(id).copy(direction = ExpansionDirection.NORTH,
                        expectedTarget = ChunkPosition(-1, -2))
                    ready.countDown(); check(start.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    coordinator.mutate({
                        DriverManager.getConnection(url).use { published.set(PlotCacheLoader.load(it).plots) }
                    }) {
                        DriverManager.getConnection(url).use { ChunkExpansionTransaction.expand(it, offer, limits) }
                    }
                } }
                assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown()
                val results = jobs.map { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
                assertEquals(1, results.count { it is ChunkExpansionTransaction.Result.Success })
                assertEquals(1, results.count { it == ChunkExpansionTransaction.Result.StaleQuote })
                assertEquals(1, level(conn, id)); assertEquals(1L, published.get().single().geometryRevision)
                assertEquals(512L, published.get().single().area)
                val winner = (results.single { it is ChunkExpansionTransaction.Result.Success } as ChunkExpansionTransaction.Result.Success).chunk
                assertTrue(published.get().single().contains(winner.bounds.minX.toInt(), winner.bounds.minZ.toInt()))
            } finally { start.countDown(); executor.shutdownNow(); conn.createStatement().use { it.execute("SHUTDOWN") } }
        }
    }
}
