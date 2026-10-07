package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager
import pl.syntaxdevteam.plotsx.identity.IdentityMigrationStore

class SqlBackupTest {
    @Test fun roundTripAndMigration() {
        for (source in listOf("sqlite", "h2")) for (target in listOf("sqlite", "h2")) {
            val directory = Files.createTempDirectory("plotsx-backup-test").toFile()
            fun connect(type: String) = DriverManager.getConnection(if (type == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${java.util.UUID.randomUUID()}")
            val text = "Zażółć 🏠 ' ;\n-- comment\n\\path\r\n"
            try {
                connect(source).use { conn ->
                    conn.createStatement().use { s ->
                        DatabaseSchema.statements(source).forEach { s.execute(it) }
                        s.execute("INSERT INTO plots VALUES (42, 'owner', 1, 2, 3, 4, 'world', 'name', 12345)")
                        s.execute("INSERT INTO plot_segments VALUES (42, 10, 2, 4)")
                        s.execute("INSERT INTO plot_expansion_levels VALUES (42, 3)")
                        s.execute("INSERT INTO plot_members VALUES (42, 'member', 'trusted')")
                        s.execute("INSERT INTO plot_flags VALUES (42, 'build', 'true')")
                        s.execute("INSERT INTO plot_logs VALUES (60, 42, 'CREATE', 'owner', 12345)")
                    }
                    conn.prepareStatement("UPDATE plots SET name = ?").use { it.setString(1, text); it.executeUpdate() }
                    val file = SqlBackup.export(conn, target, directory)
                    connect(target).use { restored ->
                        if (target == "sqlite") restored.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                        repeat(2) { SqlBackup.restore(restored, target, file) }
                        restored.createStatement().use { s ->
                            for (table in listOf("plots", "plot_segments", "plot_expansion_levels", "plot_members", "plot_flags", "plot_logs")) {
                                s.executeQuery("SELECT COUNT(*) FROM $table").use { assertTrue(it.next()); assertEquals(1, it.getInt(1)) }
                            }
                            s.executeQuery("SELECT name FROM plots").use { it.next(); assertEquals(text, it.getString(1)) }
                            s.execute("INSERT INTO plots (owner_uuid,x,z,y,radius,world,name,creation_time) VALUES ('next',5,6,7,8,'world','new',12345)")
                            s.executeQuery("SELECT MAX(plot_id) FROM plots").use { it.next(); assertTrue(it.getInt(1) > 42) }
                        }
                        // A failure after DELETE and INSERT must restore the previous data.
                        val valid = file.readText()
                        file.writeText(valid.replace("COMMIT;", "INSERT INTO missing_table VALUES (1);\nCOMMIT;"))
                        assertThrows(Exception::class.java) { SqlBackup.restore(restored, target, file) }
                        restored.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM plots").use { it.next(); assertEquals(2, it.getInt(1)) } }
                        file.writeText(valid.replace("dialect=$target", "dialect=wrong"))
                        assertThrows(IllegalArgumentException::class.java) { SqlBackup.restore(restored, target, file) }
                    }
                }
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun importsBackupFromBeforeSegments() {
        val directory = Files.createTempDirectory("plotsx-legacy-test").toFile()
        try {
            for (dialect in listOf("sqlite", "h2")) {
                val url = if (dialect == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${java.util.UUID.randomUUID()}"
                DriverManager.getConnection(url).use { conn ->
                    conn.createStatement().use { stmt ->
                        DatabaseSchema.statements(dialect).forEach { stmt.execute(it) }
                        stmt.execute("INSERT INTO plots VALUES (1, 'owner', 0, 0, 64, 16, 'world', 'home', 0)")
                    }
                    val file = SqlBackup.export(conn, dialect, directory)
                    file.writeText(file.readLines().filterNot { it.contains("plot_segments") }.joinToString("\n", postfix = "\n"))
                    conn.createStatement().use { it.execute("INSERT INTO plot_segments VALUES (1, 33, 0, 16)") }
                    SqlBackup.restore(conn, dialect, file)
                    conn.createStatement().use { stmt ->
                        stmt.executeQuery("SELECT COUNT(*) FROM plot_segments").use { it.next(); assertEquals(0, it.getInt(1)) }
                        stmt.executeQuery("SELECT radius FROM plots").use { it.next(); assertEquals(16, it.getInt(1)) }
                    }
                }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun emptyBackupHasAllTables() {
        val directory = Files.createTempDirectory("plotsx-empty-test").toFile()
        try {
            DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
                conn.createStatement().use { s -> DatabaseSchema.statements("sqlite").forEach { s.execute(it) } }
                for (dialect in SqlBackup.dialects) {
                    val normalized = SqlBackup.dialect(dialect)
                    val file = SqlBackup.export(conn, normalized, directory)
                    assertEquals(6, file.readLines().count { it.startsWith("CREATE TABLE") })
                }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun `v5 backup preserves custom teleport spawn`() {
        for (source in listOf("sqlite", "h2")) for (target in listOf("sqlite", "h2")) {
            val directory = Files.createTempDirectory("plotsx-spawn-backup-test").toFile()
            fun connect(type: String) = DriverManager.getConnection(
                if (type == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${java.util.UUID.randomUUID()}"
            )
            try {
                connect(source).use { conn ->
                    if (source == "sqlite") conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                    conn.createStatement().use { statement ->
                        DatabaseSchema.statements(source).forEach { statement.execute(it) }
                    }
                    DatabaseMigrations.migrate(conn, source)
                    OperationJournal.migrate(conn)
                    IdentityMigrationStore.migrateSchema(conn)
                    PlotSpawnRepository.migrate(conn)
                    conn.createStatement().use { statement ->
                        statement.execute(
                            "INSERT INTO plots (plot_id, owner_uuid, x, z, y, radius, world, name, creation_time, geometry_type, geometry_revision) " +
                                "VALUES (42, 'owner', 10, 20, 64, 4, 'world', 'home', 12345, 'classic', 0)"
                        )
                        statement.execute("INSERT INTO plot_segments (plot_id, x, z, radius) VALUES (42, 10, 20, 4)")
                        statement.execute("INSERT INTO plot_spawns (plot_id, x, y, z) VALUES (42, 12, 70, 18)")
                    }
                    val file = SqlBackup.export(conn, target, directory)
                    assertTrue(file.readLines().first().startsWith("-- PlotsX SQL backup v5"))
                    connect(target).use { restored ->
                        if (target == "sqlite") restored.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                        restored.createStatement().use { statement ->
                            DatabaseSchema.statements(target).forEach { statement.execute(it) }
                        }
                        SqlBackup.restore(restored, target, file, allowChunkPlots = true)
                        restored.createStatement().use { statement ->
                            statement.executeQuery("SELECT x, y, z FROM plot_spawns WHERE plot_id=42").use {
                                assertTrue(it.next())
                                assertEquals(12, it.getInt("x"))
                                assertEquals(70, it.getInt("y"))
                                assertEquals(18, it.getInt("z"))
                            }
                        }
                    }
                }
            } finally {
                directory.deleteRecursively()
            }
        }
    }
}
