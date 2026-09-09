package pl.syntaxdevteam.plotsx.interaction

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A response belongs to one player, one screen, and a bounded lifetime. */
internal class DialogSessions(private val nanoTime: () -> Long = System::nanoTime) {
    private data class Session(val token: UUID, val created: Long)
    private val active = ConcurrentHashMap<UUID, Session>()
    fun start(player: UUID): UUID = UUID.randomUUID().also { active[player] = Session(it, nanoTime()) }
    fun isCurrent(player: UUID, token: UUID) = active[player]?.token == token
    fun remove(player: UUID, token: UUID): Boolean {
        val session = active[player] ?: return false
        return session.token == token && active.remove(player, session)
    }
    fun consume(player: UUID, token: UUID): Boolean {
        val session = active[player] ?: return false
        return session.token == token && active.remove(player, session) &&
            nanoTime() - session.created < TimeUnit.SECONDS.toNanos(60)
    }
    fun cancel(player: UUID) { active.remove(player) }
    fun clear() { active.clear() }
}
