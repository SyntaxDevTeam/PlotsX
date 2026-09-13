package pl.syntaxdevteam.plotsx.geometry

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.PlotSegment

class SpatialPlotIndexTest {
    private data class Entry(val id: Int, val world: String, val shape: PlotGeometry)
    private fun index(entries: List<Entry>, limit: Int = 1024) = SpatialPlotIndex(entries, { it.shape }, { it.world }, limit)

    @Test fun `mixed lookup matches exact geometry including holes and negative boundaries`() {
        val plots = listOf(
            Entry(1, "world", ClassicGeometry(PlotSegment(-40, 0, 8), listOf(PlotSegment(-23, 0, 8)))),
            Entry(2, "world", ChunkGeometry(listOf(ChunkPosition(0, 0), ChunkPosition(1, 0), ChunkPosition(0, -1)))),
            Entry(3, "nether", ChunkGeometry(listOf(ChunkPosition(0, 0))))
        )
        val index = index(plots)
        for (world in listOf("world", "WORLD", "nether", "missing"))
            for (x in -55..40) for (z in -20..20) {
                val expected = plots.firstOrNull { it.world.equals(world, true) && it.shape.contains(x, z) }
                assertEquals("$world $x $z", expected?.id, index.at(world, x, z)?.id)
            }
    }

    @Test fun `oversized classic regions use bounded indexing without losing exact containment`() {
        val giant = Entry(1, "world", ClassicGeometry(PlotSegment(0, 0, Int.MAX_VALUE)))
        val hole = Entry(2, "other", ClassicGeometry(PlotSegment(-1000, 0, 1), listOf(PlotSegment(1000, 0, 1))))
        val index = index(listOf(giant, hole), 1)
        assertEquals(1, index.at("world", Int.MAX_VALUE, Int.MAX_VALUE)?.id)
        assertNull(index.at("world", Int.MIN_VALUE, 0))
        assertNull(index.at("other", 0, 0))
        assertEquals(2, index.at("other", 1000, 0)?.id)
    }

    @Test fun `chunk lookup only examines local candidates`() {
        val plots = (0..999).map { Entry(it, "world", ChunkGeometry(listOf(ChunkPosition(it, 0)))) }
        var inspections = 0
        val index = SpatialPlotIndex(plots, { inspections++; it.shape }, { it.world })
        inspections = 0
        assertEquals(500, index.at("world", 8000, 0)?.id)
        assertEquals(1, inspections)
    }

    @Test fun `multiple classic plots can occupy disjoint pieces of one chunk`() {
        val plots = listOf(Entry(1, "world", ClassicGeometry(PlotSegment(2, 2, 1))),
            Entry(2, "world", ClassicGeometry(PlotSegment(12, 12, 1))))
        val index = index(plots)
        assertEquals(1, index.at("world", 2, 2)?.id)
        assertEquals(2, index.at("world", 12, 12)?.id)
        assertNull(index.at("world", 7, 7))
    }

    @Test fun `world identity does not fold accents and clearing input does not erase the index`() {
        val plots = mutableListOf(Entry(1, "world", ChunkGeometry(listOf(ChunkPosition(-1, -1)))),
            Entry(2, "wórld", ChunkGeometry(listOf(ChunkPosition(-1, -1)))))
        val index = index(plots)
        plots.clear()
        assertEquals(1, index.at("WORLD", -1, -1)?.id)
        assertEquals(2, index.at("WÓRLD", -1, -1)?.id)
    }
}
