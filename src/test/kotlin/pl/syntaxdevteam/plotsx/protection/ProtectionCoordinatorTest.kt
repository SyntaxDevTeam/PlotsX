package pl.syntaxdevteam.plotsx.protection

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProtectionCoordinatorTest {
    @Test fun `decisions fail closed until commit and publication finish`() {
        val coordinator = ProtectionCoordinator()
        val publishing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val job = executor.submit<Int> {
                coordinator.mutate({ publishing.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }) { 42 }
            }
            assertTrue(publishing.await(5, TimeUnit.SECONDS))
            assertEquals("deny", coordinator.decision({ "deny" }) { "allow" })
            finish.countDown()
            assertEquals(42, job.get(5, TimeUnit.SECONDS))
            assertEquals("allow", coordinator.decision({ "deny" }) { "allow" })
        } finally { finish.countDown(); executor.shutdownNow() }
    }
    @Test fun `failed publication requires explicit successful recovery`() {
        val coordinator = ProtectionCoordinator()
        assertThrows(IllegalStateException::class.java) { coordinator.mutate({ error("load failed") }) { 1 } }
        assertFalse(coordinator.decision({ false }) { true })
        assertThrows(IllegalStateException::class.java) { coordinator.mutate({}) { 2 } }
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
}
