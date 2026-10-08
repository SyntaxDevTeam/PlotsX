package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class ClassicExpansionTransactionTest {
    private fun databases(test: (Connection, OperationJournal.Operation) -> Unit) {
        for (dialect in listOf("sqlite", "h2")) DriverManager.getConnection(
            if (dialect == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:${UUID.randomUUID()}"
        ).use { c ->
            DatabaseMigrations.migrate(c, dialect); OperationJournal.migrate(c)
            val owner = UUID.randomUUID()
            c.autoCommit = false
            val id = PlotRepository.insert(c, owner, "world", "home", 0, 64, 0, 0, ClassicGeometry(PlotSegment(0, 0, 2)))
            c.commit(); c.autoCommit = true
            test(c, OperationJournal.Operation(UUID.randomUUID(), id, owner, owner, "world", ChunkPosition(0, 0),
                ChunkPosition(5, 0), 0, BigDecimal.ZERO, "free", "free", createdAt = 100, classicRadius = 2))
        }
    }

    @Test fun `radius area and mixed geometry collisions reject without changing land`() = databases { c, op ->
        c.autoCommit = false
        assertEquals(ClassicExpansionTransaction.Result.RADIUS_LIMIT,
            ClassicExpansionTransaction.apply(c, op, 0, ClassicExpansionTransaction.Limits(6, 1000)))
        assertEquals(ClassicExpansionTransaction.Result.AREA_LIMIT,
            ClassicExpansionTransaction.apply(c, op, 0, ClassicExpansionTransaction.Limits(64, 49)))
        assertEquals(25L, PlotRepository.readAll(c).single().geometry.area)
        val limits = ClassicExpansionTransaction.Limits(64, 1000)
        assertEquals(ClassicExpansionTransaction.Result.SUCCESS, ClassicExpansionTransaction.apply(c, op, 0, limits))
        val second = op.copy(source = ChunkPosition(5, 0), target = ChunkPosition(10, 0), expectedRevision = 1)
        assertEquals(ClassicExpansionTransaction.Result.SUCCESS, ClassicExpansionTransaction.apply(c, second, 1, limits))
        PlotRepository.insert(c, UUID.randomUUID(), "WORLD", "other", 16, 64, 0, 0,
            ChunkGeometry(setOf(ChunkPosition(1, 0))))
        val third = op.copy(source = ChunkPosition(10, 0), target = ChunkPosition(15, 0), expectedRevision = 2)
        assertEquals(ClassicExpansionTransaction.Result.COLLISION,
            ClassicExpansionTransaction.apply(c, third, 2, limits))
        c.rollback()
        assertEquals(25L, PlotRepository.readAll(c).single().geometry.area)
    }

    @Test fun `stale owner world radius revision and level reject before changes`() = databases { c, op ->
        c.autoCommit = false
        for (stale in listOf(op.copy(owner = UUID.randomUUID()), op.copy(world = "other"),
            op.copy(expectedRevision = 1), op.copy(classicRadius = 3, target = ChunkPosition(7, 0)))) {
            assertEquals(ClassicExpansionTransaction.Result.REJECTED,
                ClassicExpansionTransaction.apply(c, stale, 0, ClassicExpansionTransaction.Limits(64, 1000)))
        }
        assertEquals(ClassicExpansionTransaction.Result.REJECTED,
            ClassicExpansionTransaction.apply(c, op, 1, ClassicExpansionTransaction.Limits(64, 1000)))
        assertEquals(25L, PlotRepository.readAll(c).single().geometry.area)
        c.rollback()
    }

    @Test fun `journal failure rolls back an already applied classic segment`() = databases { c, op ->
        assertTrue(OperationJournal.prepare(c, op))
        c.autoCommit = false
        assertThrows(IllegalStateException::class.java) {
            OperationJournal.applyClassicLand(c, op.id, 0, ClassicExpansionTransaction.Limits(64, 1000), 99)
        }
        c.rollback(); c.autoCommit = true
        assertEquals(25L, PlotRepository.readAll(c).single().geometry.area)
        assertEquals(OperationJournal.State.PREPARED, OperationJournal.readAll(c).single().state)
    }

    @Test fun `all directions and an L shaped continuation preserve the free corner`() = databases { c, template ->
        val limits = ClassicExpansionTransaction.Limits(64, 1000)
        for (direction in ExpansionDirection.entries) {
            val op = template.copy(target = ChunkPosition(direction.dx * 5, direction.dz * 5))
            c.autoCommit = false
            assertEquals(ClassicExpansionTransaction.Result.SUCCESS, ClassicExpansionTransaction.apply(c, op, 0, limits))
            assertEquals(50L, PlotRepository.readAll(c).single().geometry.area)
            c.rollback(); c.autoCommit = true
        }
        c.autoCommit = false
        val north = template.copy(target = ChunkPosition(0, -5))
        assertEquals(ClassicExpansionTransaction.Result.SUCCESS, ClassicExpansionTransaction.apply(c, north, 0, limits))
        val east = north.copy(source = north.target, target = ChunkPosition(5, -5), expectedRevision = 1)
        assertEquals(ClassicExpansionTransaction.Result.SUCCESS, ClassicExpansionTransaction.apply(c, east, 1, limits))
        c.commit(); c.autoCommit = true
        val shape = PlotRepository.readAll(c).single().geometry
        assertEquals(75L, shape.area)
        assertTrue(shape.contains(5, -5))
        assertFalse(shape.contains(5, 0))
    }
}
