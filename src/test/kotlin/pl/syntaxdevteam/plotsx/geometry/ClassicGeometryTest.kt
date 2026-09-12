package pl.syntaxdevteam.plotsx.geometry

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotSegment
import java.util.UUID

class ClassicGeometryTest {
    @Test fun `adapter preserves segment lookup and default classic area`() {
        val base = PlotSegment(0, 0, 16)
        val extension = PlotSegment(33, 0, 16)
        val geometry = ClassicGeometry(base, listOf(extension))
        assertEquals(2178L, geometry.area)
        assertEquals(BlockBounds(-16, -16, 49, 16), geometry.bounds)
        assertEquals(base, geometry.segmentAt(16, 0))
        assertEquals(extension, geometry.segmentAt(17, 0))
        assertNull(geometry.segmentAt(50, 0))
        assertEquals(2, geometry.regions().count())
    }

    @Test fun `classic bounds do not claim the gap between actual segments`() {
        val geometry = ClassicGeometry(PlotSegment(0, 0, 16), listOf(PlotSegment(33, 0, 16), PlotSegment(0, -33, 16)))
        val gap = ClassicGeometry(PlotSegment(33, -33, 16))
        assertTrue(geometry.bounds.intersects(gap.bounds))
        assertFalse(geometry.intersects(gap))
        assertFalse(gap.intersects(geometry))
    }

    @Test fun `bounds use long arithmetic and summed area still saturates`() {
        val geometry = ClassicGeometry(PlotSegment(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE))
        assertEquals(4294967294L, geometry.bounds.maxX)
        assertEquals(-4294967295L, geometry.bounds.minZ)
        assertEquals(Long.MAX_VALUE, geometry.area)
        val saturated = ClassicGeometry(PlotSegment(0, 0, 1), listOf(PlotSegment(0, 0, Int.MAX_VALUE)))
        assertEquals(Long.MAX_VALUE, saturated.area)
    }

    @Test fun `plot copies build independent geometry and preserve constructor contract`() {
        val extensions = mutableListOf(PlotSegment(33, 0, 16))
        val plot = PlotData(1, UUID.randomUUID(), 0, 0, 64, 16, "world", "Home", 0, extensions)
        extensions.clear()
        assertEquals(2178L, plot.area)
        assertTrue(plot.contains(33, 0))
        val copy = plot.copy(x = 100, extensions = emptyList())
        assertEquals(1089L, copy.area)
        assertFalse(copy.contains(33, 0))
        assertTrue(copy.contains(100, 0))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `published classic segments cannot be mutated`() {
        val geometry = ClassicGeometry(PlotSegment(0, 0, 16))
        (geometry.segments as MutableList<PlotSegment>).clear()
    }
}
