package pl.syntaxdevteam.plotsx.api.internal

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Completes outstanding requests when a scheduler cancels queued work during plugin shutdown. */
internal class AsyncApiTasks(private val report: (Exception) -> Unit = {}, private val execute: (Runnable) -> Unit) : AutoCloseable {
    private val pending = ConcurrentHashMap<CompletableFuture<*>, () -> Unit>()
    @Volatile private var closed = false

    fun <T> submit(failure: T, action: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        pending[result] = { result.complete(failure) }
        result.whenComplete { _, _ -> pending.remove(result) }
        if (closed) { result.complete(failure); return result }
        try {
            execute(Runnable {
                if (result.isDone) return@Runnable
                try { result.complete(action()) }
                catch (error: Exception) { try { report(error) } finally { result.complete(failure) } }
            })
        } catch (error: Exception) { try { report(error) } finally { result.complete(failure) } }
        return result
    }

    override fun close() {
        closed = true
        pending.values.forEach { it() }
    }
}
