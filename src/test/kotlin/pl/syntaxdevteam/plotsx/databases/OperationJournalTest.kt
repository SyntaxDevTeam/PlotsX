package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.math.BigDecimal
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class OperationJournalTest {
    private val owner = UUID.randomUUID()
    private val limits = ChunkExpansionTransaction.Limits(10000, 32, 64)
    private fun connect(type: String): Connection = DriverManager.getConnection(
        if (type == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${UUID.randomUUID()}"
    ).also { c ->
        if (type == "sqlite") c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        DatabaseMigrations.migrate(c, type); OperationJournal.migrate(c)
    }
    private fun databases(test: (Connection, String, OperationJournal.Operation) -> Unit) {
        for (type in listOf("sqlite", "h2")) connect(type).use { c ->
            c.autoCommit = false
            val id = PlotRepository.insert(c, owner, "wórld", "home", -1, 64, -1, 0, ChunkGeometry(setOf(ChunkPosition(-1, -1))))
            c.commit(); c.autoCommit = true
            test(c, type, OperationJournal.Operation(UUID.randomUUID(), id, owner, UUID.randomUUID(), "wórld",
                ChunkPosition(-1, -1), ChunkPosition(0, -1), 0, BigDecimal("12.3400"), "Vault:TestProvider", "default", createdAt = 100))
        }
    }
    private fun paid(c: Connection, op: OperationJournal.Operation) {
        assertTrue(OperationJournal.prepare(c, op))
        assertTrue(OperationJournal.transition(c, op.id, OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED, 101))
        assertTrue(OperationJournal.transition(c, op.id, OperationJournal.State.DEBIT_REQUESTED, OperationJournal.State.DEBITED, 102))
    }
    @Test fun `migration repeats and preserves existing payment identity and exact decimal`() = databases { c, type, op ->
        OperationJournal.prepare(c, op)
        DatabaseMigrations.migrate(c, type); OperationJournal.migrate(c)
        assertEquals(op, OperationJournal.readAll(c).single())
        c.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM schema_migrations").use { rs -> rs.next(); assertEquals(2, rs.getInt(1)) } }
    }
    @Test fun `duplicate IDs cannot create another withdrawal and unresolved owner or plot is reserved`() = databases { c, _, op ->
        assertTrue(OperationJournal.prepare(c, op)); assertFalse(OperationJournal.prepare(c, op))
        assertThrows(IllegalArgumentException::class.java) { OperationJournal.prepare(c, op.copy(amount = BigDecimal.ONE)) }
        assertThrows(IllegalArgumentException::class.java) { OperationJournal.prepare(c, op.copy(id = UUID.randomUUID(), owner = UUID.randomUUID())) }
        assertThrows(IllegalArgumentException::class.java) { OperationJournal.prepare(c, op.copy(id = UUID.randomUUID(), plotId = op.plotId + 1)) }
        OperationJournal.transition(c, op.id, OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED, 101)
        assertFalse(OperationJournal.prepare(c, op))
        assertEquals(1, OperationJournal.readAll(c).size)
    }
    @Test fun `payment transitions compare expected state reject backwards clocks and forbid uncertain replay`() = databases { c, _, op ->
        OperationJournal.prepare(c, op)
        assertFalse(OperationJournal.transition(c, op.id, OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED, 99))
        assertTrue(OperationJournal.transition(c, op.id, OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED, 101))
        assertFalse(OperationJournal.transition(c, op.id, OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED, 102))
        OperationJournal.transition(c, op.id, OperationJournal.State.DEBIT_REQUESTED, OperationJournal.State.UNCERTAIN, 102)
        assertThrows(IllegalArgumentException::class.java) {
            OperationJournal.transition(c, op.id, OperationJournal.State.UNCERTAIN, OperationJournal.State.DEBIT_REQUESTED, 103)
        }
    }
    @Test fun `restart cancels unsubmitted work and never retries uncertain debit or refund`() = databases { c, _, template ->
        for ((index, state) in listOf(OperationJournal.State.PREPARED, OperationJournal.State.DEBIT_REQUESTED,
            OperationJournal.State.DEBITED, OperationJournal.State.REFUND_REQUESTED).withIndex()) {
            val op = template.copy(id = UUID.randomUUID(), owner = UUID.randomUUID(), plotId = 100 + index)
            OperationJournal.prepare(c, op)
            // Simulate process death at each durable state, without calling any economy provider.
            c.prepareStatement("UPDATE plot_operations SET state = ? WHERE operation_id = ?").use {
                it.setString(1, state.name); it.setString(2, op.id.toString()); it.executeUpdate()
            }
        }
        val pending = OperationJournal.recoverInterrupted(c, 200)
        assertEquals(3, pending.size)
        assertEquals(2, pending.count { it.state == OperationJournal.State.UNCERTAIN })
        assertEquals(1, pending.count { it.state == OperationJournal.State.REFUND_REQUIRED })
        assertEquals(1, OperationJournal.readAll(c).count { it.state == OperationJournal.State.CANCELLED })
        assertEquals(pending, OperationJournal.recoverInterrupted(c, 201))
    }
    @Test fun `land and paid operation commit or roll back together`() = databases { c, _, op ->
        paid(c, op)
        c.autoCommit = false
        assertTrue(OperationJournal.applyLand(c, op.id, limits, 103) is ChunkExpansionTransaction.Result.Success)
        c.rollback(); c.autoCommit = true
        assertEquals(OperationJournal.State.DEBITED, OperationJournal.readAll(c).single().state)
        assertEquals(256L, PlotRepository.readAll(c).single().geometry.area)
        c.autoCommit = false
        assertTrue(OperationJournal.applyLand(c, op.id, limits, 104) is ChunkExpansionTransaction.Result.Success)
        c.commit(); c.autoCommit = true
        assertEquals(OperationJournal.State.LAND_COMMITTED, OperationJournal.readAll(c).single().state)
        assertEquals(512L, PlotRepository.readAll(c).single().geometry.area)
        assertTrue(OperationJournal.pending(c).isEmpty())
    }
    @Test fun `free operation commits without requiring a fictitious payment`() = databases { c, _, op ->
        OperationJournal.prepare(c, op.copy(amount = BigDecimal.ZERO))
        c.autoCommit = false
        assertTrue(OperationJournal.applyLand(c, op.id, limits, 101) is ChunkExpansionTransaction.Result.Success)
        c.commit(); c.autoCommit = true
        assertEquals(OperationJournal.State.LAND_COMMITTED, OperationJournal.readAll(c).single().state)
    }
    @Test fun `unconfirmed payment cannot write land`() = databases { c, _, op ->
        OperationJournal.prepare(c, op)
        c.autoCommit = false
        assertThrows(IllegalArgumentException::class.java) { OperationJournal.applyLand(c, op.id, limits, 101) }
        c.rollback(); c.autoCommit = true
        assertEquals(256L, PlotRepository.readAll(c).single().geometry.area)
    }
    @Test fun `late land failure retains confirmed debit for refund recovery`() = databases { c, _, op ->
        paid(c, op)
        c.createStatement().use { it.execute("DROP TABLE plot_logs") }
        c.autoCommit = false
        assertThrows(java.sql.SQLException::class.java) { OperationJournal.applyLand(c, op.id, limits, 103) }
        c.rollback(); c.autoCommit = true
        assertEquals(256L, PlotRepository.readAll(c).single().geometry.area)
        assertEquals(OperationJournal.State.REFUND_REQUIRED, OperationJournal.recoverInterrupted(c, 104).single().state)
    }
    @Test fun `refund failures stay pending and confirmed refund is terminal`() = databases { c, _, op ->
        paid(c, op)
        OperationJournal.transition(c, op.id, OperationJournal.State.DEBITED, OperationJournal.State.REFUND_REQUIRED, 103)
        OperationJournal.transition(c, op.id, OperationJournal.State.REFUND_REQUIRED, OperationJournal.State.REFUND_REQUESTED, 104)
        OperationJournal.transition(c, op.id, OperationJournal.State.REFUND_REQUESTED, OperationJournal.State.REFUND_REQUIRED, 105)
        assertEquals(1, OperationJournal.pending(c).size)
        OperationJournal.transition(c, op.id, OperationJournal.State.REFUND_REQUIRED, OperationJournal.State.REFUND_REQUESTED, 106)
        OperationJournal.transition(c, op.id, OperationJournal.State.REFUND_REQUESTED, OperationJournal.State.REFUNDED, 107)
        assertTrue(OperationJournal.recoverInterrupted(c, 108).isEmpty())
    }
    @Test fun `deleting plot never deletes unresolved financial evidence`() = databases { c, _, op ->
        paid(c, op)
        c.createStatement().use { it.executeUpdate("DELETE FROM plots") }
        assertEquals(op.id, OperationJournal.recoverInterrupted(c, 103).single().id)
        assertTrue(PlotRepository.readAll(c).isEmpty())
    }
    @Test fun `v3 transfers pending operations across sqlite and h2 and prevents overwriting live unresolved payments`() = databases { source, _, op ->
        paid(source, op)
        for (targetType in listOf("sqlite", "h2")) connect(targetType).use { target ->
            val dir = Files.createTempDirectory("plotsx-journal-backup").toFile()
            try {
                val file = SqlBackup.export(source, targetType, dir)
                assertTrue(file.readText().startsWith("-- PlotsX SQL backup v3 dialect=$targetType"))
                SqlBackup.restore(target, targetType, file, allowChunkPlots = true)
                val restored = OperationJournal.readAll(target).single()
                assertEquals(OperationJournal.readAll(source).single().copy(state = OperationJournal.State.UNCERTAIN, updatedAt = restored.updatedAt), restored)
                assertEquals(1, PlotRepository.readAll(target).size)
                assertThrows(IllegalArgumentException::class.java) { SqlBackup.restore(target, targetType, file, allowChunkPlots = true) }
                assertEquals(OperationJournal.State.UNCERTAIN, OperationJournal.readAll(target).single().state)
            } finally { dir.deleteRecursively() }
        }
    }
    @Test fun `invalid journal in backup rolls back previously restored geometry`() = databases { c, type, op ->
        paid(c, op)
        val dir = Files.createTempDirectory("plotsx-invalid-journal").toFile()
        try {
            val file = SqlBackup.export(c, type, dir)
            file.writeText(file.readText().replace("44454249544544", "424144")) // DEBITED -> BAD
            connect(type).use { target ->
                assertThrows(IllegalArgumentException::class.java) { SqlBackup.restore(target, type, file, allowChunkPlots = true) }
                assertTrue(OperationJournal.readAll(target).isEmpty())
                assertTrue(PlotRepository.readAll(target).isEmpty())
                assertTrue(target.autoCommit)
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun `journal write failure rolls back an already applied chunk`() = databases { c, _, op ->
        paid(c, op)
        c.autoCommit = false
        assertThrows(IllegalStateException::class.java) { OperationJournal.applyLand(c, op.id, limits, 99) }
        c.rollback(); c.autoCommit = true
        assertEquals(256L, PlotRepository.readAll(c).single().geometry.area)
        assertEquals(OperationJournal.State.DEBITED, OperationJournal.readAll(c).single().state)
    }
    @Test fun `v1 and v2 backups still restore into a runtime journal schema`() = databases { target, type, op ->
        for (version in listOf(1, 2)) {
            val sourceUrl = if (type == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${UUID.randomUUID()}"
            DriverManager.getConnection(sourceUrl).use { source ->
                if (version == 1) DatabaseSchema.statements(type).forEach { sql -> source.createStatement().use { it.execute(sql) } }
                else DatabaseMigrations.migrate(source, type)
                val dir = Files.createTempDirectory("plotsx-legacy-journal").toFile()
                try {
                    val file = SqlBackup.export(source, type, dir)
                    assertTrue(file.readText().startsWith("-- PlotsX SQL backup v$version dialect=$type"))
                    val entry = op.copy(id = UUID.randomUUID())
                    OperationJournal.prepare(target, entry)
                    assertThrows(IllegalArgumentException::class.java) { SqlBackup.restore(target, type, file, true) }
                    assertEquals(entry.id, OperationJournal.pending(target).single().id)
                    OperationJournal.transition(target, entry.id, OperationJournal.State.PREPARED, OperationJournal.State.CANCELLED, 101)
                    SqlBackup.restore(target, type, file, true)
                    assertTrue(OperationJournal.exists(target))
                    assertTrue(OperationJournal.readAll(target).isEmpty())
                    assertTrue(PlotRepository.readAll(target).isEmpty())
                } finally { dir.deleteRecursively() }
            }
        }
    }
    @Test fun `missing migrated journal is reported instead of silently recreating empty evidence`() = databases { c, _, _ ->
        c.createStatement().use { it.execute("DROP TABLE plot_operations") }
        assertThrows(IllegalArgumentException::class.java) { OperationJournal.migrate(c) }
        assertFalse(OperationJournal.exists(c))
    }

}
