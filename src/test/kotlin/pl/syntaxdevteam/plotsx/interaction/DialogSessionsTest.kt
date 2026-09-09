package pl.syntaxdevteam.plotsx.interaction

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DialogSessionsTest {
    @Test fun `old screen and another player cannot authorize an operation`() {
        val sessions = DialogSessions()
        val player = UUID.randomUUID()
        val old = sessions.start(player)
        val current = sessions.start(player)
        assertFalse(sessions.consume(player, old))
        assertFalse(sessions.consume(UUID.randomUUID(), current))
        assertFalse(sessions.remove(player, old))
        assertTrue(sessions.consume(player, current))
        assertFalse(sessions.consume(player, current))
    }

    @Test fun `expired and disconnected sessions cannot submit`() {
        var now = 0L
        val sessions = DialogSessions { now }
        val player = UUID.randomUUID()
        val expired = sessions.start(player)
        now = TimeUnit.SECONDS.toNanos(60)
        assertFalse(sessions.consume(player, expired))
        val cancelled = sessions.start(player)
        sessions.cancel(player)
        assertFalse(sessions.consume(player, cancelled))
        val disabled = sessions.start(player)
        sessions.clear()
        assertFalse(sessions.consume(player, disabled))
    }

    @Test fun `concurrent confirmations execute at most once`() {
        val sessions = DialogSessions()
        val player = UUID.randomUUID()
        val token = sessions.start(player)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..20).map { pool.submit<Boolean> { sessions.consume(player, token) } }
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }
}
