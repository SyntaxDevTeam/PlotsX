package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.api.PlotRegionSnapshot
import pl.syntaxdevteam.plotsx.api.PlotSnapshot
import java.util.UUID

class PlotGeometryTest {
    private val base = PlotData(1, UUID.randomUUID(), 0, 0, 64, 16, "world", "Home", 0)

    @Test fun `north and east form L without claiming missing corner`() {
        val north = base.expansion(ExpansionDirection.NORTH)!!
        val east = base.expansion(ExpansionDirection.EAST)!!
        val plot = base.copy(extensions = listOf(north, east))
        assertEquals(3267L, plot.area)
        assertTrue(plot.contains(16, -17))
        assertTrue(plot.contains(17, 16))
        assertTrue(plot.contains(49, 16))
        assertFalse(plot.contains(17, -17))
        assertFalse(plot.contains(50, 0))
        assertEquals(16, plot.radius)
        val api = PlotSnapshot(plot.id, plot.ownerUuid, plot.world, plot.x, plot.y, plot.z,
            plot.radius, plot.name, plot.creationTime, plot.extensions.map { PlotRegionSnapshot(it.x, it.z, it.radius) })
        assertTrue(api.contains("world", 33, 0))
        assertFalse(api.contains("world", 33, -33))
        assertFalse(api.contains("other", 33, 0))
    }

    @Test fun `each direction adds adjacent fixed size segments`() {
        ExpansionDirection.entries.forEach { direction ->
            val first = base.expansion(direction)!!
            val expanded = base.copy(extensions = listOf(first))
            val second = expanded.expansion(direction)!!
            assertEquals(33 * direction.dx, first.x)
            assertEquals(33 * direction.dz, first.z)
            assertEquals(66 * direction.dx, second.x)
            assertEquals(66 * direction.dz, second.z)
            assertEquals(base.area, second.area)
            assertEquals(2178L, expanded.area)
        }
    }

    @Test fun `coordinate overflow rejects expansion`() {
        assertNull(base.copy(x = Int.MAX_VALUE - 16).expansion(ExpansionDirection.EAST))
        assertNull(base.copy(z = Int.MIN_VALUE + 16).expansion(ExpansionDirection.NORTH))
    }
}
