package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.*
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class ClaimTransactionTest {
    private val owner = UUID.randomUUID()
    private fun databases(test: (Connection) -> Unit) {
        listOf("sqlite" to "jdbc:sqlite::memory:", "h2" to "jdbc:h2:mem:${UUID.randomUUID()}").forEach { (type, url) ->
            DriverManager.getConnection(url).use { c ->
                DatabaseSchema.statements(type).forEach { sql -> c.createStatement().use { it.execute(sql) } }
                DatabaseMigrations.migrate(c, type)
                test(c)
            }
        }
    }
    private fun claim(c: Connection, shape: PlotGeometry, maxArea: Long = 10000,
                      maxChunks: Int = 64, flags: Map<String, Boolean> = mapOf("build" to false)) =
        ClaimTransaction.create(c, owner, owner, "world", shape.bounds.minX.toInt(), 64, shape.bounds.minZ.toInt(),
            shape, 10, maxArea, 32, maxChunks, "Home", flags)
    @Test fun `chunk claim stores flags and exact negative boundaries and counts area`() = databases { c ->
        val shape = ChunkGeometry(setOf(ChunkPosition(-1, -1)))
        assertTrue(claim(c, shape) is ClaimTransaction.Result.Success)
        val loaded = PlotCacheLoader.load(c)
        val plot = loaded.plots.single()
        assertNull(plot.radius)
        assertTrue(plot.contains(-16, -1)); assertFalse(plot.contains(0, -1))
        assertEquals(256L, plot.geometry.area)
        assertEquals("false", loaded.flags[plot.id]!!.single().value)
        assertEquals(ClaimTransaction.Result.AreaLimit, claim(c, ChunkGeometry(setOf(ChunkPosition(1, 0))), 511))
        assertEquals(ClaimTransaction.Result.ChunkLimit, claim(c, ChunkGeometry(setOf(ChunkPosition(1, 0))), maxChunks = 1))
        assertEquals(1, PlotRepository.readAll(c).size)
    }
    @Test fun `mixed geometry rejects overlap in both directions`() = databases { c ->
        val chunk = ChunkGeometry(setOf(ChunkPosition(0, 0)))
        assertTrue(claim(c, chunk) is ClaimTransaction.Result.Success)
        // Classic repository anchors must be the segment centre.
        fun classic() = ClaimTransaction.create(c, owner, owner, "WORLD", 16, 64, 8,
            ClassicGeometry(PlotSegment(16, 8, 1)), 10, 10000, 32, 64, "Home", emptyMap())
        assertEquals(ClaimTransaction.Result.Overlap, classic())
        c.createStatement().use { it.executeUpdate("DELETE FROM plot_flags"); it.executeUpdate("DELETE FROM plot_logs"); it.executeUpdate("DELETE FROM plot_chunks"); it.executeUpdate("DELETE FROM plots") }
        assertTrue(classic() is ClaimTransaction.Result.Success)
        assertEquals(ClaimTransaction.Result.Overlap, claim(c, chunk))
    }
    @Test fun `failed flag batch rolls back geometry metadata and logs`() = databases { c ->
        c.createStatement().use { it.execute("DROP TABLE plot_flags") }
        assertThrows(java.sql.SQLException::class.java) { claim(c, ChunkGeometry(setOf(ChunkPosition(0, 0)))) }
        assertTrue(c.autoCommit)
        assertTrue(PlotRepository.readAll(c).isEmpty())
    }
    @Test fun `chunk transfer enforces recipient chunk limits independently of classic radius`() = databases { c ->
        val id = (claim(c, ChunkGeometry(setOf(ChunkPosition(0, 0)))) as ClaimTransaction.Result.Success).id
        val recipient = UUID.randomUUID()
        c.prepareStatement("INSERT INTO plot_members VALUES (?, ?, 'member')").use {
            it.setInt(1, id); it.setString(2, recipient.toString()); it.executeUpdate()
        }
        assertFalse(OwnershipTransfer.transfer(c, id, owner, recipient, 10, 0, 10000, 0, 64))
        assertFalse(OwnershipTransfer.transfer(c, id, owner, recipient, 10, 0, 10000, 32, 0))
        assertFalse(OwnershipTransfer.transfer(c, id, owner, recipient, 10, 0, 255, 32, 64))
        assertTrue(OwnershipTransfer.transfer(c, id, owner, recipient, 10, 0, 256, 1, 1))
    }
    @Test fun `coordinator serializes competing mixed claims and shared owner limits`() {
        for (overlap in listOf(true, false)) {
            val url = "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
            DriverManager.getConnection(url).use { c ->
                DatabaseSchema.statements("h2").forEach { sql -> c.createStatement().use { it.execute(sql) } }
                DatabaseMigrations.migrate(c, "h2")
                val coordinator = pl.syntaxdevteam.plotsx.protection.ProtectionCoordinator()
                val ready = java.util.concurrent.CountDownLatch(2)
                val start = java.util.concurrent.CountDownLatch(1)
                val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
                try {
                    val jobs = (0..1).map { index -> executor.submit<ClaimTransaction.Result> {
                        ready.countDown(); check(start.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        coordinator.mutate({}) {
                            DriverManager.getConnection(url).use { connection ->
                                val shape: PlotGeometry = if (index == 0) ChunkGeometry(setOf(ChunkPosition(0, 0)))
                                    else ClassicGeometry(PlotSegment(if (overlap) 15 else 100, 0, 1))
                                ClaimTransaction.create(connection, owner, owner, "world", if (index == 0) 0 else if (overlap) 15 else 100,
                                    64, 0, shape, if (overlap) 10 else 1, 10000, 32, 64, "home", emptyMap())
                            }
                        }
                    } }
                    assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown()
                    val results = jobs.map { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
                    assertEquals(1, results.count { it is ClaimTransaction.Result.Success })
                    assertTrue(results.contains(if (overlap) ClaimTransaction.Result.Overlap else ClaimTransaction.Result.PlotLimit))
                    assertEquals(1, PlotRepository.readAll(c).size)
                } finally { start.countDown(); executor.shutdownNow(); c.createStatement().use { it.execute("SHUTDOWN") } }
            }
        }
    }
}
