package pl.syntaxdevteam.plotsx.claiming

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry

class ClaimModeTest {
    @Test fun `missing mode preserves classic default`() {
        assertEquals(ClaimMode.CLASSIC, ClaimMode.fromSection(emptyMap<String, Any?>()))
    }

    @Test fun `mode accepts only documented names normalized independently of locale`() {
        assertEquals(ClaimMode.CLASSIC, ClaimMode.fromSection(mapOf("mode" to " CLASSIC ")))
        assertEquals(ClaimMode.CHUNKS, ClaimMode.fromSection(mapOf("mode" to " ChUnKs ")))
        assertEquals("classic", ClaimMode.CLASSIC.configValue)
        assertEquals("chunks", ClaimMode.CHUNKS.configValue)
    }

    @Test fun `explicit invalid values do not silently enable a different strategy`() {
        for (value in listOf(null, "", "chunk", "false", false, 1, listOf("chunks"), mapOf("type" to "chunks"))) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                ClaimMode.fromSection(mapOf("mode" to value))
            }
            assertTrue(error.message!!.contains("plots.claiming.mode"))
        }
    }

    @Test fun `classic candidate keeps configured radius and chunk candidate ignores it`() {
        val classic = ClaimGeometryFactory.create(ClaimMode.CLASSIC, 0, 0, 16)
        assertTrue(classic is ClassicGeometry)
        assertEquals(1089L, classic.area)
        assertTrue(classic.contains(-16, 16))
        val chunks = ClaimGeometryFactory.create(ClaimMode.CHUNKS, -1, 16, -100) as ChunkGeometry
        assertEquals(setOf(ChunkPosition(-1, 1)), chunks.chunks)
        assertEquals(256L, chunks.area)
        assertFalse(chunks.contains(0, 16))
        assertEquals(256L, ClaimGeometryFactory.create(ClaimMode.CHUNKS, 0, 0).area)
    }

    @Test fun `invalid or overflowing classic candidate is rejected`() {
        for (radius in listOf(null, -1, 0)) {
            assertThrows(IllegalArgumentException::class.java) {
                ClaimGeometryFactory.create(ClaimMode.CLASSIC, 0, 0, radius)
            }
        }
        for ((x, z) in listOf(Int.MAX_VALUE to 0, 0 to Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                ClaimGeometryFactory.create(ClaimMode.CLASSIC, x, z, 16)
            }
        }
    }
}
