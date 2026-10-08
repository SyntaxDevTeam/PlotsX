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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@org.junit.runner.RunWith(org.junit.runners.Parameterized::class)
class ExpansionPurchaseServiceTest(private val classic: Boolean, private val dialect: String) {
    companion object {
        private val servers = mapOf(
            "mariadb" to System.getenv("PLOTSX_TEST_MARIADB_URL"),
            "mysql" to System.getenv("PLOTSX_TEST_MYSQL_URL"),
            "postgresql" to System.getenv("PLOTSX_TEST_POSTGRESQL_URL")
        ).filterValues { !it.isNullOrBlank() }
        @JvmStatic @org.junit.runners.Parameterized.Parameters(name = "classic={0}, database={1}")
        fun geometries(): List<Array<Any>> = (listOf("h2", "sqlite") + servers.keys).flatMap { dialect ->
            listOf(arrayOf(false, dialect), arrayOf(true, dialect))
        }
    }
    private class Fixture(val classic: Boolean, val dialect: String) : AutoCloseable {
        private val sqliteFile = if (dialect == "sqlite") java.nio.file.Files.createTempFile("plotsx-payment", ".db") else null
        val url = servers[dialect] ?: if (sqliteFile != null) "jdbc:sqlite:$sqliteFile" else "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        private val namespace = "plotsx_test_" + UUID.randomUUID().toString().replace("-", "")
        private fun rawConnection(): Connection = (if (dialect in listOf("mariadb", "mysql"))
            DriverManager.getConnection(url, "root", "") else DriverManager.getConnection(url)).also {
            if (dialect in servers) {
                if (dialect == "postgresql") it.schema = namespace else it.catalog = namespace
            }
        }
        private val control = if (dialect in servers) (if (dialect == "postgresql") DriverManager.getConnection(url)
            else DriverManager.getConnection(url, "root", "")).also { c ->
                c.createStatement().use { it.execute("CREATE ${if (dialect == "postgresql") "SCHEMA" else "DATABASE"} $namespace") }
            } else null
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
        var disconnectAfterDebit = false
        var disconnected = false
        val initialArea = if (classic) 25L else 256L
        val expandedArea = initialArea * 2
        val id: Int
        val op: OperationJournal.Operation
        val limits = ChunkExpansionTransaction.Limits(10000, 32, 64)
        init {
            rawConnection().use { c ->
                DatabaseMigrations.migrate(c, dialect); OperationJournal.migrate(c)
                c.autoCommit = false
                id = PlotRepository.insert(c, owner, "world", "home", 0, 64, 0, 0, if (classic) ClassicGeometry(PlotSegment(0, 0, 2)) else ChunkGeometry(setOf(ChunkPosition(0, 0))))
                c.commit()
            }
            op = OperationJournal.Operation(UUID.randomUUID(), id, owner, owner, "world", ChunkPosition(0, 0),
                ChunkPosition(if (classic) 5 else 1, 0), 0, BigDecimal("500"), "test-provider", "default", createdAt = System.currentTimeMillis(), classicRadius = if (classic) 2 else null)
        }
        fun connection(): Connection {
            assertNotEquals("test-server", Thread.currentThread().name)
            val actual = rawConnection()
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
                // A reserved purchase no longer globally fail-closes protection. Readers keep using the
                // previous immutable snapshot until the committed cache snapshot is published.
                assertTrue(coordinator.decision({ false }) { true })
                withdrawals++
                if (disconnectAfterDebit) disconnected = true
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
        fun buy(operation: OperationJournal.Operation = op, validate: () -> Boolean = { true }): ExpansionPurchaseService.Result {
            val calls = object : ExpansionPurchaseService.ServerCalls {
                override fun <T> call(action: () -> T): T = server.submit<T> {
                    check(!disconnected) { "Actor disconnected" }
                    action()
                }.get(5, TimeUnit.SECONDS)
                override fun refund(action: () -> Boolean): Boolean = server.submit<Boolean> { action() }.get(5, TimeUnit.SECONDS)
            }
            return ExpansionPurchaseService(::connection, coordinator, {
                publications++
                if (publicationError) error("cache unavailable")
                connection().use { PlotCacheLoader.load(it) }
            }, calls).purchase(operation, 0, limits, if (operation.amount.signum() == 0) null else account, validate,
                if (classic) ClassicExpansionTransaction.Limits(64, 10000) else null)
        }
        fun state() = connection().use { OperationJournal.readAll(it).single().state }
        fun area() = connection().use { PlotRepository.readAll(it).single().geometry.area }
        override fun close() {
            server.shutdownNow()
            if (control != null) control.use { c -> c.createStatement().use {
                it.execute("DROP ${if (dialect == "postgresql") "SCHEMA" else "DATABASE"} $namespace" + if (dialect == "postgresql") " CASCADE" else "")
            } }
            else if (sqliteFile != null) java.nio.file.Files.deleteIfExists(sqliteFile)
            else DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("SHUTDOWN") } }
        }
    }
    @Test fun `paid purchase executes provider on server and commits before publication`() = Fixture(classic, dialect).use { f ->
        assertEquals(ExpansionPurchaseService.Result.SUCCESS, f.buy())
        assertEquals(1, f.withdrawals); assertEquals(0, f.refunds); assertEquals(1, f.publications)
        assertEquals(OperationJournal.State.LAND_COMMITTED, f.state()); assertEquals(f.expandedArea, f.area())
        assertTrue(f.coordinator.decision({ false }) { true })
        assertEquals(ExpansionPurchaseService.Result.DUPLICATE, f.buy())
        assertEquals(1, f.withdrawals)
    }
    @Test fun `free purchase requires no provider`() = Fixture(classic, dialect).use { f ->
        assertEquals(ExpansionPurchaseService.Result.SUCCESS, f.buy(f.op.copy(amount = BigDecimal.ZERO, provider = "free")))
        assertEquals(0, f.withdrawals); assertEquals(f.expandedArea, f.area())
    }
    @Test fun `stale offer and changed server validation never withdraw`() = Fixture(classic, dialect).use { f ->
        assertEquals(ExpansionPurchaseService.Result.REJECTED, f.buy(f.op.copy(expectedRevision = 1)))
        assertEquals(ExpansionPurchaseService.Result.REJECTED, f.buy { false })
        assertEquals(0, f.withdrawals); assertEquals(f.initialArea, f.area())
    }
    @Test fun `declined payment leaves land unchanged without refund`() = Fixture(classic, dialect).use { f ->
        f.decline = true
        assertEquals(ExpansionPurchaseService.Result.DECLINED, f.buy())
        assertEquals(OperationJournal.State.DECLINED, f.state()); assertEquals(f.initialArea, f.area()); assertEquals(0, f.refunds)
    }
    @Test fun `uncertain debit is never retried or refunded automatically`() = Fixture(classic, dialect).use { f ->
        f.debitError = true
        assertEquals(ExpansionPurchaseService.Result.REVIEW_REQUIRED, f.buy())
        assertEquals(OperationJournal.State.UNCERTAIN, f.state()); assertEquals(0, f.refunds)
        assertEquals(ExpansionPurchaseService.Result.DUPLICATE, f.buy()); assertEquals(1, f.withdrawals)
    }
    @Test fun `failed commit refunds once using captured provider`() = Fixture(classic, dialect).use { f ->
        f.commitFault = "before"
        assertEquals(ExpansionPurchaseService.Result.REFUNDED, f.buy())
        assertEquals(1, f.refunds); assertEquals(f.initialArea, f.area()); assertEquals(OperationJournal.State.REFUNDED, f.state())
    }
    @Test fun `lost successful commit response never refunds claimed land`() = Fixture(classic, dialect).use { f ->
        f.commitFault = "after"
        assertEquals(ExpansionPurchaseService.Result.SUCCESS, f.buy())
        assertEquals(f.expandedArea, f.area()); assertEquals(0, f.refunds); assertEquals(OperationJournal.State.LAND_COMMITTED, f.state())
    }
    @Test fun `revalidation after payment triggers compensation if offer is no longer valid`() = Fixture(classic, dialect).use { f ->
        var validations = 0
        assertEquals(ExpansionPurchaseService.Result.REFUNDED, f.buy { ++validations == 1 })
        assertEquals(1, f.withdrawals); assertEquals(1, f.refunds); assertEquals(f.initialArea, f.area())
    }
    @Test fun `failed or uncertain refund remains durable`() {
        for (uncertain in listOf(false, true)) Fixture(classic, dialect).use { f ->
            f.commitFault = "before"; f.refundError = uncertain; f.refundDeclined = !uncertain
            assertEquals(ExpansionPurchaseService.Result.REVIEW_REQUIRED, f.buy())
            assertEquals(if (uncertain) OperationJournal.State.UNCERTAIN else OperationJournal.State.REFUND_REQUIRED, f.state())
            assertEquals(1, f.refunds)
        }
    }
    @Test fun `publication failure after commit blocks protection and does not refund`() = Fixture(classic, dialect).use { f ->
        f.publicationError = true
        assertEquals(ExpansionPurchaseService.Result.REVIEW_REQUIRED, f.buy())
        assertEquals(f.expandedArea, f.area()); assertEquals(0, f.refunds)
        assertFalse(f.coordinator.decision({ false }) { true })
        f.coordinator.recover {}
        assertTrue(f.coordinator.decision({ false }) { true })
    }
    @Test fun `reserved purchase blocks competing mutations without holding server callback lock`() = Fixture(classic, dialect).use { f ->
        val token = f.coordinator.reservePurchase()!!
        assertEquals(ExpansionPurchaseService.Result.BUSY, f.buy())
        assertThrows(IllegalStateException::class.java) { f.coordinator.mutate({}) { error("must not execute") } }
        assertThrows(IllegalStateException::class.java) { f.coordinator.recover {} }
        assertEquals(0, f.withdrawals)
        f.coordinator.finishPurchase(token) {}
        assertTrue(f.coordinator.decision({ false }) { true })
    }
    @Test fun `provider and currency mismatches are rejected before withdrawal`() = Fixture(classic, dialect).use { f ->
        assertEquals(ExpansionPurchaseService.Result.REJECTED, f.buy(f.op.copy(provider = "different-provider")))
        assertEquals(ExpansionPurchaseService.Result.REJECTED, f.buy(f.op.copy(currency = "different-currency")))
        assertEquals(0, f.withdrawals)
    }

    @Test fun `disconnect after debit refunds through an independent callback`() = Fixture(classic, dialect).use { f ->
        f.disconnectAfterDebit = true
        assertEquals(ExpansionPurchaseService.Result.REFUNDED, f.buy())
        assertEquals(1, f.withdrawals); assertEquals(1, f.refunds)
        assertEquals(f.initialArea, f.area())
        assertEquals(OperationJournal.State.REFUNDED, f.state())
    }

    @Test fun `rapid concurrent purchases admit only one workflow`() = Fixture(classic, dialect).use { f ->
        val validationEntered = CountDownLatch(1)
        val releaseValidation = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val first = workers.submit<ExpansionPurchaseService.Result> {
                f.buy {
                    validationEntered.countDown()
                    releaseValidation.await(5, TimeUnit.SECONDS)
                }
            }
            assertTrue(validationEntered.await(5, TimeUnit.SECONDS))
            val secondOperation = f.op.copy(id = UUID.randomUUID())
            assertEquals(ExpansionPurchaseService.Result.BUSY, workers.submit<ExpansionPurchaseService.Result> {
                f.buy(secondOperation)
            }.get(5, TimeUnit.SECONDS))
            releaseValidation.countDown()
            assertEquals(ExpansionPurchaseService.Result.SUCCESS, first.get(5, TimeUnit.SECONDS))
            assertEquals(1, f.withdrawals)
        } finally {
            releaseValidation.countDown()
            workers.shutdownNow()
        }
    }
}
