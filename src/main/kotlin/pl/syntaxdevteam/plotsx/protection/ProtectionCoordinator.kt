package pl.syntaxdevteam.plotsx.protection

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/** Single-server mutation barrier. Readers never wait for SQL on the server thread. */
class ProtectionCoordinator {
    private val lock = ReentrantReadWriteLock(true)
    @Volatile private var recoveryRequired = false

    fun <T> decision(unavailable: () -> T, action: () -> T): T {
        if (!lock.readLock().tryLock()) return unavailable()
        try { return if (recoveryRequired) unavailable() else action() }
        finally { lock.readLock().unlock() }
    }

    fun <T> mutate(publish: () -> Unit, action: () -> T): T {
        if (lock.isWriteLockedByCurrentThread) return action()
        // Container protection can write metadata from inside a protection event.
        val heldReads = lock.readHoldCount
        repeat(heldReads) { lock.readLock().unlock() }
        lock.writeLock().lock()
        try {
            check(!recoveryRequired) { "Protection cache requires recovery before another mutation" }
            try { return action() }
            finally {
                recoveryRequired = true
                publish()
                recoveryRequired = false
            }
        } finally {
            repeat(heldReads) { lock.readLock().lock() }
            lock.writeLock().unlock()
        }
    }

    fun recover(publish: () -> Unit) = lock.writeLock().withLock {
        recoveryRequired = true
        publish()
        recoveryRequired = false
    }
}
