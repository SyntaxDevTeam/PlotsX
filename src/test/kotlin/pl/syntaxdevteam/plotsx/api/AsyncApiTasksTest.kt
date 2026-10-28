package pl.syntaxdevteam.plotsx.api

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.api.internal.AsyncApiTasks

class AsyncApiTasksTest {
    @Test fun `mutation runs only when dispatched and future observes completed publication`() {
        val queue = ArrayDeque<Runnable>()
        val tasks = AsyncApiTasks { queue.add(it) }
        var published = false
        val result = tasks.submit("failed") { published = true; "updated" }
        assertFalse(published); assertFalse(result.isDone)
        queue.removeFirst().run()
        assertTrue(published); assertEquals("updated", result.join())
    }
    @Test fun `shutdown completes queued requests and prevents their writes`() {
        val queue = ArrayDeque<Runnable>()
        val tasks = AsyncApiTasks { queue.add(it) }
        var writes = 0
        val result = tasks.submit("failed") { writes++; "updated" }
        tasks.close()
        assertEquals("failed", result.join())
        queue.removeFirst().run()
        assertEquals(0, writes)
        assertEquals("failed", tasks.submit("failed") { writes++; "updated" }.join())
    }
    @Test fun `scheduler rejection and publication failure complete rather than hang`() {
        val rejected = AsyncApiTasks { throw IllegalStateException("scheduler stopped") }
        assertEquals("failed", rejected.submit("failed") { "updated" }.join())
        val immediate = AsyncApiTasks { it.run() }
        assertEquals("failed", immediate.submit("failed") { throw IllegalStateException("publication failed") }.join())
    }
}
