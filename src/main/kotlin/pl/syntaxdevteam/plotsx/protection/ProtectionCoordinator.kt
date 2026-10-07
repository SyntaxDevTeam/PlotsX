package pl.syntaxdevteam.plotsx.protection

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Serialises plot mutations while protection readers use immutable cache snapshots.
 *
 * Runtime protection decisions must never wait for JDBC/cache publication. During a mutation they keep
 * reading the last fully published snapshot (RCU/read-copy-update). Only a publication that has already
 * failed switches decisions to fail-closed mode until an explicit recovery succeeds.
 */
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

    /**
     * Lock-free read path for server events.
     *
     * The cache is an immutable atomic snapshot, therefore an in-flight writer does not make the previous
     * snapshot unsafe. Taking the read lock here used to make every protection event fail closed while a
     * plot was being persisted/published, which translated directly into server-wide cancelled movement and
     * interactions (visible as rubber-banding). A completed publication failure is different: once the writer
     * has left the critical section there is no known-good current snapshot, so fail closed until recovery.
     */
    fun <T> decision(unavailable: () -> T, action: () -> T): T {
        if (recoveryRequired && !lock.isWriteLocked) return unavailable()
        return action()
    }

    fun <T> mutate(publish: () -> Unit, action: () -> T): T {
        if (lock.isWriteLockedByCurrentThread) return action()
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
