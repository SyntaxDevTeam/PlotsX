package pl.syntaxdevteam.plotsx.commands

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.api.MemberUpdateResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OwnershipTransferWorkflowTest {
    @Test fun `recipient permissions run on recipient region and JDBC runs on worker`() {
        val recipient = Executors.newSingleThreadExecutor { Thread(it, "recipient-region") }
        val worker = Executors.newSingleThreadExecutor { Thread(it, "database-worker") }
        try {
            val result = CompletableFuture<MemberUpdateResult>()
            OwnershipTransferWorkflow(
                onRecipient = { task, _ -> recipient.execute(task) },
                onWorker = { worker.execute(it) },
                capture = { assertEquals("recipient-region", Thread.currentThread().name); 7 },
                persist = { limits ->
                    assertEquals("database-worker", Thread.currentThread().name)
                    assertEquals(7, limits)
                    MemberUpdateResult.UPDATED
                }, report = { throw AssertionError(it) }
            ).start(result)
            assertEquals(MemberUpdateResult.UPDATED, result.get(5, TimeUnit.SECONDS))
        } finally { recipient.shutdownNow(); worker.shutdownNow() }
    }

    @Test fun `retired recipient never reads permissions or writes database`() {
        val result = CompletableFuture<MemberUpdateResult>()
        OwnershipTransferWorkflow<Int>(
            onRecipient = { _, retired -> retired.run() }, onWorker = { error("no JDBC") },
            capture = { error("no permission access") }, persist = { error("no transfer") }, report = { throw AssertionError(it) }
        ).start(result)
        assertEquals(MemberUpdateResult.RECIPIENT_OFFLINE, result.join())
    }

    @Test fun `shutdown completion prevents delayed recipient and worker callbacks`() {
        for (stopBeforeCapture in listOf(false, true)) {
            var recipientTask: Runnable? = null
            var workerTask: Runnable? = null
            var captures = 0
            val result = CompletableFuture<MemberUpdateResult>()
            OwnershipTransferWorkflow(
                onRecipient = { task, _ -> recipientTask = task }, onWorker = { workerTask = it },
                capture = { captures++; 7 }, persist = { error("no transfer after shutdown") }, report = { throw AssertionError(it) }
            ).start(result)
            if (!stopBeforeCapture) recipientTask!!.run()
            result.complete(MemberUpdateResult.DATABASE_ERROR)
            if (stopBeforeCapture) recipientTask!!.run() else workerTask!!.run()
            assertEquals(if (stopBeforeCapture) 0 else 1, captures)
            assertEquals(MemberUpdateResult.DATABASE_ERROR, result.join())
        }
    }

    @Test fun `capture worker and scheduling failures complete future`() {
        for (stage in listOf("capture", "worker", "scheduler")) {
            val result = CompletableFuture<MemberUpdateResult>()
            val failures = mutableListOf<Exception>()
            OwnershipTransferWorkflow(
                onRecipient = { task, _ -> if (stage == "scheduler") error("scheduler unavailable") else task.run() },
                onWorker = { it.run() }, capture = { if (stage == "capture") error("permissions unavailable") else 7 },
                persist = { error("database unavailable") }, report = { failures.add(it) }
            ).start(result)
            assertEquals(MemberUpdateResult.DATABASE_ERROR, result.join())
            assertEquals(1, failures.size)
        }
    }
}
