package pl.syntaxdevteam.plotsx.cache

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SnapshotStoreTest {
    @Test fun `late reload retries fresh data instead of overwriting a newer publication`() {
        val store = SnapshotStore("initial")
        val loaded = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val old = executor.submit<Boolean> {
                store.reload { current ->
                    if (calls.incrementAndGet() == 1) {
                        loaded.countDown(); check(release.await(5, TimeUnit.SECONDS)); "stale"
                    } else "$current+refreshed"
                }
            }
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            assertEquals("initial", store.read())
            store.reload { "new" }
            release.countDown()
            assertTrue(old.get(5, TimeUnit.SECONDS))
            assertEquals(2, calls.get())
            assertEquals("new+refreshed", store.read())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `clear cancels in-flight loads and never resurrects their contents`() {
        val store = SnapshotStore(listOf(1))
        val loaded = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val old = executor.submit<Boolean> {
                store.reload { loaded.countDown(); check(release.await(5, TimeUnit.SECONDS)); listOf(1, 2) }
            }
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            store.clear(emptyList())
            release.countDown()
            assertFalse(old.get(5, TimeUnit.SECONDS))
            assertTrue(store.read().isEmpty())
            assertTrue(store.reload { listOf(3) })
            assertEquals(listOf(3), store.read())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `failed load leaves the last valid snapshot intact`() {
        val store = SnapshotStore(listOf(1, 2))
        assertThrows(IllegalStateException::class.java) { store.reload { error("database unavailable") } }
        assertEquals(listOf(1, 2), store.read())
    }
}
