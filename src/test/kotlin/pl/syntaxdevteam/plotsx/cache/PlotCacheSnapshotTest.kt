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

    @Test fun `single plot metadata replacement retains unrelated models and updates every lookup atomically`() {
        val original = plot()
        val other = original.copy(id = 2, x = 100, name = "Other")
        val player = UUID.randomUUID()
        val old = PlotCacheSnapshot.create(listOf(original, other),
            mapOf(1 to listOf(PlotFlagData(1, "build", "false"))),
            mapOf(1 to listOf(PlotMemberData(1, player.toString(), "member"))))
        val next = old.withPlot(1, original.copy(name = "Renamed", ownerUuid = player),
            listOf(PlotFlagData(1, "build", "true")), listOf(PlotMemberData(1, player.toString(), "manager")))
        assertEquals("Renamed", next.at("world", 0, 0)?.name)
        assertEquals(player, next.atChunk("world", pl.syntaxdevteam.plotsx.geometry.ChunkPosition(0, 0))?.ownerUuid)
        assertSame(old.plots[2], next.at("world", 100, 0))
        assertEquals(false, old.flagValue(1, "build"))
        assertEquals(true, next.flagValue(1, "build"))
        assertEquals("member", old.member(1, player)?.memberRole)
        assertEquals("manager", next.member(1, player)?.memberRole)
        assertEquals("Home", old.at("world", 0, 0)?.name)
    }

    @Test fun `targeted geometry publication inserts moves and removes complete policies`() {
        val player = UUID.randomUUID()
        val first = PlotCacheSnapshot.create()
        val flags = mutableListOf(PlotFlagData(1, "build", "true"))
        val members = mutableListOf(PlotMemberData(1, player.toString(), "member"))
        val inserted = first.withPlot(1, plot(), flags, members)
        flags.clear(); members.clear()
        assertEquals(true, inserted.flagValue(1, "build"))
        assertNotNull(inserted.member(1, player))
        val moved = inserted.withPlot(1, plot().copy(x = 100, world = "nether"), emptyList(), emptyList())
        assertNull(moved.at("world", 0, 0))
        assertEquals(1, moved.at("NETHER", 100, 0)?.id)
        val removed = moved.withPlot(1, null, emptyList(), emptyList())
        assertNull(removed.at("nether", 100, 0))
        assertNull(removed.flagValue(1, "build")); assertNull(removed.member(1, player))
        assertNotNull(inserted.at("world", 0, 0)); assertNull(first.at("world", 0, 0))
    }

    @Test fun `policy indexes retain first-match and invalid-boolean fallback semantics`() {
        val player = UUID.randomUUID()
        val snapshot = PlotCacheSnapshot.create(listOf(plot()), mapOf(1 to listOf(
            PlotFlagData(1, "build", "invalid"), PlotFlagData(1, "build", "true"))), mapOf(1 to listOf(
            PlotMemberData(1, player.toString(), "member"), PlotMemberData(1, player.toString(), "manager"))))
        assertNull(snapshot.flagValue(1, "build"))
        assertNull(snapshot.flagValue(1, "unknown"))
        assertNull(snapshot.member(2, player))
        assertEquals("member", snapshot.member(1, player)?.memberRole)
    }
}
