package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class PlotCacheLoaderTest {
    private fun databases(block: (Connection, String) -> Unit) {
        for (dialect in listOf("sqlite", "h2")) DriverManager.getConnection(
            if (dialect == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${UUID.randomUUID()}"
        ).use { c ->
            c.createStatement().use { s ->
                DatabaseSchema.statements(dialect).forEach { s.execute(it) }
                for (id in 1..2) s.execute("INSERT INTO plots VALUES ($id, '${UUID.randomUUID()}', ${id * 100}, 0, 64, 16, 'world', 'Home$id', 0)")
                s.execute("INSERT INTO plot_segments VALUES (1, 133, 0, 16)")
                s.execute("INSERT INTO plot_flags VALUES (1, 'build', 'false')")
                s.execute("INSERT INTO plot_members VALUES (1, '${UUID.randomUUID()}', 'member')")
            }
            block(c, dialect)
        }
    }

    @Test fun `full and targeted loads include extensions flags and members and restore connection state`() = databases { c, _ ->
        val isolation = c.transactionIsolation
        val full = PlotCacheLoader.load(c)
        assertEquals(2, full.plots.size)
        val single = PlotCacheLoader.load(c, 1)
        assertEquals(2178L, single.plots.single().area)
        assertEquals(1, single.flags[1]?.size)
        assertEquals(1, single.members[1]?.size)
        assertTrue(PlotCacheLoader.load(c, 999).plots.isEmpty())
        assertTrue(c.autoCommit); assertEquals(isolation, c.transactionIsolation)
    }

    @Test fun `SQL failure propagates instead of silently returning an empty cache`() = databases { c, _ ->
        c.createStatement().use { it.execute("DROP TABLE plot_flags") }
        assertThrows(java.sql.SQLException::class.java) { PlotCacheLoader.load(c) }
        assertTrue(c.autoCommit)
    }

    @Test fun `runtime loads exact chunk geometry instead of treating null radius as zero`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.createStatement().use { it.execute("UPDATE plots SET geometry_type='chunks', radius=NULL WHERE plot_id=1")
            it.execute("DELETE FROM plot_segments WHERE plot_id=1")
            it.execute("INSERT INTO plot_chunks VALUES (1, 'world', 6, 0)") }
        val plot = PlotCacheLoader.load(c, 1).plots.single()
        assertNull(plot.radius)
        assertEquals(256L, plot.area)
        assertTrue(plot.contains(111, 15))
        assertFalse(plot.contains(112, 15))
        assertTrue(c.autoCommit)
    }
    @Test fun `chunk offer loads persisted expansion level instead of guessing from area`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.createStatement().use {
            it.execute("UPDATE plots SET geometry_type='chunks', radius=NULL WHERE plot_id=1")
            it.execute("DELETE FROM plot_segments WHERE plot_id=1")
            it.execute("INSERT INTO plot_chunks VALUES (1, 'world', 6, 0)")
            it.execute("INSERT INTO plot_expansion_levels VALUES (1, 7)")
        }
        assertEquals(7, PlotCacheLoader.load(c, 1).plots.single().expansionLevel)
        assertEquals(7, PlotCacheLoader.load(c).plots.single { it.id == 1 }.expansionLevel)
        c.createStatement().use { it.execute("DELETE FROM plot_expansion_levels WHERE plot_id=1") }
        assertEquals(0, PlotCacheLoader.load(c, 1).plots.single().expansionLevel)
    }
    @Test fun `corrupt price level fails cache load instead of silently changing the quote`() = databases { c, _ ->
        c.createStatement().use { it.execute("INSERT INTO plot_expansion_levels VALUES (1, -1)") }
        assertThrows(IllegalArgumentException::class.java) { PlotCacheLoader.load(c) }
        assertTrue(c.autoCommit)
    }
}
