package pl.syntaxdevteam.plotsx.protection

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Serialises plot mutations while protection readers use immutable cache snapshots.
 *
 * Runtime protection decisions must never wait for JDBC/cache publication. During a healthy mutation they
 * keep reading the last fully published snapshot (RCU/read-copy-update). A publication that has failed is
 * different: protection fails closed until an explicit recovery successfully publishes a replacement.
 */
class ProtectionCoordinator {
    private val lock = ReentrantReadWriteLock(true)
    @Volatile private var recoveryRequired = false
    @Volatile private var staleSnapshotReadable = false
    @Volatile private var purchase: Any? = null

    /** Cross-thread reservation; no JVM lock is held while waiting for the server/economy thread. */
    fun reservePurchase(): Any? = lock.writeLock().withLock {
        if (recoveryRequired || purchase != null) null else Any().also { purchase = it }
    }

    fun finishPurchase(token: Any, publish: () -> Unit) = lock.writeLock().withLock {
        check(purchase === token)
        beginPublication(allowPreviousSnapshot = true)
        try {
            publish()
            recoveryRequired = false
        } finally {
            staleSnapshotReadable = false
            purchase = null
        }
    }

    /**
     * Lock-free read path for server events.
     *
     * The cache is an immutable atomic snapshot, therefore an in-flight healthy writer does not make the
     * previous snapshot unsafe. Taking the read lock here used to make every protection event fail closed
     * while a plot was persisted/published, translating directly into server-wide cancelled movement and
     * interactions (visible as rubber-banding).
     */
    fun <T> decision(unavailable: () -> T, action: () -> T): T {
        if (recoveryRequired && !staleSnapshotReadable) return unavailable()
        return action()
    }

    fun <T> mutate(publish: () -> Unit, action: () -> T): T {
        if (lock.isWriteLockedByCurrentThread) return action()
        lock.writeLock().lock()
        try {
            check(purchase == null) { "A plot purchase is in progress; retry after it finishes" }
            check(!recoveryRequired) { "Protection cache requires recovery before another mutation" }
            try {
                return action()
            } finally {
                beginPublication(allowPreviousSnapshot = true)
                try {
                    publish()
                    recoveryRequired = false
                } finally {
                    staleSnapshotReadable = false
                }
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun recover(publish: () -> Unit) = lock.writeLock().withLock {
        check(purchase == null) { "Cannot reload protection during a purchase" }
        // A routine reload may keep serving its known-good snapshot. Recovery after a failed publication
        // must remain fail-closed until the replacement has actually been published.
        beginPublication(allowPreviousSnapshot = !recoveryRequired)
        try {
            publish()
            recoveryRequired = false
        } finally {
            staleSnapshotReadable = false
        }
    }

    private fun beginPublication(allowPreviousSnapshot: Boolean) {
        staleSnapshotReadable = allowPreviousSnapshot
        recoveryRequired = true
    }
}
