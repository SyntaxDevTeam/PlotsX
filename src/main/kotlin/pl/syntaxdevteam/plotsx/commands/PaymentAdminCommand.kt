package pl.syntaxdevteam.plotsx.commands

import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.OperationJournal
import java.util.UUID

/** Called by /ptx after the parent permission check. All SQL runs on a worker. */
internal class PaymentAdminCommand(private val plugin: PlotsX) {
    fun execute(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("plotsx.admin.payments")) {
            sender.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
            return
        }
        try {
            val action: () -> List<String> = when (args.firstOrNull()?.lowercase()) {
                "list" -> {
                    require(args.size <= 2)
                    val page = args.getOrNull(1)?.toInt() ?: 1
                    require(page > 0)
                    val task: () -> List<String> = {
                        val pending = plugin.databaseHandler.paymentOperations().filterNot { it.state.terminal }
                        val pages = maxOf(1, (pending.size + 9) / 10)
                        require(page <= pages) { "Page out of range (1..$pages)" }
                        listOf("Pending payments: ${pending.size}; page $page/$pages") +
                            pending.drop((page - 1) * 10).take(10).map { "${it.id} plot=${it.plotId} ${it.state} ${it.amount} ${it.currency}" }
                    }
                    task
                }
                "show" -> {
                    require(args.size == 2)
                    val id = UUID.fromString(args[1])
                    val task: () -> List<String> = {
                        val op = plugin.databaseHandler.paymentOperations().singleOrNull { it.id == id }
                            ?: error("Operation not found")
                        listOf("Payment ${op.id}: ${op.state}; updatedAt=${op.updatedAt}",
                            "plot=${op.plotId} owner=${op.owner} actor=${op.actor}",
                            "${op.amount.toPlainString()} ${op.currency}; provider=${op.provider}",
                            "world=${op.world} source=${op.source} target=${op.target} revision=${op.expectedRevision}",
                            "Resolve only after checking provider records. No transfer or land change is performed.")
                    }
                    task
                }
                "resolve" -> {
                    require(args.size >= 7 && args[5].equals("confirm", ignoreCase = true))
                    val id = UUID.fromString(args[1])
                    val state = OperationJournal.State.valueOf(args[2].uppercase())
                    val timestamp = args[3].toLong()
                    val resolution = OperationJournal.Resolution.valueOf(args[4].uppercase())
                    val reason = args.drop(6).joinToString(" ")
                    val actor = when (sender) {
                        is Player -> sender.uniqueId
                        is ConsoleCommandSender -> UUID(0, 0)
                        else -> error("Only players and the server console may reconcile payments")
                    }
                    val task: () -> List<String> = {
                        val changed = plugin.databaseHandler.reconcilePayment(id, state, timestamp, resolution, actor, reason)
                        listOf(if (changed) "Reconciliation saved: $id ($resolution). No money transferred."
                            else "Operation changed or does not exist. Read /ptx payments show $id again.")
                    }
                    task
                }
                else -> error("Unknown payment action")
            }
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                val messages = try { action() } catch (failure: Exception) {
                    plugin.logger.err("Payment administration failed: ${failure.message}")
                    listOf("Payment action failed. Read the operation again before retrying: ${failure.message}")
                }
                if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable {
                    if (sender !is Player || sender.isOnline) messages.forEach(sender::sendMessage)
                })
            })
        } catch (_: Exception) {
            sender.sendMessage("Usage: /ptx payments list [page] | show <UUID>")
            sender.sendMessage("/ptx payments resolve <UUID> <state> <updatedAt> <NO_DEBIT|REFUND_CONFIRMED> confirm <evidence/reason 8–120 chars>")
        }
    }

    fun suggest(args: Array<String>): List<String> = when (args.size) {
        1 -> listOf("list", "show", "resolve").filter { it.startsWith(args[0], ignoreCase = true) }
        5 -> if (args.firstOrNull().equals("resolve", ignoreCase = true)) {
            listOf("NO_DEBIT", "REFUND_CONFIRMED").filter { it.startsWith(args[4], ignoreCase = true) }
        } else emptyList()
        6 -> if (args.firstOrNull().equals("resolve", ignoreCase = true) &&
            "confirm".startsWith(args[5], ignoreCase = true)) listOf("confirm") else emptyList()
        else -> emptyList()
    }
}
