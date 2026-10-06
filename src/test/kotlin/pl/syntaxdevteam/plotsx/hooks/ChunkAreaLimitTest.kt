package pl.syntaxdevteam.plotsx.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkAreaLimitTest {
    @Test
    fun `chunk count raises an otherwise contradictory area limit`() {
        assertEquals(262_144L, effectiveChunkAreaLimit(16_641L, 1_024))
    }

    @Test
    fun `larger explicit area limit remains in force`() {
        assertEquals(500_000L, effectiveChunkAreaLimit(500_000L, 1_024))
    }

    @Test
    fun `maximum integer chunk limit does not overflow`() {
        assertEquals(549_755_813_632L, effectiveChunkAreaLimit(1L, Int.MAX_VALUE))
    }
}
