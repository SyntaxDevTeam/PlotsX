package pl.syntaxdevteam.plotsx.visualization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.util.UUID

class PlotBorderVisualizerTest {
    @Test
    fun `adjacent chunks expose only their outer perimeter`() {
        val plot = PlotData(1, UUID.randomUUID(), 0, 0, 64, null, "world", "plot", 0,
            chunks = setOf(ChunkPosition(0, 0), ChunkPosition(1, 0)))

        val points = BorderOutline.create(plot, 1).toSet()

        assertEquals(92, points.size)
        assertFalse(BorderOutline.Point(15, 8) in points)
        assertFalse(BorderOutline.Point(16, 8) in points)
    }

    @Test
    fun `sampling scales with perimeter rather than chunk area`() {
        val plot = PlotData(1, UUID.randomUUID(), 0, 0, 64, null, "world", "plot", 0,
            chunks = (0 until 4).flatMap { x -> (0 until 4).map { z -> ChunkPosition(x, z) } }.toSet())

        assertEquals(127, BorderOutline.create(plot, 2).toSet().size)
    }
}
