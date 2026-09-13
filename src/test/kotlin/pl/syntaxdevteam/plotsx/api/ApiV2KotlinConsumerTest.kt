package pl.syntaxdevteam.plotsx.api

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ApiV2KotlinConsumerTest {
    @Test fun `consumer handles both shapes and holes explicitly`() {
        val chunk = PlotSnapshot(1, UUID.randomUUID(), "world", 0, 64, 0, null, "home", 0,
            chunks = listOf(ChunkSnapshot(0, 0), ChunkSnapshot(1, 0), ChunkSnapshot(0, 1)))
        assertEquals("chunks", chunk.geometryType)
        assertEquals(768L, chunk.area)
        assertTrue(chunk.contains("WORLD", 16, 0))
        assertFalse(chunk.contains("world", 16, 16))
        val classic = PlotSnapshot(2, UUID.randomUUID(), "world", 0, 64, 0, 1, "home", 0)
        assertEquals(9L, classic.area)
        assertNotNull(classic.radius)
    }
}
