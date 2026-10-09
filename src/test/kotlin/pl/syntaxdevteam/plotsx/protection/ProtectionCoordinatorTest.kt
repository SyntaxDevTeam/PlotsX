package pl.syntaxdevteam.plotsx.protection

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProtectionCoordinatorTest {
    @Test fun `decisions keep using published snapshot while mutation publishes`() {
        val coordinator = ProtectionCoordinator()
        val publishing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val job = executor.submit<Int> {
                coordinator.mutate({ publishing.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }) { 42 }
            }
            assertTrue(publishing.await(5, TimeUnit.SECONDS))
            assertEquals("allow", coordinator.decision({ "deny" }) { "allow" })
            finish.countDown()
            assertEquals(42, job.get(5, TimeUnit.SECONDS))
            assertEquals("allow", coordinator.decision({ "deny" }) { "allow" })
        } finally { finish.countDown(); executor.shutdownNow() }
    }

    @Test fun `failed publication requires explicit successful recovery`() {
        val coordinator = ProtectionCoordinator()
        assertThrows(IllegalStateException::class.java) { coordinator.mutate<Unit>({ error("load failed") }) {} }
        assertFalse(coordinator.decision({ false }) { true })
        assertThrows(IllegalStateException::class.java) { coordinator.mutate<Unit>({}) {} }
        coordinator.recover {}
        assertTrue(coordinator.decision({ false }) { true })
    }

    @Test fun `nested event metadata mutation publishes once without upgrade deadlock`() {
        val coordinator = ProtectionCoordinator()
        var publications = 0
        val result = coordinator.decision({ -1 }) {
            coordinator.mutate({ publications++ }) { coordinator.mutate({ publications++ }) { 7 } }
        }
        assertEquals(7, result)
        assertEquals(1, publications)
    }

    @Test fun `failed action still republishes before releasing barrier`() {
        val coordinator = ProtectionCoordinator()
        var published = false
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.mutate({ published = true }) { throw IllegalArgumentException("SQL rollback") }
        }
        assertTrue(published)
        assertTrue(coordinator.decision({ false }) { true })
    }
    @Test fun `purchase waiters fire once only after publication and late registration fires immediately`() {
        val coordinator = ProtectionCoordinator()
        val token = coordinator.reservePurchase()!!
        var calls = 0
        assertThrows(ProtectionCoordinator.PurchaseInProgressException::class.java) { coordinator.mutate({}) {} }
        coordinator.whenAvailable("player") { calls += 10 }
        coordinator.whenAvailable("player") { calls++ }
        assertEquals(0, calls)
        coordinator.finishPurchase(token) { assertEquals(0, calls) }
        assertEquals(1, calls)
        coordinator.whenAvailable("late") { calls++ }
        assertEquals(2, calls)
    }

    @Test fun `waiters remain pending when publication fails until recovery`() {
        val coordinator = ProtectionCoordinator()
        val token = coordinator.reservePurchase()!!
        var ready = false
        coordinator.whenAvailable("player") { ready = true }
        assertThrows(IllegalStateException::class.java) { coordinator.finishPurchase(token) { error("publication") } }
        assertFalse(ready)
        coordinator.recover { assertFalse(ready) }
        assertTrue(ready)
    }
}
