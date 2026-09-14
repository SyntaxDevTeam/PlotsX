package pl.syntaxdevteam.plotsx.protection

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/** Single-server mutation barrier. Readers never wait for SQL on the server thread. */
class ProtectionCoordinator {
    private val lock = ReentrantReadWriteLock(true)
    @Volatile private var recoveryRequired = false
    @Volatile private var purchase: Any? = null

    /** Cross-thread reservation; no JVM lock is held while waiting for the server/economy thread. */
    fun reservePurchase(): Any? = lock.writeLock().withLock {
        if (recoveryRequired || purchase != null) null else Any().also { purchase = it }
    }

    fun finishPurchase(token: Any, publish: () -> Unit) = lock.writeLock().withLock {
        check(purchase === token)
        recoveryRequired = true
        try { publish(); recoveryRequired = false }
        finally { purchase = null }
    }

    fun <T> decision(unavailable: () -> T, action: () -> T): T {
        if (!lock.readLock().tryLock()) return unavailable()
        try { return if (recoveryRequired || purchase != null) unavailable() else action() }
        finally { lock.readLock().unlock() }
    }

    fun <T> mutate(publish: () -> Unit, action: () -> T): T {
        if (lock.isWriteLockedByCurrentThread) return action()
        // Container protection can write metadata from inside a protection event.
        val heldReads = lock.readHoldCount
        repeat(heldReads) { lock.readLock().unlock() }
        lock.writeLock().lock()
        try {
            check(purchase == null) { "A plot purchase is in progress; retry after it finishes" }
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
        check(purchase == null) { "Cannot reload protection during a purchase" }
        recoveryRequired = true
        publish()
        recoveryRequired = false
    }
}
