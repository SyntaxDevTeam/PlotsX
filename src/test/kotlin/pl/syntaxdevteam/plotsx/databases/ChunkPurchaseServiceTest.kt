package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.geometry.*
import pl.syntaxdevteam.plotsx.hooks.ExpansionEconomy
import pl.syntaxdevteam.plotsx.protection.ProtectionCoordinator
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChunkPurchaseServiceTest {
    private class Fixture : AutoCloseable {
        val url = "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        val owner = UUID.randomUUID()
        val coordinator = ProtectionCoordinator()
        val server = Executors.newSingleThreadExecutor { Thread(it, "test-server") }
        var withdrawals = 0
        var refunds = 0
        var publications = 0
        var decline = false
        var debitError = false
        var refundError = false
        var refundDeclined = false
        var commitFault: String? = null
        var publicationError = false
        val id: Int
        val op: OperationJournal.Operation
        val limits = ChunkExpansionTransaction.Limits(10000, 32, 64)
        init {
            DriverManager.getConnection(url).use { c ->
                DatabaseMigrations.migrate(c, "h2"); OperationJournal.migrate(c)
                c.autoCommit = false
                id = PlotRepository.insert(c, owner, "world", "home", 0, 64, 0, 0, ChunkGeometry(setOf(ChunkPosition(0, 0))))
                c.commit()
            }
            op = OperationJournal.Operation(UUID.randomUUID(), id, owner, owner, "world", ChunkPosition(0, 0),
                ChunkPosition(1, 0), 0, BigDecimal("500"), "test-provider", "default", createdAt = System.currentTimeMillis())
        }
        fun connection(): Connection {
            assertNotEquals("test-server", Thread.currentThread().name)
            val actual = DriverManager.getConnection(url)
            return object : Connection by actual {
                override fun commit() {
                    val fault = commitFault
                    commitFault = null
                    if (fault == "before") throw SQLException("commit failed")
                    actual.commit()
                    if (fault == "after") throw SQLException("commit reply lost")
                }
            }
        }
        val account = object : ExpansionEconomy.Account {
            override val providerId = "test-provider"
            override val currencyId = "default"
            override fun withdraw(): Boolean {
                assertEquals("test-server", Thread.currentThread().name)
                assertFalse(coordinator.decision({ false }) { true })
                withdrawals++
                if (debitError) error("provider response lost")
                return !decline
            }
            override fun refund(): Boolean {
                assertEquals("test-server", Thread.currentThread().name)
                refunds++
                if (refundError) error("refund response lost")
                return !refundDeclined
            }
        }
        fun buy(operation: OperationJournal.Operation = op, validate: () -> Boolean = { true }): ChunkPurchaseService.Result {
            val calls = object : ChunkPurchaseService.ServerCalls {
                override fun <T> call(action: () -> T): T = server.submit<T> { action() }.get(5, TimeUnit.SECONDS)
            }
            return ChunkPurchaseService(::connection, coordinator, {
                publications++
                if (publicationError) error("cache unavailable")
                connection().use { PlotCacheLoader.load(it) }
            }, calls).purchase(operation, 0, limits, if (operation.amount.signum() == 0) null else account, validate)
        }
        fun state() = connection().use { OperationJournal.readAll(it).single().state }
        fun area() = connection().use { PlotRepository.readAll(it).single().geometry.area }
        override fun close() {
            server.shutdownNow()
            DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("SHUTDOWN") } }
        }
    }
    @Test fun `paid purchase executes provider on server and commits before publication`() = Fixture().use { f ->
        assertEquals(ChunkPurchaseService.Result.SUCCESS, f.buy())
        assertEquals(1, f.withdrawals); assertEquals(0, f.refunds); assertEquals(1, f.publications)
        assertEquals(OperationJournal.State.LAND_COMMITTED, f.state()); assertEquals(512L, f.area())
        assertTrue(f.coordinator.decision({ false }) { true })
        assertEquals(ChunkPurchaseService.Result.DUPLICATE, f.buy())
        assertEquals(1, f.withdrawals)
    }
    @Test fun `free purchase requires no provider`() = Fixture().use { f ->
        assertEquals(ChunkPurchaseService.Result.SUCCESS, f.buy(f.op.copy(amount = BigDecimal.ZERO, provider = "free")))
        assertEquals(0, f.withdrawals); assertEquals(512L, f.area())
    }
    @Test fun `stale offer and changed server validation never withdraw`() = Fixture().use { f ->
        assertEquals(ChunkPurchaseService.Result.REJECTED, f.buy(f.op.copy(expectedRevision = 1)))
        assertEquals(ChunkPurchaseService.Result.REJECTED, f.buy { false })
        assertEquals(0, f.withdrawals); assertEquals(256L, f.area())
    }
    @Test fun `declined payment leaves land unchanged without refund`() = Fixture().use { f ->
        f.decline = true
        assertEquals(ChunkPurchaseService.Result.DECLINED, f.buy())
        assertEquals(OperationJournal.State.DECLINED, f.state()); assertEquals(256L, f.area()); assertEquals(0, f.refunds)
    }
    @Test fun `uncertain debit is never retried or refunded automatically`() = Fixture().use { f ->
        f.debitError = true
        assertEquals(ChunkPurchaseService.Result.REVIEW_REQUIRED, f.buy())
        assertEquals(OperationJournal.State.UNCERTAIN, f.state()); assertEquals(0, f.refunds)
        assertEquals(ChunkPurchaseService.Result.DUPLICATE, f.buy()); assertEquals(1, f.withdrawals)
    }
    @Test fun `failed commit refunds once using captured provider`() = Fixture().use { f ->
        f.commitFault = "before"
        assertEquals(ChunkPurchaseService.Result.REFUNDED, f.buy())
        assertEquals(1, f.refunds); assertEquals(256L, f.area()); assertEquals(OperationJournal.State.REFUNDED, f.state())
    }
    @Test fun `lost successful commit response never refunds claimed land`() = Fixture().use { f ->
        f.commitFault = "after"
        assertEquals(ChunkPurchaseService.Result.SUCCESS, f.buy())
        assertEquals(512L, f.area()); assertEquals(0, f.refunds); assertEquals(OperationJournal.State.LAND_COMMITTED, f.state())
    }
    @Test fun `revalidation after payment triggers compensation if offer is no longer valid`() = Fixture().use { f ->
        var validations = 0
        assertEquals(ChunkPurchaseService.Result.REFUNDED, f.buy { ++validations == 1 })
        assertEquals(1, f.withdrawals); assertEquals(1, f.refunds); assertEquals(256L, f.area())
    }
    @Test fun `failed or uncertain refund remains durable`() {
        for (uncertain in listOf(false, true)) Fixture().use { f ->
            f.commitFault = "before"; f.refundError = uncertain; f.refundDeclined = !uncertain
            assertEquals(ChunkPurchaseService.Result.REVIEW_REQUIRED, f.buy())
            assertEquals(if (uncertain) OperationJournal.State.UNCERTAIN else OperationJournal.State.REFUND_REQUIRED, f.state())
            assertEquals(1, f.refunds)
        }
    }
    @Test fun `publication failure after commit blocks protection and does not refund`() = Fixture().use { f ->
        f.publicationError = true
        assertEquals(ChunkPurchaseService.Result.REVIEW_REQUIRED, f.buy())
        assertEquals(512L, f.area()); assertEquals(0, f.refunds)
        assertFalse(f.coordinator.decision({ false }) { true })
        f.coordinator.recover {}
        assertTrue(f.coordinator.decision({ false }) { true })
    }
    @Test fun `reserved purchase blocks competing mutations without holding server callback lock`() = Fixture().use { f ->
        val token = f.coordinator.reservePurchase()!!
        assertEquals(ChunkPurchaseService.Result.BUSY, f.buy())
        assertThrows(IllegalStateException::class.java) { f.coordinator.mutate({}) { error("must not execute") } }
        assertThrows(IllegalStateException::class.java) { f.coordinator.recover {} }
        assertEquals(0, f.withdrawals)
        f.coordinator.finishPurchase(token) {}
        assertTrue(f.coordinator.decision({ false }) { true })
    }
    @Test fun `provider and currency mismatches are rejected before withdrawal`() = Fixture().use { f ->
        assertEquals(ChunkPurchaseService.Result.REJECTED, f.buy(f.op.copy(provider = "different-provider")))
        assertEquals(ChunkPurchaseService.Result.REJECTED, f.buy(f.op.copy(currency = "different-currency")))
        assertEquals(0, f.withdrawals)
    }
}
