package pl.syntaxdevteam.plotsx.cache

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData
import pl.syntaxdevteam.plotsx.databases.PlotMemberData
import pl.syntaxdevteam.plotsx.databases.PlotSegment
import java.util.UUID

class PlotCacheSnapshotTest {
    private fun plot() = PlotData(1, UUID.randomUUID(), 0, 0, 64, 16, "world", "Home", 0)

    @Test fun `maps and lists are immutable and detached from loader collections`() {
        val plots = mutableListOf(plot())
        val flags = mutableListOf(PlotFlagData(1, "build", "false"))
        val members = mutableListOf(PlotMemberData(1, UUID.randomUUID().toString(), "member"))
        val snapshot = PlotCacheSnapshot.create(plots, mapOf(1 to flags), mapOf(1 to members))
        plots.clear(); flags.clear(); members.clear()
        assertEquals(1, snapshot.at("world", 0, 0)?.id)
        assertEquals(1, snapshot.flags[1]?.size)
        assertEquals(1, snapshot.members[1]?.size)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.plots as MutableMap).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.flags[1] as MutableList).clear() }
    }

    @Test fun `replacement updates lookup and removes orphan metadata without altering retained snapshots`() {
        val original = plot()
        val old = PlotCacheSnapshot.create(listOf(original), mapOf(1 to listOf(PlotFlagData(1, "build", "false"))))
        val expanded = PlotCacheSnapshot.create(listOf(original.copy(extensions = listOf(PlotSegment(33, 0, 16)))))
        assertNull(old.at("world", 33, 0))
        assertEquals(1, expanded.at("world", 33, 0)?.id)
        val removed = PlotCacheSnapshot.create(emptyList(), old.flags, old.members)
        assertTrue(removed.flags.isEmpty()); assertNull(removed.at("world", 0, 0))
        assertEquals(1, old.at("world", 0, 0)?.id)
    }

    @Test fun `metadata-only update reuses geometry and preserves the previous policy snapshot`() {
        val old = PlotCacheSnapshot.create(listOf(plot()), mapOf(1 to listOf(PlotFlagData(1, "build", "false"))))
        val next = old.withMetadata(mapOf(1 to listOf(PlotFlagData(1, "build", "true"))), emptyMap())
        assertSame(old.at("world", 0, 0), next.at("world", 0, 0))
        assertEquals("false", old.flags[1]?.single()?.value)
        assertEquals("true", next.flags[1]?.single()?.value)
    }
}
