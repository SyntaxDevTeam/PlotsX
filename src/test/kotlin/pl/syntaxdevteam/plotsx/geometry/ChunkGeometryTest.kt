package pl.syntaxdevteam.plotsx.geometry

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.ExpansionDirection
import pl.syntaxdevteam.plotsx.databases.PlotSegment

class ChunkGeometryTest {
    @Test fun `block conversion floors both coordinates including negative positions`() {
        val coordinates = mapOf(-17 to -2, -16 to -1, -1 to -1, 0 to 0, 15 to 0, 16 to 1)
        for ((x, cx) in coordinates) for ((z, cz) in coordinates) {
            assertEquals(ChunkPosition(cx, cz), ChunkPosition.atBlock(x, z))
        }
    }

    @Test fun `one chunk contains exactly its 256 block columns`() {
        for (position in listOf(ChunkPosition(0, 0), ChunkPosition(-1, -1))) {
            val plot = ChunkGeometry(listOf(position))
            assertEquals(256L, plot.area)
            val minX = plot.bounds.minX.toInt()
            val minZ = plot.bounds.minZ.toInt()
            var contained = 0
            for (x in minX - 1..minX + 16) for (z in minZ - 1..minZ + 16) {
                if (plot.contains(x, z)) contained++
            }
            assertEquals(256, contained)
            assertTrue(plot.contains(minX, minZ))
            assertTrue(plot.contains(minX + 15, minZ + 15))
            assertFalse(plot.contains(minX + 16, minZ))
            assertFalse(plot.contains(minX, minZ - 1))
        }
    }

    @Test fun `duplicate chunks do not inflate area and input mutation does not change geometry`() {
        val source = mutableListOf(ChunkPosition(0, 0), ChunkPosition(0, 0), ChunkPosition(1, 0))
        val plot = ChunkGeometry(source)
        source.clear()
        assertEquals(512L, plot.area)
        assertEquals(2, plot.chunks.size)
        assertTrue(plot.contains(31, 15))
        assertEquals(BlockBounds(0, 0, 31, 15), plot.bounds)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `published chunk set cannot be mutated`() {
        val plot = ChunkGeometry(listOf(ChunkPosition(0, 0)))
        (plot.chunks as MutableSet<ChunkPosition>).clear()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty geometry is rejected`() {
        ChunkGeometry(emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corner-only adjacency is rejected`() {
        ChunkGeometry(listOf(ChunkPosition(0, 0), ChunkPosition(1, 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `separate islands are rejected`() {
        ChunkGeometry(listOf(ChunkPosition(0, 0), ChunkPosition(1, 0), ChunkPosition(3, 0)))
    }

    @Test fun `L shape does not claim its missing corner`() {
        val plot = ChunkGeometry(listOf(ChunkPosition(0, 0), ChunkPosition(1, 0), ChunkPosition(0, -1)))
        assertEquals(768L, plot.area)
        assertTrue(plot.bounds.contains(16, -1))
        assertFalse(plot.contains(16, -1))
        assertTrue(plot.contains(15, -1))
        assertTrue(plot.contains(16, 0))
        assertEquals(3, plot.regions().count())
    }

    @Test fun `enclosed hole remains free for both chunk and classic candidates`() {
        val ring = ChunkGeometry((-1..1).flatMap { x -> (-1..1).map { z -> ChunkPosition(x, z) } }
            .filterNot { it == ChunkPosition(0, 0) })
        assertEquals(2048L, ring.area)
        assertFalse(ring.contains(8, 8))
        val hole = ChunkGeometry(listOf(ChunkPosition(0, 0)))
        val classicInHole = ClassicGeometry(PlotSegment(8, 8, 7))
        assertFalse(ring.intersects(hole))
        assertFalse(hole.intersects(ring))
        assertFalse(ring.intersects(classicInHole))
        assertFalse(classicInHole.intersects(ring))
    }

    @Test fun `mixed intersections detect a single column and all classic extensions`() {
        val chunk = ChunkGeometry(listOf(ChunkPosition(0, 0)))
        val touching = ClassicGeometry(PlotSegment(17, 8, 1)) // starts at 16, no shared block
        val overlapping = ClassicGeometry(PlotSegment(16, 16, 1)) // shares only (15, 15)
        val extension = ClassicGeometry(PlotSegment(100, 100, 1), listOf(PlotSegment(16, 16, 1)))
        assertFalse(chunk.intersects(touching))
        assertFalse(touching.intersects(chunk))
        assertTrue(chunk.intersects(overlapping))
        assertTrue(overlapping.intersects(chunk))
        assertTrue(chunk.intersects(extension))
        assertTrue(extension.intersects(chunk))
    }

    @Test fun `chunk intersections are based on actual coordinates in both size orders`() {
        val base = ChunkGeometry(listOf(ChunkPosition(0, 0)))
        val expanded = ChunkGeometry(listOf(ChunkPosition(0, 0), ChunkPosition(1, 0)))
        val adjacent = ChunkGeometry(listOf(ChunkPosition(-1, 0)))
        assertTrue(base.intersects(expanded))
        assertTrue(expanded.intersects(base))
        assertFalse(base.intersects(adjacent))
        assertFalse(adjacent.intersects(base))
    }

    @Test fun `expansions use the selected occupied source and never skip occupied targets`() {
        val origin = ChunkPosition(0, 0)
        val plot = ChunkGeometry(listOf(origin))
        for (direction in ExpansionDirection.entries) {
            val target = plot.expansion(direction, origin)!!
            assertEquals(ChunkPosition(direction.dx, direction.dz), target)
            val expanded = ChunkGeometry(plot.chunks + target)
            assertNull(expanded.expansion(direction, origin))
            assertEquals(ChunkPosition(direction.dx * 2, direction.dz * 2), expanded.expansion(direction, target))
            assertEquals(512L, expanded.area)
        }
        assertNull(plot.expansion(ExpansionDirection.EAST, ChunkPosition(5, 5)))
    }

    @Test fun `integer block extremes are represented exactly and outward expansion is rejected`() {
        val low = ChunkPosition.atBlock(Int.MIN_VALUE, Int.MIN_VALUE)
        val high = ChunkPosition.atBlock(Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(Int.MIN_VALUE.toLong(), low.bounds.minX)
        assertEquals(Int.MIN_VALUE.toLong(), low.bounds.minZ)
        assertEquals(Int.MAX_VALUE.toLong(), high.bounds.maxX)
        assertEquals(Int.MAX_VALUE.toLong(), high.bounds.maxZ)
        assertNull(low.neighbour(ExpansionDirection.WEST))
        assertNull(low.neighbour(ExpansionDirection.NORTH))
        assertNull(high.neighbour(ExpansionDirection.EAST))
        assertNull(high.neighbour(ExpansionDirection.SOUTH))
        assertTrue(ChunkGeometry(listOf(low)).contains(Int.MIN_VALUE, Int.MIN_VALUE))
        assertTrue(ChunkGeometry(listOf(high)).contains(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun `out of block range chunk coordinates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ChunkPosition(ChunkPosition.MAX_COORDINATE + 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ChunkPosition(0, ChunkPosition.MIN_COORDINATE - 1) }
    }
}
