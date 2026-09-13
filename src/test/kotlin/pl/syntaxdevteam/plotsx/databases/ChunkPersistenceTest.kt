package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class ChunkPersistenceTest {
    private val owner = UUID.randomUUID()
    private val servers = mapOf(
        "mariadb" to System.getenv("PLOTSX_TEST_MARIADB_URL"),
        "mysql" to System.getenv("PLOTSX_TEST_MYSQL_URL"),
        "postgresql" to System.getenv("PLOTSX_TEST_POSTGRESQL_URL")
    ).filterValues { !it.isNullOrBlank() }
    private val dialects = listOf("sqlite", "h2") + servers.keys
    private fun connect(type: String): Connection {
        if (type in servers) {
            val namespace = "plotsx_test_" + UUID.randomUUID().toString().replace("-", "")
            val url = servers.getValue(type)
            fun connection() = if (type == "postgresql") DriverManager.getConnection(url)
                else DriverManager.getConnection(url, "root", "")
            val control = connection()
            val objectType = if (type == "postgresql") "SCHEMA" else "DATABASE"
            control.createStatement().use { it.execute("CREATE $objectType $namespace") }
            val actual = connection()
            if (type == "postgresql") actual.schema = namespace else actual.catalog = namespace
            return object : Connection by actual {
                override fun close() {
                    try { actual.close() } finally {
                        control.use { it.createStatement().use { stmt ->
                            stmt.execute("DROP $objectType $namespace" + if (type == "postgresql") " CASCADE" else "")
                        } }
                    }
                }
            }
        }
        return DriverManager.getConnection(
            if (type == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${UUID.randomUUID()}"
        ).also { if (type == "sqlite") it.createStatement().use { stmt -> stmt.execute("PRAGMA foreign_keys=ON") } }
    }

    private fun databases(test: (Connection, String) -> Unit) {
        for (dialect in dialects) connect(dialect).use { test(it, dialect) }
    }

    private fun execute(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }
    private fun count(c: Connection, table: String): Int = c.createStatement().use { stmt ->
        stmt.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getInt(1) }
    }
    private fun legacy(c: Connection, dialect: String) {
        DatabaseSchema.statements(dialect).forEach { execute(c, it) }
        execute(c, "INSERT INTO plots VALUES (42, '$owner', 0, 0, 64, 16, 'world', 'Dom', 123456)")
        execute(c, "INSERT INTO plot_segments VALUES (42, 33, 0, 16)")
        execute(c, "INSERT INTO plot_members VALUES (42, '$owner', 'member')")
        execute(c, "INSERT INTO plot_flags VALUES (42, 'build', 'true')")
        execute(c, "INSERT INTO plot_expansion_levels VALUES (42, 2)")
        execute(c, "INSERT INTO plot_logs VALUES (60, 42, 'CREATE', '$owner', 123456)")
    }

    private fun insertChunk(c: Connection, cx: Int = -1, cz: Int = -1, world: String = "world"): Int {
        return PlotRepository.insert(c, owner, world, "Chunk", cx * 16, 64, cz * 16, 123456,
            ChunkGeometry(listOf(ChunkPosition(cx, cz))))
    }

    @Test fun `legacy migration is repeatable and preserves all metadata and relationships`() = databases { c, dialect ->
        legacy(c, dialect)
        repeat(2) { DatabaseMigrations.migrate(c, dialect) }
        assertTrue(c.autoCommit)
        assertTrue(DatabaseMigrations.isChunkSchema(c))
        val plot = PlotRepository.readAll(c).single()
        assertEquals(42, plot.id)
        assertEquals(owner, plot.ownerUuid)
        assertEquals("Dom", plot.name)
        assertEquals(123456L, plot.creationTime)
        assertEquals(64, plot.y)
        assertEquals(0L, plot.geometryRevision)
        assertEquals(2178L, plot.geometry.area)
        assertTrue(plot.geometry.contains(49, 16))
        assertEquals(1, count(c, "schema_migrations"))
        for (table in listOf("plot_members", "plot_flags", "plot_logs", "plot_expansion_levels", "plot_segments")) assertEquals(1, count(c, table))
        if (dialect == "sqlite") c.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA foreign_keys").use { it.next(); assertEquals(1, it.getInt(1)) }
        }
    }

    @Test fun `fresh schema stores mixed geometry with null chunk radius and normalized world key`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.autoCommit = false
        val classic = PlotRepository.insert(c, owner, "world", "Classic", 100, 70, 100, 10,
            ClassicGeometry(PlotSegment(100, 100, 16), listOf(PlotSegment(133, 100, 16))))
        val chunk = insertChunk(c, world = "World")
        c.commit(); c.autoCommit = true
        val plots = PlotRepository.readAll(c)
        assertEquals(2, plots.size)
        assertEquals(2178L, plots.first { it.id == classic }.geometry.area)
        assertEquals(256L, plots.first { it.id == chunk }.geometry.area)
        assertTrue(plots.first { it.id == chunk }.geometry.contains(-1, -1))
        c.createStatement().use { stmt ->
            stmt.executeQuery("SELECT radius, geometry_type FROM plots WHERE plot_id=$chunk").use {
                it.next(); assertNull(it.getObject(1)); assertEquals("chunks", it.getString(2))
            }
            stmt.executeQuery("SELECT world_key FROM plot_chunks").use { it.next(); assertEquals("world", it.getString(1)) }
        }
        PlotGeometryRepository.validateNoOverlaps(PlotGeometryRepository.readAll(c))
    }

    @Test fun `duplicate chunk insert rolls back its metadata and preserves the callers transaction`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.autoCommit = false
        val first = insertChunk(c)
        assertThrows(Exception::class.java) { insertChunk(c, world = "WORLD") }
        // The first entry of this batch is free; the second conflicts. Neither may survive.
        assertThrows(Exception::class.java) {
            PlotRepository.insert(c, owner, "world", "Partial batch", -32, 64, -16, 123456,
                ChunkGeometry(listOf(ChunkPosition(-2, -1), ChunkPosition(-1, -1))))
        }
        assertFalse(c.autoCommit)
        assertEquals(1, count(c, "plots"))
        assertEquals(1, count(c, "plot_chunks"))
        c.commit(); c.autoCommit = true
        assertEquals(first, PlotRepository.readAll(c).single().id)
    }

    @Test fun `outer rollback removes both metadata and chunks`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        assertThrows(IllegalArgumentException::class.java) { insertChunk(c) }
        c.autoCommit = false
        insertChunk(c)
        c.rollback(); c.autoCommit = true
        assertEquals(0, count(c, "plots")); assertEquals(0, count(c, "plot_chunks"))
    }

    @Test fun `distinct accented world names do not collide under server default collation`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.autoCommit = false
        insertChunk(c, world = "world")
        insertChunk(c, world = "wórld")
        c.commit(); c.autoCommit = true
        assertEquals(2, count(c, "plot_chunks"))
        PlotGeometryRepository.validateNoOverlaps(PlotGeometryRepository.readAll(c))
    }

    @Test fun `deleting a chunk plot cascades all geometry and dependent records`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.autoCommit = false
        val id = insertChunk(c)
        execute(c, "INSERT INTO plot_members VALUES ($id, '$owner', 'member')")
        execute(c, "INSERT INTO plot_flags VALUES ($id, 'build', 'true')")
        execute(c, "INSERT INTO plot_expansion_levels VALUES ($id, 0)")
        c.commit(); c.autoCommit = true
        execute(c, "DELETE FROM plots WHERE plot_id=$id")
        for (table in listOf("plots", "plot_chunks", "plot_members", "plot_flags", "plot_expansion_levels")) assertEquals(0, count(c, table))
    }

    @Test fun `partial server migration resumes and unknown versions are rejected`() = databases { c, dialect ->
        legacy(c, dialect)
        execute(c, "ALTER TABLE plots ADD COLUMN geometry_type VARCHAR(16) NOT NULL DEFAULT 'classic'")
        DatabaseMigrations.migrate(c, dialect)
        assertEquals(2178L, PlotRepository.readAll(c).single().geometry.area)
        execute(c, "INSERT INTO schema_migrations VALUES (999, 'future')")
        assertThrows(IllegalArgumentException::class.java) { DatabaseMigrations.migrate(c, dialect) }
        assertEquals(1, count(c, "plots"))
    }

    @Test fun `invalid persisted geometry is rejected rather than reinterpreted as classic`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.autoCommit = false
        val id = insertChunk(c)
        c.commit(); c.autoCommit = true
        val corruptions = listOf(
            "UPDATE plots SET geometry_type='unknown' WHERE plot_id=$id",
            "UPDATE plots SET geometry_revision=-1 WHERE plot_id=$id",
            "UPDATE plots SET radius=8 WHERE plot_id=$id",
            "UPDATE plot_chunks SET world_key='other' WHERE plot_id=$id",
            "DELETE FROM plot_chunks WHERE plot_id=$id",
            "INSERT INTO plot_chunks VALUES ($id, 'world', 5, 5)",
            "INSERT INTO plot_segments VALUES ($id, 0, 0, 16)"
        )
        for (sql in corruptions) {
            c.autoCommit = false
            execute(c, sql)
            assertThrows(Exception::class.java) { PlotGeometryRepository.readAll(c) }
            c.rollback(); c.autoCommit = true
        }
    }

    @Test fun `mixed overlaps are found independently of world name casing`() = databases { c, dialect ->
        DatabaseMigrations.migrate(c, dialect)
        c.autoCommit = false
        insertChunk(c, 0, 0)
        PlotRepository.insert(c, owner, "WORLD", "Overlap", 16, 64, 16, 0, ClassicGeometry(PlotSegment(16, 16, 1)))
        assertThrows(IllegalArgumentException::class.java) {
            PlotGeometryRepository.validateNoOverlaps(PlotGeometryRepository.readAll(c))
        }
        c.rollback(); c.autoCommit = true
    }

    @Test fun `v2 backups round trip mixed data across available engines and reject runtime chunk imports`() {
        for (source in dialects) for (target in dialects) {
            val directory = Files.createTempDirectory("plotsx-chunks-backup").toFile()
            try {
                connect(source).use { c ->
                    legacy(c, source)
                    DatabaseMigrations.migrate(c, source)
                    c.autoCommit = false
                    insertChunk(c, -10, -10)
                    c.commit(); c.autoCommit = true
                    val file = SqlBackup.export(c, SqlBackup.dialect(target), directory)
                    assertTrue(file.readLines().first().contains("v2 dialect=${SqlBackup.dialect(target)}"))
                    assertTrue(c.autoCommit)
                    connect(target).use { restored ->
                        repeat(2) { SqlBackup.restore(restored, SqlBackup.dialect(target), file, allowChunkPlots = true) }
                        assertEquals(2, PlotRepository.readAll(restored).size)
                        assertEquals(1, count(restored, "plot_chunks"))
                        assertEquals(1, count(restored, "plot_segments"))
                        assertEquals(1, count(restored, "plot_members"))
                        assertEquals(1, count(restored, "schema_migrations"))
                        assertThrows(IllegalArgumentException::class.java) { SqlBackup.restore(restored, SqlBackup.dialect(target), file, allowChunkPlots = false) }
                        assertEquals(2, PlotRepository.readAll(restored).size)
                        assertTrue(restored.autoCommit)
                        restored.autoCommit = false
                        val next = insertChunk(restored, -20, -20)
                        assertTrue(next > 42)
                        restored.commit(); restored.autoCommit = true
                    }
                }
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun `legacy backup replaces mixed geometry and supplies classic defaults`() = databases { c, dialect ->
        val directory = Files.createTempDirectory("plotsx-chunks-legacy").toFile()
        try {
            legacy(c, dialect)
            val file = SqlBackup.export(c, SqlBackup.dialect(dialect), directory)
            DatabaseMigrations.migrate(c, dialect)
            c.autoCommit = false
            insertChunk(c, -10, -10)
            c.commit(); c.autoCommit = true
            SqlBackup.restore(c, SqlBackup.dialect(dialect), file)
            val plot = PlotRepository.readAll(c).single()
            assertTrue(plot.geometry is ClassicGeometry)
            assertEquals(0L, plot.geometryRevision)
            assertEquals(0, count(c, "plot_chunks"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun `invalid v2 import restores previous data and connection state`() = databases { c, dialect ->
        val directory = Files.createTempDirectory("plotsx-chunks-invalid").toFile()
        try {
            DatabaseMigrations.migrate(c, dialect)
            c.autoCommit = false
            insertChunk(c)
            c.commit(); c.autoCommit = true
            val file = SqlBackup.export(c, SqlBackup.dialect(dialect), directory)
            val original = file.readText()
            for (invalid in listOf(
                original.lineSequence().filterNot { it.startsWith("INSERT INTO plot_chunks ") }.joinToString("\n"),
                original.replace("COMMIT;", "UPDATE plots SET radius=8;\nCOMMIT;"),
                original.replace("COMMIT;", "INSERT INTO missing_table VALUES (1);\nCOMMIT;")
            )) {
                file.writeText(invalid)
                assertThrows(Exception::class.java) { SqlBackup.restore(c, SqlBackup.dialect(dialect), file, allowChunkPlots = true) }
                assertTrue(c.autoCommit)
                assertEquals(256L, PlotRepository.readAll(c).single().geometry.area)
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun `SQLite migration rollback preserves old schema and autoincrement high water mark`() {
        connect("sqlite").use { c ->
            legacy(c, "sqlite")
            execute(c, "INSERT INTO plots VALUES (1000, '$owner', 200, 0, 64, 16, 'world', 'deleted', 0)")
            execute(c, "DELETE FROM plots WHERE plot_id=1000")
            execute(c, "UPDATE plots SET radius=-1 WHERE plot_id=42")
            assertThrows(IllegalArgumentException::class.java) { DatabaseMigrations.migrate(c, "sqlite") }
            assertFalse(DatabaseMigrations.isChunkSchema(c))
            assertTrue(c.autoCommit)
            assertEquals(1, count(c, "plot_members"))
            execute(c, "UPDATE plots SET radius=16 WHERE plot_id=42")
            DatabaseMigrations.migrate(c, "sqlite")
            c.autoCommit = false
            assertTrue(insertChunk(c, -10, -10) > 1000)
            c.commit(); c.autoCommit = true
        }
    }

    @Test fun `migration finishes when type columns exist but radius is still required`() = databases { c, dialect ->
        legacy(c, dialect)
        execute(c, "ALTER TABLE plots ADD COLUMN geometry_type VARCHAR(16) NOT NULL DEFAULT 'classic'")
        execute(c, "ALTER TABLE plots ADD COLUMN geometry_revision BIGINT NOT NULL DEFAULT 0")
        DatabaseMigrations.migrate(c, dialect)
        c.autoCommit = false
        insertChunk(c, -10, -10)
        c.commit(); c.autoCommit = true
        assertEquals(2, PlotRepository.readAll(c).size)
    }

    @Test fun `orphan geometry is detected even when foreign keys were disabled during external import`() {
        connect("sqlite").use { c ->
            DatabaseMigrations.migrate(c, "sqlite")
            execute(c, "PRAGMA foreign_keys=OFF")
            execute(c, "INSERT INTO plot_chunks VALUES (999, 'world', 0, 0)")
            assertThrows(IllegalArgumentException::class.java) { PlotGeometryRepository.readAll(c) }
        }
    }
}
