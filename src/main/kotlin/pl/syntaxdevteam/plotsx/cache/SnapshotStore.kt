package pl.syntaxdevteam.plotsx.cache

import java.util.concurrent.atomic.AtomicReference

/** Readers never block on database I/O. Failed/stale loads cannot publish a partial or older state. */
internal class SnapshotStore<T>(initial: T) {
    internal data class Version<T>(val revision: Long, val epoch: Long, val value: T)
    private val state = AtomicReference(Version(0L, 0L, initial))
    fun read(): T = state.get().value

    /** Loader must read fresh data on each invocation; it can run more than once after a race. */
    fun reload(loader: (T) -> T): Boolean {
        val epoch = state.get().epoch
        while (true) {
            val previous = state.get()
            if (previous.epoch != epoch) return false
            val next = loader(previous.value)
            if (state.compareAndSet(previous, Version(previous.revision + 1, epoch, next))) return true
        }
    }

    /** Invalidate all in-flight loads, e.g. when clearing or disabling the cache. */
    fun clear(value: T) {
        state.updateAndGet { Version(it.revision + 1, it.epoch + 1, value) }
    }
}
